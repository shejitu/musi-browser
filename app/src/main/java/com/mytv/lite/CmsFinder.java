package com.mytv.lite;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

/**
 * 自动找可用的影视源（慕思 5.0 从 ChannelActivity 抽出来复用）。
 *
 * 依次扫「我的仓库 + 内置仓库」，兼容两种 TVBox 配置：
 *   · 单仓  { "sites": [ { "name": "...", "api": "http://..." } ] }
 *   · 多仓  { "urls":  [ { "url": "http://..." } ] }  → 递归进子仓找一次
 * 只认 http 直链源（type 3 是 Spider，需要 JS 引擎，本 App 不支持）。
 * 回调都在主线程。
 */
public final class CmsFinder {

    public interface Cb {
        void found(String api, String siteName);

        void fail(String msg);
    }

    private static final Handler UI = new Handler(Looper.getMainLooper());
    private static final int MAX_SUB_REPO = 8;

    private static class Hit {
        String api, name;
        Hit(String a, String n) { api = a; name = n; }
    }

    /** 后台扫描，找到第一个可用源就回调；全部失败回调 fail。 */
    public static void find(final Context ctx, final Cb cb) {
        new Thread(() -> {
            List<ReposActivity.Repo> repos;
            try {
                repos = ReposActivity.allRepos(ctx);
            } catch (Throwable t) {
                postFail(cb, "读取仓库列表失败");
                return;
            }
            if (repos == null || repos.isEmpty()) {
                postFail(cb, "没有可用的仓库");
                return;
            }
            for (ReposActivity.Repo r : repos) {
                Hit h = scanRepo(r.url, 0);
                if (h != null) {
                    final String api = h.api, name = h.name;
                    UI.post(() -> cb.found(api, name));
                    return;
                }
            }
            postFail(cb, "所有仓库都没找到可直连的影视源");
        }, "cms-finder").start();
    }

    private static void postFail(final Cb cb, final String msg) {
        UI.post(() -> cb.fail(msg));
    }

    private static Hit scanRepo(String url, int depth) {
        try {
            Object cfg = parseAny(ReposHttp.get(url));
            if (cfg == null) return null;
            return pick(cfg, depth);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Hit pick(Object cfg, int depth) {
        try {
            if (cfg instanceof JSONArray) {
                JSONArray arr = (JSONArray) cfg;
                for (int i = 0; i < arr.length(); i++) {
                    Object o = arr.opt(i);
                    if (o instanceof JSONObject) {
                        Hit h = pick(o, depth);
                        if (h != null) return h;
                    }
                }
                return null;
            }
            JSONObject o = (JSONObject) cfg;

            JSONArray sites = o.optJSONArray("sites");
            if (sites != null) {
                for (int i = 0; i < sites.length(); i++) {
                    JSONObject s = sites.optJSONObject(i);
                    if (s == null) continue;
                    String api = s.optString("api", "").trim();
                    int type = s.optInt("type", -1);
                    if (type != 3 && (api.startsWith("http://") || api.startsWith("https://"))) {
                        String name = s.optString("name", "影视源");
                        return new Hit(api, name);
                    }
                }
            }

            // 多仓：只往里钻一层，避免仓库互相引用导致长时间扫描
            JSONArray urls = o.optJSONArray("urls");
            if (urls != null && depth < 1) {
                int tried = 0;
                for (int i = 0; i < urls.length() && tried < MAX_SUB_REPO; i++) {
                    JSONObject u = urls.optJSONObject(i);
                    if (u == null) continue;
                    String sub = u.optString("url", "").trim();
                    if (!sub.startsWith("http")) continue;
                    tried++;
                    Hit h = scanRepo(sub, depth + 1);
                    if (h != null) return h;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /** 配置有时是 JSON 对象，有时直接是数组，两种都收。 */
    private static Object parseAny(String raw) {
        JSONObject o = JSONUtil.parse(raw);
        if (o != null) return o;
        try {
            int b = raw.indexOf('[');
            if (b >= 0) return new JSONArray(raw.substring(b));
        } catch (Throwable ignored) {
        }
        return null;
    }
}
