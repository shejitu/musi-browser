package com.mytv.lite;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 慕思影视 5.0 — 打开即海报墙。
 *
 * 以前这里是四个大卡片的功能入口，要点「海报墙」再经过一层仓库扫描才看得到片子，
 * 扫描失败就只剩一行提示，所以实际用起来等于「没有海报墙」。
 *
 * 现在：开屏直接是一屏竖版海报墙，默认数据来自豆瓣（热映 / 最新 / 高分），
 * 不依赖任何仓库配置，装完就能看到最近在放什么。原功能入口缩成顶栏小按钮，
 * 一个都没少。点海报可看简介，也可带着片名去影视源里搜来直接播。
 */
public class HomeActivity extends Activity {

    /* ============ 数据源 ============ */

    private static final String[] TAB_LABEL = {"🔥 热映", "🆕 最新", "⭐ 高分", "📚 影视源"};
    private static final String[] TAB_TAG   = {"热门", "最新", "豆瓣高分", null};
    private static final String[] TAB_SORT  = {"recommend", "time", "recommend", null};
    private static final int PAGE_SIZE = 20;

    private int tab = 0;
    private int pageStart = 0;
    private int requestSeq = 0;          // 防止慢请求回来覆盖新结果
    private final List<Poster> items = new ArrayList<>();

    /* ============ 视图 ============ */

    private FrameLayout wrapper;
    private LinearLayout tabRow, grid, detailPanel;
    private ScrollView gridScroll;
    private TextView status, clock;
    private TextView prevBtn, nextBtn;

    private int cols = 5, cellW = 240, posterH = 340;
    private boolean firstRender = true;   // 只有首屏抢焦点，翻页不打断当前选中

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable clockTick = new Runnable() {
        @Override public void run() {
            if (clock != null)
                clock.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()));
            handler.postDelayed(this, 30000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        measureGrid();
        buildUi();
        selectTab(0);
        handler.post(clockTick);
        startServer();
        try { UpdateChecker.check(this); } catch (Throwable ignore) { }
    }

    /* ==================== 布局 ==================== */

    private void measureGrid() {
        int sw = getResources().getDisplayMetrics().widthPixels;
        int gap = dp(8) * 2;
        int pad = dp(28) * 2;
        cols = Math.round(sw / 330f);
        cols = Math.max(4, Math.min(7, cols));
        cellW = (sw - pad - gap * cols) / cols;
        if (cellW < dp(90)) cellW = dp(90);
        posterH = (int) (cellW / 0.71f);
    }

    private void buildUi() {
        wrapper = new FrameLayout(this);
        setContentView(wrapper);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF0D0D16);
        root.setPadding(dp(28), dp(12), dp(28), dp(10));
        wrapper.addView(root, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        /* ---- 顶栏：标题 / 时间 / 功能入口 ---- */
        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(topRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(this);
        title.setText("慕思影视");
        title.setTextColor(Color.WHITE);
        title.setTextSize(22);
        title.setPadding(0, 0, dp(18), 0);
        topRow.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        clock = new TextView(this);
        clock.setTextColor(0xFF7A7A90);
        clock.setTextSize(14);
        clock.setPadding(0, 0, dp(20), 0);
        topRow.addView(clock, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        topRow.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));

        addTopAction(topRow, "📺 直播", v -> MainActivity.launch(this));
        addTopAction(topRow, "🌐 网页", v -> MainActivity.launchBrowserPlain(this));
        addTopAction(topRow, "📁 仓库", v -> ReposActivity.launch(this));
        addTopAction(topRow, "⚡ 代理", v -> ProxyActivity.launch(this));
        addTopAction(topRow, "⚙ 设置", v -> SettingsActivity.launch(this));
        addTopAction(topRow, "⟳ 刷新", v -> selectTab(tab));

        /* ---- tab 行 ---- */
        tabRow = new LinearLayout(this);
        tabRow.setOrientation(LinearLayout.HORIZONTAL);
        tabRow.setPadding(0, dp(8), 0, 0);
        root.addView(tabRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        for (int i = 0; i < TAB_LABEL.length; i++) addTabItem(i);

        /* ---- 状态行 + 翻页 ---- */
        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setOrientation(LinearLayout.HORIZONTAL);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);
        statusRow.setPadding(0, dp(6), 0, dp(4));
        root.addView(statusRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        status = new TextView(this);
        status.setTextColor(0xFF80809A);
        status.setTextSize(13);
        statusRow.addView(status, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        prevBtn = addPagerBtn(statusRow, "◀ 上一页", v -> {
            if (pageStart >= PAGE_SIZE) load(pageStart - PAGE_SIZE, false);
            else toast("已经是第一页");
        });
        nextBtn = addPagerBtn(statusRow, "下一页 ▶", v -> load(pageStart + PAGE_SIZE, true));

        /* ---- 海报墙 ---- */
        gridScroll = new ScrollView(this);
        gridScroll.setVerticalScrollBarEnabled(false);
        gridScroll.setClipToPadding(false);
        grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        gridScroll.addView(grid, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(gridScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        /* ---- 右侧详情面板（覆盖层） ---- */
        detailPanel = new LinearLayout(this);
        detailPanel.setOrientation(LinearLayout.VERTICAL);
        detailPanel.setBackgroundColor(0xF8101019);
        detailPanel.setPadding(dp(26), dp(18), dp(26), dp(18));
        detailPanel.setVisibility(View.GONE);
        FrameLayout.LayoutParams dlp = new FrameLayout.LayoutParams(
                dp(560), ViewGroup.LayoutParams.MATCH_PARENT);
        dlp.gravity = Gravity.END;
        wrapper.addView(detailPanel, dlp);
    }

    private void addTopAction(LinearLayout row, String text, View.OnClickListener l) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(0xFFB0B0C8);
        t.setTextSize(13);
        t.setPadding(dp(12), dp(5), dp(12), dp(5));
        t.setFocusable(true);
        t.setFocusableInTouchMode(true);
        t.setClickable(true);
        GradientDrawable normal = roundRect(0xFF1C1C2A, 0, 0);
        GradientDrawable focus = roundRect(0xFF264A5E, dp(1.5f), 0xFF4FC3F7);
        t.setBackground(normal);
        t.setOnFocusChangeListener((v, f) -> {
            t.setBackground(f ? focus : normal);
            t.setTextColor(f ? 0xFFFFFFFF : 0xFFB0B0C8);
        });
        t.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(4), 0, dp(4), 0);
        row.addView(t, lp);
    }

    private TextView addPagerBtn(LinearLayout row, String text, View.OnClickListener l) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(0xFF9090A8);
        t.setTextSize(12);
        t.setPadding(dp(12), dp(4), dp(12), dp(4));
        t.setFocusable(true);
        t.setFocusableInTouchMode(true);
        t.setClickable(true);
        GradientDrawable normal = roundRect(0x00000000, 0, 0);
        GradientDrawable focus = roundRect(0xFF264A5E, dp(1.5f), 0xFF4FC3F7);
        t.setBackground(normal);
        t.setOnFocusChangeListener((v, f) -> {
            t.setBackground(f ? focus : normal);
            t.setTextColor(f ? 0xFFFFFFFF : 0xFF9090A8);
        });
        t.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(6), 0, 0, 0);
        row.addView(t, lp);
        return t;
    }

    private void addTabItem(final int idx) {
        TextView t = new TextView(this);
        t.setText(TAB_LABEL[idx]);
        t.setTextSize(15);
        t.setPadding(dp(16), dp(6), dp(16), dp(6));
        t.setFocusable(true);
        t.setFocusableInTouchMode(true);
        t.setClickable(true);
        styleTab(t, idx == tab, false);
        t.setOnFocusChangeListener((v, f) -> styleTab(t, idx == tab, f));
        t.setOnClickListener(v -> selectTab(idx));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, dp(8), 0);
        tabRow.addView(t, lp);
    }

    private void styleTab(TextView t, boolean selected, boolean focused) {
        GradientDrawable bg;
        if (selected) bg = roundRect(0xFFE42112, 0, 0);
        else if (focused) bg = roundRect(0xFF264A5E, dp(1.5f), 0xFF4FC3F7);
        else bg = roundRect(0x00000000, 0, 0);
        t.setBackground(bg);
        t.setTextColor(selected || focused ? 0xFFFFFFFF : 0xFF9A9AB4);
        t.setTypeface(null, selected ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
    }

    /* ==================== 数据 ==================== */

    private void selectTab(int idx) {
        tab = idx;
        pageStart = 0;
        firstRender = true;
        for (int i = 0; i < tabRow.getChildCount(); i++) {
            View v = tabRow.getChildAt(i);
            if (v instanceof TextView) styleTab((TextView) v, i == tab, v.hasFocus());
        }
        if (idx == 3) { openCmsWall(); return; }
        items.clear();
        grid.removeAllViews();
        load(0, false);
    }

    private void load(final int from, final boolean append) {
        final int mySeq = ++requestSeq;
        if (!append) {
            grid.removeAllViews();
            status.setText("正在获取豆瓣「" + TAB_TAG[tab] + "」…");
        } else {
            status.setText("加载更多…");
        }

        final String tag = TAB_TAG[tab];
        final String sort = TAB_SORT[tab];

        new Thread(() -> {
            try {
                final List<Poster> got = DoubanSource.list(tag, sort, from, PAGE_SIZE);
                handler.post(() -> {
                    if (mySeq != requestSeq || isFinishing()) return;
                    if (got.isEmpty() && !append) {
                        status.setText("豆瓣没有返回内容，可点上方「📚 影视源」直接进影视源");
                        return;
                    }
                    if (!append) items.clear();
                    items.addAll(got);
                    pageStart = from;
                    renderGrid();
                    status.setText("共 " + items.size() + " 部 · " + tag
                            + " · 第 " + (from / PAGE_SIZE + 1) + " 页");
                });
            } catch (final Exception e) {
                handler.post(() -> {
                    if (mySeq != requestSeq || isFinishing()) return;
                    String msg = e.getMessage() == null ? "未知错误" : e.getMessage();
                    status.setText("✗ 豆瓣加载失败：" + msg + "　（可点「📚 影视源」走影视源海报墙）");
                    if (items.isEmpty()) renderEmptyHint();
                });
            }
        }, "douban-list").start();
    }

    private void renderGrid() {
        grid.removeAllViews();
        LinearLayout row = null;
        for (int i = 0; i < items.size(); i++) {
            if (i % cols == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setClipChildren(false);
                grid.addView(row, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            row.addView(buildCard(items.get(i), i));
        }
        // 末尾放一个「加载更多」，遥控器往下走到底就能继续翻
        LinearLayout tail = new LinearLayout(this);
        tail.setGravity(Gravity.CENTER);
        tail.setPadding(0, dp(14), 0, dp(24));
        TextView more = new TextView(this);
        more.setText("↓ 加载更多");
        more.setTextColor(0xFF9A9AB4);
        more.setTextSize(14);
        more.setPadding(dp(28), dp(10), dp(28), dp(10));
        GradientDrawable normal = roundRect(0xFF1C1C2A, 0, 0);
        GradientDrawable focus = roundRect(0xFF264A5E, dp(1.5f), 0xFF4FC3F7);
        more.setBackground(normal);
        more.setFocusable(true);
        more.setFocusableInTouchMode(true);
        more.setClickable(true);
        more.setOnFocusChangeListener((v, f) -> {
            more.setBackground(f ? focus : normal);
            more.setTextColor(f ? 0xFFFFFFFF : 0xFF9A9AB4);
        });
        more.setOnClickListener(v -> load(pageStart + PAGE_SIZE, true));
        tail.addView(more, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        grid.addView(tail, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 首屏把焦点放到第一张海报；翻页时不抢焦点，避免打断当前的选中位置
        if (firstRender && !tabRow.hasFocus()) {
            firstRender = false;
            grid.post(() -> {
                if (grid.getChildCount() > 0 && grid.getChildAt(0) instanceof LinearLayout) {
                    LinearLayout firstRow = (LinearLayout) grid.getChildAt(0);
                    if (firstRow.getChildCount() > 0) firstRow.getChildAt(0).requestFocus();
                }
            });
        }
    }

    private View buildCard(final Poster p, int index) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable normal = roundRect(0xFF141420, 0, 0);
        GradientDrawable focus = roundRect(0xFF1E2A36, dp(2.5f), 0xFF4FC3F7);
        card.setBackground(normal);

        final ImageView img = new ImageView(this);
        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        img.setBackgroundColor(0xFF1E1E2C);
        card.addView(img, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, posterH));

        TextView name = new TextView(this);
        name.setText(p.title);
        name.setTextColor(Color.WHITE);
        name.setTextSize(13);
        name.setMaxLines(1);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setPadding(dp(4), dp(6), dp(4), dp(2));
        card.addView(name, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView meta = new TextView(this);
        StringBuilder sb = new StringBuilder();
        if (!TextUtils.isEmpty(p.rate) && !"0".equals(p.rate)) sb.append("★ ").append(p.rate);
        if (!TextUtils.isEmpty(p.remarks)) {
            if (sb.length() > 0) sb.append("  ");
            sb.append(p.remarks);
        }
        if (sb.length() == 0) sb.append(" ");
        meta.setText(sb.toString());
        meta.setTextColor(0xFFFFB74D);
        meta.setTextSize(11);
        meta.setMaxLines(1);
        meta.setPadding(dp(4), 0, dp(4), dp(8));
        card.addView(meta, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                cellW, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.setMargins(dp(4), dp(4), dp(4), dp(8));
        card.setLayoutParams(clp);

        card.setFocusable(true);
        card.setFocusableInTouchMode(true);
        card.setClickable(true);
        card.setOnFocusChangeListener((v, f) -> {
            card.setBackground(f ? focus : normal);
            name.setTextColor(f ? 0xFF4FC3F7 : Color.WHITE);
        });
        card.setOnClickListener(v -> showDetail(p));

        ImagePool.load(p.pic, cellW, posterH, bmp -> {
            if (bmp != null) img.setImageBitmap(bmp);
        });
        return card;
    }

    private void renderEmptyHint() {
        grid.removeAllViews();
        TextView t = new TextView(this);
        t.setText("没有取到内容。\n\n可能原因：网络不通 / 豆瓣暂时受限。\n可以先试下面两个入口，都不影响正常看片。");
        t.setTextColor(0xFF80809A);
        t.setTextSize(15);
        t.setPadding(dp(10), dp(30), dp(10), dp(18));
        grid.addView(t, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        btns.setPadding(dp(10), 0, 0, 0);
        grid.addView(btns, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        btns.addView(emptyAction("⟳ 重试", v -> selectTab(tab)));
        btns.addView(emptyAction("📚 影视源海报墙", v -> selectTab(3)));
        btns.addView(emptyAction("🌐 豆瓣网页版", v -> {
            android.content.Intent it = new android.content.Intent(this, MainActivity.class);
            it.putExtra(MainActivity.MODE, MainActivity.MODE_BROWSER);
            it.putExtra("show_sites", false);
            it.putExtra("browser_url", "https://movie.douban.com/");
            startActivity(it);
        }));
    }

    private TextView emptyAction(String text, View.OnClickListener l) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(0xFFB0B0C8);
        t.setTextSize(14);
        t.setPadding(dp(20), dp(10), dp(20), dp(10));
        GradientDrawable normal = roundRect(0xFF1C1C2A, 0, 0);
        GradientDrawable focus = roundRect(0xFF264A5E, dp(1.5f), 0xFF4FC3F7);
        t.setBackground(normal);
        t.setFocusable(true);
        t.setFocusableInTouchMode(true);
        t.setClickable(true);
        t.setOnFocusChangeListener((v, f) -> {
            t.setBackground(f ? focus : normal);
            t.setTextColor(f ? 0xFFFFFFFF : 0xFFB0B0C8);
        });
        t.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, dp(10), 0);
        t.setLayoutParams(lp);
        return t;
    }

    /* ==================== 详情 ==================== */

    private void showDetail(final Poster p) {
        detailPanel.removeAllViews();
        detailPanel.setVisibility(View.VISIBLE);

        TextView close = new TextView(this);
        close.setText("✕ 关闭");
        close.setTextColor(0xFF80809A);
        close.setTextSize(13);
        close.setPadding(0, 0, 0, dp(10));
        close.setFocusable(true);
        close.setFocusableInTouchMode(true);
        close.setClickable(true);
        close.setOnFocusChangeListener((v, f) -> close.setTextColor(f ? 0xFFFFFFFF : 0xFF80809A));
        close.setOnClickListener(v -> hideDetail());
        detailPanel.addView(close, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView sc = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        sc.addView(box);
        detailPanel.addView(sc, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView name = new TextView(this);
        name.setText(p.title);
        name.setTextColor(Color.WHITE);
        name.setTextSize(22);
        box.addView(name, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final TextView meta = new TextView(this);
        StringBuilder sb = new StringBuilder();
        if (!TextUtils.isEmpty(p.rate)) sb.append("★ ").append(p.rate);
        if (!TextUtils.isEmpty(p.year)) { if (sb.length() > 0) sb.append("　"); sb.append(p.year); }
        meta.setText(sb.toString());
        meta.setTextColor(0xFFFFB74D);
        meta.setTextSize(14);
        meta.setPadding(0, dp(6), 0, 0);
        box.addView(meta, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final TextView intro = new TextView(this);
        intro.setText("简介加载中…");
        intro.setTextColor(0xFFB0B0C8);
        intro.setTextSize(13);
        intro.setLineSpacing(dp(3), 1f);
        intro.setPadding(0, dp(12), 0, dp(12));
        box.addView(intro, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView play = new TextView(this);
        play.setText("▶ 在影视源里搜这部片");
        play.setTextColor(Color.WHITE);
        play.setTextSize(15);
        play.setGravity(Gravity.CENTER);
        play.setPadding(dp(16), dp(12), dp(16), dp(12));
        play.setFocusable(true);
        play.setFocusableInTouchMode(true);
        play.setClickable(true);
        GradientDrawable playNormal = roundRect(0xFFE42112, 0, 0);
        GradientDrawable playFocus = roundRect(0xFFFF5A39, dp(2f), 0xFFFFFFFF);
        play.setBackground(playNormal);
        play.setOnFocusChangeListener((v, f) -> play.setBackground(f ? playFocus : playNormal));
        box.addView(play, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final TextView site = new TextView(this);
        site.setText("匹配可用影视源中…");
        site.setTextColor(0xFF70708A);
        site.setTextSize(12);
        site.setPadding(0, dp(8), 0, dp(8));
        box.addView(site, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView web = new TextView(this);
        web.setText("豆瓣页面（浏览器打开）");
        web.setTextColor(0xFF4FC3F7);
        web.setTextSize(13);
        web.setPadding(dp(4), dp(10), dp(4), dp(10));
        web.setFocusable(true);
        web.setFocusableInTouchMode(true);
        web.setClickable(true);
        web.setOnFocusChangeListener((v, f) -> web.setTextColor(f ? 0xFFFFFFFF : 0xFF4FC3F7));
        web.setOnClickListener(v -> {
            android.content.Intent it = new android.content.Intent(this, MainActivity.class);
            it.putExtra(MainActivity.MODE, MainActivity.MODE_BROWSER);
            it.putExtra("show_sites", false);
            it.putExtra("browser_url", "https://movie.douban.com/subject/" + p.id + "/");
            startActivity(it);
        });
        box.addView(web, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        play.setOnClickListener(v -> {
            if (p.playable()) {
                VodActivity.launchSearch(this, p.apiBase, "影视源", p.title);
                return;
            }
            site.setText("正在匹配影视源…");
            CmsFinder.find(this, new CmsFinder.Cb() {
                @Override public void found(String api, String siteName) {
                    if (isFinishing()) return;
                    site.setText("✓ 源：" + siteName);
                    VodActivity.launchSearch(HomeActivity.this, api, siteName, p.title);
                }
                @Override public void fail(String msg) {
                    if (isFinishing()) return;
                    site.setText("✗ " + msg + "，请到「仓库」里加一个 CMS 接口");
                }
            });
        });

        // 豆瓣详情：只影响面板文字，失败就保持「暂无简介」
        if (p.douban) {
            new Thread(() -> {
                String text;
                try {
                    DoubanSource.Detail d = DoubanSource.detail(p.id);
                    StringBuilder b = new StringBuilder();
                    if (!TextUtils.isEmpty(d.year) || !TextUtils.isEmpty(d.genres) || !TextUtils.isEmpty(d.countries)) {
                        if (!TextUtils.isEmpty(d.year)) b.append(d.year).append("　");
                        if (!TextUtils.isEmpty(d.genres)) b.append(d.genres).append("　");
                        if (!TextUtils.isEmpty(d.countries)) b.append(d.countries);
                        b.append("\n\n");
                    }
                    b.append(TextUtils.isEmpty(d.intro) ? "豆瓣暂无简介" : d.intro);
                    text = b.toString();
                } catch (Throwable t) {
                    text = "简介加载失败（不影响播放）";
                }
                final String out = text;
                handler.post(() -> {
                    if (isFinishing() || detailPanel.getVisibility() != View.VISIBLE) return;
                    intro.setText(out);
                });
            }, "douban-detail").start();
        } else {
            intro.setText(TextUtils.isEmpty(p.remarks) ? "来自影视源" : p.remarks);
        }

        play.post(play::requestFocus);
    }

    private void hideDetail() {
        detailPanel.setVisibility(View.GONE);
        detailPanel.removeAllViews();
    }

    /* ==================== 影视源 / 其他 ==================== */

    /** 「影视源」tab：直接进影视源海报墙；本页不做停留。 */
    private void openCmsWall() {
        status.setText("正在寻找可用的影视源…");
        grid.removeAllViews();
        CmsFinder.find(this, new CmsFinder.Cb() {
            @Override public void found(String api, String siteName) {
                if (isFinishing()) return;
                status.setText("✓ " + siteName);
                VodActivity.launch(HomeActivity.this, api, siteName);
            }
            @Override public void fail(String msg) {
                if (isFinishing()) return;
                status.setText("✗ " + msg);
                TextView t = new TextView(HomeActivity.this);
                t.setText(msg + "\n\n到「📁 仓库」里添加一个影视接口地址即可：\n形如 http://xxx/api.php/provide/vod/");
                t.setTextColor(0xFF80809A);
                t.setTextSize(15);
                t.setPadding(0, dp(40), 0, 0);
                grid.addView(t, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
        });
    }

    private void startServer() {
        LiteServer.ensure(this, url -> {
            android.content.Intent it = new android.content.Intent(this, MainActivity.class);
            it.putExtra("browser_url", url);
            startActivity(it);
        });
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (detailPanel.getVisibility() == View.VISIBLE) { hideDetail(); return true; }
            finish();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private GradientDrawable roundRect(int fill, int strokeW, int strokeColor) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setColor(fill);
        d.setCornerRadius(dp(6));
        if (strokeW > 0) d.setStroke(strokeW, strokeColor);
        return d;
    }

    private void toast(String m) { Toast.makeText(this, m, Toast.LENGTH_SHORT).show(); }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(clockTick);
        super.onDestroy();
    }
}
