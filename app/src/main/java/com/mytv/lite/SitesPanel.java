package com.mytv.lite;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
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

/**
 * 网页模式的站点面板。
 *
 * 6.1 改了两处：
 *   1. 「我的站点」改成后台加载 —— 原来在主线程拉 GitHub 配置，网一慢整屏卡住十几秒，
 *      电视系统会把它当死机。现在推荐站点立刻可见，自己的站点加载好再补上。
 *   2. 面板不再是一次性的：顶栏「📁 站点」/ 菜单键随时叫回来，点完一个站还能接着点下一个。
 */
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
        // 1) 云端（与 assets 同格式）
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
        // 3) 内置兜底
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

    public static View build(final Activity act, final BrowserView browser, final Runnable onClose) {
        final LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(act, 26), dp(act, 18), dp(act, 26), dp(act, 24));
        box.setBackgroundColor(0xF2101010);

        /* ---------- 顶部：关闭 + 说明 ---------- */
        TextView close = new TextView(act);
        close.setText("✕ 收起面板（返回键）");
        close.setTextColor(0xFF9A9AB4);
        close.setTextSize(13);
        close.setPadding(0, 0, 0, dp(act, 8));
        close.setFocusable(true);
        close.setFocusableInTouchMode(true);
        close.setClickable(true);
        close.setOnFocusChangeListener((v, f) -> close.setTextColor(f ? Color.WHITE : 0xFF9A9AB4));
        close.setOnClickListener(v -> onClose.run());
        box.addView(close);

        /* ---------- 推荐影视（6.0 起放最上面） ---------- */
        box.addView(sectionTitle(act, "🎬 推荐影视　上下选，OK 进入", 0xFF4FC3F7));

        TextView hint = new TextView(act);
        hint.setText("「▶」开头的自带片源接口，点进去就是海报墙，遥控器一路 OK 到底；\n"
                + "「🌐」开头的是网页站，进去后按顶栏【⚡轻站】可转成电视界面。");
        hint.setTextColor(0xFF80809A);
        hint.setTextSize(12);
        hint.setLineSpacing(dp(act, 2), 1f);
        hint.setPadding(0, 0, 0, dp(act, 10));
        box.addView(hint);

        for (final SourceBook.WebSite s : SourceBook.webSites()) {
            Button b = new Button(act);
            b.setText(s.nativePlay() ? ("▶ " + s.name + "　" + s.desc) : ("🌐 " + s.name + "　" + s.desc));
            b.setTextSize(15);
            b.setAllCaps(false);
            b.setPadding(dp(act, 16), dp(act, 10), dp(act, 16), dp(act, 10));
            b.setFocusable(true);
            b.setFocusableInTouchMode(true);
            b.setTextColor(s.nativePlay() ? 0xFFFFD180 : Color.WHITE);
            GradientDrawable n = round(0xFF222230);
            GradientDrawable f = round(0xFF2C5D75);
            b.setBackground(n);
            b.setOnFocusChangeListener((v, hasF) -> {
                b.setTextColor(hasF ? Color.WHITE : (s.nativePlay() ? 0xFFFFD180 : Color.WHITE));
                b.setBackground(hasF ? f : n);
            });
            b.setOnClickListener(v -> {
                if (s.nativePlay()) {
                    // 有苹果CMS接口的直接走原生海报墙，比在电视上跑网页省太多
                    VodActivity.launch(act, s.api, s.name);
                    onClose.run();
                } else {
                    if (browser != null && browser.getWebView() != null) browser.getWebView().loadUrl(s.url);
                    onClose.run();
                }
            });
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            blp.bottomMargin = dp(act, 6);
            box.addView(b, blp);
        }

        /* ---------- 我自己配的站点（后台加载，先占位） ---------- */
        box.addView(sectionTitle(act, "📁 我的站点", 0xFF9A9AB4));

        final LinearLayout mine = new LinearLayout(act);
        mine.setOrientation(LinearLayout.VERTICAL);
        box.addView(mine);

        final TextView loading = new TextView(act);
        loading.setText("站点配置加载中…");
        loading.setTextColor(0xFF6A6A80);
        loading.setTextSize(12);
        loading.setPadding(0, dp(act, 4), 0, dp(act, 4));
        mine.addView(loading);

        new Thread(() -> {
            final List<Site> sites = loadSites(act);
            act.runOnUiThread(() -> {
                mine.removeAllViews();
                for (final Site s : sites) {
                    Button b = new Button(act);
                    b.setText(s.name + "   " + s.desc);
                    b.setTextSize(14);
                    b.setAllCaps(false);
                    b.setPadding(dp(act, 16), dp(act, 8), dp(act, 16), dp(act, 8));
                    b.setFocusable(true);
                    b.setFocusableInTouchMode(true);
                    b.setTextColor(Color.WHITE);
                    GradientDrawable n = round(0xFF222230);
                    GradientDrawable f = round(0xFF2C5D75);
                    b.setBackground(n);
                    b.setOnFocusChangeListener((v, hasF) -> {
                        b.setTextColor(hasF ? Color.WHITE : Color.WHITE);
                        b.setBackground(hasF ? f : n);
                    });
                    b.setOnClickListener(v -> {
                        if (browser != null && browser.getWebView() != null) browser.getWebView().loadUrl(s.url);
                        onClose.run();
                    });
                    LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                    blp.bottomMargin = dp(act, 4);
                    mine.addView(b, blp);
                }
            });
        }, "sites-load").start();

        /* ---------- 遥控器指引 ---------- */
        box.addView(sectionTitle(act, "🎮 遥控器怎么用", 0xFF9A9AB4));
        for (String line : SourceBook.GUIDE_BROWSER) {
            TextView t = new TextView(act);
            t.setText("· " + line);
            t.setTextColor(0xFF7A7A90);
            t.setTextSize(12);
            t.setLineSpacing(dp(act, 3), 1f);
            t.setPadding(0, dp(act, 2), 0, dp(act, 2));
            box.addView(t);
        }

        ScrollView sc = new ScrollView(act);
        sc.setVerticalScrollBarEnabled(false);
        sc.addView(box);
        return sc;
    }

    private static TextView sectionTitle(Activity act, String text, int color) {
        TextView t = new TextView(act);
        t.setText(text);
        t.setTextColor(color);
        t.setTextSize(14);
        t.setPadding(0, dp(act, 14), 0, dp(act, 6));
        return t;
    }

    private static GradientDrawable round(int fill) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setColor(fill);
        d.setCornerRadius(8);
        return d;
    }

    private static int dp(Activity act, int v) {
        return Math.round(v * act.getResources().getDisplayMetrics().density);
    }
}
