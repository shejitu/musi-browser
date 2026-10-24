package com.mytv.lite;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 豆瓣电影数据源（慕思 5.0 新增）。
 *
 * 走豆瓣的公开 JSON 接口，比直接开网页更适合遥控器：
 *   · 列表  https://movie.douban.com/j/search_subjects?type=movie&tag=热门&sort=recommend&page_limit=20&page_start=0
 *   · 详情  https://m.douban.com/rexxar/api/v2/movie/{subjectId}
 *
 * 两个接口都要带 UA + Referer，否则会拿到 403 或空结果。
 * 只做只读抓取，不涉及账号，不需要登录。
 */
public final class DoubanSource {

    /** 部分接口会校验 UA，伪装成手机浏览器最稳。 */
    public static final String UA =
            "Mozilla/5.0 (Linux; Android 9; TV) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/120.0 Mobile Safari/537.36";

    /** 详情：简介 / 年份 / 类型 / 地区 / 评分。 */
    public static class Detail {
        public String intro = "";
        public String year = "";
        public String genres = "";
        public String countries = "";
        public String rate = "";
    }

    /**
     * 取一页列表。
     *
     * @param tag   热门 / 最新 / 豆瓣高分 / 冷门佳片
     * @param sort  recommend（按热度）或 time（按时间）
     * @param start 从第几条开始，用于翻页
     * @param count 每页条数
     */
    public static List<Poster> list(String tag, String sort, int start, int count) throws Exception {
        String url = "https://movie.douban.com/j/search_subjects?type=movie"
                + "&tag=" + URLEncoder.encode(tag, "UTF-8")
                + "&sort=" + sort
                + "&page_limit=" + count
                + "&page_start=" + start;
        String raw = get(url, "https://movie.douban.com/");
        JSONObject d = JSONUtil.parse(raw);
        if (d == null) throw new Exception("豆瓣返回无法解析");

        JSONArray arr = d.optJSONArray("subjects");
        if (arr == null) throw new Exception(d.optString("msg", "豆瓣未返回 subjects"));

        List<Poster> out = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            Poster p = new Poster();
            p.douban = true;
            p.id = o.optString("id", "");
            p.title = o.optString("title", "");
            p.pic = o.optString("cover", "");
            p.rate = o.optString("rate", "");
            p.remarks = o.optBoolean("is_new", false) ? "新" : "";
            out.add(p);
        }
        return out;
    }

    /** 取单条详情；失败只影响详情面板，不影响海报墙。 */
    public static Detail detail(String subjectId) throws Exception {
        String url = "https://m.douban.com/rexxar/api/v2/movie/" + subjectId;
        String raw = get(url, "https://m.douban.com/movie/");
        JSONObject o = JSONUtil.parse(raw);
        Detail d = new Detail();
        if (o == null) return d;

        d.intro = o.optString("intro", "").trim();
        d.year = o.optString("year", "");
        JSONArray g = o.optJSONArray("genres");
        if (g != null) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < g.length(); i++) {
                if (sb.length() > 0) sb.append(" / ");
                sb.append(g.optString(i));
            }
            d.genres = sb.toString();
        }
        JSONArray c = o.optJSONArray("countries");
        if (c != null) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < c.length(); i++) {
                if (sb.length() > 0) sb.append(" / ");
                sb.append(c.optString(i));
            }
            d.countries = sb.toString();
        }
        Object rating = o.opt("rating");
        if (rating instanceof JSONObject) d.rate = ((JSONObject) rating).optString("value", "");
        else if (rating != null) d.rate = String.valueOf(rating);
        return d;
    }

    /** 统一的 GET，带 Referer 与超时；非 2xx 直接抛错，交给上层静默回退。 */
    static String get(String url, String referer) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(15000);
        c.setRequestProperty("User-Agent", UA);
        c.setRequestProperty("Referer", referer);
        c.setRequestProperty("Accept", "application/json, text/plain, */*");
        c.setInstanceFollowRedirects(true);

        int code = c.getResponseCode();
        if (code < 200 || code >= 300) {
            c.disconnect();
            throw new Exception("豆瓣 HTTP " + code);
        }
        BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) sb.append(line).append('\n');
        r.close();
        c.disconnect();
        return sb.toString();
    }
}
