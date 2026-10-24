package com.mytv.lite;

import android.app.Application;
import android.util.Log;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 慕思 6.1：兜底崩溃记录。
 *
 * 电视上没有 logcat，闪退之后只能靠猜。这里把最后一次异常写到
 * /data/data/com.mytv.lite/files/crash.log（也能在「文件」页里看到），
 * 下次出问题直接把这段发出来就能定位，不用再反复试。
 */
public class MusiApp extends Application {

    private static final String TAG = "MusiApp";
    public static final String CRASH_FILE = "crash.log";

    @Override
    public void onCreate() {
        super.onCreate();
        final Thread.UncaughtExceptionHandler def = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, ex) -> {
            save(ex);
            if (def != null) def.uncaughtException(thread, ex);
        });
    }

    private void save(Throwable ex) {
        try {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            pw.println(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()));
            pw.println("thread: " + Thread.currentThread().getName());
            ex.printStackTrace(pw);
            pw.flush();

            File f = new File(getFilesDir(), CRASH_FILE);
            java.io.FileWriter fw = new java.io.FileWriter(f, false);
            fw.write(sw.toString());
            fw.close();
            Log.e(TAG, sw.toString());
        } catch (Throwable ignore) { }
    }
}
