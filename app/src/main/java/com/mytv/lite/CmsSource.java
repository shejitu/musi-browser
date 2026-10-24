package com.mytv.lite;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 慕思 6.0：从苹果CMS 标准接口取海报列表，转成首页能直接用的 Poster。
 *
 * 只依赖 HTTP + JSON，不需要 TVBox 的爬虫内核，所以「小盒子多仓」里那些
 * type=1 的站点拿来就能用。
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

        JSONObject d = JSONUtil.parse(ReposHttp.get(url));
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

        if (out.isEmpty()) throw new Exception("该源返回空列表");
        return out;
    }
}
