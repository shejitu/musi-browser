package com.mytv.lite;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Color;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/**
 * 浏览器模块：WebView + 搜索框 + 视频嗅探。
 * 检测到页面里的 m3u8/mp4 视频流时弹出"▶ 播放"，交给主播放器全屏点播。
 */
public class BrowserView extends FrameLayout {

    public interface OnPlayVideo { void play(String url, String title); }

    private WebView web;
    private EditText searchBox;
    private TextView statusChip;
    private String pendingVideoUrl = null;
    private String pendingVideoTitle = null;
    private final OnPlayVideo callback;
    private static final String[] VIDEO_EXT = {".m3u8", ".mp4", ".flv", ".ts", ".mkv", ".avi"};

    @SuppressLint("SetJavaScriptEnabled")
    public BrowserView(Activity act, OnPlayVideo onPlay) {
        super(act);
        this.callback = onPlay;
        setBackgroundColor(Color.BLACK);

        LinearLayout root = new LinearLayout(act);
        root.setOrientation(LinearLayout.VERTICAL);
        addView(root, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        // 顶栏：搜索框 + 播放按钮
        LinearLayout bar = new LinearLayout(act);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setPadding(16, 12, 16, 12);
        bar.setBackgroundColor(0xE6101010);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(bar, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        searchBox = new EditText(act);
        searchBox.setHint("输入网址或搜索关键词");
        searchBox.setTextSize(14);
        searchBox.setSingleLine(true);
        searchBox.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        LinearLayout.LayoutParams sbLp = new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        sbLp.setMargins(0, 0, 16, 0);
        bar.addView(searchBox, sbLp);
        searchBox.setOnEditorActionListener((v, actionId, ev) -> { go(); return true; });

        Button playBtn = new Button(act);
        playBtn.setText("▶ 播放");
        playBtn.setTextSize(14);
        playBtn.setEnabled(false);
        playBtn.setOnClickListener(v -> playPending());
        bar.addView(playBtn, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        playBtn.setTag("play");

        statusChip = new TextView(act);
        statusChip.setTextColor(0xFF4FC3F7);
        statusChip.setTextSize(12);
        statusChip.setPadding(16, 4, 16, 8);
        root.addView(statusChip, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        web = new WebView(act);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setUserAgentString("Mozilla/5.0 (Linux; Android 10; TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36");
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) { return false; }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView v, int p) {
                statusChip.setText(p < 100 ? "加载中 " + p + "%  " + v.getUrl() : (v.getTitle() == null ? "" : v.getTitle()));
            }
        });
        // 视频嗅探：拦截所有资源请求，发现视频流地址记录下来
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) { return false; }
            @Override public android.webkit.WebResourceResponse shouldInterceptRequest(WebView v, android.webkit.WebResourceRequest r) {
                String u = r.getUrl().toString().toLowerCase();
                for (String ext : VIDEO_EXT) {
                    if (u.contains(ext)) {
                        if (!u.equals(pendingVideoUrl == null ? "" : pendingVideoUrl.toLowerCase())) {
                            pendingVideoUrl = r.getUrl().toString();
                            pendingVideoTitle = v.getTitle();
                            post(() -> {
                                Button b = findViewWithTag("play");
                                if (b != null) { b.setEnabled(true); b.setText("▶ 播放 Found"); b.setTextColor(0xFF81C784); }
                                statusChip.setText("发现视频流！点 [▶ 播放] 观看");
                            });
                        }
                        break;
                    }
                }
                return null;
            }
        });
        root.addView(web, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));
    }

    private void playPending() {
        if (pendingVideoUrl != null) callback.play(pendingVideoUrl, pendingVideoTitle == null ? "视频" : pendingVideoTitle);
    }

    /** 外部直接播放（如内置站点推荐） */
    public void playDirect(String url, String title) { callback.play(url, title); }

    public void go() {
        String q = searchBox.getText().toString().trim();
        if (q.isEmpty()) return;
        pendingVideoUrl = null;
        Button b = findViewWithTag("play");
        if (b != null) { b.setEnabled(false); b.setText("▶ 播放"); b.setTextColor(Color.WHITE); }
        if (q.startsWith("http://") || q.startsWith("https://")) web.loadUrl(q);
        else web.loadUrl("https://www.bing.com/search?q=" + android.net.Uri.encode(q));
    }

    public WebView getWebView() { return web; }

    public boolean handleBack() {
        if (web.canGoBack()) { web.goBack(); return true; }
        return false;
    }
}
