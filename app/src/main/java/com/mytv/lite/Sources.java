package com.mytv.lite;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/** 频道源管理：内置公开源 + 自定义订阅，设置里挑选。 */
public class Sources {

    public static class Source {
        public String name;       // 显示名
        public String asset;      // 内置文件名（null=远程）
        public String url;        // 远程地址
        public boolean builtin;
        Source(String n, String a, String u, boolean b) { name=n; asset=a; url=u; builtin=b; }
    }

    public static List<Source> builtins() {
        List<Source> l = new ArrayList<>();
        l.add(new Source("中国大陆 (IPTV-org)", "iptv_cn.m3u", null, true));
        l.add(new Source("香港 (IPTV-org)", "iptv_hk.m3u", null, true));
        l.add(new Source("台湾 (IPTV-org)", "iptv_tw.m3u", null, true));
        l.add(new Source("国际新闻 (IPTV-org)", "iptv_intl_news.m3u", null, true));
        return l;
    }

    public static String active(Context ctx) {
        return ctx.getSharedPreferences("mytv", 0).getString("active_source", "iptv_cn.m3u");
    }
    public static void setActive(Context ctx, String key) {
        ctx.getSharedPreferences("mytv", 0).edit().putString("active_source", key).apply();
    }
    public static String customUrl(Context ctx) {
        return ctx.getSharedPreferences("mytv", 0).getString("custom_url", "");
    }
    public static void setCustomUrl(Context ctx, String u) {
        ctx.getSharedPreferences("mytv", 0).edit().putString("custom_url", u == null ? "" : u).apply();
    }
    public static String customName(Context ctx) {
        return ctx.getSharedPreferences("mytv", 0).getString("custom_name", "我的订阅");
    }
    public static void setCustomName(Context ctx, String n) {
        ctx.getSharedPreferences("mytv", 0).edit().putString("custom_name", n).apply();
    }

    /** 读取当前激活源的 m3u 内容：内置走 assets，远程/自定义走网络。 */
    public static String loadContent(Context ctx, Source s) throws Exception {
        if (s.builtin && s.asset != null) {
            BufferedReader r = new BufferedReader(new InputStreamReader(ctx.getAssets().open(s.asset), "UTF-8"));
            StringBuilder sb = new StringBuilder(); String line;
            while ((line = r.readLine()) != null) sb.append(line).append('\n');
            r.close();
            return sb.toString();
        }
        HttpURLConnection c = (HttpURLConnection) new URL(s.url).openConnection();
        c.setConnectTimeout(10000); c.setReadTimeout(15000);
        c.setRequestProperty("User-Agent", "Mozilla/5.0 MyTV/1.0");
        BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
        StringBuilder sb = new StringBuilder(); String line;
        while ((line = r.readLine()) != null) sb.append(line).append('\n');
        r.close(); c.disconnect();
        return sb.toString();
    }

    /** 设置面板：列出内置源 + 自定义订阅入口。 */
    public interface Callback { void onSourcePicked(); }

    public static View buildPanel(final Activity act, final Callback cb) {
        SharedPreferences p = act.getSharedPreferences("mytv", 0);
        String active = p.getString("active_source", "iptv_cn.m3u");

        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(40, 30, 40, 30);

        TextView title = new TextView(act);
        title.setText("频道源设置");
        title.setTextColor(0xFF4FC3F7); title.setTextSize(20);
        title.setPadding(0, 0, 0, 24);
        box.addView(title);

        for (final Source s : builtins()) {
            String key = s.asset;
            Button b = new Button(act);
            b.setText(("iptv_cn.m3u".equals(active) ? "● " : "○ ") + s.name);
            b.setTextSize(15);
            b.setOnClickListener(v -> { setActive(act, key); cb.onSourcePicked(); });
            box.addView(b);
        }

        String cur = p.getString("custom_url", "");
        Button cb2 = new Button(act);
        cb2.setText((cur.equals(active) ? "● " : "○ ") + "自定义订阅" + (cur.isEmpty() ? "（未设置）" : "：" + p.getString("custom_name", "我的订阅")));
        cb2.setTextSize(15);
        cb2.setOnClickListener(v -> showCustomDialog(act, cb));
        box.addView(cb2);

        ScrollView sc = new ScrollView(act);
        sc.addView(box);
        return sc;
    }

    private static void showCustomDialog(final Activity act, final Callback cb) {
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(40, 30, 40, 30);

        final EditText nameIn = new EditText(act);
        nameIn.setHint("订阅名称（可选）");
        nameIn.setText(customName(act));
        box.addView(nameIn);

        final EditText urlIn = new EditText(act);
        urlIn.setHint("m3u 订阅地址 http://...");
        urlIn.setText(customUrl(act));
        box.addView(urlIn);

        android.app.AlertDialog.Builder dlg = new android.app.AlertDialog.Builder(act);
        dlg.setTitle("自定义订阅");
        dlg.setView(box);
        dlg.setPositiveButton("保存并切换", (d, w) -> {
            setCustomUrl(act, urlIn.getText().toString().trim());
            setCustomName(act, nameIn.getText().toString().trim());
            if (!customUrl(act).isEmpty()) setActive(act, "__custom__");
            cb.onSourcePicked();
        });
        dlg.setNegativeButton("取消", null);
        dlg.show();
    }

    /** 返回当前激活的 Source。 */
    public static Source currentSource(Context ctx) {
        String key = active(ctx);
        if ("__custom__".equals(key)) {
            String u = customUrl(ctx);
            return new Source(customName(ctx), null, u, false);
        }
        for (Source s : builtins()) if (s.asset.equals(key)) return s;
        return builtins().get(0);
    }
}
