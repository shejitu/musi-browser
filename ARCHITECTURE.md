# 架构与设计思路（给后续开发者 / AI 协作者）

> 本文档说明每个模块的职责、协议、设计取舍，以及如何安全地迭代。改动前先读完。

## 总体设计原则

1. **纯 Java + Android 原生 API，零 UI 框架依赖** — 所有界面代码用 `LinearLayout/FrameLayout/TextView` 程序化构建，无 XML 布局（除了 FileProvider 的 paths）。好处：无 AAPT 资源冲突、编译快、AI 易改。
2. **每个功能一个 Activity**，通过静态 `launch(Context)` 方法跳转，Intent extras 传参。
3. **流式播放，永不落盘** — ExoPlayer 只用内存缓冲（4~12 秒），无 SimpleCache/DownloadManager。
4. **不内置任何破解引擎** — TVBox Spider(type 3) 源不做 dex/JS 引擎还原，遇到就转网页嗅探或提示。

## 模块地图

```
HomeActivity          主页：6张功能卡片 + ⚡代理按钮 + 局域网地址显示
  ├─ MainActivity     播放器宿主（直播/浏览器/点播播放三合一，靠 Intent mode 区分）
  │    ├─ ExoPlayer   OkHttp DataSource（可挂代理）+ LoadControl(4~12s内存缓冲)
  │    ├─ BrowserView WebView + shouldInterceptRequest 视频嗅探(m3u8/mp4/flv/ts…)
  │    └─ 频道列表/OSD/数字跳台/画面缩放切换(OK键)
  ├─ ReposActivity    仓库管理：内置12仓+手动添加，拉取 TVBox 配置 JSON
  │    └─ 站点点击 → CMS源→VodActivity(海报墙)；其他→浏览器嗅探
  ├─ RepoPickerActivity  影视点播入口（当前复用 ReposActivity，可改为默认直入某CMS源）
  ├─ VodActivity      海报墙点播：苹果CMS V10 协议
  ├─ FilesActivity    本地文件：Download/download 视频/安装APK
  ├─ SettingsActivity 频道源管理（内置源+自定义m3u订阅，Sources.java 数据层）
  ├─ ProxyActivity    代理设置：Clash HTTP 代理 IP:端口
  └─ LiteServer       手写 HTTP 服务器 :8080（电脑控制台）
       ├─ GET  /push?url=      推送网址→电视浏览器打开
       ├─ GET  /setproxy?host=&port=   电脑一键推送Clash代理
       ├─ POST /upload         multipart 文件上传→Download/download/
       └─ GET  /files          已上传文件列表
```

## 关键协议知识

### 苹果CMS V10（海报墙数据源）
- 列表：`GET {api}/api.php?ac=videolist&pg=1` → `{class:[{type_id,type_name}], list:[{vod_id,vod_name,vod_pic,type_name,vod_remarks}]}`
- 分类：加 `&t={type_id}`；搜索：加 `&wd={关键词}`
- 详情：`?ac=videolist&ids={vod_id}` → `vod_content`(简介，含HTML需strip) + `vod_play_url` 格式 `选集名$url#选集名$url#...`（多播放源用 `$$$` 分隔）
- **识别**：站点 `api` 字段含 `api.php` / `provide/vod` / `ac=videolist` 即可进海报墙（见 ReposActivity.openSite）

### TVBox 配置格式
- 单仓：`{spider, sites:[{name,api,type}], lives:[{name,urls:[...]}]}`
- `type`: 0/1/4=直链(网页或CMS接口)，3=Spider(JAR/JS爬虫，**无法直接播**)
- 多仓：`{storeHouse:[{sourceName,sourceUrl}]}` → 递归展开
- 配置可能带前导混淆字符（hex 等），用 `JSONUtil.parse` 容错（找第一个 `{`）

### 视频嗅探（BrowserView）
`WebViewClient.shouldInterceptRequest` 拦截所有资源请求，URL 含 `.m3u8/.mp4/.flv/.ts/.mkv/.avi` 即记录为"发现视频"，顶栏「▶播放」按钮点亮 → 交给 ExoPlayer 全屏。

### 代理
- ExoPlayer：`OkHttpDataSource.Factory(OkHttpClient.Builder().proxy(...))` — **不能用** `DefaultHttpDataSource`（不支持代理）
- WebView：`androidx.webkit.ProxyController.setProxyOverride`（需依赖 `androidx.webkit:webkit:1.8.0`，设备 WebView 需较新版本，失败静默降级）
- 电脑端一键推送：控制台页 JS 拼电脑IP → GET `/setproxy` → 电视端写 SharedPreferences → 下次构建播放器/WebView 时生效

### 局域网服务器（LiteServer）
- 手写 ServerSocket，逐行解析 HTTP；上传用 multipart 解析：从**请求头** Content-Type 取 boundary，body 从第一个 `\r\n\r\n` 后开始，尾部裁掉 `\r\n--{boundary}`。改上传逻辑务必重跑独立测试（参考 git 历史里曾因 boundary 从 body 找导致文件尾粘垃圾字节的 bug）。
- 端口 8080；文件存 `Environment.DIRECTORY_DOWNLOADS/download/`，失败降级到 app 私有目录。

## 编译

见 [BUILD.md](BUILD.md)。要点：JDK17 + platform-34 + build-tools 34.0.0 + Gradle 8.5，`gradle assembleRelease`，签名 `app/mytv.jks`（密码 mytv2026，自用）。

## 迭代指南（AI 必读）

1. **版本号**：改 `app/build.gradle` 的 `versionCode`(+1) 和 `versionName`。
2. **新增 Activity**：Java 类 + Manifest 注册（`exported=false`，横屏）+（需要时）HomeActivity 加卡片 + `CARD_COUNT` 同步改。
3. **lambda 里引用循环变量**必须 copy 成 final 局部变量（本项目已踩过 3 次此坑，编译报 "must be final or effectively final"）。
4. **不要用** `DefaultHttpDataSource.Factory.setProxy`（不存在）；代理走 OkHttp factory。
5. **WebView 代理**对部分老 WebView 无效，catch Throwable 静默——别改成崩溃。
6. **图片加载**（海报墙）是每图一线程的简易实现；如果未来列表变大，考虑 LruCache + 线程池。
7. **测试习惯**：网络/解析类逻辑先抽成纯 Java 在桌面 JVM 跑通（参考 /tmp/srvtest 的做法），再进 APK。
8. **发布**：`cp app/build/outputs/apk/release/app-release.apk ~/慕思浏览器-vX.Y.apk`，GitHub Release 传附件。
9. **隐私红线**：不给这个 app 加账号体系/上报/统计；保持无注册无广告。

## 已知限制（诚实清单）

- TVBox Spider(type 3) 源无法直接播放（不内置 dex/JS 引擎），自动转网页嗅探
- 海报墙只支持苹果CMS V10 协议源；其他协议会提示
- WebView 代理在部分设备上不生效（系统 WebView 太旧）
- Android 10+ 公共 Download 目录写入可能受限，自动降级 app 私有目录（/Android/data/com.mytv.lite/files/download）
- 上传大文件（>32MB）未做流式优化，整包读进内存
