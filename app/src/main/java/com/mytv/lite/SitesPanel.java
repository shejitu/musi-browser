package com.mytv.lite;

import android.app.Activity;
import android.graphics.Color;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/** 影视站点面板：站点列表来自 assets/site_config.json（兼容 OK浏览器 site_config 格式），支持云端更新覆盖。 */
public class SitesPanel {

    static class Site {
        String name, url, desc;
        Site(String n, String u, String d) { name=n; url=u; desc=d; }
    }

    private static List<Site> cached;

    /** 读取站点：优先云端 site_config.json（改源不重装），否则 assets，失败回退内置。 */
    public static List<Site> loadSites(Activity act) {
        if (cached != null) return cached;
        List<Site> out = new ArrayList<>();
        // 1) 云端（与 assets 同格式，v4.1 起用它替代已删除的 musitv_sites.json）
        try {
            String s = ReposHttp.get("https://raw.githubusercontent.com/shejitu/musi-browser/master/app/src/main/assets/site_config.json", act);
            parseJson(out, s, true);
        } catch (Throwable ignore) { }
        // 2) assets
        if (out.isEmpty()) {
            try {
                InputStream is = act.getAssets().open("site_config.json");
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                byte[] buf = new byte[8192]; int n;
                while ((n = is.read(buf)) > 0) bo.write(buf, 0, n);
                is.close();
                parseJson(out, bo.toString("UTF-8"), true);
            } catch (Throwable ignore) { }
        }
        // 3) 内置兜底（与 OK浏览器 v1.3.3 同款 6 站）
        if (out.isEmpty()) {
            out.add(new Site("乌龟影院", "https://www.wuguiyy.com", "影视"));
            out.add(new Site("思古影视", "https://www.siguyy.com", "影视"));
            out.add(new Site("酷壳影视", "https://cokemv.me", "影视"));
            out.add(new Site("乐猪影视", "http://www.lezhutv.com", "影视"));
            out.add(new Site("阿里影视", "https://auete.com", "影视"));
            out.add(new Site("扛把子影院", "https://www.ququmc.com", "影视"));
        }
        cached = out;
        return out;
    }

    /** 兼容两种格式：OK site_config {sites:[{name,url}]} 与 简化数组 [{name,url,desc}] */
    private static void parseJson(List<Site> out, String s, boolean okFormat) {
        if (s == null || s.trim().isEmpty()) return;
        try {
            JSONArray arr;
            if (okFormat) {
                JSONObject root = new JSONObject(s);
                arr = root.optJSONArray("sites");
            } else {
                arr = new JSONArray(s);
            }
            if (arr == null) return;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                String name = o.optString("name", "").trim();
                String url = o.optString("url", "").trim();
                if (name.isEmpty() || url.isEmpty()) continue;
                String desc = o.optString("desc", "影视");
                if (desc.isEmpty()) desc = "影视";
                out.add(new Site(name, url, desc));
            }
        } catch (Throwable ignore) { }
    }

    public static View build(Activity act, BrowserView browser, Runnable onClose) {
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(36, 26, 36, 26);
        box.setBackgroundColor(0xF2101010);

        /* ---------- 推荐影视（6.0 新增，放最上面，遥控器一进来就能选） ---------- */
        box.addView(sectionTitle(act, "🎬 推荐影视　上下键选，OK 进入", 0xFF4FC3F7));

        TextView hint = new TextView(act);
        hint.setText("标「可直接播」的点进去就是海报墙，遥控器一路 OK 到底；\n没标的会用浏览器打开原站。");
        hint.setTextColor(0xFF80809A);
        hint.setTextSize(12);
        hint.setPadding(0, 0, 0, 10);
        box.addView(hint);

        for (final SourceBook.WebSite s : SourceBook.webSites()) {
            Button b = new Button(act);
            b.setText(s.nativePlay() ? ("▶ " + s.name + "　" + s.desc) : ("🌐 " + s.name + "　" + s.desc));
            b.setTextSize(16);
            b.setPadding(22, 12, 22, 12);
            b.setFocusable(true);
            b.setFocusableInTouchMode(true);
            b.setTextColor(s.nativePlay() ? 0xFFFFD180 : Color.WHITE);
            b.setBackgroundColor(0xFF222230);
            b.setOnFocusChangeListener((v, hasF) -> {
                b.setTextColor(hasF ? 0xFFFFB74D : (s.nativePlay() ? 0xFFFFD180 : Color.WHITE));
                b.setBackgroundColor(hasF ? 0x554FC3F7 : 0xFF222230);
            });
            b.setOnClickListener(v -> {
                if (s.nativePlay()) {
                    // 有苹果CMS接口的直接走原生海报墙，比在电视上跑网页省太多
                    VodActivity.launch(act, s.api, s.name);
                } else {
                    browser.getWebView().loadUrl(s.url);
                }
                onClose.run();
            });
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            blp.bottomMargin = 6;
            box.addView(b, blp);
        }

        /* ---------- 我自己配的站点 ---------- */
        box.addView(sectionTitle(act, "📁 我的站点", 0xFF9A9AB4));
        List<Site> sites = loadSites(act);
        for (Site s : sites) {
            Button b = new Button(act);
            b.setText(s.name + "   " + s.desc);
            b.setTextSize(15);
            b.setPadding(22, 10, 22, 10);
            b.setFocusable(true);
            b.setFocusableInTouchMode(true);
            b.setTextColor(Color.WHITE);
            b.setBackgroundColor(0xFF222230);
            b.setOnFocusChangeListener((v, hasF) -> {
                b.setTextColor(hasF ? 0xFFFFB74D : Color.WHITE);
                b.setBackgroundColor(hasF ? 0x554FC3F7 : 0xFF222230);
            });
            b.setOnClickListener(v -> { browser.getWebView().loadUrl(s.url); onClose.run(); });
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            blp.bottomMargin = 4;
            box.addView(b, blp);
        }

        /* ---------- 遥控器指引（轻量，常驻在底部） ---------- */
        box.addView(sectionTitle(act, "🎮 遥控器怎么用", 0xFF9A9AB4));
        for (String line : SourceBook.GUIDE_BROWSER) {
            TextView t = new TextView(act);
            t.setText("· " + line);
            t.setTextColor(0xFF7A7A90);
            t.setTextSize(12);
            t.setLineSpacing(6, 1f);
            t.setPadding(0, 3, 0, 3);
            box.addView(t);
        }

        ScrollView sc = new ScrollView(act);
        sc.addView(box);
        return sc;
    }

    private static TextView sectionTitle(Activity act, String text, int color) {
        TextView t = new TextView(act);
        t.setText(text);
        t.setTextColor(color);
        t.setTextSize(14);
        t.setPadding(0, 16, 0, 8);
        return t;
    }
}
