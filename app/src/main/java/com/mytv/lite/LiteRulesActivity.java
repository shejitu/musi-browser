package com.mytv.lite;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/**
 * 轻站规则管理。
 *
 * 影视站改版很频繁，把规则做成「可导入」而不是写死在代码里，就不用为了适配
 * 一个站点重新发版：导入一份 {rules:[{hosts:[...],selector/js}]} 的 JSON 即可。
 */
public class LiteRulesActivity extends Activity {

    private LinearLayout listHost;
    private TextView status;
    private final Handler handler = new Handler(Looper.getMainLooper());

    public static void launch(android.content.Context ctx) {
        ctx.startActivity(new android.content.Intent(ctx, LiteRulesActivity.class));
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF191926);
        root.setPadding(dp(40), dp(26), dp(40), dp(24));
        setContentView(root);

        TextView title = new TextView(this);
        title.setText("轻站规则");
        title.setTextColor(Color.WHITE);
        title.setTextSize(24);
        root.addView(title);

        TextView desc = new TextView(this);
        desc.setText("规则按域名匹配，命中后用它的选择器/脚本抓取选集与直链；未命中则走通用嗅探。\n导入格式：{\"rules\":[{\"id\":\"x\",\"name\":\"某站\",\"hosts\":[\"a.com\"],\"selector\":\"a[href*=/play/]\"}]}");
        desc.setTextColor(0xFF8A8AA0);
        desc.setTextSize(13);
        desc.setPadding(0, dp(8), 0, dp(14));
        root.addView(desc);

        status = new TextView(this);
        status.setTextColor(0xFFFFB74D);
        status.setTextSize(13);
        status.setPadding(0, 0, 0, dp(10));
        root.addView(status);

        // 导入行
        LinearLayout importRow = new LinearLayout(this);
        importRow.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(importRow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final EditText input = new EditText(this);
        input.setHint("规则 JSON 的网址（https://…/lite_rules.json）");
        input.setTextSize(13);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        ilp.setMargins(0, 0, dp(12), 0);
        importRow.addView(input, ilp);

        importRow.addView(btn("导入", 0xFF1E5F3A, v -> {
            final String u = input.getText().toString().trim();
            if (u.isEmpty()) { status.setText("请先填规则地址"); return; }
            status.setText("导入中…");
            new Thread(() -> {
                try {
                    final int n = LiteEngine.importRules(this, u);
                    handler.post(() -> { status.setText("已导入 " + n + " 条规则"); rebuild(); });
                } catch (final Throwable e) {
                    handler.post(() -> status.setText("导入失败: " + e.getMessage()));
                }
            }).start();
        }));

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setPadding(0, dp(12), 0, dp(12));
        root.addView(btnRow);

        addBtn(btnRow, btn("拉取官方规则", 0xFF2B2B3D, v -> {
            status.setText("拉取中…");
            new Thread(() -> {
                try {
                    final int n = LiteEngine.pullOfficialRules(this);
                    handler.post(() -> { status.setText("官方规则已更新：" + n + " 条"); rebuild(); });
                } catch (final Throwable e) {
                    handler.post(() -> status.setText("拉取失败: " + e.getMessage()));
                }
            }).start();
        }));
        addBtn(btnRow, btn("清除用户规则", 0xFF3A1F26, v -> {
            LiteEngine.clearUserRules(this);
            status.setText("已清除用户规则（内置规则仍生效）");
            rebuild();
        }));

        ScrollView sc = new ScrollView(this);
        listHost = new LinearLayout(this);
        listHost.setOrientation(LinearLayout.VERTICAL);
        sc.addView(listHost);
        root.addView(sc, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        rebuild();
    }

    private void rebuild() {
        listHost.removeAllViews();
        List<LiteEngine.Rule> rules = LiteEngine.rules(this);
        TextView head = new TextView(this);
        head.setText("当前生效规则 " + rules.size() + " 条" + (LiteEngine.hasUserRules(this) ? "（含用户导入）" : ""));
        head.setTextColor(0xFF4FC3F7);
        head.setTextSize(14);
        head.setPadding(0, 0, 0, dp(8));
        listHost.addView(head);
        for (LiteEngine.Rule r : rules) {
            TextView t = new TextView(this);
            StringBuilder h = new StringBuilder();
            for (int i = 0; i < r.hosts.size(); i++) {
                if (i > 0) h.append(", ");
                h.append(r.hosts.get(i));
            }
            t.setText((r.builtin ? "· " : "· 用户 ") + r.name + "\n   " + h
                    + (r.selector.isEmpty() ? "" : "   [选择器]")
                    + (r.js.isEmpty() ? "" : "   [脚本]"));
            t.setTextColor(0xFFCFCFE0);
            t.setTextSize(13);
            t.setPadding(dp(4), dp(8), dp(4), dp(8));
            listHost.addView(t);
        }
        if (rules.isEmpty()) {
            TextView t = new TextView(this);
            t.setText("没有规则，将全部走通用嗅探（仍可用）");
            t.setTextColor(0xFF8A8AA0);
            t.setTextSize(13);
            listHost.addView(t);
        }
    }

    private Button btn(String text, int bg, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(13);
        b.setAllCaps(false);
        b.setBackgroundColor(bg);
        b.setTextColor(Color.WHITE);
        b.setOnClickListener(l);
        b.setFocusable(true);
        b.setOnFocusChangeListener((v, has) -> b.setBackgroundColor(has ? 0xFF4FC3F7 : bg));
        return b;
    }

    private void addBtn(LinearLayout row, Button b) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, dp(12), 0);
        row.addView(b, lp);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) { finish(); return true; }
        return super.onKeyDown(keyCode, event);
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
