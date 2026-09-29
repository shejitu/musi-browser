package com.mytv.lite;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MyTV Lite — 打开即用的电视直播。
 * 频道源内置（assets/channels.json），遥控器：上下换台，菜单键出频道列表，数字键直接跳台。
 */
public class MainActivity extends Activity {

    private ExoPlayer player;
    private PlayerView playerView;
    private LinearLayout channelPanel;
    private TextView osdText;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final List<Channel> channels = new ArrayList<>();
    private final Map<String, List<Integer>> groups = new LinkedHashMap<>();
    private int current = 0;
    private boolean panelVisible = false;
    private StringBuilder digits = new StringBuilder();
    private final Runnable hideOsd = this::hideOsd;
    private final Runnable hidePanel = this::hidePanel;

    static class Channel {
        String name; String group; String url; String logo;
        Channel(String n, String g, String u, String l) { name=n; group=g; url=u; logo=l; }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        playerView = new PlayerView(this);
        playerView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(playerView);

        // OSD：换台时显示频道名
        osdText = new TextView(this);
        osdText.setTextColor(Color.WHITE);
        osdText.setTextSize(28);
        osdText.setPadding(40, 30, 40, 30);
        osdText.setBackgroundColor(0x99000000);
        FrameLayout.LayoutParams osdLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        osdLp.gravity = Gravity.TOP | Gravity.START;
        osdLp.setMargins(40, 40, 0, 0);
        osdText.setLayoutParams(osdLp);
        osdText.setVisibility(View.GONE);
        root.addView(osdText);

        // 频道面板
        channelPanel = new LinearLayout(this);
        channelPanel.setOrientation(LinearLayout.VERTICAL);
        channelPanel.setBackgroundColor(0xE6101010);
        channelPanel.setPadding(30, 30, 30, 30);
        FrameLayout.LayoutParams panelLp = new FrameLayout.LayoutParams(dp(360), ViewGroup.LayoutParams.MATCH_PARENT);
        panelLp.gravity = Gravity.END;
        channelPanel.setLayoutParams(panelLp);
        channelPanel.setVisibility(View.GONE);
        root.addView(channelPanel);

        setContentView(root);

        loadChannels();
        initPlayer();
        if (!channels.isEmpty()) play(current);
        // 首次启动提示如何打开设置
        handler.postDelayed(() -> showOsd("按菜单键(INFO)频道列表 · 按搜索键进入浏览器点播 · OK键设置"), 4000);
        startServer();
    }

    private LinearLayout settingsPanel;
    private boolean settingsVisible = false;

    // ===== 浏览器模式 =====
    private BrowserView browser;
    private LinearLayout sitesHost;
    private boolean browserMode = false;

    private void enterBrowser() {
        if (browser == null) {
            browser = new BrowserView(this, (url, title) -> {
                exitBrowser();
                player.stop();
                player.setMediaItem(androidx.media3.common.MediaItem.fromUri(url));
                player.prepare();
                player.setPlayWhenReady(true);
                showOsd("▶ 点播: " + title);
            });
            ((FrameLayout) findViewById(android.R.id.content)).addView(browser,
                    new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        browserMode = true;
        playerView.setVisibility(View.GONE);
        if (player != null) player.setPlayWhenReady(false);
        browser.setVisibility(View.VISIBLE);
        showSites();
    }

    private void showSites() {
        if (sitesHost == null) {
            sitesHost = new LinearLayout(this);
            sitesHost.setBackgroundColor(0xF2101010);
            ((FrameLayout) findViewById(android.R.id.content)).addView(sitesHost,
                    new FrameLayout.LayoutParams(dp(440), ViewGroup.LayoutParams.MATCH_PARENT));
        }
        sitesHost.removeAllViews();
        sitesHost.addView(SitesPanel.build(this, browser, () -> sitesHost.setVisibility(View.GONE)));
        sitesHost.setVisibility(View.VISIBLE);
    }

    private void exitBrowser() {
        browserMode = false;
        if (browser != null) browser.setVisibility(View.GONE);
        if (sitesHost != null) sitesHost.setVisibility(View.GONE);
        playerView.setVisibility(View.VISIBLE);
        if (player != null) player.setPlayWhenReady(true);
    }

    private void showSettings() {
        if (settingsPanel == null) {
            settingsPanel = new LinearLayout(this);
            settingsPanel.setOrientation(LinearLayout.VERTICAL);
            settingsPanel.setBackgroundColor(0xF2101010);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(420), ViewGroup.LayoutParams.MATCH_PARENT);
            lp.gravity = Gravity.START;
            settingsPanel.setLayoutParams(lp);
            ((FrameLayout) findViewById(android.R.id.content)).addView(settingsPanel);
        }
        settingsPanel.removeAllViews();
        settingsPanel.addView(Sources.buildPanel(this, this::onSourceChanged));
        settingsVisible = true;
        settingsPanel.setVisibility(View.VISIBLE);
    }

    private void onSourceChanged() {
        settingsVisible = false;
        if (settingsPanel != null) settingsPanel.setVisibility(View.GONE);
        reloadChannels();
    }

    private void reloadChannels() {
        new Thread(() -> {
            java.util.List<Channel> fresh = new java.util.ArrayList<>();
            java.util.Map<String, java.util.List<Integer>> freshGroups = new java.util.LinkedHashMap<>();
            try {
                Sources.Source s = Sources.currentSource(this);
                String content = Sources.loadContent(this, s);
                parseM3u(content, fresh, freshGroups);
            } catch (Exception e) {
                handler.post(() -> showOsd("加载频道源失败: " + e.getMessage()));
                return;
            }
            handler.post(() -> {
                channels.clear();
                channels.addAll(fresh);
                groups.clear();
                groups.putAll(freshGroups);
                current = 0;
                if (!channels.isEmpty()) play(0); else showOsd("该源没有频道");
            });
        }).start();
    }

    private void initPlayer() {
        DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
                .setUserAgent("Mozilla/5.0 (Linux; Android TV) MyTV/1.0")
                .setConnectTimeoutMs(8000)
                .setAllowCrossProtocolRedirects(true);
        player = new ExoPlayer.Builder(this)
                .setMediaSourceFactory(new DefaultMediaSourceFactory(http))
                .build();
        playerView.setPlayer(player);
        player.addListener(new Player.Listener() {
            @Override public void onPlayerError(PlaybackException e) {
                showOsd("播放失败：" + channels.get(current).name + "\n自动尝试下一个…");
                handler.removeCallbacks(hideOsd);
                handler.postDelayed(() -> { current = (current+1) % channels.size(); play(current); }, 2000);
            }
        });
    }

    private void play(int idx) {
        if (channels.isEmpty()) { showOsd("没有可用频道"); return; }
        idx = (idx + channels.size()) % channels.size();
        current = idx;
        Channel c = channels.get(idx);
        player.setMediaItem(MediaItem.fromUri(c.url));
        player.prepare();
        player.setPlayWhenReady(true);
        showOsd((idx+1) + ". " + c.name + "  [" + c.group + "]");
    }

    private void showOsd(String text) {
        osdText.setText(text);
        osdText.setVisibility(View.VISIBLE);
        handler.removeCallbacks(hideOsd);
        handler.postDelayed(hideOsd, 3500);
    }
    private void hideOsd() { osdText.setVisibility(View.GONE); }

    private void buildPanel() {
        channelPanel.removeAllViews();
        int currentGroupStart = 0;
        TextView title = new TextView(this);
        title.setText("频道列表  ▲▼选择  OK播放  返回关闭");
        title.setTextColor(0xFF4FC3F7); title.setTextSize(16);
        channelPanel.addView(title);
        for (int i = 0; i < channels.size(); i++) {
            Channel c = channels.get(i);
            TextView tv = new TextView(this);
            tv.setText((i+1) + ". " + c.name);
            tv.setTextSize(15);
            tv.setPadding(16, dp(6), 16, dp(6));
            if (i == current) { tv.setTextColor(0xFFFFB74D); tv.setBackgroundColor(0x33FFFFFF); }
            else tv.setTextColor(Color.WHITE);
            final int idx = i;
            tv.setOnClickListener(v -> { play(idx); hidePanelNow(); });
            channelPanel.addView(tv);
        }
    }

    private void showPanel() {
        buildPanel();
        panelVisible = true;
        channelPanel.setVisibility(View.VISIBLE);
        handler.removeCallbacks(hidePanel);
        handler.postDelayed(hidePanel, 10000);
    }
    private void hidePanelNow() { handler.removeCallbacks(hidePanel); hidePanel(); }
    private void hidePanel() { panelVisible = false; channelPanel.setVisibility(View.GONE); }

    private void handleDigit(char d) {
        digits.append(d);
        int n;
        try { n = Integer.parseInt(digits.toString()); } catch (Exception e) { return; }
        boolean prefixExists = false;
        for (int i = 0; i < channels.size(); i++) {
            String s = String.valueOf(i+1);
            if (s.equals(digits.toString())) { play(i); digits.setLength(0); return; }
            if (s.startsWith(digits.toString())) prefixExists = true;
        }
        if (!prefixExists || digits.length() >= 4) { if (n >= 1 && n <= channels.size()) play(n-1); digits.setLength(0); }
        else showOsd("频道号: " + digits);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (browserMode) {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                if (sitesHost != null && sitesHost.getVisibility() == View.VISIBLE) { sitesHost.setVisibility(View.GONE); return true; }
                if (!browser.handleBack()) exitBrowser();
                return true;
            }
            return super.onKeyDown(keyCode, event);
        }
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
                if (panelVisible) break;
                play(current - 1); return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (panelVisible) break;
                play(current + 1); return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (!panelVisible) { play(keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ? current + 1 : current - 1); return true; }
                break;
            case KeyEvent.KEYCODE_MENU:
            case KeyEvent.KEYCODE_INFO:
                if (panelVisible) hidePanelNow(); else showPanel();
                return true;
            case KeyEvent.KEYCODE_BACK:
                if (settingsVisible) { settingsVisible = false; settingsPanel.setVisibility(View.GONE); return true; }
                if (panelVisible) { hidePanelNow(); return true; }
                break;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                if (!panelVisible && !settingsVisible) { showSettings(); return true; }
                break;
            case KeyEvent.KEYCODE_SEARCH:
                enterBrowser();
                return true;
            case KeyEvent.KEYCODE_0: case KeyEvent.KEYCODE_1: case KeyEvent.KEYCODE_2:
            case KeyEvent.KEYCODE_3: case KeyEvent.KEYCODE_4: case KeyEvent.KEYCODE_5:
            case KeyEvent.KEYCODE_6: case KeyEvent.KEYCODE_7: case KeyEvent.KEYCODE_8:
            case KeyEvent.KEYCODE_9:
                handleDigit((char) ('0' + keyCode - KeyEvent.KEYCODE_0)); return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    /** 解析 m3u 文本 → channels/groups（内置与远程源共用） */
    private void parseM3u(String content, List<Channel> out, Map<String, List<Integer>> outGroups) {
        String name = null, group = "其他", logo = null;
        for (String line0 : content.split("\n")) {
            String line = line0.trim();
            if (line.startsWith("#EXTINF")) {
                name = null; logo = null; group = "其他";
                int g1 = line.indexOf("group-title=\"");
                if (g1 >= 0) { int g2 = line.indexOf('"', g1+13); group = line.substring(g1+13, g2); }
                int l1 = line.indexOf("tvg-logo=\"");
                if (l1 >= 0) { int l2 = line.indexOf('"', l1+10); logo = line.substring(l1+10, l2); }
                int c = line.lastIndexOf(',');
                if (c >= 0) name = line.substring(c+1).trim();
            } else if (!line.isEmpty() && !line.startsWith("#")) {
                if (name != null && line.length() > 5) {
                    out.add(new Channel(name, group, line, logo));
                    List<Integer> list = outGroups.get(group);
                    if (list == null) { list = new ArrayList<>(); outGroups.put(group, list); }
                    list.add(out.size()-1);
                }
                name = null;
            }
        }
    }

    /** 解析内置 channels.m3u（assets），无需外部依赖 */
    private void loadChannels() {
        try {
            Sources.Source s = Sources.currentSource(this);
            parseM3u(Sources.loadContent(this, s), channels, groups);
        } catch (Exception e) {
            showOsd("读取频道列表失败: " + e.getMessage());
        }
    }

    // ===== 局域网控制服务器 =====
    private LiteServer server;

    private void startServer() {
        server = new LiteServer(this, url -> {
            // 电脑推送网址：切到浏览器模式打开
            if (!browserMode) enterBrowser();
            if (sitesHost != null) sitesHost.setVisibility(View.GONE);
            if (!url.startsWith("http")) url = "https://www.bing.com/search?q=" + android.net.Uri.encode(url);
            browser.getWebView().loadUrl(url);
            showOsd("收到推送: " + url);
        });
        server.start();
        handler.postDelayed(() -> showOsd("局域网控制已开启: " + LiteServer.localIp(this) + ":8080"), 1500);
    }

    @Override
    protected void onDestroy() {
        if (server != null) server.stop();
        if (player != null) player.release();
        super.onDestroy();
    }
}
