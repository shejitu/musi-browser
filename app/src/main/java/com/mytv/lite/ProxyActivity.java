package com.mytv.lite;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import static android.view.ViewGroup.LayoutParams;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** 代理设置：填局域网内 Clash/V2Ray 的 HTTP 代理地址（如 192.168.1.5:7890），播放器与 WebView 全走代理。 */
public class ProxyActivity extends Activity {

    public static String host(Context ctx) { return ctx.getSharedPreferences("mytv", 0).getString("proxy_host", ""); }
    public static int port(Context ctx) { return ctx.getSharedPreferences("mytv", 0).getInt("proxy_port", 0); }
    public static boolean enabled(Context ctx) { return !host(ctx).isEmpty() && port(ctx) > 0; }

    /** ExoPlayer 数据源应用代理：OkHttp DataSource 原生支持 proxy */
    public static androidx.media3.datasource.HttpDataSource.Factory httpFactory(Context ctx) {
        okhttp3.OkHttpClient client;
        if (enabled(ctx)) {
            client = new okhttp3.OkHttpClient.Builder()
                    .proxy(new java.net.Proxy(java.net.Proxy.Type.HTTP,
                            new java.net.InetSocketAddress(host(ctx), port(ctx))))
                    .build();
        } else {
            client = new okhttp3.OkHttpClient.Builder().build();
        }
        return new androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(client)
                .setUserAgent("Mozilla/5.0 (Linux; Android TV) MyTV/1.0");
    }

    /** WebView 代理（androidx.webkit ProxyController，设备 WebView 支持时生效） */
    public static void applyToWebView(android.webkit.WebView web, Context ctx) {
        if (!enabled(ctx)) return;
        try {
            androidx.webkit.ProxyConfig cfg = new androidx.webkit.ProxyConfig.Builder()
                    .addProxyRule(host(ctx) + ":" + port(ctx)).build();
            androidx.webkit.ProxyController.getInstance().setProxyOverride(cfg,
                    runnable -> new Thread(runnable).start(), null);
        } catch (Throwable ignored) {}
    }

    public static void launch(android.content.Context ctx) {
        ctx.startActivity(new android.content.Intent(ctx, ProxyActivity.class));
    }

    private EditText hostIn, portIn;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF191926);
        root.setPadding(dp(48), dp(32), dp(48), dp(32));
        setContentView(root);

        TextView title = new TextView(this);
        title.setText("代理设置");
        title.setTextColor(Color.WHITE); title.setTextSize(24);
        root.addView(title, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        TextView tip = new TextView(this);
        tip.setText("前提：你的局域网内已有一个可用的 HTTP 代理（如电脑/路由器上跑 Clash，混合端口 7890）。\n"
                + "填入后，本应用的网页浏览与视频播放将全部走代理，可用于观看 YouTube 等境外内容。\n"
                + "留空并保存 = 关闭代理。");
        tip.setTextColor(0xFF808090); tip.setTextSize(13);
        tip.setPadding(0, dp(10), 0, dp(20));
        root.addView(tip, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        hostIn = new EditText(this);
        hostIn.setHint("代理 IP，例如 192.168.1.5");
        hostIn.setText(host(this));
        root.addView(hostIn, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        portIn = new EditText(this);
        portIn.setHint("端口，例如 7890");
        portIn.setText(port(this) > 0 ? String.valueOf(port(this)) : "");
        root.addView(portIn, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        TextView save = new TextView(this);
        save.setText("保存（返回键取消）");
        save.setTextColor(Color.WHITE); save.setTextSize(17);
        save.setPadding(dp(20), dp(14), dp(20), dp(14));
        save.setBackgroundColor(0xFF1E88E5);
        save.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(20), 0, 0);
        save.setLayoutParams(lp);
        save.setOnClickListener(v -> doSave());
        root.addView(save);
    }

    private void doSave() {
        String h = hostIn.getText().toString().trim();
        String p = portIn.getText().toString().trim();
        getSharedPreferences("mytv", 0).edit()
                .putString("proxy_host", h)
                .putInt("proxy_port", p.isEmpty() ? 0 : Integer.parseInt(p))
                .apply();
        Toast.makeText(this, h.isEmpty() ? "代理已关闭" : "代理已设置: " + h + ":" + p, Toast.LENGTH_LONG).show();
        finish();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) { finish(); return true; }
        return super.onKeyDown(keyCode, event);
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
