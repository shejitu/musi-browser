package com.mytv.lite;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
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

/**
 * 慕思 6.1：轻量播放页。
 *
 * 只做一件事——把 m3u8/mp4 直链全屏播出来。
 * 6.0 的选集是跳 MainActivity（直播+WebView+站点面板那套），进页面要建 WebView、
 * 还要在主线程拉站点列表，电视上表现为长时间黑屏甚至被系统判成卡死。
 * 这里只起一个 ExoPlayer，几百毫秒就能出画面。
 *
 * 遥控器：OK = 显示/隐藏进度条，返回 = 退出，菜单键 = 切换铺满/完整画面。
 */
public class PlayActivity extends Activity {

    public static final String EXTRA_URL = "url";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_ALTS = "alts";

    public static void launch(Context ctx, String url, String title) {
        launch(ctx, url, title, null);
    }

    public static void launch(Context ctx, String url, String title, java.util.List<String> alts) {
        if (url == null || url.trim().isEmpty()) return;
        android.content.Intent it = new android.content.Intent(ctx, PlayActivity.class);
        it.putExtra(EXTRA_URL, url.trim());
        it.putExtra(EXTRA_TITLE, title == null ? "" : title);
        if (alts != null && !alts.isEmpty()) {
            it.putExtra(EXTRA_ALTS, new java.util.ArrayList<String>(alts));
        }
        ctx.startActivity(it);
    }

    /** 备用地址（同一集在其它线路的地址），主地址播不了就自动换一个 */
    private final java.util.List<String> alts = new java.util.ArrayList<>();
    private String curUrl = "";
    private int altTried = 0;

    private PlayerView playerView;
    private ExoPlayer player;
    private TextView osd;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean controllerShown = true;
    private final Runnable hideOsd = () -> osd.setVisibility(View.GONE);

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        String url = getIntent().getStringExtra(EXTRA_URL);
        String title = getIntent().getStringExtra(EXTRA_TITLE);
        if (url == null) { finish(); return; }
        curUrl = url.trim();
        java.util.ArrayList<String> extra = getIntent().getStringArrayListExtra(EXTRA_ALTS);
        if (extra != null) for (String a : extra) if (a != null && !a.trim().isEmpty()) alts.add(a.trim());

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        playerView = new PlayerView(this);
        playerView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(playerView);

        osd = new TextView(this);
        osd.setTextColor(Color.WHITE);
        osd.setTextSize(20);
        osd.setPadding(dp(28), dp(14), dp(28), dp(14));
        osd.setBackgroundColor(0x99000000);
        FrameLayout.LayoutParams olp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        olp.gravity = Gravity.TOP | Gravity.START;
        olp.setMargins(dp(36), dp(24), 0, 0);
        osd.setLayoutParams(olp);
        root.addView(osd);

        setContentView(root);

        player = new ExoPlayer.Builder(this)
                .setMediaSourceFactory(new DefaultMediaSourceFactory(dataSource()))
                .setLoadControl(new DefaultLoadControl.Builder()
                        .setBufferDurationsMs(3000, 12000, 400, 1200)
                        .build())
                .build();
        playerView.setPlayer(player);
        playerView.setUseController(true);
        playerView.setControllerShowTimeoutMs(4000);
        playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_ZOOM);

        player.addListener(new Player.Listener() {
            @Override public void onPlayerError(PlaybackException e) {
                if (isFinishing() || isDestroyed()) return;
                // 主线路播不了：自动换同一集在其它线路的地址，最多试 3 条
                while (altTried < alts.size()) {
                    String next = alts.get(altTried++);
                    if (next.equals(curUrl)) continue;
                    curUrl = next;
                    showOsd("主线路播不了，正在换线路重试（" + altTried + "/" + alts.size() + "）…", true);
                    player.setMediaItem(MediaItem.fromUri(next));
                    player.prepare();
                    player.setPlayWhenReady(true);
                    return;
                }
                showOsd("✗ 播放失败：" + brief(e.getMessage()) + "\n按返回键换一集 / 换条线路", true);
            }
            @Override public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_READY) {
                    altTried = 0;
                    handler.postDelayed(hideOsd, 2500);
                }
            }
        });

        player.setMediaItem(MediaItem.fromUri(curUrl));
        player.prepare();
        player.setPlayWhenReady(true);

        showOsd("▶ " + (title == null || title.isEmpty() ? "播放中" : title)
                + "\nOK 进度条 · 返回 退出 · 菜单键 切换画面", false);
        playerView.requestFocus();
        handler.postDelayed(hideOsd, 5000);
    }

    private DataSource.Factory dataSource() {
        try {
            return new DefaultDataSource.Factory(this, ProxyActivity.httpFactory(this));
        } catch (Throwable t) {
            return new DefaultDataSource.Factory(this);
        }
    }

    private String brief(String s) {
        if (s == null) return "未知错误";
        return s.length() > 90 ? s.substring(0, 90) + "…" : s;
    }

    private void showOsd(String text, boolean sticky) {
        osd.setText(text);
        osd.setVisibility(View.VISIBLE);
        handler.removeCallbacks(hideOsd);
        if (!sticky) handler.postDelayed(hideOsd, 5000);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) { finish(); return true; }
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            if (controllerShown) { playerView.hideController(); controllerShown = false; }
            else { playerView.showController(); controllerShown = true; }
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_INFO) {
            boolean zoom = playerView.getResizeMode() == AspectRatioFrameLayout.RESIZE_MODE_ZOOM;
            playerView.setResizeMode(zoom ? AspectRatioFrameLayout.RESIZE_MODE_FIT
                    : AspectRatioFrameLayout.RESIZE_MODE_ZOOM);
            showOsd(zoom ? "画面：完整显示（黑边）" : "画面：铺满全屏", false);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (player != null) player.setPlayWhenReady(false);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        try { if (player != null) player.release(); } catch (Throwable ignore) { }
        super.onDestroy();
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
