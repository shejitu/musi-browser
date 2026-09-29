package com.mytv.lite;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** HTTP 工具（仓库拉取等） */
public final class ReposHttp {
    public static String get(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(12000); c.setReadTimeout(20000);
        c.setRequestProperty("User-Agent", "okhttp/3.15");
        BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder(); String line;
        while ((line = r.readLine()) != null) sb.append(line).append('\n');
        r.close(); c.disconnect();
        return sb.toString();
    }
}
