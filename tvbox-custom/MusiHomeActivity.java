package com.github.tvbox.osc.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.util.HawkConfig;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import static android.view.ViewGroup.LayoutParams;

/**
 * 慕思影视主界面 — 卡片式入口（仿慕思浏览器），TVBox 引擎作内核。
 * 卡片：影视点播(引擎首页) / 电视台直播 / 配置地址(TVBox设置) / 网页浏览 / 网盘 / 历史收藏。
 */
public class MusiHomeActivity extends Activity {

    private TextView tvDate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF0D0D14);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        root.addView(col, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        // 标题
        TextView title = new TextView(this);
        title.setText("慕思影视");
        title.setTextColor(Color.WHITE);
        title.setTextSize(34);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(0, 20, 0, 4);
        col.addView(title, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));

        TextView sub = new TextView(this);
        sub.setText("TVBox 引擎内核 · 海报墙点播 · 电视直播");
        sub.setTextColor(0xFF8A8A99);
        sub.setTextSize(13);
        sub.setPadding(0, 0, 0, 26);
        col.addView(sub, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));

        // 卡片区 2x3
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(3);
        int pad = 40;
        grid.setPadding(pad, 0, pad, 0);
        col.addView(grid);

        addCard(grid, "🎬", "影视点播", "海报墙 · Spider引擎", 0xFF3D2B1F, new Runnable() {
            @Override public void run() { go(HomeActivity.class); }
        });
        addCard(grid, "📺", "电视台直播", "内置145频道 · 可换源", 0xFF1F2B3D, new Runnable() {
            @Override public void run() { go(LivePlayActivity.class); }
        });
        addCard(grid, "⚙️", "配置地址", "仓库/直播源 · 扫码配置", 0xFF2B3D1F, new Runnable() {
            @Override public void run() { go(SettingActivity.class); }
        });
        addCard(grid, "📁", "本地文件", "视频播放 · 安装APK", 0xFF3D1F2B, new Runnable() {
            @Override public void run() { Toast.makeText(this2(), "请使用文件管理器或推送功能", Toast.LENGTH_SHORT).show(); }
        });
        addCard(grid, "☁️", "网盘", "云盘资源播放", 0xFF2B1F3D, new Runnable() {
            @Override public void run() { go(DriveActivity.class); }
        });
        addCard(grid, "🕘", "历史收藏", "观看记录", 0xFF1F3D33, new Runnable() {
            @Override public void run() { go(HistoryActivity.class); }
        });

        // 底栏：时间 + 引擎状态
        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        bottom.setPadding(40, 14, 40, 14);
        bottom.setBackgroundColor(0xFF13131C);

        tvDate = new TextView(this);
        tvDate.setTextColor(0xFF8A8A99);
        tvDate.setTextSize(13);
        bottom.addView(tvDate, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));

        TextView engine = new TextView(this);
        engine.setText("源: " + shortSource());
        engine.setTextColor(0xFFFF8C42);
        engine.setTextSize(13);
        bottom.addView(engine, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        root.addView(bottom, bp);

        setContentView(root);
        mHandler();
    }

    private MusiHomeActivity this2() { return this; }

    private String shortSource() {
        try {
            SharedPreferences sp = getSharedPreferences("hawk", 0);
            String u = sp.getString(HawkConfig.API_URL, "");
            if (u == null || u.isEmpty()) return "默认内置源";
            return u.length() > 40 ? u.substring(0, 40) + "…" : u;
        } catch (Throwable t) { return "默认内置源"; }
    }

    private void mHandler() {
        Runnable r = new Runnable() {
            @Override public void run() {
                tvDate.setText(new SimpleDateFormat("yyyy年MM月dd日 EEEE  HH:mm", Locale.CHINA).format(new Date()));
                tvDate.postDelayed(this, 15000);
            }
        };
        tvDate.post(r);
    }

    private void addCard(GridLayout grid, String icon, String name, String desc, int color, final Runnable action) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setBackgroundColor(color);
        card.setClickable(true);
        card.setFocusable(true);
        card.setFocusableInTouchMode(true);

        GridLayout.LayoutParams glp = new GridLayout.LayoutParams();
        glp.width = 300;
        glp.height = 200;
        glp.setMargins(14, 14, 14, 14);
        card.setLayoutParams(glp);

        TextView ic = new TextView(this);
        ic.setText(icon);
        ic.setTextSize(30);
        card.addView(ic);

        TextView nm = new TextView(this);
        nm.setText(name);
        nm.setTextColor(Color.WHITE);
        nm.setTextSize(17);
        nm.setTypeface(Typeface.DEFAULT_BOLD);
        nm.setPadding(0, 8, 0, 0);
        card.addView(nm);

        TextView ds = new TextView(this);
        ds.setText(desc);
        ds.setTextColor(0xFF9A9AAB);
        ds.setTextSize(11);
        ds.setPadding(0, 4, 0, 0);
        card.addView(ds);

        // 简化：用背景色切换而非占位
        final int baseColor = color;
        card.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean hasF) {
                v.setBackgroundColor(hasF ? 0xFFFF8C42 : baseColor);
                v.setScaleX(hasF ? 1.05f : 1.0f);
                v.setScaleY(hasF ? 1.05f : 1.0f);
            }
        });
        card.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { action.run(); }
        });
        grid.addView(card);
    }

    private void go(Class<?> cls) {
        startActivity(new Intent(this, cls));
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        return super.onKeyDown(keyCode, event);
    }
}
