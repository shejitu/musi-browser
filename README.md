# 慕思浏览器（Musi Browser）v4.1

一个给电视盒子用的轻量浏览器：**打开任意影视网页，按一个键，把它变成适配遥控器的 TV 界面**。
无账号、无广告、无统计、不内置任何破解引擎。

- 包名 `com.mytv.lite`　minSdk 21（Android 5.0+）　targetSdk 34
- 签名：`app/mytv.jks`（自用证书，保证 v3.x 起可覆盖升级）
- 当前版本 **4.1 / versionCode 41**

---

## 1. 这一版做了什么（v4.1 的核心）

v4.0 及以前，「轻量化」只做到**嗅探**：网页在后台请求 m3u8/mp4 时抓住地址，点亮一个「▶ 播放」按钮。
这有两个硬伤：

1. 必须等页面自己发起视频请求，很多站要手动点一下播放器才会请求，抓不到；
2. 抓到了也只是一条裸地址，选集、海报、剧名全丢，用户在电视上还得回到密密麻麻的网页里点。

v4.1 补上的是 OK浏览器 里那套「轻站」的**结果**：一键把当前页**结构化**成
`标题 + 海报 + 选集网格`，并且落成原生 TV 界面，遥控器方向键就能选，选中即播。

实现上没有照搬它的脚本引擎，而是换了一条更轻的路（详见第 3 节）。

---

## 2. 逆向取证：OK浏览器 到底是什么

目标是 `J:\素材\考国税\OK浏览器.apk`。方法是证据驱动，顺序为
**定位入口 → 逐层追踪 → 还原机制 → 用产物/行为验证**（参考 `morluto/rea` 的思路）。
每一步结论下面都标注了**证据**和**证据的局限**，不把推断说成事实。

### 2.1 静态指纹

| 项 | 值 |
|---|---|
| 包名 | `com.ok.browser` |
| 版本 | versionName `1.3.3` / versionCode `133` |
| SDK | minSdk 21 / targetSdk 31 / compileSdk 31 |
| 形态 | 声明 `android.software.leanback`（电视应用），横屏 |
| 加固 | `lib/` 下存在加固运行时 so，业务类不在可读 dex 中 |

**结论**：它是一个为电视做的、加过壳的商用浏览器，不是开源项目的直接 fork。

### 2.2 界面与业务层的证据

业务包名是 `com/iku/**`，类名未被混淆（方法名被 R8 压成 `a/b/c`，所以逐方法还原没有意义，
改从**字符串与资源**取证）。能直接读到的线索：

- `DialogLiteSourceTipsBinding` —— 存在一个「轻站来源提示」对话框；
- 中文串：`轻站检测!`、`正在尝试"轻站化"适配检测，请稍候...`、`】轻站适配成功`、
  `申请轻站适配`、`轻站脚本尚不支持该网站`；
- `assets/site_config.json` 里每个站点都带 `isLite` 与 `autoParse` 布尔标记，外加 `filters`（电影/电视剧/动漫/综艺 分类）。

**结论**：它的「轻量化」不是通用能力，而是**按站点逐站适配**的：
只有被适配过的站（`isLite: true`）才有轻站体验，没适配的会提示「轻站脚本尚不支持该网站」，
并给用户一个「申请轻站适配」的入口 —— 这是典型的运营型产品结构。

### 2.3 脚本执行链的证据

- `assets/jar/module.jar` 内嵌 `classes.dex`，含 `com/iku/**`、`com/p2p/**` 的 spider/model 类；
- APK 根目录存在 `org/mozilla/javascript/**`（Rhino）——**不在 dex 里，而以资源形式打包**，
  说明由 `DexClassLoader` 动态加载；
- 远端脚本地址：`jihulab.com/renwfy/iku2/-/raw/main/index.js`（实测 HTTP 200，返回
  `[{project:"iku",hosts:["http://101.132.195.168:8001"]},{project:"v2",hosts:["http://124.221.41.2:3016"]}]`
  这样的**线路表**），以及 `qiniu-btfactory.renwfy.top/host/index.js`（实测已指向一个前端 SPA，
  不再是脚本）。

**结论**：轻站脚本是**远端下发**的，App 只负责「取线路表 → 取脚本 → 用 Rhino 跑 → 通过 JS 桥回收播放地址」。
这也解释了它为什么要背一个 JS 引擎：脚本运行在页面之外，拿不到页面的 DOM 与登录态。

### 2.4 为什么不搬它的代码

| 原因 | 说明 |
|---|---|
| 拿不到明文 | 加固后业务逻辑不在可读 dex；强行脱壳得到的是带壳残留的产物，不稳定 |
| 搬了也是半成品 | 轻站脚本在服务端，本地只有「容器」；没有脚本，功能等于空壳 |
| 合规红线 | 那套结构本质是「解析第三方影视站的播放地址」，附带商业层（会员/公告），不适合直接继承 |
| 有更好的路 | 它用的开源底子思路（浏览器 + 嗅探 + 播放器）本身是公开的，自己重做一遍反而更干净 |

所以最终选择：**同源重制**，只保留「一键轻量化」这个产品目标，实现方式重新设计。

---

## 3. 我的实现：为什么不用 Rhino

OK浏览器必须背 JS 引擎，是因为它的脚本跑在**页面之外**。而本项目的入口本来就是 WebView，
WebView 自己就是 JS 引擎，而且额外拥有：

- 已渲染的 DOM（不用重新请求页面，不用对抗反爬）；
- 已登录的 Cookie / localStorage（会员页、试看页都能过）；
- 真实的 `location`、`document`，脚本可以直接读播放器配置。

于是注入脚本 `evaluateJavascript(...)` 就能完成同样的事，**省掉 Rhino、省掉 module.jar、
省掉脚本下发链路**，代价只是脚本必须能在页面里执行（对本场景完全够用）。

### 3.1 三级策略（成本从低到高，逐级降级）

```
① 站点规则 rule      assets/lite_rules.json 或用户导入：selector 精确抓选集，可选 js 片段
        ↓ 没匹配到
② 通用嗅探 generic   扫 <video>/<source>、正则扫整个 outerHTML、收集 iframe、复用网络嗅探
        ↓ 还没有可播地址
③ 逐集解析 resolve   点某一集 → 隐藏 WebView 打开该集页 → 嗅探 m3u8/mp4 → 命中即播
```

**任何一级失败都不会让功能变成「不可用」**：规则写错只会退到通用嗅探，通用嗅探失败还有逐集解析，
逐集解析超时会明确提示「回网页手动点播放后再按轻站」。

### 3.2 一键轻站的时序

```
用户按【⚡轻站】（或遥控器菜单键）
   ↓
BrowserView.runLite()
   ↓ LiteEngine.match(url) 按域名后缀取最长命中的规则
   ↓ evaluateJavascript(LiteEngine.buildProbeJs(rule))
页面里执行：
   海报(og:image / .detail-pic img)
   <video>/<source> 的 currentSrc
   iframe 的 src（最多 8 个）
   全页正则抓 m3u8/mp4/flv（播放器配置常内联在 HTML 里）
   选集：规则 selector 优先，否则 a[href] 按 /vodplay/、/play/、第N集 等特征过滤
   若规则带 js：new Function(...) 跑一遍，用它的返回值覆盖上面结果
   → 返回 JSON
   ↓
LiteEngine.parse() 归一化（直链按 m3u8 > mp4 > flv > mkv > ts 排序）
   ↓
LiteActivity：左边海报、右边标题+选集网格（5 列），遥控器直接操作
   ↓ 选中某一集
隐藏 WebView 打开该集 → shouldInterceptRequest 抓媒体请求 → 播放
   ↓ 抓不到
1.2s/4s/9s 三次兜底：注入「点一下播放器」脚本（video.play() + 常见播放按钮选择器），
并尝试把页面里的 iframe 直接载进来再抓一次
```

### 3.3 规则格式

```json
{
  "rules": [
    {
      "id": "wuguiyy",
      "name": "乌龟影院",
      "hosts": ["wuguiyy.com"],
      "selector": "a[href*='/vodplay/'], .playlist a",
      "js": "return {items: [...document.querySelectorAll('.episode a')].map(a=>({name:a.textContent,url:a.href}))}"
    }
  ]
}
```

- `hosts` 按域名后缀匹配，取**最长命中**（`m.a.com` 会命中 `a.com`）；
- `selector` 抓不到时自动回退通用嗅探，写错不会把功能写死；
- `js` 是函数体，拿到的变量是 `document`、`location`、`RES`（当前已探测到的结果），
  需要 `return {title, poster, items:[{name,url}], media:[...]}`。

**维护方式**：设置 →「⚡ 轻站规则管理」→ 粘贴一个 JSON 网址即可导入/覆盖，
或点「拉取官方规则」从本仓库 `app/src/main/assets/lite_rules.json` 取最新版。
站点改版时**不需要重新发版**——这是把 OK浏览器 的「服务端脚本」用「可导入规则」替代的方案。

---

## 4. 功能清单

| 模块 | 能力 |
|---|---|
| 电视直播 | 内置 m3u 频道源（央视/卫视/港澳台/国际新闻），上下键换台，左键频道列表，数字键直接跳台 |
| 轻站（v4.1） | 网页一键转 TV 界面；选集网格；逐集自动解析；三条兜底路径 |
| 网页浏览 | 任意网址 / Bing 搜索；边翻页边嗅探 m3u8/mp4/flv；发现即点亮「▶ 播放」 |
| 影视点播 | 苹果 CMS V10 海报墙（分类/搜索/选集） |
| 仓库管理 | TVBox 单仓/多仓配置拉取，站点直接进海报墙或浏览器 |
| 本地文件 | 播放 `Download/download` 里的视频、安装 APK |
| 局域网控制 | 手机/电脑打开 `http://电视IP:8080` 推送网址、推送代理、上传文件 |
| 代理 | Clash 等 HTTP 代理，同时作用于 ExoPlayer 与 WebView |
| 轻站规则管理（v4.1） | 查看生效规则、从网址导入、拉取官方、清除用户规则 |

## 5. 遥控器操作

| 场景 | 按键 |
|---|---|
| 主页 | ◀ ▶ 选卡片，OK 进入 |
| 直播 | ▲▼ 换台，◀ 频道列表，数字键跳台，OK 切换 铺满/完整 |
| 浏览器 | **菜单键 = ⚡轻站**，返回 = 上一页 |
| 轻站面板 | 方向键选集，OK 播放，返回 = 回网页 |
| 播放中 | OK 显示/隐藏控制条，返回 = 回轻站面板（再按退出） |

---

## 6. 构建与发布

云端（推荐）：push 到 `master` 触发 `.github/workflows/build.yml`，
JDK 17 + Android SDK 34 + Gradle 8.5 → `assembleRelease` → 签名 → 产物同时进
artifact 和 `apk` 分支（`apk` 分支用于无 artifact 通道时按二进制取回）。

本地：
```bash
gradle assembleRelease          # 需要 JDK17 与 platform-34
# 产物 app/build/outputs/apk/release/app-release.apk
```

⚠️ **签名密钥是公开的**：`app/mytv.jks` 与密码写在 `app/build.gradle` 里，而这个仓库是公开的。
好处是任何一次构建都能覆盖升级，坏处是任何人都能签出「同签名」的包。
如果要收紧，把 jks 改成 base64 存 GitHub Secrets、CI 里还原、并从仓库删除 jks —— 但那会导致
新包与旧包不同签名，必须卸载重装一次。当前为保持升级连续性，暂不切换。

---

## 7. 版本演进

| 版本 | 内容 |
|---|---|
| v3.5 – v3.9 | 早期迭代（历史 APK 已从仓库移除，可在 git 历史中找回） |
| 4.0 | 直播 + 浏览器嗅探 + CMS 海报墙 + 局域网控制；VERSION 2 |
| **4.1** | **轻站：一键网页转 TV 界面**（LiteEngine / LiteActivity / LiteRulesActivity）；修正嗅探误报（`poster.mp4.jpg`）；WebView 释放防泄漏；版本 41 |

> versionCode 从 4.0 的 `2` 直接跳到 `41`，是为了让后续版本号有清晰的号段（4.x = 4x）。

---

## 8. 已知限制（诚实清单）

- 轻站依赖页面结构，**站点大改版会让通用嗅探失效**，此时需要更新规则或手动点播放；
- 部分站点播放器在 iframe 里且需要点击/验证，逐集解析可能超时；
- DRM（Widevine）加密源无法播放；
- TVBox Spider（type 3，dex/js 爬虫）源不内置引擎，会转网页嗅探；
- 海报墙只支持苹果 CMS V10 协议；
- WebView 代理在系统 WebView 过旧的设备上可能不生效（失败静默降级）；
- 上传 >32MB 文件未做流式优化。

## 9. 合规

本项目**不内置、不分发任何影视内容，也不内置任何破解/解密引擎**。
轻站只是把「用户自己在浏览器里打开的网页」重新排版成适合遥控器的界面，
播放地址来自该网页自身。请在有合法授权的前提下使用。

## 10. 目录

```
app/src/main/java/com/mytv/lite/
  HomeActivity      主页
  MainActivity      直播 + 浏览器宿主 + 播放器
  BrowserView       WebView / 嗅探 / ⚡轻站入口
  LiteEngine        轻站引擎：规则、注入脚本、结果解析
  LiteActivity      轻站 TV 面板 + 逐集解析 + 播放
  LiteRulesActivity 规则管理（导入/更新）
  VodActivity       苹果CMS海报墙
  ReposActivity     TVBox 仓库
  ChannelActivity   影视频道
  FilesActivity     本地文件
  SettingsActivity  设置
  ProxyActivity     代理
  LiteServer        局域网 HTTP 服务
app/src/main/assets/
  lite_rules.json   轻站站点规则
  site_config.json  影视站点列表
  iptv_*.m3u        内置直播源
.github/workflows/build.yml
```

