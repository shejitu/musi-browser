package com.mytv.lite;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import static android.view.ViewGroup.LayoutParams;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 点播界面：海报墙（苹果CMS V10 标准 api.php?ac=videolist 协议）。
 * 分类导航 + 搜索 + 5列海报网格 + 详情(简介+选集) + 播放。
 */
public class VodActivity extends Activity {

    static class Video {
        String id, name, pic, category, remarks;
        List<String[]> episodes = new ArrayList<>(); // [name, url]
    }

    private String apiBase;         // http://host/api.php?ac=videolist&pg=
    private LinearLayout navRow, grid, detailPanel;
    private ScrollView gridScroll;
    private TextView status, searchText;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Video> videos = new ArrayList<>();
    private final List<String> categories = new ArrayList<>();
    private int page = 1, focus = 0, currentCategory = -1; // -1=全部

    public static void launch(Context ctx, String apiUrl, String siteName) {
        android.content.Intent it = new android.content.Intent(ctx, VodActivity.class);
        it.putExtra("api", normalizeApi(apiUrl));
        it.putExtra("site", siteName);
        ctx.startActivity(it);
    }

    /** 各类 CMS 接口统一转成 videolist 协议 */
    static String normalizeApi(String api) {
        api = api.trim();
        // 已是完整资源站接口
        if (api.contains("ac=") || api.contains("at=json")) return api;
        // /api.php/provide/vod/ → 苹果CMS10
        if (api.contains("/api.php/provide/vod")) {
            return api.endsWith("/") ? api + "?ac=videolist" : api + "/?ac=videolist";
        }
        // 裸域名或目录 → 常见路径猜测
        String base = api.endsWith("/") ? api : api + "/";
        if (!api.contains(".php") && !api.contains("/api")) return base + "api.php?ac=videolist";
        return api + (api.contains("?") ? "&" : "?") + "ac=videolist";
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        apiBase = getIntent().getStringExtra("api");
        String site = getIntent().getStringExtra("site");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF0D0D16);
        root.setPadding(dp(24), dp(14), dp(24), dp(14));
        setContentView(root);

        // 顶栏：站名 + 搜索 + 分类导航
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        root.addView(top, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(row1, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        TextView siteName = new TextView(this);
        siteName.setText((site == null ? "点播" : site) + " · 海报墙");
        siteName.setTextColor(0xFF4FC3F7); siteName.setTextSize(18);
        row1.addView(siteName, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

        searchText = new TextView(this);
        searchText.setText("🔍 搜索");
        searchText.setTextColor(Color.WHITE); searchText.setTextSize(15);
        searchText.setPadding(dp(24), dp(6), dp(24), dp(6));
        searchText.setBackgroundColor(0xFF00C4D4);
        searchText.setOnClickListener(v -> showSearchDialog());
        row1.addView(searchText);

        navRow = new LinearLayout(this);
        navRow.setOrientation(LinearLayout.HORIZONTAL);
        top.addView(navRow, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        status = new TextView(this);
        status.setTextColor(0xFF808090); status.setTextSize(13);
        status.setPadding(0, dp(6), 0, dp(6));
        root.addView(status, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        // 内容区：海报网格 + 详情面板
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(body, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));

        gridScroll = new ScrollView(this);
        grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        gridScroll.addView(grid);
        body.addView(gridScroll, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        detailPanel = new LinearLayout(this);
        detailPanel.setOrientation(LinearLayout.VERTICAL);
        detailPanel.setBackgroundColor(0xF2101018);
        detailPanel.setPadding(dp(24), dp(16), dp(24), dp(16));
        detailPanel.setVisibility(android.view.View.GONE);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(480), LayoutParams.MATCH_PARENT);
        dlp.gravity = Gravity.END;
        detailPanel.setLayoutParams(dlp);
        body.addView(detailPanel);

        load(page = 1, currentCategory);
    }

    private void load(final int pg, final int cat) {
        status.setText("加载中… 第" + pg + "页");
        new Thread(() -> {
            try {
                String url = apiBase + (apiBase.contains("?") ? "&" : "?") + "pg=" + pg;
                if (cat >= 0 && cat < categories.size()) url += "&t=" + URLEncoder.encode(categories.get(cat), "UTF-8");
                JSONObject d = JSONUtil.parse(ReposHttp.get(url));
                JSONArray list = d == null ? null : d.optJSONArray("list");
                if (list == null) throw new Exception("接口返回无 list 字段，不是苹果CMS协议");

                JSONArray cls = d.optJSONArray("class");
                final List<String> cats = new ArrayList<>();
                if (cls != null) for (int i = 0; i < cls.length(); i++)
                    cats.add(cls.getJSONObject(i).optString("type_name"));

                final List<Video> vids = new ArrayList<>();
                for (int i = 0; i < list.length(); i++) {
                    JSONObject o = list.getJSONObject(i);
                    Video v = new Video();
                    v.id = o.optString("vod_id");
                    v.name = o.optString("vod_name", "?");
                    v.pic = o.optString("vod_pic", "");
                    v.category = o.optString("type_name", "");
                    v.remarks = o.optString("vod_remarks", "");
                    vids.add(v);
                }
                handler.post(() -> {
                    if (!cats.isEmpty() && categories.isEmpty()) { categories.addAll(cats); buildNav(); }
                    videos.clear(); videos.addAll(vids);
                    status.setText("共 " + videos.size() + " 部 · 第 " + pg + " 页（左右翻页）");
                    buildGrid();
                });
            } catch (Exception e) {
                handler.post(() -> {
                    status.setText("✗ " + e.getMessage());
                    toast("非CMS源，已转网页嗅探模式");
                    // 自动回退：浏览器打开该接口地址嗅探
                    String site = getIntent().getStringExtra("site");
                    android.content.Intent it = new android.content.Intent(this, MainActivity.class);
                    it.putExtra(MainActivity.MODE, MainActivity.MODE_BROWSER);
                    it.putExtra("show_sites", false);
                    it.putExtra("browser_url", apiBase);
                    startActivity(it);
                    finish();
                });
            }
        }).start();
    }

    private void buildNav() {
        navRow.removeAllViews();
        addNavItem("全部", -1);
        for (int i = 0; i < Math.min(categories.size(), 12); i++) {
            final int idx = i;
            addNavItem(categories.get(i), idx);
        }
    }

    private void addNavItem(String name, final int idx) {
        TextView t = new TextView(this);
        t.setText(name);
        t.setTextColor(idx == currentCategory ? 0xFFE42112 : 0xFFB0B0C0);
        t.setTextSize(14);
        t.setPadding(dp(14), dp(6), dp(14), dp(6));
        t.setOnClickListener(v -> { currentCategory = idx; page = 1; categories.clear(); buildNav(); load(1, idx); });
        navRow.addView(t);
    }

    private void buildGrid() {
        grid.removeAllViews();
        LinearLayout row = null;
        for (int i = 0; i < videos.size(); i++) {
            if (i % 5 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                grid.addView(row, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            }
            final Video v = videos.get(i);
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
            clp.setMargins(dp(6), dp(6), dp(6), dp(6));
            card.setLayoutParams(clp);

            ImageView img = new ImageView(this);
            img.setBackgroundColor(0xFF222230);
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(180));
            img.setLayoutParams(ilp);
            img.setTag("img_" + i);
            card.addView(img);

            TextView nm = new TextView(this);
            nm.setText(v.name + (v.remarks.isEmpty() ? "" : " " + v.remarks));
            nm.setTextColor(Color.WHITE); nm.setTextSize(12);
            nm.setMaxLines(1);
            nm.setPadding(dp(4), dp(4), dp(4), dp(8));
            card.addView(nm, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

            card.setOnClickListener(vv -> showDetail(v));
            row.addView(card);
            loadImage(img, v.pic);
        }
    }

    private void loadImage(final ImageView img, String url) {
        if (url == null || url.isEmpty()) return;
        new Thread(() -> {
            try {
                java.net.URLConnection c = new java.net.URL(url).openConnection();
                c.setConnectTimeout(8000);
                java.io.InputStream is = c.getInputStream();
                final android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeStream(is);
                is.close();
                if (bmp != null) handler.post(() -> img.setImageBitmap(bmp));
            } catch (Exception ignored) {}
        }).start();
    }

    private void showDetail(final Video v) {
        detailPanel.setVisibility(android.view.View.VISIBLE);
        detailPanel.removeAllViews();
        TextView close = new TextView(this);
        close.setText("✕ 关闭");
        close.setTextColor(0xFF808090);
        detailPanel.addView(close, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        close.setOnClickListener(vv -> detailPanel.setVisibility(android.view.View.GONE));

        TextView name = new TextView(this);
        name.setText(v.name);
        name.setTextColor(Color.WHITE); name.setTextSize(20);
        name.setPadding(0, dp(10), 0, dp(6));
        detailPanel.addView(name, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        TextView cat = new TextView(this);
        cat.setText(v.category + (v.remarks.isEmpty() ? "" : " · " + v.remarks));
        cat.setTextColor(0xFF808090); cat.setTextSize(13);
        detailPanel.addView(cat, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        final TextView intro = new TextView(this);
        intro.setTextColor(0xFFB0B0C0); intro.setTextSize(13);
        intro.setPadding(0, dp(8), 0, 0);
        detailPanel.addView(intro, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        TextView epTitle = new TextView(this);
        epTitle.setText("加载选集…");
        epTitle.setTextColor(0xFF4FC3F7); epTitle.setTextSize(14);
        epTitle.setPadding(0, dp(12), 0, 0);
        detailPanel.addView(epTitle, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        final LinearLayout epBox = new LinearLayout(this);
        epBox.setOrientation(LinearLayout.VERTICAL);
        detailPanel.addView(epBox, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        new Thread(() -> {
            try {
                JSONObject d = JSONUtil.parse(ReposHttp.get(apiBase + (apiBase.contains("?") ? "&" : "?") + "ids=" + v.id));
                JSONArray list = d == null ? null : d.optJSONArray("list");
                if (list != null && list.length() > 0) {
                    JSONObject o = list.getJSONObject(0);
                    final String blurb = o.optString("vod_content", "暂无简介").replaceAll("<[^>]+>", "").trim();
                    JSONArray plays = o.optJSONArray("vod_play_url");
                    final List<String[]> eps = new ArrayList<>();
                    if (plays != null) {
                        for (int p = 0; p < plays.length(); p++) {
                            for (String seg : plays.getString(p).split("#")) {
                                int dollar = seg.indexOf('$');
                                if (dollar > 0) eps.add(new String[]{seg.substring(0, dollar), seg.substring(dollar + 1)});
                                else if (!seg.trim().isEmpty()) eps.add(new String[]{"第" + (eps.size()+1) + "集", seg});
                            }
                        }
                    }
                    handler.post(() -> {
                        intro.setText(blurb.length() > 300 ? blurb.substring(0, 300) + "…" : blurb);
                        if (eps.isEmpty()) { epTitle.setText("没有可播放的选集"); return; }
                        epTitle.setText("选集 (" + eps.size() + ")");
                        LinearLayout epRow = null;
                        for (int i = 0; i < eps.size(); i++) {
                            final int ii = i;
                            if (ii % 4 == 0) {
                                epRow = new LinearLayout(this);
                                epRow.setOrientation(LinearLayout.HORIZONTAL);
                                epBox.addView(epRow, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
                            }
                            final String url = eps.get(ii)[1];
                            TextView ep = new TextView(this);
                            ep.setText(eps.get(ii)[0]);
                            ep.setTextColor(Color.WHITE); ep.setTextSize(13);
                            ep.setPadding(dp(10), dp(8), dp(10), dp(8));
                            ep.setBackgroundColor(0xFF2A2A3C);
                            LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
                            elp.setMargins(dp(4), dp(4), dp(4), dp(4));
                            ep.setLayoutParams(elp);
                            ep.setOnClickListener(vv -> {
                                // 直接播放（支持嗅探型 m3u8/mp4 直链）
                                final String epName = eps.get(ii)[0];
                                android.content.Intent it = new android.content.Intent(this, MainActivity.class);
                                it.putExtra(MainActivity.MODE, MainActivity.MODE_BROWSER);
                                it.putExtra("show_sites", false);
                                it.putExtra("play_url", url);
                                it.putExtra("play_title", v.name + " " + epName);
                                startActivity(it);
                            });
                            epRow.addView(ep);
                        }
                    });
                } else handler.post(() -> intro.setText("详情获取失败"));
            } catch (Exception e) {
                handler.post(() -> intro.setText("详情获取失败: " + e.getMessage()));
            }
        }).start();
    }

    private void showSearchDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(30), dp(20), dp(30), dp(10));
        final EditText in = new EditText(this);
        in.setHint("输入片名关键词");
        box.addView(in);
        new android.app.AlertDialog.Builder(this)
            .setTitle("搜索 " + (getIntent().getStringExtra("site") == null ? "" : getIntent().getStringExtra("site")))
            .setView(box)
            .setPositiveButton("搜索", (d, w) -> {
                String wd = in.getText().toString().trim();
                if (wd.isEmpty()) return;
                new Thread(() -> {
                    try {
                        JSONObject d2 = JSONUtil.parse(ReposHttp.get(apiBase + (apiBase.contains("?") ? "&" : "?") + "wd=" + URLEncoder.encode(wd, "UTF-8")));
                        JSONArray list = d2 == null ? null : d2.optJSONArray("list");
                        final List<Video> vids = new ArrayList<>();
                        if (list != null) for (int i = 0; i < list.length(); i++) {
                            JSONObject o = list.getJSONObject(i);
                            Video v = new Video();
                            v.id = o.optString("vod_id"); v.name = o.optString("vod_name", "?");
                            v.pic = o.optString("vod_pic", ""); v.category = o.optString("type_name", "");
                            v.remarks = o.optString("vod_remarks", "");
                            vids.add(v);
                        }
                        handler.post(() -> {
                            videos.clear(); videos.addAll(vids);
                            status.setText("搜索「" + wd + "」: " + vids.size() + " 个结果");
                            buildGrid();
                        });
                    } catch (Exception e) { handler.post(() -> toast("搜索失败: " + e.getMessage())); }
                }).start();
            })
            .setNegativeButton("取消", null).show();
    }

    private void toast(String m) { Toast.makeText(this, m, Toast.LENGTH_SHORT).show(); }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (detailPanel.getVisibility() == android.view.View.VISIBLE) {
            if (keyCode == KeyEvent.KEYCODE_BACK) { detailPanel.setVisibility(android.view.View.GONE); return true; }
        } else if (keyCode == KeyEvent.KEYCODE_BACK) { finish(); return true; }
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (page > 1) load(page - 1, currentCategory);
                else toast("已是第一页");
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                load(page + 1, currentCategory); return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
