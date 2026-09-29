package com.mytv.lite;

import android.os.Bundle;

/** 影视点播入口：仓库选源进海报墙。独立标题，视觉上与"仓库管理"区分。 */
public class RepoPickerActivity extends ReposActivity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 修改标题区，让它看起来是"影视点播"而不是仓库管理
        try {
            android.view.ViewGroup root = (android.view.ViewGroup) findViewById(android.R.id.content);
            relabel(root);
        } catch (Exception ignored) {}
    }

    private void relabel(android.view.ViewGroup vg) {
        for (int i = 0; i < vg.getChildCount(); i++) {
            android.view.View c = vg.getChildAt(i);
            if (c instanceof android.widget.TextView) {
                android.widget.TextView tv = (android.widget.TextView) c;
                if ("仓库管理".equals(tv.getText().toString())) {
                    tv.setText("影视点播 — 选择仓库与站点");
                    return;
                }
            } else if (c instanceof android.view.ViewGroup) {
                relabel((android.view.ViewGroup) c);
            }
        }
    }
}
