package com.mytv.lite;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * 轻站面板 —— 「一键把影视网页轻量化成 TV 界面」的落地页。
 *
 * 界面分两态：
 *   面板态：左边海报、右边标题 + 选集网格 + 操作按钮，全部可用遥控器方向键操作；
 *   播放态：ExoPlayer 全屏，按返回键回到面板（再按一次退出）。
 *
 * 播放地址来源三条，命中即用：
 *   ① 探测结果里已有的直链（页面内联/已请求过）；
 *   ② 选集 → 隐藏 WebView 打开该集 → 嗅探 m3u8/mp4；
 *   ③ 嗅探不到时注入「点一下播放器」脚本再嗅一次。
 */
public class LiteActivity extends Activity {

    public static final String EXTRA_URL = "url";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_PROBE = "probe";

    public static void launch(android.content.Context ctx, String url, String title, String probeJson) {
        android.content.Intent it = new android.content.Intent(ctx, LiteActivity.class);
        it.putExtra(EXTRA_URL, url);
        it.putExtra(EXTRA_TITLE, title == null ? "" : title);
        if (probeJson != null) it.putExtra(EXTRA_PROBE, probeJson);
        ctx.startActivity(it);
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private FrameLayout root;
    private LinearLayout panel;
    private PlayerView playerView;
    private ExoPlayer player;
    private WebView resolver;
    private TextView status;
    private TextView titleView;
    private TextView summaryView;
    private ImageView posterView;
    private GridLayout grid;
    private Button playBtn;
    private LinearLayout btnRow;

    private String pageUrl = "";
    private String pageTitle = "";
    private LiteEngine.Result result;
    private LiteEngine.Rule rule;
    private boolean playing = false;
    private String pendingResolveName = "";
    private int resolveTick = 0;
    private final List<Runnable> resolveTimers = new ArrayList<Runnable>();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        pageUrl = getIntent().getStringExtra(EXTRA_URL);
        pageTitle = getIntent().getStringExtra(EXTRA_TITLE);
        if (pageUrl == null) pageUrl = "";
        if (pageTitle == null) pageTitle = "";
        rule = LiteEngine.match(this, pageUrl);

        buildUi();

        String probe = getIntent().getStringExtra(EXTRA_PROBE);
        if (probe != null && !probe.isEmpty()) {
            apply(LiteEngine.parse(probe));
        } else {
            status.setText("正在打开页面并轻量化…");
            startResolve(pageUrl, "");
        }
    }

    // ================= 界面 =================

    private void buildUi() {
        root = new FrameLayout(this);
        root.setBackgroundColor(0xFF14141C);
        setContentView(root);

        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.HORIZONTAL);
        panel.setPadding(dp(36), dp(28), dp(36), dp(24));
        root.addView(panel, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // ---- 左：海报 + 来源 ----
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        panel.addView(left, new LinearLayout.LayoutParams(dp(280), ViewGroup.LayoutParams.WRAP_CONTENT));

        posterView = new ImageView(this);
        posterView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        posterView.setBackgroundColor(0xFF23232F);
        left.addView(posterView, new LinearLayout.LayoutParams(dp(268), dp(380)));

        TextView src = new TextView(this);
        src.setText(rule == null ? "通用轻量化 · 未匹配站点规则" : ("规则: " + rule.name));
        src.setTextColor(0xFF8A8AA0);
        src.setTextSize(12);
        src.setPadding(0, dp(10), 0, 0);
        left.addView(src);

        // ---- 右：标题 + 选集 + 按钮 ----
        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        rlp.setMargins(dp(28), 0, 0, 0);
        panel.addView(right, rlp);

        titleView = new TextView(this);
        titleView.setText(pageTitle.isEmpty() ? "轻量化结果" : pageTitle);
        titleView.setTextColor(Color.WHITE);
        titleView.setTextSize(24);
        titleView.setMaxLines(2);
        right.addView(titleView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        summaryView = new TextView(this);
        summaryView.setTextColor(0xFF4FC3F7);
        summaryView.setTextSize(13);
        summaryView.setPadding(0, dp(8), 0, dp(12));
        right.addView(summaryView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView sc = new ScrollView(this);
        right.addView(sc, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        grid = new GridLayout(this);
        grid.setColumnCount(5);
        sc.addView(grid, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setPadding(0, dp(12), 0, 0);
        right.addView(btnRow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        playBtn = actionButton("▶ 直接播放", 0xFF1E5F3A, v -> playFirstMedia());
        addBtn(playBtn);
        addBtn(actionButton("⟳ 重新解析", 0xFF2B2B3D, v -> {
            status.setText("正在重新解析当前页面…");
            startResolve(pageUrl, "");
        }));
        addBtn(actionButton("🌐 回网页", 0xFF2B2B3D, v -> finish()));

        status = new TextView(this);
        status.setTextColor(0xFFFFB74D);
        status.setTextSize(13);
        status.setPadding(0, dp(6), 0, 0);
        root.addView(status);

        playerView = new PlayerView(this);
        playerView.setVisibility(View.GONE);
        playerView.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        playerView.setBackgroundColor(Color.BLACK);
        playerView.setUseController(true);
        playerView.setControllerShowTimeoutMs(4000);
        root.addView(playerView);

        initPlayer();
        buildResolver();
    }

    private Button actionButton(String text, int bg, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(14);
        b.setAllCaps(false);
        b.setBackgroundColor(bg);
        b.setTextColor(Color.WHITE);
        b.setFocusable(true);
        b.setFocusableInTouchMode(true);
        b.setOnClickListener(l);
        b.setOnFocusChangeListener((v, has) -> {
            b.setBackgroundColor(has ? 0xFF4FC3F7 : bg);
            b.setTextColor(has ? 0xFF101018 : Color.WHITE);
        });
        return b;
    }

    private void addBtn(Button b) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, dp(12), 0);
        btnRow.addView(b, lp);
    }

    private void initPlayer() {
        DataSource.Factory http = ProxyActivity.httpFactory(this);
        DataSource.Factory ds = new DefaultDataSource.Factory(this, http);
        DefaultLoadControl lc = new DefaultLoadControl.Builder()
                .setBufferDurationsMs(4000, 12000, 500, 1500).build();
        player = new ExoPlayer.Builder(this)
                .setMediaSourceFactory(new DefaultMediaSourceFactory(ds))
                .setLoadControl(lc).build();
        playerView.setPlayer(player);
        playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_ZOOM);
        player.addListener(new Player.Listener() {
            @Override public void onPlayerError(PlaybackException e) {
                status.setText("播放失败：" + e.getMessage() + "（可换选集或回网页）");
                showPanel(false);
            }
        });
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void buildResolver() {
        resolver = new WebView(this);
        WebSettings s = resolver.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setUserAgentString("Mozilla/5.0 (Linux; Android 10; TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36");
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        ProxyActivity.applyToWebView(resolver, this);
        resolver.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) { return false; }
            @Override public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                try {
                    String u = r.getUrl().toString();
                    if (LiteEngine.isMedia(u)) {
                        final String f = u;
                        handler.post(() -> onMediaFound(f));
                    }
                } catch (Throwable ignore) { }
                return null;
            }
            @Override public void onPageFinished(WebView v, String url) {
                handler.post(() -> probeResolver(v));
            }
        });
        // 隐藏：1x1 放在屏幕外，避免抢焦点
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(1, 1);
        lp.gravity = Gravity.BOTTOM | Gravity.END;
        resolver.setLayoutParams(lp);
        resolver.setVisibility(View.INVISIBLE);
        root.addView(resolver);
    }

    // ================= 探测与解析 =================

    private void startResolve(String url, String name) {
        pendingResolveName = name;
        resolveTick = 0;
        clearTimers();
        if (resolver == null) buildResolver();
        if (url != null && !url.isEmpty()) resolver.loadUrl(url);
        schedule(1500, this::probeResolver);
        schedule(4000, () -> kickAndProbe(""));
        schedule(9000, () -> kickAndProbe("iframe"));
        schedule(16000, () -> {
            if (!playing) {
                status.setText("没能自动取到播放地址。可换个选集，或点「回网页」手动点播放后再按轻站。");
            }
        });
    }

    private void schedule(int ms, Runnable r) {
        final Runnable[] holder = new Runnable[1];
        holder[0] = () -> {
            try { r.run(); } catch (Throwable ignore) { }
            resolveTimers.remove(holder[0]);
        };
        resolveTimers.add(holder[0]);
        handler.postDelayed(holder[0], ms);
    }

    private void clearTimers() {
        for (Runnable r : resolveTimers) handler.removeCallbacks(r);
        resolveTimers.clear();
    }

    private void kickAndProbe(String mode) {
        if (playing || resolver == null) return;
        if ("iframe".equals(mode) && result != null && !result.frames.isEmpty()) {
            // 页面是内嵌播放器：直接把第一个 iframe 载进来
            resolver.loadUrl(result.frames.get(0));
            return;
        }
        resolver.evaluateJavascript(LiteEngine.buildKickJs(), null);
        handler.postDelayed(this::probeResolver, 1200);
    }

    /** 在解析用的 WebView 上跑一次探测脚本 */
    private void probeResolver() { probeResolver(resolver); }

    private void probeResolver(WebView v) {
        if (v == null || playing) return;
        v.evaluateJavascript(LiteEngine.buildProbeJs(rule), raw -> {
            if (playing) return;
            LiteEngine.Result r = LiteEngine.parse(raw);
            if (!r.media.isEmpty()) {
                onMediaFound(r.media.get(0));
            } else if (!r.items.isEmpty() && (result == null || result.items.isEmpty())) {
                apply(r);
            }
        });
    }

    /** 拿到可播地址 */
    private void onMediaFound(String url) {
        if (playing) return;
        // .ts 分片很可能是预加载碎片，先放着等更好的候选
        String low = url.toLowerCase();
        if (low.contains(".ts") && resolveTick < 2) { resolveTick++; return; }
        clearTimers();
        play(url, pendingResolveName.isEmpty() ? pageTitle : pendingResolveName);
    }

    private void apply(LiteEngine.Result r) {
        result = r;
        if (r == null) return;
        if (!r.ok && !r.err.isEmpty()) status.setText("轻量化失败：" + r.err);
        else status.setText("");

        if (r.title != null && !r.title.isEmpty()) {
            pageTitle = r.title;
            titleView.setText(r.title);
        }
        summaryView.setText(r.summary);
        if (r.poster != null && !r.poster.isEmpty()) loadPoster(r.poster);

        grid.removeAllViews();
        if (r.items.isEmpty()) {
            TextView t = new TextView(this);
            t.setText(r.media.isEmpty()
                    ? "没有解析到选集或直链。\n提示：进入具体的播放页再点「轻站」，命中率更高。"
                    : "已取到直链，点下方「▶ 直接播放」。");
            t.setTextColor(0xFF9A9AB0);
            t.setTextSize(14);
            grid.addView(t);
        } else {
            for (int i = 0; i < r.items.size(); i++) {
                final LiteEngine.Item it = r.items.get(i);
                grid.addView(episodeButton(it.name, it.url));
            }
        }
        playBtn.setEnabled(!r.media.isEmpty());
        playBtn.setAlpha(r.media.isEmpty() ? 0.4f : 1f);

        // 首焦点：优先选集第一个，否则播放按钮
        if (grid.getChildCount() > 0 && grid.getChildAt(0).isFocusable()) grid.getChildAt(0).requestFocus();
        else playBtn.requestFocus();

        // 页面只有一个直链且没有选集 → 直接播
        if (r.items.isEmpty() && !r.media.isEmpty() && r.frames.isEmpty()) {
            status.setText("发现直链，正在播放…");
        }
    }

    private Button episodeButton(final String name, final String url) {
        Button b = new Button(this);
        b.setText(name);
        b.setTextSize(13);
        b.setAllCaps(false);
        b.setTextColor(0xFFE0E0F0);
        b.setBackgroundColor(0xFF262634);
        b.setFocusable(true);
        b.setFocusableInTouchMode(true);
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.width = dp(104);
        lp.height = dp(52);
        lp.setMargins(dp(5), dp(5), dp(5), dp(5));
        b.setLayoutParams(lp);
        b.setOnClickListener(v -> {
            status.setText("正在解析：" + name + " …");
            showPanel(false);
            startResolve(url, name);
        });
        b.setOnFocusChangeListener((v, has) -> {
            b.setBackgroundColor(has ? 0xFF4FC3F7 : 0xFF262634);
            b.setTextColor(has ? 0xFF101018 : 0xFFE0E0F0);
        });
        return b;
    }

    private void loadPoster(final String url) {
        new Thread(() -> {
            Bitmap bm = null;
            InputStream in = null;
            HttpURLConnection c = null;
            try {
                c = (HttpURLConnection) new URL(url).openConnection();
                c.setConnectTimeout(8000);
                c.setReadTimeout(8000);
                c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; TV) AppleWebKit/537.36 Chrome/120.0");
                c.setRequestProperty("Referer", pageUrl);
                in = c.getInputStream();
                bm = BitmapFactory.decodeStream(in);
            } catch (Throwable ignore) {
            } finally {
                try { if (in != null) in.close(); } catch (Throwable ignore) { }
                try { if (c != null) c.disconnect(); } catch (Throwable ignore) { }
            }
            final Bitmap f = bm;
            if (f != null) handler.post(() -> posterView.setImageBitmap(f));
        }).start();
    }

    // ================= 播放 =================

    private void playFirstMedia() {
        if (result == null || result.media.isEmpty()) {
            status.setText("还没有直链，先选一集解析。");
            return;
        }
        play(result.media.get(0), pageTitle);
    }

    private void play(String url, String name) {
        playing = true;
        clearTimers();
        status.setText("");
        panel.setVisibility(View.GONE);
        playerView.setVisibility(View.VISIBLE);
        playerView.requestFocus();
        player.setMediaItem(MediaItem.fromUri(url));
        player.prepare();
        player.setPlayWhenReady(true);
        playerView.showController();
        android.util.Log.i("LiteActivity", "play " + name + " -> " + url);
    }

    private void showPanel(boolean stop) {
        if (stop) { playing = false; player.stop(); }
        playerView.setVisibility(View.GONE);
        panel.setVisibility(View.VISIBLE);
        if (grid.getChildCount() > 0 && grid.getChildAt(0).isFocusable()) grid.getChildAt(0).requestFocus();
        else playBtn.requestFocus();
    }

    // ================= 按键 =================

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (playing) { showPanel(true); return true; }
            finish();
            return true;
        }
        if (playing) {
            if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_INFO) {
                showPanel(true);
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                if (playerView.isControllerVisible()) playerView.hideController();
                else playerView.showController();
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        clearTimers();
        try { if (player != null) player.release(); } catch (Throwable ignore) { }
        try {
            if (resolver != null) {
                resolver.stopLoading();
                resolver.loadUrl("about:blank");
                resolver.destroy();
                resolver = null;
            }
        } catch (Throwable ignore) { }
        super.onDestroy();
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
