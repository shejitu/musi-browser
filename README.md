# 慕思浏览器 (musi-browser)

Android TV 电视应用：**主菜单首页 + 电视直播 + TVBox仓库影视点播（海报墙）+ 网页浏览/视频嗅探 + 本地文件 + 局域网控制台 + Clash代理**。无注册、无登录、无广告、无加固，纯净自用。

![版本](https://img.shields.io/badge/version-3.5-orange) ![平台](https://img.shields.io/badge/platform-Android%20TV%205.0+-green)

## 下载

[Releases](https://github.com/shejitu/musi-browser/releases) 或仓库根目录 `慕思浏览器-v3.5.apk`（约 5.7MB）。

## 主页功能卡片（按序）

| # | 卡片 | 功能 |
|---|---|---|
| 1 | 📺 电视直播 | 内置 IPTV-org 公开源（大陆/港/台/国际新闻），上下换台、数字跳台 |
| 2 | 📚 仓库管理 | TVBox 单仓/多仓：内置 12 仓 + 手动添加 + 拉取配置 + 站点一键进点播 |
| 3 | 🎬 影视点播 | 海报墙点播（苹果CMS 协议源）：5列海报网格 + 分类筛选 + 搜索 + 详情选集播放 |
| 4 | 📁 本地文件 | 打开 `Download/download/`：视频点击播放、APK 点击安装 |
| 5 | ⚙️ 设置 | 频道源切换（内置源+自定义 m3u 订阅） |
| 6 | 🌐 网页浏览 | WebView 浏览器 + m3u8/mp4 视频嗅探 + 内置影视站点面板 |

**副标题右侧 ⚡代理按钮**：填局域网内 Clash 代理（IP:端口），网页+播放全走代理。
**底部**：显示 `局域网控制: http://电视IP:8080`。

## 局域网控制台（电脑 → 电视）

浏览器打开 `http://电视IP:8080`：

- **推送网址**：电视端直接打开网页
- **一键推送代理**：把电脑上的 Clash（需开启"允许局域网连接"）变成电视的代理，填电脑IP+端口（默认7890）一键推送
- **上传文件**：存到电视 `Download/download/`（支持中文文件名），电视主页"本地文件"直接打开

## 🎮 遥控器

| 按键 | 主页 | 直播 | 点播/海报墙 | 浏览器 |
|---|---|---|---|---|
| ◀▶ | 选卡片 | 换台 | **翻页** | 网页焦点 |
| OK | 进卡片 | 切换画面 ZOOM/FIT | 打开详情 | — |
| 菜单(INFO) | — | 频道列表 | — | — |
| 搜索键 | — | 进浏览器 | — | — |
| 数字键 | — | 跳台 | — | 输入 |
| 返回 | 退出 | 回主页 | 关详情/回仓库 | 网页后退 |

## 架构与开发（给后续开发者/AI）

详见 [ARCHITECTURE.md](ARCHITECTURE.md)（设计思路、模块职责、协议说明、迭代指南）。编译步骤见 [BUILD.md](BUILD.md)。

## 技术栈

- 纯 Java + Android SDK（无第三方 UI 框架），minSdk 21 / target 34
- [Media3/ExoPlayer](https://github.com/androidx/media) 1.3.1 + OkHttp DataSource（支持代理）、HLS
- Android WebView + `shouldInterceptRequest` 视频嗅探；androidx.webkit ProxyController（WebView 代理）
- 手写零依赖 HTTP 服务器（推送/上传/代理推送，端口 8080）
- 苹果CMS V10 `api.php?ac=videolist` 协议（海报墙点播数据源）
- 频道数据：[IPTV-org](https://github.com/iptv-org/iptv) 公开 m3u

## 免责声明

仅供个人学习自用。频道/影视数据来自公开渠道，内容版权归各平台所有，本应用不绕过任何付费机制。请遵守当地法律法规。
