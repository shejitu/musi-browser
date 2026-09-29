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
import static android.view.ViewGroup.LayoutParams;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 慕思浏览器 — 主页：功能入口（电视直播 / 电影点播 / 浏览器 / 设置）。
 * 暗色 TV 风格：顶部时间+标题，中间大卡片入口，焦点高亮。
 */
public class HomeActivity extends Activity {

    private LinearLayout root;
    private TextView clock;
    private LinearLayout cardRow;
    private FrameLayout contentHost;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private static final int CARD_COUNT = 6;
    private final Runnable clockTick = new Runnable() {
        @Override public void run() {
            if (clock != null)
                clock.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()));
            handler.postDelayed(this, 15000);
        }
    };

    interface Launcher { void launch(); }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF191926);
        root.setPadding(dp(48), dp(28), dp(48), dp(28));
        setContentView(root);

        // ===== 顶栏：标题（左）+ 时间（右）=====
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(top, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(this);
        title.setText("慕思浏览器");
        title.setTextColor(Color.WHITE);
        title.setTextSize(30);
        top.addView(title, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

        clock = new TextView(this);
        clock.setTextColor(0xFFB0B0C0);
        clock.setTextSize(20);
        top.addView(clock, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

        // 副标题 + 代理入口
        LinearLayout subRow = new LinearLayout(this);
        subRow.setOrientation(LinearLayout.HORIZONTAL);
        subRow.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(subRow, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        TextView subtitle = new TextView(this);
        subtitle.setText("直播 · 点播 · 浏览 · 一站直达");
        subtitle.setTextColor(0xFF808090);
        subtitle.setTextSize(14);
        subtitle.setPadding(0, dp(6), dp(20), dp(24));
        subRow.addView(subtitle, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

        TextView proxyBtn = new TextView(this);
        proxyBtn.setText("⚡ 代理" + (ProxyActivity.enabled(this) ? ": " + ProxyActivity.host(this) + ":" + ProxyActivity.port(this) : "（未启用）"));
        proxyBtn.setTextColor(0xFFFFB74D);
        proxyBtn.setTextSize(14);
        proxyBtn.setPadding(dp(20), dp(6), dp(20), dp(6));
        proxyBtn.setBackgroundColor(0x33FFB74D);
        proxyBtn.setOnClickListener(v -> ProxyActivity.launch(this));
        subRow.addView(proxyBtn, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

        // ===== 功能卡片行 =====
        cardRow = new LinearLayout(this);
        cardRow.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(cardRow, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        addCard("📺", "电视直播", "央视/卫视 · 打开即看", 0xFF1E3A5F, new Launcher() {
            @Override public void launch() { MainActivity.launch(HomeActivity.this); }
        });
        addCard("📚", "仓库管理", "TVBox 单仓/多仓 · 拉取配置", 0xFF3A1F33, new Launcher() {
            @Override public void launch() { ReposActivity.launch(HomeActivity.this); }
        });
        addCard("🎬", "影视点播", "海报墙 · 搜索 · 选集", 0xFF3D2B1F, new Launcher() {
            @Override public void launch() { RepoPickerActivity.launch(HomeActivity.this); }
        });
        addCard("📁", "本地文件", "Download/download · 播放/安装", 0xFF33301F, new Launcher() {
            @Override public void launch() { FilesActivity.launch(HomeActivity.this); }
        });
        addCard("⚙️", "设置", "频道源 · 关于", 0xFF2B2B3D, new Launcher() {
            @Override public void launch() { SettingsActivity.launch(HomeActivity.this); }
        });
        addCard("🌐", "网页浏览", "任意网址 · Bing搜索", 0xFF1F3326, new Launcher() {
            @Override public void launch() { MainActivity.launchBrowserPlain(HomeActivity.this); }
        });

        // ===== 底部提示 =====
        TextView tip = new TextView(this);
        tip.setText("◀ ▶ 选择   OK 确认   局域网控制: http://" + LiteServer.localIp(this) + ":8080");
        tip.setTextColor(0xFF606070);
        tip.setTextSize(13);
        tip.setPadding(0, dp(40), 0, 0);
        tip.setGravity(Gravity.CENTER);
        root.addView(tip, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        contentHost = new FrameLayout(this);
        root.addView(contentHost, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));

        // 初始焦点给第一张卡片
        cardRow.post(() -> { if (!cardRow.hasFocus() && cardRow.getChildCount() > 0) cardRow.getChildAt(0).requestFocus(); });
        handler.post(clockTick);
        startServer();
    }

    private void addCard(String icon, String name, String desc, int bgColor, final Launcher l) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setBackgroundColor(bgColor);
        card.setTag(R.id.card_bg, bgColor);   // 保存原始背景色
        int m = dp(10);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(220), 1f);
        lp.setMargins(m, 0, m, 0);
        card.setLayoutParams(lp);

        TextView ic = new TextView(this);
        ic.setText(icon);
        ic.setTextSize(44);
        ic.setGravity(Gravity.CENTER);
        card.addView(ic, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        TextView nm = new TextView(this);
        nm.setText(name);
        nm.setTextColor(Color.WHITE);
        nm.setTextSize(20);
        nm.setGravity(Gravity.CENTER);
        nm.setPadding(0, dp(12), 0, 0);
        card.addView(nm, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        TextView de = new TextView(this);
        de.setText(desc);
        de.setTextColor(0xFF9090A0);
        de.setTextSize(13);
        de.setGravity(Gravity.CENTER);
        de.setPadding(0, dp(4), 0, 0);
        card.addView(de, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        card.setFocusable(true);
        card.setOnClickListener(v -> l.launch());
        // 焦点即高亮：系统焦点移动驱动视觉，OK 键由系统派发给聚焦卡片
        card.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                ((LinearLayout) v).setBackgroundColor(0xFF4FC3F7);
                v.setScaleX(1.08f); v.setScaleY(1.08f); v.setAlpha(1f);
            } else {
                Object bg = v.getTag(R.id.card_bg);
                ((LinearLayout) v).setBackgroundColor(bg instanceof Integer ? (Integer) bg : 0xFF2B2B3D);
                v.setScaleX(1f); v.setScaleY(1f); v.setAlpha(0.9f);
            }
        });
        card.setTag(l);
        cardRow.addView(card);
    }

    private void startServer() {
        LiteServer server = new LiteServer(this, url -> {
            // 从电脑推送：直接进浏览器打开
            android.content.Intent it = new android.content.Intent(this, MainActivity.class);
            it.putExtra("browser_url", url);
            startActivity(it);
        });
        server.start();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // 方向/OK 全部交给系统焦点机制处理，不再手动拦截
        return super.onKeyDown(keyCode, event);
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    @Override
    protected void onDestroy() { handler.removeCallbacks(clockTick); super.onDestroy(); }
}
