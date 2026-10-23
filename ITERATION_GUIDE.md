# 迭代指南（AI 协作者必读）

## 一、动手前的三条红线

1. **不换签名 key** —— 换 `app/mytv.jks` 会导致老用户无法覆盖升级。
2. **不加账号 / 上报 / 统计 / 广告** —— 这个 App 的定位是「无注册、无广告、本地优先」。
3. **不内置破解引擎** —— 不打包 spider dex/js 运行时，不做 DRM 绕过。

## 二、常见改动怎么落地

| 想改什么 | 改哪里 | 注意 |
|---|---|---|
| 应用名 / 图标 | `AndroidManifest.xml` 的 `android:label`、`res/mipmap/ic_launcher.png` | 图标是猫头 logo，圆角在图片里，不要加背景 |
| 主页入口 | `HomeActivity.addCard(...)` | 新增卡片后 CARD_COUNT 同步；卡片宽度是均分，8 张会变窄 |
| 轻站解析逻辑 | `LiteEngine.buildProbeJs` | 改完必须在真机页面上验证，桌面跑不了 |
| 轻站界面 | `LiteActivity.buildUi/apply` | 所有可点元素必须 `setFocusable(true)` + 焦点高亮，否则遥控器没法用 |
| 站点适配 | `assets/lite_rules.json` 或「设置 → 轻站规则管理」导入 | 优先只写 `selector`，`js` 是最后手段 |
| 直播源 | `assets/iptv_*.m3u` + `Sources.java` | — |
| 海报墙协议 | `VodActivity` | 只支持苹果CMS V10 |
| 局域网服务 | `LiteServer` | multipart boundary 必须从请求头取 |

## 三、踩过的坑（别重复踩）

1. **lambda 里引用循环变量**必须 copy 成 final 局部变量，否则编译报
   `must be final or effectively final`（本项目已踩过 3 次）。
2. **不要用** `DefaultHttpDataSource.Factory.setProxy`（不存在）；代理走 OkHttp factory。
3. **WebView 代理**对部分老 WebView 无效，`catch Throwable` 静默，别改成崩溃。
4. **`evaluateJavascript` 的返回值是带引号的 JSON 字符串**，要 `JSONTokener` 解一层。
5. **不要在 `shouldInterceptRequest` 里直接改 UI** —— 它在后台线程，必须 `handler.post`。
6. **规则 `selector` 抓不到时必须回退通用嗅探**，否则一个站点改版会让轻站彻底不可用。
7. **嗅探判 media 要剥 query/fragment 再按路径 endsWith**，否则 `poster.mp4.jpg` 会误报。
8. **WebView 泄漏**：Activity 销毁时 `stopLoading → about:blank → destroy`。
9. **`.ts` 不要当首选直链** —— 多半是分片，真正该拿的是 m3u8。
10. 图片加载是每图一线程的简易实现，列表变大要换 LruCache + 线程池。

## 四、验证方法

- **编译**：`gradle assembleRelease`（本地无 SDK 就走 GitHub Actions）。
- **纯逻辑**：把解析/拼接类抽成不依赖 Android 的纯 Java，在桌面 JVM 跑（历史做法：`/tmp/srvtest`）。
- **轻站**：必须真机/模拟器打开真实影视页验证，重点看
  ① 规则是否命中（状态条会显示「按规则『X』解析中」）② 选集数量是否合理 ③ 点一集能否出画面。
- **回归**：README 第 6 节的自检清单。

## 五、发布流程

1. `app/build.gradle`：`versionName` +0.1，`versionCode` 递增；
2. push `master` → 等 Actions 绿；
3. 从 `apk` 分支或 artifact 取回 APK，核对版本号/包名/签名（`apksigner verify --print-certs`）；
4. 另存为 `MusiBrowser-vX.Y.apk`，不覆盖旧版本；
5. 需要正式发布时建 Release 挂附件，tag 与 versionName 一致。
