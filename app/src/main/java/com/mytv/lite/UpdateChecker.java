package com.mytv.lite;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * 慕思浏览器 4.2 版本检测。
 *
 * 后台读取 GitHub Releases 的 latest，与本机 versionName 比较；远端更高才弹窗。
 * 断网、超时、仓库不可访问、JSON 残缺一律静默 —— 识别不到就保持原样，不打扰使用。
 */
public final class UpdateChecker {

    /** 仓库必须 public，private 的 releases/latest 返回 404。 */
    public static final String OWNER_REPO = "shejitu/musi-browser";

    private static final String API = "https://api.github.com/repos/" + OWNER_REPO + "/releases/latest";
    private static final String PREF = "musi_update";
    private static final String KEY_LAST = "last_prompted_version";
    private static final String KEY_LAST_CHECK = "last_check_ms";
    /** 同一版本只提示一次；两次检查至少隔 6 小时。 */
    private static final long MIN_INTERVAL_MS = 6L * 60 * 60 * 1000;

    private static volatile boolean running = false;

    private UpdateChecker() {
    }

    /** 启动时调用，非阻塞。 */
    public static void check(final Activity act) {
        if (act == null || running) return;
        final String current = versionName(act);
        if (TextUtils.isEmpty(current)) return;

        final SharedPreferences sp = act.getApplicationContext()
                .getSharedPreferences(PREF, Activity.MODE_PRIVATE);
        long last = sp.getLong(KEY_LAST_CHECK, 0L);
        if (System.currentTimeMillis() - last < MIN_INTERVAL_MS) return;

        running = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    JSONObject rel = fetchLatest();
                    sp.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply();
                    if (rel == null) return;

                    final String tag = normalize(rel.optString("tag_name", ""));
                    if (TextUtils.isEmpty(tag)) return;
                    if (compareVersions(tag, normalize(current)) <= 0) return;
                    if (tag.equals(sp.getString(KEY_LAST, ""))) return;

                    final String body = rel.optString("body", "");
                    final String page = rel.optString("html_url", "");
                    final String name = rel.optString("name", "");

                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            if (act.isFinishing()) return;
                            try {
                                showDialog(act, tag, name, body, page, current, sp);
                            } catch (Throwable ignore) {
                            }
                        }
                    });
                } catch (Throwable ignore) {
                    // 静默
                } finally {
                    running = false;
                }
            }
        }, "musi-update-check").start();
    }

    /** 从 PackageManager 取 versionName，不依赖 buildConfig 开关。 */
    private static String versionName(Activity act) {
        try {
            return act.getPackageManager().getPackageInfo(act.getPackageName(), 0).versionName;
        } catch (Throwable t) {
            return "";
        }
    }

    private static JSONObject fetchLatest() {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(API);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/vnd.github+json");
            conn.setRequestProperty("User-Agent", "MusiBrowser-Android");
            if (conn.getResponseCode() != 200) return null;

            InputStream is = conn.getInputStream();
            BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();
            return new JSONObject(sb.toString());
        } catch (Throwable t) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static String normalize(String v) {
        if (v == null) return "";
        v = v.trim();
        if (v.startsWith("v") || v.startsWith("V")) v = v.substring(1);
        return v;
    }

    /** 按 '.' 分段比数字，缺位补 0。 */
    static int compareVersions(String a, String b) {
        if (TextUtils.isEmpty(a) && TextUtils.isEmpty(b)) return 0;
        if (TextUtils.isEmpty(a)) return -1;
        if (TextUtils.isEmpty(b)) return 1;
        String[] pa = a.split("[.\\-+]");
        String[] pb = b.split("[.\\-+]");
        int n = Math.max(pa.length, pb.length);
        for (int i = 0; i < n; i++) {
            int va = i < pa.length ? toInt(pa[i]) : 0;
            int vb = i < pb.length ? toInt(pb[i]) : 0;
            if (va != vb) return va > vb ? 1 : -1;
        }
        return 0;
    }

    private static int toInt(String s) {
        try {
            StringBuilder d = new StringBuilder();
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c >= '0' && c <= '9') d.append(c);
                else break;
            }
            return d.length() == 0 ? 0 : Integer.parseInt(d.toString());
        } catch (Throwable t) {
            return 0;
        }
    }

    private static void showDialog(final Activity act, final String tag, String name,
                                   String body, final String page, String current,
                                   final SharedPreferences sp) {
        StringBuilder msg = new StringBuilder();
        if (!TextUtils.isEmpty(name) && !name.contains(tag)) {
            msg.append(name).append("\n\n");
        }
        msg.append("当前版本：v").append(current).append("\n");
        msg.append("最新版本：v").append(tag).append("\n\n");
        if (!TextUtils.isEmpty(body)) {
            String clean = body.replace("\r", "").trim();
            if (clean.length() > 600) clean = clean.substring(0, 600) + " …";
            msg.append(clean);
        }

        AlertDialog.Builder b = new AlertDialog.Builder(act);
        b.setTitle("发现新版本 v" + tag);
        b.setMessage(msg.toString());
        b.setCancelable(true);
        b.setPositiveButton("立即更新", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                sp.edit().putString(KEY_LAST, tag).apply();
                if (TextUtils.isEmpty(page)) return;
                try {
                    Intent it = new Intent(Intent.ACTION_VIEW, Uri.parse(page));
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    act.startActivity(it);
                } catch (Throwable ignore) {
                }
            }
        });
        b.setNegativeButton("以后再说", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                sp.edit().putString(KEY_LAST, tag).apply();
            }
        });
        b.show();
    }
}
