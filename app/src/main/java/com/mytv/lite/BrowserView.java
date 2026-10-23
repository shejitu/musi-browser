package com.mytv.lite;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Color;
import android.view.Gravity;
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

/**
 * 浏览器模块：WebView + 搜索框 + 视频嗅探 + 轻站入口。
 *
 * 三件事：
 *   1. 打开任意网址（或 Bing 搜索）；
 *   2. 边翻页边嗅探 m3u8/mp4，发现就点亮「▶ 播放」；
 *   3. v4.1 新增「⚡轻站」：对当前页执行 LiteEngine 探测脚本（内置规则优先），
 *      把结果交给 LiteActivity 渲染成 TV 界面。遥控器按【菜单键】等效。
 */
public class BrowserView extends FrameLayout {

    public interface OnPlayVideo { void play(String url, String title); }
    /** 轻站：把探测结果（JSON 原文）交给轻站面板 */
    public interface OnLite { void open(String url, String title, String probeJson); }

    private WebView web;
    private EditText searchBox;
    private TextView statusChip;
    private Button playBtn;
    private Button liteBtn;
    private String pendingVideoUrl = null;
    private String pendingVideoTitle = null;
    private final OnPlayVideo callback;
    private final OnLite liteCallback;
    private static final String[] VIDEO_EXT = {".m3u8", ".mp4", ".flv", ".ts", ".mkv", ".avi"};

    @SuppressLint("SetJavaScriptEnabled")
    public BrowserView(Activity act, OnPlayVideo onPlay, OnLite onLite) {
        super(act);
        this.callback = onPlay;
        this.liteCallback = onLite;
        setBackgroundColor(Color.BLACK);

        LinearLayout root = new LinearLayout(act);
        root.setOrientation(LinearLayout.VERTICAL);
        addView(root, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        // 顶栏：搜索框 + 轻站 + 播放
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
        sbLp.setMargins(0, 0, 12, 0);
        bar.addView(searchBox, sbLp);
        searchBox.setOnEditorActionListener((v, actionId, ev) -> { go(); return true; });

        liteBtn = new Button(act);
        liteBtn.setText("⚡ 轻站");
        liteBtn.setTextSize(14);
        liteBtn.setAllCaps(false);
        liteBtn.setTextColor(0xFF101018);
        liteBtn.setBackgroundColor(0xFFFFB74D);
        liteBtn.setOnClickListener(v -> runLite());
        LinearLayout.LayoutParams liteLp = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        liteLp.setMargins(0, 0, 12, 0);
        bar.addView(liteBtn, liteLp);

        playBtn = new Button(act);
        playBtn.setText("▶ 播放");
        playBtn.setTextSize(14);
        playBtn.setAllCaps(false);
        playBtn.setEnabled(false);
        playBtn.setOnClickListener(v -> playPending());
        bar.addView(playBtn, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

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
        ProxyActivity.applyToWebView(web, act);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) { return false; }

            @Override public android.webkit.WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                try {
                    String u = r.getUrl().toString();
                    String lower = u.toLowerCase();
                    String path = lower.split("[?#]")[0];
                    for (String ext : VIDEO_EXT) {
                        if (path.endsWith(ext)) {
                            if (!lower.equals(pendingVideoUrl == null ? "" : pendingVideoUrl.toLowerCase())) {
                                pendingVideoUrl = u;
                                pendingVideoTitle = v.getTitle();
                                post(BrowserView.this::markFound);
                            }
                            break;
                        }
                    }
                } catch (Throwable ignore) { }
                return null;
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView v, int p) {
                statusChip.setText(p < 100
                        ? "加载中 " + p + "%  " + v.getUrl()
                        : (v.getTitle() == null ? "" : v.getTitle()) + "   【按 ⚡轻站 / 菜单键 转 TV 界面】");
            }
        });
        root.addView(web, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));
    }

    private void markFound() {
        if (playBtn == null) return;
        playBtn.setEnabled(true);
        playBtn.setText("▶ 播放 已发现");
        playBtn.setTextColor(0xFF81C784);
        statusChip.setText("发现视频流！点 [▶ 播放] 直接看，或点 [⚡轻站] 转 TV 界面");
    }

    /** v4.0: 释放 WebView（stopLoading → about:blank → destroy），onDestroy 调用防泄漏。 */
    public void destroyWebView() {
        if (web != null) {
            web.stopLoading();
            web.loadUrl("about:blank");
            web.destroy();
            web = null;
        }
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
        if (playBtn != null) {
            playBtn.setEnabled(false);
            playBtn.setText("▶ 播放");
            playBtn.setTextColor(Color.WHITE);
        }
        if (q.startsWith("http://") || q.startsWith("https://")) web.loadUrl(q);
        else web.loadUrl("https://www.bing.com/search?q=" + android.net.Uri.encode(q));
    }

    /**
     * 一键轻站：在当前（已登录、已渲染）的页面上跑探测脚本。
     * 直接把 WebView 当 JS 引擎用，省掉独立引擎与脚本下发链路。
     */
    public void runLite() {
        if (web == null) return;
        final String url = web.getUrl();
        final String title = web.getTitle() == null ? "" : web.getTitle();
        if (url == null || url.isEmpty() || url.startsWith("about:")) {
            statusChip.setText("先打开一个影视页面，再用轻站");
            return;
        }
        LiteEngine.Rule rule = LiteEngine.match(getContext(), url);
        statusChip.setText(rule == null ? "轻站：通用嗅探中…" : ("轻站：按规则「" + rule.name + "」解析中…"));
        liteBtn.setEnabled(false);
        web.evaluateJavascript(LiteEngine.buildProbeJs(rule), raw -> {
            if (liteBtn != null) liteBtn.setEnabled(true);
            LiteEngine.Result r = LiteEngine.parse(raw);
            statusChip.setText(r.summary);
            if (liteCallback != null) liteCallback.open(url, title, raw);
        });
    }

    public WebView getWebView() { return web; }

    public boolean handleBack() {
        if (web.canGoBack()) { web.goBack(); return true; }
        return false;
    }
}
