package com.mytv.lite;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/**
 * 慕思 6.0：从苹果CMS 标准接口取海报列表，转成首页能直接用的 Poster。
 *
 * 只依赖 HTTP + JSON，不需要 TVBox 的爬虫内核，所以「小盒子多仓」里那些
 * type=1 的站点拿来就能用。
 *
 * 6.2 补了 search()：豆瓣榜单里的片名要拿它去各源里找能播的那一条。
 */
public class CmsSource {

    /** 接口统一转成 videolist 协议（复用播放页那份，避免两处规则不一致） */
    public static String normalize(String api) {
        return VodActivity.normalizeApi(api);
    }

    /**
     * 取一页列表。
     *
     * @param api  CMS 接口地址（未规范化也行）
     * @param page 页码，从 1 开始
     */
    public static List<Poster> list(String api, int page) throws Exception {
        final String base = normalize(api);
        String url = base + (base.contains("?") ? "&" : "?") + "pg=" + page;
        List<Poster> out = parse(JSONUtil.parse(ReposHttp.get(url)), base);
        if (out.isEmpty()) throw new Exception("该源返回空列表");
        return out;
    }

    /**
     * 按关键词搜。
     *
     * 苹果CMS 的搜索参数是 wd，走的是同一个 videolist 接口，
     * 所以拿到的字段和列表页完全一致，不用另写一套解析。
     *
     * @return 可能为空列表（这个源没有这部片），不抛异常
     */
    public static List<Poster> search(String api, String keyword) throws Exception {
        final String base = normalize(api);
        String url = base + (base.contains("?") ? "&" : "?")
                + "wd=" + URLEncoder.encode(keyword == null ? "" : keyword.trim(), "UTF-8");
        return parse(JSONUtil.parse(ReposHttp.get(url)), base);
    }

    /** 列表页与搜索页共用的解析。 */
    private static List<Poster> parse(JSONObject d, String base) throws Exception {
        if (d == null) throw new Exception("接口无响应");
        JSONArray arr = d.optJSONArray("list");
        if (arr == null) throw new Exception("不是苹果CMS协议(无 list)");

        List<Poster> out = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            String id = o.optString("vod_id", "");
            String name = o.optString("vod_name", "");
            if (id.isEmpty() || name.isEmpty()) continue;

            Poster p = new Poster();
            p.id = id;
            p.title = name;
            p.pic = o.optString("vod_pic", "");
            p.remarks = o.optString("vod_remarks", "");
            p.category = o.optString("type_name", "");
            p.year = o.optString("vod_year", "");
            p.rate = o.optString("vod_douban_score", "");
            p.apiBase = base;          // 非空 = 可直接取选集播放
            p.douban = false;
            out.add(p);
        }
        return out;
    }
}
