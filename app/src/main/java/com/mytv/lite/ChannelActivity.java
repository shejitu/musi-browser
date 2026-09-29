package com.mytv.lite;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import static android.view.ViewGroup.LayoutParams;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/** 影视频道：自动扫描内置仓库，找到第一个可用 CMS 源并进入其推荐海报墙。 */
public class ChannelActivity extends Activity {

    private TextView status;
    private LinearLayout repoList;
    private final Handler handler = new Handler(Looper.getMainLooper());

    public static void launch(android.content.Context ctx) {
        ctx.startActivity(new android.content.Intent(ctx, ChannelActivity.class));
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
        title.setText("影视频道 — 正在寻找可用的影视源…");
        title.setTextColor(Color.WHITE); title.setTextSize(22);
        root.addView(title, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        status = new TextView(this);
        status.setTextColor(0xFF4FC3F7); status.setTextSize(14);
        status.setPadding(0, dp(10), 0, dp(10));
        root.addView(status, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        ScrollView sc = new ScrollView(this);
        repoList = new LinearLayout(this);
        repoList.setOrientation(LinearLayout.VERTICAL);
        sc.addView(repoList);
        root.addView(sc, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));

        // 显示手动添加的仓库 + 内置仓库，逐个扫描
        List<ReposActivity.Repo> repos = ReposActivity.allRepos(this);
        scanNext(repos, 0);
    }

    private void scanNext(final List<ReposActivity.Repo> repos, final int idx) {
        if (idx >= repos.size()) {
            status.setText("所有仓库都没有可用的 CMS 海报墙源。请到「仓库管理」手动添加 CMS 接口地址\n（格式如 http://xxx/api.php?ac=videolist 或 http://xxx/api.php/provide/vod/）");
            return;
        }
        final ReposActivity.Repo r = repos.get(idx);
        status.setText("扫描仓库 " + (idx + 1) + "/" + repos.size() + ": " + r.name + " …");
        new Thread(() -> {
            try {
                JSONObject cfg = JSONUtil.parse(ReposHttp.get(r.url));
                JSONArray sites = cfg == null ? null : cfg.optJSONArray("sites");
                if (sites != null) {
                    // 找第一个 http 直链源
                    for (int i = 0; i < sites.length(); i++) {
                        JSONObject s = sites.getJSONObject(i);
                        String api = s.optString("api", "").trim();
                        int type = s.optInt("type", -1);
                        if (type != 3 && api.startsWith("http")) {
                            final String name = s.optString("name", "影视源");
                            handler.post(() -> {
                                status.setText("✓ 找到影视源: " + name + "（来自 " + r.name + "），进入海报墙…");
                                toast("源: " + name);
                                VodActivity.launch(this, api, name);
                                finish();
                            });
                            return;
                        }
                    }
                }
                handler.post(() -> addSkipRow(r.name, "无直链源"));
                scanNext(repos, idx + 1);
            } catch (Exception e) {
                handler.post(() -> { addSkipRow(r.name, e.getMessage()); scanNext(repos, idx + 1); });
            }
        }).start();
    }

    private void addSkipRow(String name, String reason) {
        TextView t = new TextView(this);
        t.setText("· " + name + "（跳过: " + reason + "）");
        t.setTextColor(0xFF606070); t.setTextSize(13);
        t.setPadding(dp(12), dp(6), 0, dp(6));
        repoList.addView(t);
    }

    private void toast(String m) { Toast.makeText(this, m, Toast.LENGTH_SHORT).show(); }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == android.view.KeyEvent.KEYCODE_BACK) { finish(); return true; }
        return super.onKeyDown(keyCode, event);
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
