package com.mytv.lite;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.KeyEvent;
import static android.view.ViewGroup.LayoutParams;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 本地文件：Download/download 目录，视频点击播放，APK 点击安装。 */
public class FilesActivity extends Activity {

    private LinearLayout list;
    private TextView status;

    public static void launch(android.content.Context ctx) {
        ctx.startActivity(new android.content.Intent(ctx, FilesActivity.class));
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
        title.setText("本地文件（Download/download）");
        title.setTextColor(Color.WHITE); title.setTextSize(24);
        root.addView(title, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        status = new TextView(this);
        status.setTextColor(0xFF808090); status.setTextSize(13);
        status.setPadding(0, dp(6), 0, dp(10));
        root.addView(status, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        ScrollView sc = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        sc.addView(list);
        root.addView(sc, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));

        refresh();
    }

    private void refresh() {
        list.removeAllViews();
        File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "download");
        if (!dir.exists()) dir = new File(getExternalFilesDir(null), "download");
        status.setText("目录: " + dir.getAbsolutePath());

        List<File> files = new ArrayList<>();
        File[] fs = dir.listFiles();
        if (fs != null) { Collections.addAll(files, fs); Collections.sort(files, (a, b2) -> Long.compare(b2.lastModified(), a.lastModified())); }
        if (files.isEmpty()) {
            status.setText(status.getText() + "  —  空目录，先用电脑控制台(http://电视IP:8080)上传文件");
            return;
        }
        for (final File f : files) {
            TextView row = new TextView(this);
            String type = kindOf(f.getName());
            row.setText(type + "\n" + f.getName() + "   " + (f.length() / 1024 / 1024) + " MB");
            row.setTextColor(Color.WHITE); row.setTextSize(18);
            row.setLineSpacing(dp(4), 1f);
            row.setPadding(dp(24), dp(18), dp(24), dp(18));
            row.setBackgroundColor(0xFF222230);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, dp(8), 0, dp(8));
            row.setLayoutParams(lp);
            row.setFocusable(true);
            row.setOnFocusChangeListener((v, hasF) -> row.setBackgroundColor(hasF ? 0xFF4FC3F7 : 0xFF222230));
            row.setOnClickListener(v -> open(f, type));
            list.addView(row, lp);
        }
    }

    private String kindOf(String name) {
        String n = name.toLowerCase();
        if (n.endsWith(".apk")) return "📦安装";
        if (n.endsWith(".m3u8") || n.endsWith(".mp4") || n.endsWith(".mkv") || n.endsWith(".flv")
            || n.endsWith(".ts") || n.endsWith(".avi") || n.endsWith(".webm") || n.endsWith(".mov")) return "▶视频";
        if (n.endsWith(".mp3") || n.endsWith(".flac") || n.endsWith(".wav")) return "🎵音频";
        if (n.endsWith(".jpg") || n.endsWith(".png") || n.endsWith(".webp")) return "🖼图片";
        return "📄文件";
    }

    private void open(final File f, String type) {
        try {
            Uri uri;
            if (android.os.Build.VERSION.SDK_INT >= 24) {
                uri = androidx.core.content.FileProvider.getUriForFile(this, getPackageName() + ".files", f);
            } else uri = Uri.fromFile(f);

            if (type.contains("安装")) {
                Intent it = new Intent(Intent.ACTION_VIEW);
                it.setDataAndType(uri, "application/vnd.android.package-archive");
                it.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(it);
            } else if (type.contains("视频") || type.contains("音频")) {
                // 走自家播放器（全屏+嗅探一致的体验）
                Intent it = new Intent(this, MainActivity.class);
                it.putExtra(MainActivity.MODE, MainActivity.MODE_BROWSER);
                it.putExtra("show_sites", false);
                it.putExtra("play_url", Uri.fromFile(f).toString());
                it.putExtra("play_title", f.getName());
                startActivity(it);
            } else {
                Intent it = new Intent(Intent.ACTION_VIEW);
                it.setDataAndType(uri, "*/*");
                it.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(it);
            }
        } catch (Exception e) {
            Toast.makeText(this, "打开失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) { finish(); return true; }
        return super.onKeyDown(keyCode, event);
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
