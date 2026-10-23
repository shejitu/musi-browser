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
        List<Site> sites = loadSites(act);
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(40, 30, 40, 30);
        box.setBackgroundColor(0xF2101010);

        TextView title = new TextView(act);
        title.setText("影视站点 — 进入后点【⚡轻站】或按菜单键，一键转 TV 界面");
        title.setTextColor(0xFF4FC3F7); title.setTextSize(16);
        title.setPadding(0, 0, 0, 24);
        box.addView(title);

        for (Site s : sites) {
            Button b = new Button(act);
            b.setText(s.name + "   " + s.desc);
            b.setTextSize(16);
            b.setPadding(24, 14, 24, 14);
            b.setFocusable(true);
            b.setFocusableInTouchMode(true);
            b.setOnFocusChangeListener((v, hasF) -> {
                b.setTextColor(hasF ? 0xFFFFB74D : Color.WHITE);
                b.setBackgroundColor(hasF ? 0x444FC3F7 : 0xFF222230);
            });
            b.setOnClickListener(v -> { browser.getWebView().loadUrl(s.url); onClose.run(); });
            box.addView(b);
        }

        ScrollView sc = new ScrollView(act);
        sc.addView(box);
        return sc;
    }
}
