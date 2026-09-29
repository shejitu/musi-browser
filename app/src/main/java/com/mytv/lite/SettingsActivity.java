package com.mytv.lite;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import static android.view.ViewGroup.LayoutParams;

/** 设置页：频道源管理 + 局域网信息。 */
public class SettingsActivity extends Activity {

    private LinearLayout panelHost;

    public static void launch(android.content.Context ctx) {
        ctx.startActivity(new android.content.Intent(ctx, SettingsActivity.class));
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF191926);
        root.setPadding(dp(40), dp(28), dp(40), dp(28));
        setContentView(root);

        TextView title = new TextView(this);
        title.setText("设置");
        title.setTextColor(Color.WHITE); title.setTextSize(26);
        root.addView(title, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        TextView ip = new TextView(this);
        ip.setText("局域网控制: http://" + LiteServer.localIp(this) + ":8080  （电脑浏览器打开即可推送/传文件）");
        ip.setTextColor(0xFF808090); ip.setTextSize(14);
        ip.setPadding(0, dp(8), 0, dp(16));
        root.addView(ip, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        panelHost = new LinearLayout(this);
        panelHost.setOrientation(LinearLayout.VERTICAL);
        root.addView(panelHost, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));

        rebuild();
    }

    private void rebuild() {
        panelHost.removeAllViews();
        panelHost.addView(Sources.buildPanel(this, this::rebuild));
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) { finish(); return true; }
        return super.onKeyDown(keyCode, event);
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
