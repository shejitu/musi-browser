package com.mytv.lite;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * 海报图加载（慕思 5.0 新增）。
 * 固定 4 线程 + 内存 LRU，避免一屏二十多张图各开一个线程把电视拖死。
 * 豆瓣图床对 Referer 敏感，这里统一带上。
 */
public final class ImagePool {

    private static final java.util.concurrent.ExecutorService POOL =
            java.util.concurrent.Executors.newFixedThreadPool(4);
    private static final Handler UI = new Handler(Looper.getMainLooper());
    private static final java.util.Set<String> LOADING =
            java.util.Collections.synchronizedSet(new java.util.HashSet<String>());
    private static LruCache<String, Bitmap> cache;

    public interface Cb { void done(Bitmap bmp); }

    private static LruCache<String, Bitmap> cache() {
        if (cache == null) {
            int maxKb = (int) (Runtime.getRuntime().maxMemory() / 1024 / 6);
            cache = new LruCache<String, Bitmap>(maxKb) {
                @Override protected int sizeOf(String key, Bitmap value) {
                    return value.getByteCount() / 1024;
                }
            };
        }
        return cache;
    }

    public static void load(final String url, final int targetW, final int targetH, final Cb cb) {
        if (url == null || url.isEmpty() || cb == null) return;
        final String key = url + "|" + targetW + "x" + targetH;

        Bitmap hit = cache().get(key);
        if (hit != null) { cb.done(hit); return; }
        if (!LOADING.add(key)) return;

        POOL.execute(() -> {
            Bitmap bmp = null;
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                c.setConnectTimeout(9000);
                c.setReadTimeout(15000);
                c.setRequestProperty("User-Agent", DoubanSource.UA);
                c.setRequestProperty("Referer", "https://movie.douban.com/");
                if (c.getResponseCode() < 200 || c.getResponseCode() >= 300) {
                    c.disconnect();
                } else {
                    // 先读尺寸算采样率，避免整张大图进内存
                    byte[] data = readAll(c.getInputStream());
                    c.disconnect();
                    BitmapFactory.Options o = new BitmapFactory.Options();
                    o.inJustDecodeBounds = true;
                    BitmapFactory.decodeByteArray(data, 0, data.length, o);
                    o.inSampleSize = sample(o.outWidth, o.outHeight, targetW, targetH);
                    o.inJustDecodeBounds = false;
                    o.inPreferredConfig = Bitmap.Config.RGB_565;
                    bmp = BitmapFactory.decodeByteArray(data, 0, data.length, o);
                }
            } catch (Throwable ignored) {
            } finally {
                LOADING.remove(key);
            }
            final Bitmap result = bmp;
            if (result != null) cache().put(key, result);
            UI.post(() -> cb.done(result));
        });
    }

    private static byte[] readAll(InputStream is) throws Exception {
        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) > 0) bo.write(buf, 0, n);
        is.close();
        return bo.toByteArray();
    }

    private static int sample(int w, int h, int tw, int th) {
        int s = 1;
        if (tw <= 0 || th <= 0) return s;
        while (w / (s * 2) >= tw && h / (s * 2) >= th) s *= 2;
        return s;
    }
}
