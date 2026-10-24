package com.mytv.lite;

import android.app.Activity;
import android.content.Context;
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
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 点播界面：海报墙（苹果CMS V10 标准 api.php?ac=videolist 协议）。
 *
 * 慕思 6.1 修的三件事：
 *   1. 点海报会闪退 —— 6.0 用 new Video() 手搓条目，remarks/category 是 null，
 *      showDetail 里 v.remarks.isEmpty() 直接 NPE。现在字段默认空串，且全部走 s() 兜底。
 *   2. 选集点了播不动 —— 苹果CMS 的 vod_play_url 是「$$$ 分隔的多条线路」，
 *      6.0 把几条线路揉成一条列表，经常点到云播网页地址。现在按线路拆开，
 *      m3u8 线路排前面并默认选中，可以手动切线路。
 *   3. 播放走 MainActivity 太重 —— 现在跳 PlayActivity，只起一个 ExoPlayer，秒开。
 */
public class VodActivity extends Activity {

    /** 单集 */
    static class Ep {
        final String name, url;
        Ep(String n, String u) { name = n; url = u; }
    }

    /** 一条播放线路（vod_play_from 的一项） */
    static class Line {
        final String from;
        final List<Ep> eps = new ArrayList<>();
        Line(String f) { from = f == null ? "" : f; }
    }

    static class Video {
        String id = "", name = "", pic = "", category = "", remarks = "";
        String intro = "";
        final List<Line> lines = new ArrayList<>();
        int line = 0;
    }

    private String apiBase;
    private String siteLabel = "点播";
    private LinearLayout navRow, grid, detailPanel;
    private ScrollView gridScroll;
    private TextView status, searchText;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Video> videos = new ArrayList<>();
    private final List<String> categories = new ArrayList<>();
    private int page = 1, currentCategory = -1; // -1=全部

    /** 空值兜底：6.0 的闪退就是 null.isEmpty() 引起的 */
    static String s(String v) { return v == null ? "" : v; }

    public static void launch(Context ctx, String apiUrl, String siteName) {
        android.content.Intent it = new android.content.Intent(ctx, VodActivity.class);
        it.putExtra("api", normalizeApi(apiUrl));
        it.putExtra("site", siteName);
        ctx.startActivity(it);
    }

    /** 带着片名进来，直接出搜索结果。 */
    public static void launchSearch(Context ctx, String apiUrl, String siteName, String keyword) {
        android.content.Intent it = new android.content.Intent(ctx, VodActivity.class);
        it.putExtra("api", normalizeApi(apiUrl));
        it.putExtra("site", siteName);
        it.putExtra("search", keyword);
        ctx.startActivity(it);
    }

    /** 带着 vod_id 直接进选集（首页海报墙点一下就走这里）。 */
    public static void launchDetail(Context ctx, String apiUrl, String siteName,
                                    String vodId, String title) {
        android.content.Intent it = new android.content.Intent(ctx, VodActivity.class);
        it.putExtra("api", normalizeApi(apiUrl));
        it.putExtra("site", siteName);
        it.putExtra("vod_id", vodId);
        it.putExtra("title", title);
        ctx.startActivity(it);
    }

    /** 各类 CMS 接口统一转成 videolist 协议 */
    static String normalizeApi(String api) {
        if (api == null) return "";
        api = api.trim();
        if (api.isEmpty()) return api;
        if (api.contains("ac=videolist") || api.contains("at=json")) return api;
        if (api.contains("/api.php/provide/vod")) {
            return api.endsWith("/") ? api + "?ac=videolist" : api + "/?ac=videolist";
        }
        if (api.contains("ac=list")) return api.replace("ac=list", "ac=videolist");
        String base = api.endsWith("/") ? api : api + "/";
        if (!api.contains(".php") && !api.contains("/api")) return base + "api.php?ac=videolist";
        return api + (api.contains("?") ? "&" : "?") + "ac=videolist";
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        apiBase = s(getIntent().getStringExtra("api"));
        String site = getIntent().getStringExtra("site");
        if (!TextUtils.isEmpty(site)) siteLabel = site;

        buildUi();

        String kw = getIntent().getStringExtra("search");
        final String directId = getIntent().getStringExtra("vod_id");
        final String directTitle = getIntent().getStringExtra("title");

        if (!TextUtils.isEmpty(kw)) {
            navRow.setVisibility(View.GONE);
            doSearch(kw.trim());
        } else {
            load(1, currentCategory);
            if (!TextUtils.isEmpty(directId)) {
                // 海报墙点进来：先用传入信息秒开选集面板，不等列表
                Video dv = new Video();
                dv.id = s(directId);
                dv.name = s(directTitle);
                grid.postDelayed(() -> { if (!isFinishing()) showDetail(dv, true); }, 120);
            }
        }
    }

    /* ==================== 界面 ==================== */

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF0D0D16);
        root.setPadding(dp(24), dp(14), dp(24), dp(14));
        setContentView(root);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        root.addView(top, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(row1, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView siteName = new TextView(this);
        siteName.setText(siteLabel + " · 海报墙");
        siteName.setTextColor(0xFF4FC3F7);
        siteName.setTextSize(18);
        row1.addView(siteName, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        searchText = new TextView(this);
        searchText.setText("🔍 搜索");
        searchText.setTextColor(Color.WHITE);
        searchText.setTextSize(14);
        searchText.setPadding(dp(20), dp(6), dp(20), dp(6));
        GradientDrawable sn = round(0xFF00A6B8, 0, 0);
        GradientDrawable sf = round(0xFFFF8C42, dp(2f), 0xFFFFFFFF);
        searchText.setBackground(sn);
        searchText.setFocusable(true);
        searchText.setFocusableInTouchMode(true);
        searchText.setClickable(true);
        searchText.setOnFocusChangeListener((vv, f) -> searchText.setBackground(f ? sf : sn));
        searchText.setOnClickListener(v -> showSearchDialog());
        row1.addView(searchText);

        navRow = new LinearLayout(this);
        navRow.setOrientation(LinearLayout.HORIZONTAL);
        navRow.setPadding(0, dp(6), 0, 0);
        top.addView(navRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        status = new TextView(this);
        status.setTextColor(0xFF808090);
        status.setTextSize(13);
        status.setPadding(0, dp(6), 0, dp(6));
        root.addView(status, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(body, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        gridScroll = new ScrollView(this);
        gridScroll.setVerticalScrollBarEnabled(false);
        grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        gridScroll.addView(grid, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        // 关键：宽度 0 + weight 1，否则 480dp 的详情面板会被挤到屏幕外，看着像「点了没反应」
        body.addView(gridScroll, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        detailPanel = new LinearLayout(this);
        detailPanel.setOrientation(LinearLayout.VERTICAL);
        detailPanel.setBackgroundColor(0xF2101018);
        detailPanel.setPadding(dp(22), dp(14), dp(22), dp(14));
        detailPanel.setVisibility(View.GONE);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                dp(620), ViewGroup.LayoutParams.MATCH_PARENT);
        dlp.setMargins(dp(16), 0, 0, 0);
        body.addView(detailPanel, dlp);
    }

    private GradientDrawable round(int fill, int strokeW, int strokeColor) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setColor(fill);
        d.setCornerRadius(dp(6));
        if (strokeW > 0) d.setStroke(strokeW, strokeColor);
        return d;
    }

    /* ==================== 列表 ==================== */

    private void doSearch(final String wd) {
        status.setText("正在影视源里搜「" + wd + "」…");
        new Thread(() -> {
            try {
                JSONObject d2 = JSONUtil.parse(ReposHttp.get(
                        url(wd.isEmpty() ? "" : "wd=" + URLEncoder.encode(wd, "UTF-8"))));
                JSONArray list = d2 == null ? null : d2.optJSONArray("list");
                final List<Video> vids = new ArrayList<>();
                if (list != null) for (int i = 0; i < list.length(); i++) {
                    JSONObject o = list.optJSONObject(i);
                    if (o == null) continue;
                    Video v = new Video();
                    v.id = s(o.optString("vod_id"));
                    v.name = s(o.optString("vod_name", "?"));
                    v.pic = s(o.optString("vod_pic"));
                    v.category = s(o.optString("type_name"));
                    v.remarks = s(o.optString("vod_remarks"));
                    if (!v.id.isEmpty()) vids.add(v);
                }
                handler.post(() -> {
                    if (isFinishing()) return;
                    videos.clear();
                    videos.addAll(vids);
                    status.setText(vids.isEmpty()
                            ? "这个源里没搜到「" + wd + "」，换个源或换个关键词"
                            : "搜索「" + wd + "」：" + vids.size() + " 个结果");
                    buildGrid();
                });
            } catch (Exception e) {
                handler.post(() -> {
                    if (!isFinishing()) status.setText("✗ 搜索失败：" + brief(e));
                });
            }
        }, "vod-search").start();
    }

    private String url(String query) {
        if (query == null || query.isEmpty()) return apiBase;
        return apiBase + (apiBase.contains("?") ? "&" : "?") + query;
    }

    private void load(final int pg, final int cat) {
        page = pg;
        status.setText("加载中… 第 " + pg + " 页");
        new Thread(() -> {
            try {
                StringBuilder q = new StringBuilder("pg=" + pg);
                if (cat >= 0 && cat < categories.size()) {
                    String cn = categories.get(cat);
                    int bar = cn.indexOf('|');
                    q.append("&t=").append(URLEncoder.encode(bar >= 0 ? cn.substring(0, bar) : cn, "UTF-8"));
                }
                JSONObject d = JSONUtil.parse(ReposHttp.get(url(q.toString())));
                JSONArray list = d == null ? null : d.optJSONArray("list");
                if (list == null) throw new Exception("接口没有 list 字段，不是苹果CMS协议");

                JSONArray cls = d.optJSONArray("class");
                final List<String> cats = new ArrayList<>();
                if (cls != null) for (int i = 0; i < cls.length(); i++) {
                    JSONObject c = cls.optJSONObject(i);
                    if (c == null) continue;
                    String id = s(c.optString("type_id")), nm = s(c.optString("type_name"));
                    if (!id.isEmpty() && !nm.isEmpty()) cats.add(id + "|" + nm);
                }

                final List<Video> vids = new ArrayList<>();
                for (int i = 0; i < list.length(); i++) {
                    JSONObject o = list.optJSONObject(i);
                    if (o == null) continue;
                    Video v = new Video();
                    v.id = s(o.optString("vod_id"));
                    v.name = s(o.optString("vod_name", "?"));
                    v.pic = s(o.optString("vod_pic"));
                    v.category = s(o.optString("type_name"));
                    v.remarks = s(o.optString("vod_remarks"));
                    if (!v.id.isEmpty()) vids.add(v);
                }
                handler.post(() -> {
                    if (isFinishing()) return;
                    if (!cats.isEmpty() && categories.isEmpty()) { categories.addAll(cats); buildNav(); }
                    videos.clear();
                    videos.addAll(vids);
                    status.setText("共 " + videos.size() + " 部 · 第 " + pg + " 页（左右键翻页）");
                    buildGrid();
                });
            } catch (Exception e) {
                handler.post(() -> {
                    if (isFinishing()) return;
                    status.setText("✗ " + brief(e) + " · 按「←」返回换一个源");
                });
            }
        }, "vod-list").start();
    }

    private String brief(Exception e) {
        String m = e.getMessage();
        if (m == null || m.isEmpty()) m = e.getClass().getSimpleName();
        return m.length() > 70 ? m.substring(0, 70) + "…" : m;
    }

    private void buildNav() {
        navRow.removeAllViews();
        addNavItem("全部", -1);
        for (int i = 0; i < Math.min(categories.size(), 12); i++) {
            String[] kv = categories.get(i).split("\\|", 2);
            addNavItem(kv.length > 1 ? kv[1] : kv[0], i);
        }
    }

    private void addNavItem(String name, final int idx) {
        TextView t = new TextView(this);
        t.setText(name);
        t.setTextSize(13);
        t.setPadding(dp(12), dp(5), dp(12), dp(5));
        boolean sel = idx == currentCategory;
        GradientDrawable n = round(sel ? 0xFFE42112 : 0x00000000, 0, 0);
        GradientDrawable f = round(0xFF264A5E, dp(1.5f), 0xFF4FC3F7);
        t.setBackground(n);
        t.setTextColor(sel ? Color.WHITE : 0xFFB0B0C0);
        t.setFocusable(true);
        t.setFocusableInTouchMode(true);
        t.setClickable(true);
        t.setOnFocusChangeListener((vv, has) -> {
            t.setBackground(has ? f : n);
            t.setTextColor(has ? Color.WHITE : (idx == currentCategory ? Color.WHITE : 0xFFB0B0C0));
        });
        t.setOnClickListener(v -> { currentCategory = idx; load(1, idx); });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, dp(6), 0);
        navRow.addView(t, lp);
    }

    private void buildGrid() {
        grid.removeAllViews();
        int gridW = gridScroll.getWidth();
        if (gridW <= 0) gridW = getResources().getDisplayMetrics().widthPixels - dp(48);
        int cols = 5;
        int cellW = (gridW - dp(12) * cols) / cols;
        if (cellW < dp(60)) cellW = dp(60);
        int posterH = (int) (cellW / 0.71f);

        LinearLayout row = null;
        for (int i = 0; i < videos.size(); i++) {
            if (i % cols == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                grid.addView(row, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            final Video v = videos.get(i);
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(4), dp(4), dp(4), dp(4));
            GradientDrawable cn = round(0x00000000, 0, 0);
            GradientDrawable cf = round(0xFF1E2A36, dp(2.5f), 0xFF4FC3F7);
            card.setBackground(cn);

            ImageView img = new ImageView(this);
            img.setBackgroundColor(0xFF222230);
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            card.addView(img, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, posterH));

            TextView nm = new TextView(this);
            nm.setText(v.name + (v.remarks.isEmpty() ? "" : " " + v.remarks));
            nm.setTextColor(Color.WHITE);
            nm.setTextSize(12);
            nm.setMaxLines(1);
            nm.setEllipsize(TextUtils.TruncateAt.END);
            nm.setPadding(dp(2), dp(6), dp(2), dp(2));
            card.addView(nm, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                    cellW, ViewGroup.LayoutParams.WRAP_CONTENT);
            clp.setMargins(dp(4), dp(4), dp(4), dp(8));
            card.setLayoutParams(clp);

            card.setFocusable(true);
            card.setFocusableInTouchMode(true);
            card.setClickable(true);
            card.setOnFocusChangeListener((vv, f) -> {
                card.setBackground(f ? cf : cn);
                nm.setTextColor(f ? 0xFF4FC3F7 : Color.WHITE);
            });
            card.setOnClickListener(vv -> showDetail(v, false));

            ImagePool.load(v.pic, cellW, posterH, bmp -> { if (bmp != null) img.setImageBitmap(bmp); });
            row.addView(card);
        }

        if (!videos.isEmpty()) {
            LinearLayout tail = new LinearLayout(this);
            tail.setGravity(Gravity.CENTER);
            tail.setPadding(0, dp(10), 0, dp(20));
            TextView more = new TextView(this);
            more.setText("↓ 下一页");
            more.setTextColor(0xFF9A9AB4);
            more.setTextSize(14);
            more.setPadding(dp(28), dp(8), dp(28), dp(8));
            GradientDrawable tn = round(0xFF1C1C2A, 0, 0);
            GradientDrawable tf = round(0xFF264A5E, dp(1.5f), 0xFF4FC3F7);
            more.setBackground(tn);
            more.setFocusable(true);
            more.setFocusableInTouchMode(true);
            more.setClickable(true);
            more.setOnFocusChangeListener((v, f) -> more.setBackground(f ? tf : tn));
            more.setOnClickListener(v -> load(page + 1, currentCategory));
            tail.addView(more);
            grid.addView(tail);
        }
    }

    /* ==================== 详情 + 选集 ==================== */

    private void showDetail(final Video v, boolean direct) {
        detailPanel.setVisibility(View.VISIBLE);
        detailPanel.removeAllViews();

        TextView name = new TextView(this);
        name.setText(v.name.isEmpty() ? "选集" : v.name);
        name.setTextColor(Color.WHITE);
        name.setTextSize(21);
        name.setMaxLines(2);
        detailPanel.addView(name, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView meta = new TextView(this);
        meta.setText((v.category.isEmpty() ? "" : v.category)
                + (v.remarks.isEmpty() ? "" : "  ·  " + v.remarks)
                + (siteLabel.isEmpty() ? "" : "  ·  " + siteLabel));
        meta.setTextColor(0xFF808090);
        meta.setTextSize(12);
        meta.setPadding(0, dp(4), 0, dp(8));
        detailPanel.addView(meta, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final TextView intro = new TextView(this);
        intro.setText(v.intro.isEmpty() ? "简介加载中…" : v.intro);
        intro.setTextColor(0xFF9A9AB0);
        intro.setTextSize(12);
        intro.setLineSpacing(dp(2), 1f);
        intro.setMaxLines(4);
        intro.setEllipsize(TextUtils.TruncateAt.END);
        detailPanel.addView(intro, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final LinearLayout lineRow = new LinearLayout(this);
        lineRow.setOrientation(LinearLayout.HORIZONTAL);
        lineRow.setPadding(0, dp(8), 0, 0);
        detailPanel.addView(lineRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final TextView epTitle = new TextView(this);
        epTitle.setText("加载选集…");
        epTitle.setTextColor(0xFF4FC3F7);
        epTitle.setTextSize(13);
        epTitle.setPadding(0, dp(6), 0, dp(4));
        detailPanel.addView(epTitle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final ScrollView sc = new ScrollView(this);
        sc.setVerticalScrollBarEnabled(false);
        final LinearLayout epBox = new LinearLayout(this);
        epBox.setOrientation(LinearLayout.VERTICAL);
        sc.addView(epBox, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        detailPanel.addView(sc, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView close = new TextView(this);
        close.setText("✕ 关闭选集（返回键）");
        close.setTextColor(0xFF808090);
        close.setTextSize(12);
        close.setPadding(0, dp(8), 0, 0);
        close.setFocusable(true);
        close.setFocusableInTouchMode(true);
        close.setClickable(true);
        close.setOnFocusChangeListener((vv, f) -> close.setTextColor(f ? Color.WHITE : 0xFF808090));
        close.setOnClickListener(vv -> hideDetail());
        detailPanel.addView(close, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (!v.lines.isEmpty()) {
            renderLines(v, lineRow, epTitle, epBox);
            return;
        }

        // 6.0 遗留兼容：直接带着 id 进来的，这里异步补详情
        new Thread(() -> {
            try {
                JSONObject d = JSONUtil.parse(ReposHttp.get(url("ids=" + v.id)));
                JSONArray list = d == null ? null : d.optJSONArray("list");
                if (list == null || list.length() == 0) throw new Exception("详情为空");
                JSONObject o = list.getJSONObject(0);
                String blurb = s(o.optString("vod_content")).replaceAll("<[^>]+>", "").trim();
                v.intro = blurb.length() > 220 ? blurb.substring(0, 220) + "…" : blurb;
                if (v.name.isEmpty()) v.name = s(o.optString("vod_name"));
                parseLines(o, v.lines);
                handler.post(() -> {
                    if (isFinishing()) return;
                    intro.setText(v.intro.isEmpty() ? "暂无简介" : v.intro);
                    name.setText(v.name.isEmpty() ? "选集" : v.name);
                    if (v.lines.isEmpty()) {
                        epTitle.setText("这部片在这个源没有可播地址，返回换一个源");
                        return;
                    }
                    renderLines(v, lineRow, epTitle, epBox);
                });
            } catch (Exception e) {
                handler.post(() -> {
                    if (isFinishing()) return;
                    epTitle.setText("选集获取失败：" + brief(e) + "（返回键关闭）");
                });
            }
        }, "vod-detail").start();
    }

    /** 把 vod_play_from / vod_play_url 拆成多条线路，m3u8 线路排前面 */
    private static void parseLines(JSONObject o, List<Line> out) {
        out.clear();
        String fromStr = s(o.optString("vod_play_from"));
        String urlStr = s(o.optString("vod_play_url"));

        // 少数接口把 play_url 给成数组
        if (urlStr.isEmpty()) {
            JSONArray arr = o.optJSONArray("vod_play_url");
            if (arr != null) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < arr.length(); i++) {
                    if (i > 0) sb.append("$$$");
                    sb.append(s(arr.optString(i)));
                }
                urlStr = sb.toString();
            }
        }
        if (urlStr.isEmpty()) return;

        String[] froms = fromStr.isEmpty() ? new String[0] : fromStr.split("\\$\\$\\$");
        String[] urls = urlStr.split("\\$\\$\\$");
        for (int i = 0; i < urls.length; i++) {
            String from = i < froms.length && !froms[i].trim().isEmpty() ? froms[i].trim() : ("线路" + (i + 1));
            Line line = new Line(from);
            for (String seg : urls[i].split("#")) {
                seg = seg.trim();
                if (seg.isEmpty()) continue;
                int d = seg.indexOf('$');
                if (d > 0) {
                    String nm = seg.substring(0, d).trim();
                    String u = seg.substring(d + 1).trim();
                    if (u.startsWith("http")) line.eps.add(new Ep(nm.isEmpty() ? ("第" + (line.eps.size() + 1) + "集") : nm, u));
                } else if (seg.startsWith("http")) {
                    line.eps.add(new Ep("第" + (line.eps.size() + 1) + "集", seg));
                }
            }
            if (!line.eps.isEmpty()) out.add(line);
        }

        // m3u8 线路优先：这些才是 ExoPlayer 能直接吃的直链
        Collections.sort(out, new Comparator<Line>() {
            @Override public int compare(Line a, Line b) { return score(b.from) - score(a.from); }
        });
    }

    private static int score(String from) {
        String f = from == null ? "" : from.toLowerCase();
        int sc = f.contains("m3u8") ? 100 : 0;
        String[] good = {"bfzy", "lz", "ffzy", "js", "hn", "gs", "wsy", "xigua", "1080"};
        for (String g : good) if (f.contains(g)) { sc += 10; break; }
        return sc;
    }

    private void renderLines(final Video v, LinearLayout lineRow, TextView epTitle,
                             LinearLayout epBox) {
        lineRow.removeAllViews();
        if (v.line >= v.lines.size()) v.line = 0;

        for (int i = 0; i < v.lines.size(); i++) {
            final int idx = i;
            Line ln = v.lines.get(i);
            TextView t = new TextView(this);
            t.setText(ln.from + "(" + ln.eps.size() + ")");
            t.setTextSize(11);
            t.setPadding(dp(9), dp(4), dp(9), dp(4));
            boolean sel = idx == v.line;
            GradientDrawable n = round(sel ? 0xFF2C5D75 : 0xFF1C1C2A, 0, 0);
            GradientDrawable f = round(0xFF264A5E, dp(1.5f), 0xFF4FC3F7);
            t.setBackground(n);
            t.setTextColor(sel ? 0xFFFFFFFF : 0xFFA0A0B8);
            t.setFocusable(true);
            t.setFocusableInTouchMode(true);
            t.setClickable(true);
            t.setOnFocusChangeListener((vv, has) -> t.setBackground(has ? f : n));
            t.setOnClickListener(vv -> { v.line = idx; renderLines(v, lineRow, epTitle, epBox); });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, dp(6), 0);
            lineRow.addView(t, lp);
        }

        final Line line = v.lines.get(v.line);
        epTitle.setText("选集 · " + line.from + "（" + line.eps.size() + " 集）　左右选线路，OK 播放");
        epBox.removeAllViews();
        LinearLayout row = null;
        for (int i = 0; i < line.eps.size(); i++) {
            if (i % 5 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                epBox.addView(row, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            final int ii = i;
            TextView ep = new TextView(this);
            ep.setText(line.eps.get(i).name);
            ep.setTextColor(Color.WHITE);
            ep.setTextSize(11);
            ep.setGravity(Gravity.CENTER);
            ep.setSingleLine(true);
            ep.setEllipsize(TextUtils.TruncateAt.END);
            ep.setPadding(dp(4), dp(7), dp(4), dp(7));
            GradientDrawable en = round(0xFF2A2A3C, 0, 0);
            GradientDrawable ef = round(0xFF4FC3F7, 0, 0);
            ep.setBackground(en);
            ep.setFocusable(true);
            ep.setFocusableInTouchMode(true);
            ep.setClickable(true);
            ep.setOnFocusChangeListener((vv, has) -> {
                ep.setBackground(has ? ef : en);
                ep.setTextColor(has ? 0xFF101018 : Color.WHITE);
            });
            ep.setOnClickListener(vv -> playEpisode(v, ii));
            LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            elp.setMargins(dp(3), dp(3), dp(3), dp(3));
            row.addView(ep, elp);
        }

        if (epBox.getChildCount() > 0) {
            final LinearLayout firstRow = (LinearLayout) epBox.getChildAt(0);
            if (firstRow.getChildCount() > 0) {
                final View first = firstRow.getChildAt(0);
                first.post(first::requestFocus);
            }
        }
    }

    /** 播放某一集；同一集在其它线路也有的，一起带过去做失败重试 */
    private void playEpisode(Video v, int epIndex) {
        Line line = v.lines.get(v.line);
        if (epIndex >= line.eps.size()) return;
        Ep ep = line.eps.get(epIndex);

        List<String> alts = new ArrayList<>();
        for (int li = 0; li < v.lines.size(); li++) {
            if (li == v.line) continue;
            List<Ep> other = v.lines.get(li).eps;
            if (other.size() != line.eps.size()) continue;   // 集数不同，不敢按序号对应
            String u = other.get(epIndex).url;
            if (!u.equals(ep.url) && !alts.contains(u)) alts.add(u);
            if (alts.size() >= 3) break;
        }

        PlayActivity.launch(this, ep.url, v.name + " " + ep.name, alts);
    }

    private void hideDetail() {
        detailPanel.setVisibility(View.GONE);
        detailPanel.removeAllViews();
    }

    private void showSearchDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(28), dp(16), dp(28), dp(8));
        final EditText in = new EditText(this);
        in.setHint("输入片名关键词");
        box.addView(in);
        new android.app.AlertDialog.Builder(this)
                .setTitle("搜索 " + siteLabel)
                .setView(box)
                .setPositiveButton("搜索", (d, w) -> {
                    String wd = in.getText().toString().trim();
                    if (!wd.isEmpty()) doSearch(wd);
                })
                .setNegativeButton("取消", null).show();
    }

    private void toast(String m) { Toast.makeText(this, m, Toast.LENGTH_SHORT).show(); }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (detailPanel.getVisibility() == View.VISIBLE) {
            if (keyCode == KeyEvent.KEYCODE_BACK) { hideDetail(); return true; }
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                // 详情打开时左右键在面板内走焦点，不翻背后列表
                return super.onKeyDown(keyCode, event);
            }
        } else if (keyCode == KeyEvent.KEYCODE_BACK) {
            finish();
            return true;
        }
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (page > 1) load(page - 1, currentCategory); else toast("已是第一页");
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                load(page + 1, currentCategory);
                return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
