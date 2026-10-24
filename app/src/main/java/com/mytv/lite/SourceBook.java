package com.mytv.lite;

import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;

/**
 * 慕思 6.2：首页数据源清单。
 *
 * 首页的分工变了 —— 第 1 页是「豆瓣高分」，第 2 页起才是影视源。
 *
 * 为什么要这样分：
 *   豆瓣的高分榜是**发现**，榜单权威、海报好看、一眼就知道该看什么；
 *   影视源是**播放**，同一部片在不同源里的画质/线路差别很大。
 *   6.0/6.1 一上来就让用户面对某个片源的最新更新，那是"仓库视角"不是"观众视角"。
 *   所以 6.2 把两者分开：第 1 页负责"看什么"，第 2 页起负责"在哪播"。
 *   豆瓣海报点进去之后，会自动拿片名去下面这几个源里搜，搜到就直进选集。
 *
 * 影视源全部取自「小盒子多仓」(http://xhztv.top/dc/)，2026-10-24 逐个实测：
 * 不只是看接口通不通，还真的把「选集 → m3u8 直链」拉下来验证过前几集能取到 #EXTM3U。
 *
 *   玄珠   https://jihulab.com/xuanzhuapp/xzys/-/raw/main/xzvip.json
 *   小马   https://szyyds.cn/tv/x.json
 *   dxawi  https://dxawi.github.io/0/0.json
 *
 * 三点注意：
 * 1. 这三个仓里绝大多数站点是 TVBox 的 JS 爬虫（type=3），慕思跑不了，所以只挑标准
 *    苹果CMS JSON 接口（type=1）的站点，这类是纯 HTTP，不依赖任何爬虫内核。
 * 2. 小马仓里的「索尼[直连]」(suoniapi.com) 按要求不启用。
 * 3. 乌龟影院 / 思古影视 / 酷壳影视 / 乐猪影视 / 阿里影视 / 扛把子影院
 *    这 6 个站 6.2 已整体移除：站点本身打不开或改版，留着只是让人白点。
 */
public class SourceBook {

    /** 单个影视源 */
    public static class Src {
        public final String name;
        public final String api;
        public Src(String n, String a) { name = n; api = a; }
    }

    /** 一个分组 = 首页的一个 tab */
    public static class Group {

        /** 苹果CMS 接口源：能直接出海报墙、能直接播 */
        public static final int KIND_CMS = 0;
        /** 豆瓣榜单：只能发现，不能播；点进去会自动去 KIND_CMS 组里找源 */
        public static final int KIND_DOUBAN = 1;

        public final String label;
        public final int kind;
        public final String dbTag;    // 豆瓣组专用：热门 / 最新 / 豆瓣高分 / 冷门佳片
        public final String dbSort;   // recommend=按热度 / time=按时间
        public final List<Src> list = new ArrayList<>();

        Group(String l) { this(l, KIND_CMS, "", ""); }

        Group(String l, int k, String tag, String sort) {
            label = l; kind = k; dbTag = tag; dbSort = sort;
        }

        Group add(String n, String a) { list.add(new Src(n, a)); return this; }

        public boolean isDouban() { return kind == KIND_DOUBAN; }
    }

    private static List<Group> groups;

    public static List<Group> groups() {
        if (groups != null) return groups;
        List<Group> g = new ArrayList<>();

        /*
         * 第 1 页：豆瓣高分榜。
         * 豆瓣的 tag 是固定值，「豆瓣高分」+ recommend 就是豆瓣站内那个高分排序，
         * 比自建"精选榜"可信，也不用维护。
         */
        g.add(new Group("🎯 豆瓣高分", Group.KIND_DOUBAN, "豆瓣高分", "recommend"));

        /* 第 2 页：玄珠仓。红牛实测 m3u8 直链可播（前 3 集全通），放第一位 */
        g.add(new Group("🦊 玄珠")
                .add("红牛", "https://www.hongniuzy2.com/api.php/provide/vod/")
                .add("极速", "https://jszyapi.com/api.php/provide/vod/")
                .add("量子", "https://cj.lziapi.com/api.php/provide/vod/")
                .add("非凡", "http://cj.ffzyapi.com/api.php/provide/vod/"));

        /* 第 3 页：小马仓。光速实测可播；含 suoniapi.com（索尼），按要求排除 */
        g.add(new Group("🐴 小马")
                .add("光速", "https://api.guangsuapi.com/api.php/provide/vod/")
                .add("电影天堂", "http://caiji.dyttzyapi.com/api.php/provide/vod")
                .add("百度", "https://api.apibdzy.com/api.php/provide/vod"));

        /* 第 4 页：dxawi 仓。暴风实测可播，IK 次之 */
        g.add(new Group("🐹 dxawi")
                .add("暴风", "https://bfzyapi.com/api.php/provide/vod")
                .add("IK", "https://ikunzyapi.com/api.php/provide/vod/from/ikm3u8/"));

        /* 下面两个站点自带苹果CMS接口，直接影响原生海报墙，比跑网页省资源 */
        g.add(new Group("🎬 影视星球")
                .add("影视星球", "https://ysxq.cc/api.php/provide/vod/"));

        g.add(new Group("⚡ 飞快")
                .add("飞快影视", "https://feikuai.in/api.php/provide/vod/"));

        groups = g;
        return groups;
    }

    public static int size() { return groups().size(); }

    public static Group group(int i) {
        List<Group> all = groups();
        if (i < 0 || i >= all.size()) return all.get(0);
        return all.get(i);
    }

    /** 所有可直连的影视源（扁平），豆瓣找片源时按这个顺序试。 */
    public static List<Src> allSrcs() {
        List<Src> out = new ArrayList<>();
        for (Group g : groups()) if (!g.isDouban()) out.addAll(g.list);
        return out;
    }

    /* ==================== 豆瓣 → 片源 ==================== */

    public interface Found {
        /** @param vodId 命中的条目 id，可直接进选集 */
        void ok(String api, String siteName, String vodId, String title);

        void fail(String msg);
    }

    private static final Handler UI = new Handler(Looper.getMainLooper());

    /**
     * 拿一个片名，去内置的所有影视源里找能播的那一条。
     *
     * 顺序就是首页 tab 的顺序（红牛 → 极速 → … → 飞快），第一个搜到结果的直接用，
     * 所以绝大多数片在前两三个源里就能命中，不用等全部扫完。
     *
     * 只在内置源里找、不去扫用户仓库：内置源是实测过能播的，
     * 仓库里那堆 type=3 爬虫源扫一遍又慢又常常是空的。
     *
     * 一个坑：CMS 源的搜索是模糊匹配，搜「年会不能停！」会先返回「年会不能停！2」。
     * 所以这里不比"有没有结果"，而是给每个结果打相似度分，只认片名真的对得上的那条
     * （详见 {@link #score}）。宁可说没找到，也不给用户播错片。
     */
    public static void findPlayable(final String keyword, final Found cb) {
        if (keyword == null || keyword.trim().isEmpty()) {
            UI.post(() -> cb.fail("片名为空"));
            return;
        }
        final String kw = keyword.trim();
        new Thread(() -> {
            int tried = 0;
            for (Src s : allSrcs()) {
                tried++;
                try {
                    List<Poster> hits = CmsSource.search(s.api, kw);
                    if (hits == null || hits.isEmpty()) continue;

                    Poster best = null;
                    int bestScore = 0;
                    for (Poster p : hits) {
                        int sc = score(kw, p.title);
                        if (sc > bestScore) { bestScore = sc; best = p; }
                    }
                    // 低于 80 分基本是续集/同名前缀的别的片子，换下一个源再找
                    if (best == null || bestScore < 80) continue;

                    final String api = best.apiBase;
                    final String name = s.name;
                    final String id = best.id;
                    final String title = best.title;
                    UI.post(() -> cb.ok(api, name, id, title));
                    return;
                } catch (Throwable ignore) {
                    // 这个源没搜到/超时，换下一个
                }
            }
            final String msg = "试了 " + tried + " 个源都没找到「" + kw + "」";
            UI.post(() -> cb.fail(msg));
        }, "find-playable").start();
    }

    /** 去掉标点空格，只留文字和数字，用来比对片名。 */
    private static String norm(String s) {
        if (s == null) return "";
        return s.replaceAll("[\\s·・:：!！?？,，.。'\"“”‘’()（）\\[\\]【】《》<>\\-—_/、|]", "")
                .toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * 片名相似度：
     *   100 = 去掉标点后完全一样
     *    85 = 一方包含另一方（如「唐人街探案」vs「唐人街探案 合集」）
     *    60 = 只是前缀相同、后面跟的是数字（「年会不能停」vs「年会不能停2」）—— 这多半是续集，不算命中
     *    20 = 无关
     */
    private static int score(String kw, String title) {
        String a = norm(kw), b = norm(title);
        if (a.isEmpty() || b.isEmpty()) return 0;
        if (a.equals(b)) return 100;
        if (b.contains(a) || a.contains(b)) {
            // 多出来的部分如果是纯数字/第N部/II 这种，就是续集，不算同一部
            String extra = b.length() > a.length() ? b.replace(a, "") : a.replace(b, "");
            if (extra.matches("^[0-9]{1,4}$") || extra.matches("^第.{1,3}[部季集]$")
                    || extra.matches("^[ivx]{1,4}$")) {
                return 60;
            }
            return 85;
        }
        return 20;
    }

    /* ==================== 网页端推荐影视 ==================== */

    /**
     * 网页端展示的推荐站点。
     * api 非空 = 该站有苹果CMS接口，点一下直接进原生海报墙（遥控器友好）；
     * api 为空 = 只能用浏览器打开原站，进去后可按「⚡轻站」尝试转电视界面。
     */
    public static class WebSite {
        public final String name, url, desc, api;
        public WebSite(String n, String u, String d, String a) {
            name = n; url = u; desc = d; api = a;
        }
        public boolean nativePlay() { return api != null && !api.isEmpty(); }
    }

    private static List<WebSite> webSites;

    public static List<WebSite> webSites() {
        if (webSites != null) return webSites;
        List<WebSite> l = new ArrayList<>();
        l.add(new WebSite("影视星球", "https://ysxq.cc/", "42 万部 · 海报墙直连",
                "https://ysxq.cc/api.php/provide/vod/"));
        l.add(new WebSite("飞快影视", "https://feikuai.in/", "22 万部 · 海报墙直连",
                "https://feikuai.in/api.php/provide/vod/"));
        l.add(new WebSite("飞快 · 电影频道", "https://feikuai.in/vodtype/1.html", "只看电影 · 网页打开", ""));
        l.add(new WebSite("肥嘟影视", "https://fdzys.com/", "网页打开", ""));
        l.add(new WebSite("低端影视", "https://ddys.io/", "网页打开 · 海外剧多", ""));
        l.add(new WebSite("快快影视", "https://www.kkys02.com/", "网页打开 · 备用", ""));
        webSites = l;
        return webSites;
    }

    /* ==================== 遥控器指引 ==================== */

    /** 首页首次进入时显示一次，按 OK / 返回即消失，不再打扰。 */
    public static final String[] GUIDE_HOME = {
            "① 第 1 页「豆瓣高分」挑片，上下左右选，OK 打开",
            "② 豆瓣海报点 OK 会自动去影视源里找片源，找到直接进选集",
            "③ 第 2 页起是玄珠 / 小马 / dxawi，一个源没有就换下一个",
            "④ 选集里先用左右键挑「线路」，再选第几集，OK 直接播",
            "⑤ 返回键退回上一层；播放中按返回键回选集"
    };

    /** 浏览器面板里常驻的提示。 */
    public static final String[] GUIDE_BROWSER = {
            "点顶栏【📁 站点】或按菜单键，随时叫回这个面板换站",
            "网页退到第一页后再按返回，也会回到这个面板",
            "带「▶」的站点点进去就是海报墙，遥控器一路 OK 到底",
            "网页站进去后按顶栏【⚡轻站】可尝试转成电视界面",
            "面板开着时按返回 = 退出浏览器回首页"
    };
}
