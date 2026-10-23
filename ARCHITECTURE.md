# 架构与设计思路（给后续开发者 / AI 协作者）

> 改动前先读完本文。产品定位与逆向取证过程见 [README.md](README.md)。

## 总体设计原则

1. **纯 Java + Android 原生 API，零 UI 框架** —— 界面全部用 `LinearLayout/FrameLayout/GridLayout`
   程序化构建，无 XML 布局（除 FileProvider 的 paths）。好处：无 AAPT 资源冲突、编译快、AI 易改。
2. **每个功能一个 Activity**，通过静态 `launch(Context)` 跳转，Intent extras 传参。
3. **流式播放，永不落盘** —— ExoPlayer 只用内存缓冲（4~12 秒），无 SimpleCache/DownloadManager。
4. **不内置任何破解引擎** —— TVBox Spider(type 3) 不做 dex/JS 引擎还原，转网页嗅探或提示。
5. **轻站不引入独立 JS 引擎** —— 直接注入 WebView（见下），省掉 Rhino 与脚本下发链路。

## 模块地图

```
HomeActivity              主页：7 张功能卡片 + 代理入口 + 局域网地址
  ├─ MainActivity         播放器宿主（直播 / 浏览器 / 直接播放，靠 Intent mode 区分）
  │    ├─ ExoPlayer       DefaultDataSource 分发 + OkHttp(可挂代理) + LoadControl(4~12s)
  │    ├─ BrowserView     WebView + shouldInterceptRequest 嗅探(m3u8/mp4/flv/ts…)
  │    │    └─ ⚡轻站 ─────→ LiteActivity
  │    └─ 频道列表 / OSD / 数字跳台 / 画面缩放(OK 键)
  ├─ LiteActivity         轻站面板：海报 + 选集网格 + 逐集解析 + 播放
  │    ├─ 隐藏 WebView    loadUrl(选集) → 嗅探媒体 → 命中即播
  │    └─ ExoPlayer       全屏播放，返回键回面板
  ├─ LiteRulesActivity    规则管理：查看 / 从网址导入 / 拉官方 / 清除
  ├─ ReposActivity        TVBox 配置：内置仓库 + 手动添加
  ├─ ChannelActivity      仓库推荐影视（海报墙入口）
  ├─ VodActivity          苹果CMS V10 海报墙
  ├─ FilesActivity        本地文件：Download/download 视频 / 安装 APK
  ├─ SettingsActivity     频道源管理 + 轻站规则入口
  ├─ ProxyActivity        代理设置：Clash HTTP 代理 IP:端口
  └─ LiteServer           手写 HTTP 服务 :8080（电脑控制台）
       ├─ GET  /push?url=            推送网址 → 电视浏览器打开
       ├─ GET  /setproxy?host=&port= 推送代理
       ├─ POST /upload               multipart 上传 → Download/download/
       └─ GET  /files                已上传文件列表
```

## 轻站（LiteEngine）设计要点

### 为什么不让脚本跑在独立引擎

OK浏览器 用 Rhino + 远端脚本，是因为它的轻站脚本运行在页面之外。本项目入口就是 WebView，
`evaluateJavascript` 天然拥有 DOM、Cookie、location，**脚本在页面内执行**反而更容易过反爬。
因此：不引 Rhino、不打包 module.jar、不做脚本热更新 —— 改用「规则可导入」。

### 三级降级

1. `rule`：`assets/lite_rules.json` / 用户导入，`selector` 精确抓选集，可选 `js` 片段；
2. `generic`：`<video>/<source>` + 全页正则 + iframe 收集 + 网络嗅探；
3. `resolve`：选集 → 隐藏 WebView → 嗅探 → 播放；1.2s/4s/9s 三次兜底注入「点播放」脚本。

**任何一级失败都不能让功能不可用**，必须退到下一级并给出可执行提示。

### 注入脚本约定（`buildProbeJs`）

返回 JSON 字符串，字段：

| 字段 | 含义 |
|---|---|
| `ok` / `err` | 是否成功 / 错误信息 |
| `title` | 剧名（`document.title` 或规则覆盖） |
| `poster` | 海报（og:image / 详情页封面） |
| `items[]` | 选集 `{name,url}`（绝对地址，最多 300） |
| `media[]` | 直链（m3u8 > mp4 > flv > mkv > ts 排序，最多 20） |
| `frames[]` | iframe src（最多 8） |
| `via` | `rule-js` / `rule` / `generic` |

注意：`evaluateJavascript` 的 result 是**带引号的 JSON 字面量**，必须先用 `JSONTokener` 解一层
（`LiteEngine.parse` 已处理）。

### 规则匹配

`hosts` 按后缀匹配取最长命中，`m.a.com` 会命中 `a.com`。用户规则文件在
`getFilesDir()/lite_rules_user.json`，同 `id` 覆盖内置。改完必须 `LiteEngine.invalidate()`。

## 关键协议知识

### 苹果CMS V10（海报墙数据源）
- 列表：`GET {api}/api.php?ac=videolist&pg=1` → `{class:[{type_id,type_name}], list:[{vod_id,vod_name,vod_pic,type_name,vod_remarks}]}`
- 分类：加 `&t={type_id}`；搜索：加 `&wd={关键词}`
- 详情：`?ac=videolist&ids={vod_id}` → `vod_content` + `vod_play_url`（`选集名$url#选集名$url#…`，多源用 `$$$`）
- 识别：站点 `api` 含 `api.php` / `provide/vod` / `ac=videolist`

### TVBox 配置
- 单仓：`{spider, sites:[{name,api,type}], lives:[{name,urls:[...]}]}`
- `type`：0/1/4=直链（网页或CMS接口），3=Spider（无法直接播）
- 多仓：`{storeHouse:[{sourceName,sourceUrl}]}` → 递归展开
- 配置可能带前导混淆字符，用 `JSONUtil.parse` 容错（找第一个 `{`）

### 代理
- ExoPlayer：`OkHttpDataSource.Factory(OkHttpClient.Builder().proxy(...))` —— **不能用** `DefaultHttpDataSource`
- WebView：`androidx.webkit.ProxyController.setProxyOverride`，失败静默降级

### 局域网服务器
- 手写 ServerSocket；multipart 的 boundary 从**请求头**取，body 从第一个 `\r\n\r\n` 后开始，
  尾部裁掉 `\r\n--{boundary}`（历史 bug：boundary 从 body 找导致文件尾粘垃圾字节）。

## 已知限制

- TVBox Spider(type 3) 源无法直接播放（自动转网页嗅探）
- 海报墙只支持苹果CMS V10
- WebView 代理在旧 WebView 上可能不生效
- Android 10+ 公共 Download 写入可能受限，降级到 app 私有目录
- 上传大文件（>32MB）未做流式优化
- 轻站依赖页面结构，站点改版需更新规则
