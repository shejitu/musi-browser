package com.mytv.lite;

import android.content.Context;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** HTTP 工具（仓库拉取等）。v4.0: 新增代理感知版本，配置/订阅也走已设置的代理。 */
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

    /** v4.0: 代理感知版本——已启用代理时经 socks/http 代理拉取。 */
    public static String get(String url, Context ctx) throws Exception {
        java.net.URI uri = java.net.URI.create(url);
        String host = ProxyActivity.host(ctx);
        int port = ProxyActivity.port(ctx);
        if (uri.getScheme() != null && uri.getScheme().startsWith("http")
                && host != null && !host.isEmpty() && port > 0) {
            java.net.Proxy proxy = new java.net.Proxy(java.net.Proxy.Type.HTTP, new InetSocketAddress(host, port));
            java.net.InetAddress test;
            try { test = java.net.InetAddress.getByName(host); } catch (Exception e) { test = null; }
            okhttp3.OkHttpClient client = new okhttp3.OkHttpClient.Builder()
                    .proxy(proxy)
                    .connectTimeout(12, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                    .build();
            okhttp3.Request req = new okhttp3.Request.Builder().url(url)
                    .header("User-Agent", "okhttp/3.15").build();
            try (okhttp3.Response resp = client.newCall(req).execute()) {
                if (resp.body() == null) throw new java.io.IOException("empty body");
                return resp.body().string();
            }
        }
        return get(url);
    }
}
