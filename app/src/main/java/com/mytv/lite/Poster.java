package com.mytv.lite;

/** 海报墙条目：可来自豆瓣（发现用）或影视源 CMS（可直接播放）。 */
public class Poster {
    public String id = "";          // 豆瓣 subject id / CMS vod_id
    public String title = "";
    public String pic = "";
    public String rate = "";        // 豆瓣评分，CMS 源为空
    public String year = "";
    public String category = "";
    public String remarks = "";     // CMS 的更新集数等
    public String apiBase = "";     // 非空 = CMS 条目，可直接取选集
    public boolean douban = false;

    public boolean playable() { return apiBase != null && !apiBase.isEmpty(); }
}
