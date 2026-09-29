package com.mytv.lite;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.KeyEvent;
import static android.view.ViewGroup.LayoutParams;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/** 仓库管理：内置仓库 + 手动添加 + 一键配置站点（直链源自动进点播，Spider 源开网页嗅探）。 */
public class ReposActivity extends Activity {

    static class Repo { String name, url; Repo(String n, String u) { name=n; url=u; } }

    private static final Repo[] BUILTIN = {
        new Repo("T4接口", "https://gitee.com/free-kingdom/dc/raw/main/T4.json"),
        new Repo("南风接口", "https://raw.githubusercontent.com/yoursmile66/TVBox/main/XC.json"),
        new Repo("俊佬接口", "http://home.jundie.top:81/top98.json"),
        new Repo("游魂多仓", "https://www.iyouhun.com/tv/dc"),
        new Repo("小盒子多仓", "http://xhztv.top/dc/"),
        new Repo("多多影音", "https://bitbucket.org/xduo/cool/raw/main/room.json"),
        new Repo("七星宝盒", "https://qixing.myhkw.com/DC.txt"),
        new Repo("rose仓", "http://47.96.82.41:5188/rose/1"),
        new Repo("饭太硬(备1)", "http://fty.888484.xyz/tv"),
        new Repo("神秘哥哥们", "http://fty.xxooo.cf/tv"),
        new Repo("fish多仓", "https://6800.kstore.vip/fish.json"),
        new Repo("肥猫", "http://肥猫.net/tv"),
    };

    static List<Repo> allRepos(Context ctx) {
        List<Repo> l = new ArrayList<>();
        for (Repo r : BUILTIN) l.add(r);
        try {
            String raw = ctx.getSharedPreferences("mytv", 0).getString("custom_repos", null);
            if (raw != null) {
                JSONArray a = new JSONArray(raw);
                for (int i = 0; i < a.length(); i++) {
                    JSONObject o = a.getJSONObject(i);
                    l.add(new Repo(o.getString("name"), o.getString("url")));
                }
            }
        } catch (Exception ignored) {}
        return l;
    }

    static void addCustomRepo(Context ctx, String name, String url) {
        try {
            JSONArray a = new JSONArray();
            try { a = new JSONArray(ctx.getSharedPreferences("mytv", 0).getString("custom_repos", "[]")); } catch (Exception ignored) {}
            JSONObject o = new JSONObject(); o.put("name", name); o.put("url", url);
            a.put(o);
            ctx.getSharedPreferences("mytv", 0).edit().putString("custom_repos", a.toString()).apply();
        } catch (Exception ignored) {}
    }

    private LinearLayout list;
    private TextView status;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int focus = 0;
    private List<Site> currentSites = null;

    public static void launch(android.content.Context ctx) {
        ctx.startActivity(new android.content.Intent(ctx, ReposActivity.class));
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF191926);
        root.setPadding(dp(40), dp(24), dp(40), dp(24));
        setContentView(root);

        TextView title = new TextView(this);
        title.setText("仓库管理");
        title.setTextColor(Color.WHITE); title.setTextSize(24);
        root.addView(title, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        TextView tip = new TextView(this);
        tip.setText("点仓库名拉取配置；拉取成功后点站点名一键进点播（Spider 源自动转网页嗅探）。");
        tip.setTextColor(0xFF808090); tip.setTextSize(12);
        tip.setPadding(0, dp(4), 0, dp(8));
        root.addView(tip, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        status = new TextView(this);
        status.setTextColor(0xFF4FC3F7); status.setTextSize(13);
        status.setPadding(0, 0, 0, dp(8));
        root.addView(status, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        ScrollView sc = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        sc.addView(list);
        root.addView(sc, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));

        TextView add = new TextView(this);
        add.setText("＋ 手动添加仓库");
        add.setTextColor(0xFF81C784); add.setTextSize(16);
        add.setPadding(dp(16), dp(10), dp(16), dp(10));
        add.setBackgroundColor(0x2281C784);
        add.setOnClickListener(v -> showAddDialog());
        list.addView(add, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        rebuildList();

        TextView proxyRow = new TextView(this);
        proxyRow.setText("⚡ 代理设置（Clash/VPN，用于 YouTube 等境外源）" + (ProxyActivity.enabled(this) ? "  [已启用]" : ""));
        proxyRow.setTextColor(0xFFFFB74D); proxyRow.setTextSize(15);
        proxyRow.setPadding(dp(16), dp(12), dp(16), dp(12));
        proxyRow.setOnClickListener(v -> ProxyActivity.launch(this));
        root.addView(proxyRow, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    private void rebuildList() {
        while (list.getChildCount() > 1) list.removeViewAt(1);
        int i = 1;
        for (final Repo r : allRepos(this)) {
            TextView row = new TextView(this);
            row.setText("▾ " + r.name + "   " + r.url);
            row.setTextColor(Color.WHITE); row.setTextSize(15);
            row.setPadding(dp(16), dp(10), dp(16), dp(10));
            row.setOnClickListener(v -> loadRepo(r));
            list.addView(row, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            i++;
        }
        focus = 1;
        highlight();
    }

    private void showAddDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(30), dp(20), dp(30), dp(10));

        final EditText nameIn = new EditText(this);
        nameIn.setHint("仓库名（如：我的仓库）");
        box.addView(nameIn);
        final EditText urlIn = new EditText(this);
        urlIn.setHint("仓库地址 http://... 或 https://...");
        urlIn.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        box.addView(urlIn);

        new android.app.AlertDialog.Builder(this)
            .setTitle("手动添加仓库")
            .setView(box)
            .setPositiveButton("添加", (d, w) -> {
                String n = nameIn.getText().toString().trim();
                String u = urlIn.getText().toString().trim();
                if (u.isEmpty()) { toast("地址不能为空"); return; }
                if (n.isEmpty()) n = "我的仓库" + (allRepos(this).size() - BUILTIN.length + 1);
                addCustomRepo(this, n, u);
                rebuildList();
                toast("已添加 " + n);
            })
            .setNegativeButton("取消", null).show();
    }

    private void loadRepo(final Repo repo) {
        status.setText("正在拉取: " + repo.name + " …");
        new Thread(() -> {
            try {
                String raw = ReposHttp.get(repo.url);
                JSONObject cfg = JSONUtil.parse(raw);
                if (cfg == null) throw new Exception("无法解析为 JSON（可能加密/混淆）");
                JSONArray store = cfg.optJSONArray("storeHouse");
                if (store != null && store.length() > 0) {
                    StringBuilder sb = new StringBuilder("多仓，含 ").append(store.length()).append(" 个子仓:\n");
                    for (int i = 0; i < Math.min(store.length(), 20); i++) {
                        JSONObject s = store.getJSONObject(i);
                        sb.append("  · ").append(s.optString("sourceName", "?")).append(" → ").append(s.optString("sourceUrl", "")).append("\n");
                    }
                    handler.post(() -> status.setText(sb.toString()));
                    return;
                }
                JSONArray sites = cfg.optJSONArray("sites");
                if (sites == null) throw new Exception("配置里没有 sites 字段");

                List<Site> parsed = new ArrayList<>();
                for (int i = 0; i < sites.length(); i++) {
                    JSONObject s = sites.getJSONObject(i);
                    parsed.add(new Site(s.optString("name", "?"), s.optString("api", ""), s.optInt("type", -1)));
                }
                currentSites = parsed;
                final int t3;
                { int c = 0; for (Site s : parsed) if (s.type == 3) c++; t3 = c; }
                handler.post(() -> {
                    status.setText("✓ " + repo.name + " 共 " + parsed.size() + " 个站点（直链 " + (parsed.size() - t3) + "，Spider转网页 " + t3 + "）— 点站点名进入点播");
                    buildSiteRows();
                });
            } catch (Exception e) {
                handler.post(() -> status.setText("✗ " + repo.name + " 失败: " + e.getMessage()));
            }
        }).start();
    }

    static class Site { String name, api; int type; Site(String n, String a, int t) { name=n; api=a; type=t; } }

    private void buildSiteRows() {
        while (list.getChildCount() > 1) list.removeViewAt(1);
        if (currentSites == null) return;
        for (final Site s : currentSites) {
            TextView row = new TextView(this);
            String tag = s.type == 3 ? " [Spider→网页]" : " [直链]";
            row.setText("▶ " + s.name + tag);
            row.setTextColor(s.type == 3 ? 0xFFCE93D8 : 0xFF81C784);
            row.setTextSize(15);
            row.setPadding(dp(32), dp(8), dp(16), dp(8));
            row.setOnClickListener(v -> openSite(s));
            list.addView(row, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        }
        highlight();
    }

    /** 一键配置：CMS 直链源 → 海报墙点播；其他 → 网页嗅探。 */
    private void openSite(Site s) {
        String api = s.api.trim();
        boolean cmsLike = (s.type == 0 || s.type == 1 || s.type == 4)
                && (api.contains("api.php") || api.contains("provide/vod") || api.contains("ac=videolist"));
        if (cmsLike) {
            VodActivity.launch(this, api, s.name);
            return;
        }
        android.content.Intent it = new android.content.Intent(this, MainActivity.class);
        it.putExtra(MainActivity.MODE, MainActivity.MODE_BROWSER);
        it.putExtra("show_sites", false);
        String target;
        if ((s.type == 0 || s.type == 1 || s.type == 4) && api.startsWith("http")) {
            target = api;
        } else {
            target = "https://www.bing.com/search?q=" + android.net.Uri.encode(s.name + " 在线观看");
        }
        it.putExtra("browser_url", target);
        startActivity(it);
    }

    private void highlight() {
        for (int i = 0; i < list.getChildCount(); i++) {
            TextView r = (TextView) list.getChildAt(i);
            if (i == focus) { r.setTextColor(0xFFFFB74D); r.setBackgroundColor(0x33FFFFFF); }
            else if (i != 0) { r.setBackgroundColor(Color.TRANSPARENT); }
        }
    }

    private void toast(String m) { Toast.makeText(this, m, Toast.LENGTH_SHORT).show(); }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP: focus = Math.max(1, focus - 1); highlight(); return true;
            case KeyEvent.KEYCODE_DPAD_DOWN: focus = Math.min(list.getChildCount() - 1, focus + 1); highlight(); return true;
            case KeyEvent.KEYCODE_DPAD_CENTER: case KeyEvent.KEYCODE_ENTER:
                list.getChildAt(focus).performClick(); return true;
            case KeyEvent.KEYCODE_BACK: finish(); return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
