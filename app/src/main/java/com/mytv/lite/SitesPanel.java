package com.mytv.lite;

import android.app.Activity;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** 内置影视站推荐面板：列出可一键打开的公开影视/内容站点。 */
public class SitesPanel {

    static class Site {
        String name, url, desc;
        Site(String n, String u, String d) { name=n; url=u; desc=d; }
    }

    // 公开、知名、免费内容站点（用户可在浏览器里自行搜索更多）
    static final Site[] SITES = {
        new Site("YouTube", "https://www.youtube.com", "全球视频"),
        new Site("Bilibili", "https://www.bilibili.com", "B站"),
        new Site("爱奇艺", "https://www.iqiyi.com", "影视综艺"),
        new Site("腾讯视频", "https://v.qq.com", "影视综艺"),
        new Site("优酷", "https://www.youku.com", "影视综艺"),
        new Site("芒果TV", "https://www.mgtv.com", "影视综艺"),
        new Site("CCTV官网", "https://tv.cctv.com/live/", "央视直播"),
        new Site("Bing搜索", "https://www.bing.com", "通用搜索"),
    };

    public static View build(Activity act, BrowserView browser, Runnable onClose) {
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(40, 30, 40, 30);
        box.setBackgroundColor(0xF2101010);

        TextView title = new TextView(act);
        title.setText("影视站点 — 选择后进入浏览，页面里发现视频流即可点播");
        title.setTextColor(0xFF4FC3F7); title.setTextSize(16);
        title.setPadding(0, 0, 0, 24);
        box.addView(title);

        for (Site s : SITES) {
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

        android.widget.ScrollView sc = new android.widget.ScrollView(act);
        sc.addView(box);
        return sc;
    }
}
