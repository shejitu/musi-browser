package com.mytv.lite;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * 轻站引擎（LiteEngine）—— 慕思浏览器 v4.1 的核心。
 *
 * 职责：把一个「重」的影视网页一键轻量化成 TV 界面。
 *
 * 为什么不需要 Rhino / dex 引擎（对比 OK浏览器）：
 * OK浏览器把轻站脚本放在独立 JS 引擎里跑，所以必须打包 Rhino + module.jar；
 * 本实现直接注入当前 WebView —— WebView 本身就是 JS 引擎，还额外拥有已登录的
 * Cookie、localStorage、DOM 与真实页面上下文（反爬更容易通过）。代价是脚本
 * 必须在页面里可执行，收益是体积与复杂度大幅下降。
 *
 * 三级策略（成本从低到高，逐级降级）：
 *   1. 站点规则（rule）：assets/lite_rules.json 内置，或用户/云端导入，可用
 *      selector 精确抓取剧集，也可用 js 片段完全自定义；
 *   2. 通用嗅探（generic）：扫 DOM 的 video/source、正则扫整个 outerHTML、
 *      收集 iframe —— 大多数影视站无需写规则即可用；
 *   3. 逐集解析（resolve）：点某一集后由隐藏 WebView 打开该集页面并嗅探
 *      m3u8/mp4，命中即播。这是「点开就能看」的最后一公里。
 */
public class LiteEngine {

    private static final String TAG = "LiteEngine";
    public static final String ASSET_RULES = "lite_rules.json";
    private static final String USER_RULES = "lite_rules_user.json";
    private static final String REMOTE_RULES = "https://raw.githubusercontent.com/shejitu/musi-browser/master/app/src/main/assets/lite_rules.json";

    /** 一条剧集/选集条目 */
    public static class Item {
        public String name;
        public String url;
        public Item(String n, String u) { name = n; url = u; }
    }

    /** 一次轻量化的结果 */
    public static class Result {
        public boolean ok = false;
        public String err = "";
        public String title = "";
        public String poster = "";
        public String pageUrl = "";
        public String via = "generic";     // generic / rule / rule-js
        public String ruleName = "通用";
        public final List<Item> items = new ArrayList<Item>();
        public final List<String> media = new ArrayList<String>();
        public final List<String> frames = new ArrayList<String>();
        /** 面板上给用户看的一行说明 */
        public String summary = "";
    }

    /** 站点规则 */
    public static class Rule {
        public String id = "";
        public String name = "";
        public List<String> hosts = new ArrayList<String>();
        public String selector = "";
        public String js = "";
        public boolean builtin = true;
    }

    // ================= 规则加载 =================

    private static List<Rule> cache;

    public static synchronized List<Rule> rules(Context ctx) {
        if (cache != null) return cache;
        List<Rule> out = new ArrayList<Rule>();
        // 内置
        try { parseRules(out, readAsset(ctx, ASSET_RULES), true); }
        catch (Throwable e) { Log.w(TAG, "asset rules: " + e); }
        // 用户导入（覆盖同 id）
        try {
            File f = new File(ctx.getFilesDir(), USER_RULES);
            if (f.exists()) parseRules(out, readFile(f), false);
        } catch (Throwable e) { Log.w(TAG, "user rules: " + e); }
        cache = out;
        return out;
    }

    public static synchronized void invalidate() { cache = null; }

    /** 按 URL 的 host 做后缀匹配，取最长命中（最具体的规则优先） */
    public static Rule match(Context ctx, String url) {
        String host = hostOf(url);
        if (host.isEmpty()) return null;
        Rule best = null;
        int bestLen = -1;
        for (Rule r : rules(ctx)) {
            for (String h : r.hosts) {
                String hh = h.toLowerCase();
                if (hh.isEmpty()) continue;
                if ((host.equals(hh) || host.endsWith("." + hh)) && hh.length() > bestLen) {
                    best = r; bestLen = hh.length();
                }
            }
        }
        return best;
    }

    public static String hostOf(String url) {
        try {
            if (url == null) return "";
            String u = url.trim();
            int p = u.indexOf("://");
            if (p > 0) u = u.substring(p + 3);
            int s = u.indexOf('/');
            if (s >= 0) u = u.substring(0, s);
            int c = u.indexOf(':');
            if (c >= 0) u = u.substring(0, c);
            return u.toLowerCase();
        } catch (Throwable e) { return ""; }
    }

    private static void parseRules(List<Rule> out, String json, boolean builtin) {
        if (json == null) return;
        try {
            JSONObject root = new JSONObject(json);
            JSONArray arr = root.optJSONArray("rules");
            if (arr == null) return;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                Rule r = new Rule();
                r.id = o.optString("id", "r" + i);
                r.name = o.optString("name", r.id);
                r.selector = o.optString("selector", "");
                r.js = o.optString("js", "");
                r.builtin = builtin;
                JSONArray hs = o.optJSONArray("hosts");
                if (hs != null) for (int k = 0; k < hs.length(); k++) r.hosts.add(hs.optString(k, "").toLowerCase());
                if (r.hosts.isEmpty()) continue;
                // 同 id 覆盖
                for (int k = out.size() - 1; k >= 0; k--) if (out.get(k).id.equals(r.id)) out.remove(k);
                out.add(r);
            }
        } catch (Throwable e) { Log.w(TAG, "parseRules: " + e); }
    }

    /** 从网址导入规则（追加/覆盖），返回导入条数 */
    public static int importRules(Context ctx, String url) throws Exception {
        String json = httpGet(url);
        List<Rule> incoming = new ArrayList<Rule>();
        parseRules(incoming, json, false);
        if (incoming.isEmpty()) throw new Exception("该地址没有可用规则（需 {rules:[{hosts:[...]}]}）");
        JSONObject root = new JSONObject();
        root.put("version", 1);
        JSONArray arr = new JSONArray();
        for (Rule r : incoming) {
            JSONObject o = new JSONObject();
            o.put("id", r.id); o.put("name", r.name);
            o.put("selector", r.selector); o.put("js", r.js);
            JSONArray hs = new JSONArray();
            for (String h : r.hosts) hs.put(h);
            o.put("hosts", hs);
            arr.put(o);
        }
        root.put("rules", arr);
        File f = new File(ctx.getFilesDir(), USER_RULES);
        FileOutputStream fo = new FileOutputStream(f);
        fo.write(root.toString().getBytes("UTF-8"));
        fo.close();
        invalidate();
        return incoming.size();
    }

    /** 从官方仓库拉最新规则，写成本地规则文件（不改内置 assets） */
    public static int pullOfficialRules(Context ctx) throws Exception {
        return importRules(ctx, REMOTE_RULES);
    }

    public static void clearUserRules(Context ctx) {
        File f = new File(ctx.getFilesDir(), USER_RULES);
        if (f.exists()) f.delete();
        invalidate();
    }

    public static boolean hasUserRules(Context ctx) {
        return new File(ctx.getFilesDir(), USER_RULES).exists();
    }

    // ================= 注入脚本 =================

    /**
     * 生成探测脚本。返回值是 JSON 字符串（evaluateJavascript 的 result 是带引号的
     * JSON 字面量，调用方需先 JSONTokener 解一层）。
     */
    public static String buildProbeJs(Rule rule) {
        String sel = rule == null ? "" : rule.selector;
        String js = rule == null ? "" : rule.js;
        StringBuilder b = new StringBuilder(4096);
        b.append("(function(){try{");
        b.append("var SEL=").append(JSONObject.quote(sel)).append(";");
        b.append("var RJS=").append(JSONObject.quote(js)).append(";");
        // ---- 工具 ----
        b.append("function abs(u){if(!u)return '';u=(''+u).trim();if(!u)return '';")
                .append("if(u.indexOf('data:')===0||u.indexOf('blob:')===0)return '';")
                .append("try{return new URL(u,location.href).href}catch(e){return u}}");
        b.append("function push(a,v){v=abs(v);if(!v)return;if(a.indexOf(v)<0)a.push(v)}");
        b.append("var RES={ok:true,title:(document.title||'').trim(),url:location.href,poster:'',items:[],media:[],frames:[],via:'generic',ruleErr:''};");
        // ---- 海报 ----
        b.append("try{var m=document.querySelector('meta[property=\\'og:image\\'],meta[name=\\'og:image\\']');")
                .append("if(m&&m.content)RES.poster=abs(m.content);}catch(e){}");
        b.append("if(!RES.poster){var im=document.querySelector('.detail-pic img,.pic img,.poster img,img.pic,img.lazyload,img[data-original]');")
                .append("if(im)RES.poster=abs(im.getAttribute('data-original')||im.getAttribute('data-src')||im.src);}");
        // ---- DOM 里的 video/source ----
        b.append("try{var vs=document.querySelectorAll('video,audio,source');")
                .append("for(var i=0;i<vs.length;i++){var e=vs[i];push(RES.media,e.currentSrc||e.src||e.getAttribute('src'));}}catch(e){}");
        // ---- iframe（很多站是 iframe 播放器）----
        b.append("try{var fr=document.querySelectorAll('iframe');")
                .append("for(var i=0;i<fr.length&&i<8;i++){var s=fr[i].getAttribute('src')||fr[i].getAttribute('data-src');if(s)push(RES.frames,s);}}catch(e){}");
        // ---- 全页正则（播放器配置常内联在 HTML 里）----
        b.append("var html='';try{html=document.documentElement.outerHTML||''}catch(e){}");
        b.append("try{var re=/(https?:\\/\\/[^\\s\\\"'<>\\\\]+?\\.(?:m3u8|mp4|flv)(?:\\?[^\\s\\\"'<>\\\\]*)?)/gi,mm,g=0;")
                .append("while((mm=re.exec(html))!==null&&g++<60)push(RES.media,mm[1]);}catch(e){}");
        // ---- 选集 ----
        b.append("var seen={};function addItem(n,u){u=abs(u);if(!u||seen[u])return;")
                .append("seen[u]=1;if(RES.items.length<300)RES.items.push({name:n,url:u});}");
        b.append("function scan(q,strict){var ns=[];try{ns=document.querySelectorAll(q)}catch(e){return 0}");
        b.append("var PLAY=/[\\/\\-_.](play|vodplay|watch|view|detail|episode|bf|bfzy|v_)\\b|\\/vod\\/|\\/v\\/|集|第.{0,3}集/i;");
        b.append("var cnt=0;for(var i=0;i<ns.length&&i<1200;i++){var a=ns[i];")
                .append("var t=(a.textContent||'').replace(/[\\s\\u00a0]+/g,' ').trim();")
                .append("var h=a.getAttribute('href')||'';")
                .append("if(!h||h.charAt(0)==='#'||h.indexOf('javascript')===0)continue;")
                .append("if(!t||t.length>26)continue;")
                .append("if(strict&&!PLAY.test(h)&&!PLAY.test(t))continue;")
                .append("addItem(t,h);cnt++;}return cnt;}");
        b.append("if(SEL){if(scan(SEL,false)===0){")
                .append("if(scan('a[href]',true)===0)scan('a[href]',false);}}else{")
                .append("if(scan('a[href]',true)===0)scan('a[href]',false);}");
        // ---- 站点自定义 JS ----
        b.append("if(RJS){try{var f=new Function('document','location','RES',RJS);var r=f(document,location,RES);")
                .append("if(r){if(r.title)RES.title=(''+r.title).trim();")
                .append("if(r.poster)RES.poster=abs(r.poster);")
                .append("if(r.media&&r.media.length){RES.media=[];for(var i=0;i<r.media.length;i++)push(RES.media,r.media[i]);}")
                .append("if(r.items&&r.items.length){RES.items=[];seen={};for(var i=0;i<r.items.length;i++){var it=r.items[i];")
                .append("addItem(it.name||it.title||('第'+(i+1)+'集'),it.url||it.href||'')}}")
                .append("RES.via='rule-js';}}catch(e){RES.ruleErr=''+e}}");
        b.append("RES.media=RES.media.slice(0,20);RES.frames=RES.frames.slice(0,8);");
        b.append("return JSON.stringify(RES);");
        b.append("}catch(e){return JSON.stringify({ok:false,err:''+e});}})()");
        return b.toString();
    }

    /** 解析注入脚本的返回值（先剥掉 evaluateJavascript 外层引号） */
    public static Result parse(String raw) {
        Result r = new Result();
        if (raw == null) { r.err = "无返回"; return r; }
        String s = raw.trim();
        if (s.isEmpty() || "null".equals(s)) { r.err = "脚本无返回"; return r; }
        try {
            if (s.charAt(0) == '"') s = new org.json.JSONTokener(s).nextValue().toString();
            JSONObject o = new JSONObject(s);
            r.ok = o.optBoolean("ok", false);
            r.err = o.optString("err", "");
            r.title = o.optString("title", "");
            r.poster = o.optString("poster", "");
            r.pageUrl = o.optString("url", "");
            r.via = o.optString("via", "generic");
            String ruleErr = o.optString("ruleErr", "");
            if (!ruleErr.isEmpty()) r.err = "规则脚本出错: " + ruleErr;
            JSONArray items = o.optJSONArray("items");
            if (items != null) {
                for (int i = 0; i < items.length(); i++) {
                    JSONObject it = items.optJSONObject(i);
                    if (it == null) continue;
                    String n = it.optString("name", "").trim();
                    String u = it.optString("url", "").trim();
                    if (u.isEmpty()) continue;
                    if (n.isEmpty()) n = "第" + (i + 1) + "集";
                    r.items.add(new Item(n, u));
                }
            }
            JSONArray media = o.optJSONArray("media");
            if (media != null) for (int i = 0; i < media.length(); i++) {
                String u = media.optString(i, "").trim();
                if (!u.isEmpty() && !r.media.contains(u)) r.media.add(u);
            }
            JSONArray frames = o.optJSONArray("frames");
            if (frames != null) for (int i = 0; i < frames.length(); i++) {
                String u = frames.optString(i, "").trim();
                if (!u.isEmpty() && !r.frames.contains(u)) r.frames.add(u);
            }
            // 串流优先：m3u8 > mp4 > 其他（.ts 分片放最后）
            sortMedia(r.media);
            r.summary = buildSummary(r);
        } catch (Throwable e) {
            r.ok = false;
            r.err = "解析失败: " + e;
        }
        return r;
    }

    private static void sortMedia(List<String> media) {
        try {
            java.util.Collections.sort(media, new java.util.Comparator<String>() {
                int rank(String u) {
                    String s = u.toLowerCase();
                    if (s.contains(".m3u8")) return 0;
                    if (s.contains(".mp4")) return 1;
                    if (s.contains(".flv")) return 2;
                    if (s.contains(".mkv")) return 3;
                    if (s.contains(".ts")) return 5;
                    return 4;
                }
                @Override public int compare(String a, String b) { return rank(a) - rank(b); }
            });
        } catch (Throwable ignore) { }
    }

    private static String buildSummary(Result r) {
        StringBuilder b = new StringBuilder();
        if (!r.media.isEmpty()) b.append("直链 ").append(r.media.size()).append(" 条");
        if (!r.items.isEmpty()) b.append(b.length() > 0 ? " · " : "").append("选集 ").append(r.items.size()).append(" 个");
        if (!r.frames.isEmpty()) b.append(b.length() > 0 ? " · " : "").append("内嵌播放器 ").append(r.frames.size()).append(" 个");
        if (b.length() == 0) b.append("未找到视频，可点「逐集解析」或换站点规则");
        b.append(" · 来源: ").append("rule-js".equals(r.via) ? "站点脚本" : ("rule".equals(r.via) ? "站点规则" : "通用嗅探"));
        return b.toString();
    }

    /** 判断一个地址是不是媒体流（供逐集解析使用） */
    public static boolean isMedia(String url) {
        if (url == null) return false;
        String p = url.toLowerCase().split("[?#]")[0];
        return p.endsWith(".m3u8") || p.endsWith(".mp4") || p.endsWith(".flv")
                || p.endsWith(".mkv") || p.endsWith(".mp3") || p.endsWith(".m4a");
    }

    /** 播放页常见的「点一下才播」兜底注入 */
    public static String buildKickJs() {
        return "(function(){try{"
                + "var vs=document.querySelectorAll('video');"
                + "for(var i=0;i<vs.length;i++){try{vs[i].muted=true;var p=vs[i].play();if(p&&p.catch)p.catch(function(){});}catch(e){}}"
                + "var c=['.play-btn','.vjs-big-play-button','.dplayer-play-icon','.xgplayer-start','#play','.player-play'];"
                + "for(var j=0;j<c.length;j++){var n=document.querySelector(c[j]);if(n){try{n.click()}catch(e){}}}"
                + "return 'ok';}catch(e){return 'err';}})()";
    }

    // ================= 小工具 =================

    private static String readAsset(Context ctx, String name) throws Exception {
        InputStream is = ctx.getAssets().open(name);
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) > 0) bo.write(buf, 0, n);
        is.close();
        return bo.toString("UTF-8");
    }

    private static String readFile(File f) throws Exception {
        java.io.FileInputStream in = new java.io.FileInputStream(f);
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        in.close();
        return bo.toString("UTF-8");
    }

    private static String httpGet(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(12000);
        c.setReadTimeout(15000);
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; TV) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36");
        c.setInstanceFollowRedirects(true);
        int code = c.getResponseCode();
        InputStream is = code >= 400 ? c.getErrorStream() : c.getInputStream();
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        if (is != null) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) bo.write(buf, 0, n);
            is.close();
        }
        c.disconnect();
        if (code >= 400) throw new Exception("HTTP " + code);
        return bo.toString("UTF-8");
    }
}
