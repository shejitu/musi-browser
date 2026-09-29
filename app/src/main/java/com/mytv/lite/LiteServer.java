package com.mytv.lite;

import android.app.Activity;
import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.Environment;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 局域网控制服务器：电脑浏览器打开 http://电视IP:8080
 *  - 推送网址 → 电视端浏览器打开
 *  - 上传文件 → 保存到电视 Download/download/ 文件夹
 */
public class LiteServer {

    public interface UrlHandler { void onUrl(String url); }

    private final Activity act;
    private final UrlHandler onUrl;
    private ServerSocket server;
    private Thread thread;

    public LiteServer(Activity act, UrlHandler onUrl) { this.act = act; this.onUrl = onUrl; }

    public static String localIp(Context ctx) {
        WifiManager wm = (WifiManager) ctx.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wm != null) {
            int ip = wm.getConnectionInfo().getIpAddress();
            if (ip != 0) return (ip & 0xff) + "." + ((ip >> 8) & 0xff) + "." + ((ip >> 16) & 0xff) + "." + ((ip >> 24) & 0xff);
        }
        try {
            for (java.net.NetworkInterface nif : java.util.Collections.list(java.net.NetworkInterface.getNetworkInterfaces())) {
                if (!nif.isUp() || nif.isLoopback()) continue;
                for (java.net.InetAddress a : java.util.Collections.list(nif.getInetAddresses()))
                    if (a instanceof java.net.Inet4Address && !a.isLoopbackAddress()) return a.getHostAddress();
            }
        } catch (Exception ignored) {}
        return "127.0.0.1";
    }

    public void start() {
        stop();
        thread = new Thread(() -> {
            try {
                server = new ServerSocket(8080, 50, java.net.InetAddress.getByName("0.0.0.0"));
                while (!server.isClosed()) {
                    final Socket s = server.accept();
                    new Thread(() -> handle(s)).start();
                }
            } catch (Exception ignored) {}
        }, "LiteServer");
        thread.start();
    }

    public void stop() {
        try { if (server != null) server.close(); } catch (Exception ignored) {}
    }

    private void handle(Socket s) {
        try {
            s.setSoTimeout(30000);
            InputStream in = s.getInputStream();
            String requestLine = readLine(in);
            if (requestLine == null) { s.close(); return; }
            String[] parts = requestLine.split(" ");
            String method = parts[0], path = parts.length > 1 ? parts[1] : "/";
            int contentLen = 0;
            String contentType = "";
            String line;
            while ((line = readLine(in)) != null && !line.isEmpty()) {
                String low = line.toLowerCase(Locale.US);
                if (low.startsWith("content-length:"))
                    contentLen = Integer.parseInt(line.substring(15).trim());
                else if (low.startsWith("content-type:"))
                    contentType = line.substring(13).trim();
            }

            if (method.equals("GET")) {
                if (path.startsWith("/push?")) {
                    String u = param(path, "url");
                    if (u != null && !u.isEmpty()) {
                        final String fu = u;
                        act.runOnUiThread(() -> onUrl.onUrl(fu));
                        respond(s, 200, "ok: 电视端正在打开 " + u);
                    } else respond(s, 400, "missing url");
                } else if (path.startsWith("/setproxy?")) {
                    String h = param(path, "host"), p = param(path, "port");
                    if (h != null && p != null) {
                        final String fh = h; final int fp = Integer.parseInt(p);
                        act.runOnUiThread(() -> {
                            act.getSharedPreferences("mytv", 0).edit()
                               .putString("proxy_host", fh).putInt("proxy_port", fp).apply();
                            android.widget.Toast.makeText(act, "代理已设置: " + fh + ":" + fp, android.widget.Toast.LENGTH_LONG).show();
                        });
                        respond(s, 200, "ok: 电视端代理已设置为 " + h + ":" + p);
                    } else respond(s, 400, "missing host/port");
                } else if (path.startsWith("/files")) {
                    respond(s, 200, filesHtml());
                } else {
                    respond(s, 200, indexHtml());
                }
            } else if (method.equals("POST") && path.startsWith("/upload")) {
                saveUpload(in, contentLen, contentType);
                respond(s, 200, "ok: 文件已保存到电视 download 文件夹");
            } else respond(s, 404, "not found");
            s.close();
        } catch (Exception e) {
            try { s.close(); } catch (Exception ignored) {}
        }
    }

    private void saveUpload(InputStream in, int contentLen, String contentType) throws IOException {
        File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "download");
        if (!dir.exists() && !dir.mkdirs()) {
            File alt = new File(act.getExternalFilesDir(null), "download");
            alt.mkdirs();
            dir = alt;
        }
        // 读取整个请求体（推视频文件可能较大，按 32MB 上限保护）
        java.io.ByteArrayOutputStream body = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[65536];
        int total = 0;
        while (total < contentLen) {
            int n = in.read(buf, 0, Math.min(buf.length, contentLen - total));
            if (n < 0) break;
            body.write(buf, 0, n);
            total += n;
        }
        byte[] data = body.toByteArray();
        String head = new String(data, 0, Math.min(data.length, 8192), StandardCharsets.ISO_8859_1);
        String filename = "upload_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        int fn = head.indexOf("filename=\"");
        if (fn >= 0) {
            int fe = head.indexOf('"', fn + 10);
            String raw = head.substring(fn + 10, fe);
            if (!raw.isEmpty()) filename = new String(raw.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
        }
        // 正文起点：第一个 \r\n\r\n（头部结束）之后
        int sep = -1;
        for (int i = 0; i < data.length - 3; i++) {
            if (data[i] == 13 && data[i+1] == 10 && data[i+2] == 13 && data[i+3] == 10) { sep = i; break; }
        }
        if (sep < 0) throw new IOException("bad multipart");
        int bodyStart = sep + 4;
        // 去掉末尾 multipart boundary：boundary 在 HTTP 请求头的 Content-Type 里
        int bodyEnd = data.length;
        if (contentType != null) {
            int bLine = contentType.indexOf("boundary=");
            if (bLine >= 0) {
                String boundary = contentType.substring(bLine + 9).split("\"|\\s|;")[0];
                byte[] tail = ("\r\n--" + boundary).getBytes(StandardCharsets.ISO_8859_1);
                int scanFrom = Math.max(bodyStart, data.length - tail.length - 256);
                outer:
                for (int i = data.length - tail.length; i >= scanFrom; i--) {
                    for (int j = 0; j < tail.length; j++) {
                        if (data[i + j] != tail[j]) continue outer;
                    }
                    bodyEnd = i;
                    break;
                }
            }
        }
        FileOutputStream fo = new FileOutputStream(new File(dir, sanitize(filename)));
        fo.write(data, bodyStart, bodyEnd - bodyStart);
        fo.close();
    }

    private String sanitize(String f) {
        return f.replaceAll("[/\\\\:*?\"<>|]", "_");
    }

    private String filesHtml() {
        File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "download");
        StringBuilder sb = new StringBuilder("<html><meta charset='utf-8'><body><h3>已上传文件</h3>");
        File[] fs = dir.listFiles();
        if (fs != null) for (File f : fs) sb.append(f.getName()).append("  (").append(f.length()/1024).append(" KB)<br>");
        sb.append("<br><a href='/'>返回</a></body></html>");
        return sb.toString();
    }

    private String indexHtml() {
        return "<!DOCTYPE html><html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'>"
            + "<title>慕思浏览器 - 电视控制</title><style>"
            + "body{font-family:sans-serif;max-width:560px;margin:40px auto;padding:0 16px;background:#fafafa}"
            + "h2{color:#e65100}input,button{font-size:18px;padding:10px;margin:6px 0;width:100%;box-sizing:border-box}"
            + "button{background:#ff6d00;color:#fff;border:0;border-radius:8px;cursor:pointer}"
            + ".small{font-size:14px;background:#555}</style></head><body>"
            + "<h2>🐱 慕思浏览器 · 电视控制台</h2>"
            + "<p>电视 IP: <b>" + localIp(act) + ":8080</b></p>"
            + "<h3>推送网址</h3>"
            + "<input id='u' placeholder='https://... 或关键词'><button onclick=\"location.href='/push?url='+encodeURIComponent(document.getElementById('u').value)\">在电视上打开</button>"
            + "<h3>一键设置代理（用这台电脑当代理）</h3>"
            + "<p style='color:#888;font-size:13px'>前提：这台电脑已开启 Clash/V2Ray 并勾选「允许局域网连接」（混合端口通常 7890）。点击后电视端网页与播放将全部走本电脑代理。</p>"
            + "<input id='ph' placeholder='本电脑局域网IP，如 192.168.1.5'><input id='pp' placeholder='代理端口，如 7890' value='7890'>"
            + "<button onclick=\"location.href='/setproxy?host='+encodeURIComponent(document.getElementById('ph').value)+'&port='+encodeURIComponent(document.getElementById('pp').value)\">推送代理到电视</button>"
            + "<h3>发送文件到电视</h3>"
            + "<form method='POST' action='/upload' enctype='multipart/form-data'><input type='file' name='f' required><button type='submit'>上传到电视 download 文件夹</button></form>"
            + "<p><a href='/files'>查看已上传文件</a></p>"
            + "<p style='color:#888'>上传的文件保存在电视存储 Download/download/ 下，主页「本地文件」可打开</p>"
            + "</body></html>";
    }

    private String param(String path, String key) {
        int q = path.indexOf('?');
        if (q < 0) return null;
        for (String p : path.substring(q + 1).split("&")) {
            int eq = p.indexOf('=');
            if (eq > 0 && p.substring(0, eq).equals(key))
                try { return URLDecoder.decode(p.substring(eq + 1), "UTF-8"); } catch (Exception e) { return null; }
        }
        return null;
    }

    private String readLine(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') return sb.toString().trim();
            if (c != '\r') sb.append((char) c);
            if (sb.length() > 8192) break;
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    private void respond(Socket s, int code, String msg) {
        try {
            byte[] body = msg.getBytes(StandardCharsets.UTF_8);
            OutputStream o = s.getOutputStream();
            o.write(("HTTP/1.1 " + code + " OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: " + body.length + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            o.write(body);
            o.flush();
        } catch (Exception ignored) {}
    }
}
