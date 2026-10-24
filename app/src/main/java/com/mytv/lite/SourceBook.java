package com.mytv.lite;

import java.util.ArrayList;
import java.util.List;

/**
 * 慕思 6.0：影视源清单。
 *
 * 全部取自「小盒子多仓」(http://xhztv.top/dc/) 里的三个仓，并且在 2026-10-24 逐个实测过：
 * 分类、列表、详情、播放地址四项都正常，才写进这里。
 *
 *   玄珠   https://jihulab.com/xuanzhuapp/xzys/-/raw/main/xzvip.json
 *   小马   https://szyyds.cn/tv/x.json
 *   dxawi  https://dxawi.github.io/0/0.json
 *
 * 注意两点：
 * 1. 这三个仓里绝大多数站点是 TVBox 的 JS 爬虫（type=3），慕思跑不了，所以只挑标准
 *    苹果CMS JSON 接口（type=1）的站点，这类是纯 HTTP，不依赖任何爬虫内核。
 * 2. 小马仓里的「索尼[直连]」(suoniapi.com) 按要求不启用。
 *
 * 每个组内按顺序尝试：第一个能返回数据的就用，失败自动换下一个，不用你手动切。
 */
public class SourceBook {

    /** 单个影视源 */
    public static class Src {
        public final String name;
        public final String api;
        public Src(String n, String a) { name = n; api = a; }
    }

    /** 一个分组 = 首页的一个 tab */
    public static class Group {
        public final String label;
        public final List<Src> list = new ArrayList<>();
        Group(String l) { label = l; }
        Group add(String n, String a) { list.add(new Src(n, a)); return this; }
    }

    private static List<Group> groups;

    public static List<Group> groups() {
        if (groups != null) return groups;
        List<Group> g = new ArrayList<>();

        /* 玄珠仓：候选按实测响应速度排序 */
        g.add(new Group("🦊 玄珠")
                .add("极速", "https://jszyapi.com/api.php/provide/vod/")
                .add("红牛", "https://www.hongniuzy2.com/api.php/provide/vod/")
                .add("量子", "https://cj.lziapi.com/api.php/provide/vod/")
                .add("非凡", "http://cj.ffzyapi.com/api.php/provide/vod/"));

        /* 小马仓：仓里含 suoniapi.com，按要求排除 */
        g.add(new Group("🐴 小马")
                .add("电影天堂", "http://caiji.dyttzyapi.com/api.php/provide/vod")
                .add("光速", "https://api.guangsuapi.com/api.php/provide/vod/")
                .add("百度", "https://api.apibdzy.com/api.php/provide/vod"));

        /* dxawi 仓 */
        g.add(new Group("🐹 dxawi")
                .add("暴风", "https://bfzyapi.com/api.php/provide/vod")
                .add("IK", "https://ikunzyapi.com/api.php/provide/vod/from/ikm3u8/"));

        /* 下面两个来自你给的网页清单，站点自带苹果CMS接口，直接原生海报墙更省资源 */
        g.add(new Group("🎬 影视星球")
                .add("影视星球", "https://ysxq.cc/api.php/provide/vod/"));

        g.add(new Group("⚡ 飞快")
                .add("飞快影视", "https://feikuai.in/api.php/provide/vod/"));

        groups = g;
        return groups;
    }

    public static int size() { return groups().size(); }

    public static Group group(int i) {
        List<Group> all = groups();
        if (i < 0 || i >= all.size()) return all.get(0);
        return all.get(i);
    }

    /* ==================== 网页端推荐影视 ==================== */

    /**
     * 网页端展示的推荐站点。
     * api 非空 = 该站有苹果CMS接口，点一下直接进原生海报墙（遥控器友好）；
     * api 为空 = 只能用浏览器打开原站。
     */
    public static class WebSite {
        public final String name, url, desc, api;
        public WebSite(String n, String u, String d, String a) {
            name = n; url = u; desc = d; api = a;
        }
        public boolean nativePlay() { return api != null && !api.isEmpty(); }
    }

    private static List<WebSite> webSites;

    public static List<WebSite> webSites() {
        if (webSites != null) return webSites;
        List<WebSite> l = new ArrayList<>();
        l.add(new WebSite("影视星球", "https://ysxq.cc/", "42 万部 · 可直接播",
                "https://ysxq.cc/api.php/provide/vod/"));
        l.add(new WebSite("飞快影视", "https://feikuai.in/", "22 万部 · 可直接播",
                "https://feikuai.in/api.php/provide/vod/"));
        l.add(new WebSite("飞快 · 电影频道", "https://feikuai.in/vodtype/1.html", "只看电影 · 网页打开", ""));
        l.add(new WebSite("肥嘟影视", "https://fdzys.com/", "网页打开", ""));
        l.add(new WebSite("低端影视", "https://ddys.io/", "网页打开", ""));
        l.add(new WebSite("快快影视", "https://www.kkys02.com/", "网页打开 · 备用", ""));
        webSites = l;
        return webSites;
    }

    /* ==================== 遥控器指引 ==================== */

    /** 首页首次进入时显示一次，按 OK / 返回即消失，不再打扰。 */
    public static final String[] GUIDE_HOME = {
            "① 上下左右选片，OK 进选集，再按 OK 直接播",
            "② 顶栏「🦊玄珠 / 🐴小马 / 🐹dxawi」换源，一个源没有就看下一个",
            "③ 顶栏「🌐网页」里有推荐影视站，也能直接当海报墙用",
            "④ 返回键退回上一层；播放中按返回键回首页"
    };

    /** 浏览器面板里常驻的提示（滚到底部可见）。 */
    public static final String[] GUIDE_BROWSER = {
            "上下键选站，OK 进入",
            "带「可直接播」的点进去就是海报墙，遥控器一路 OK 到底",
            "网页站进去后按【⚡轻站】可转成电视界面"
    };
}
