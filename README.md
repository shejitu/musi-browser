# 慕思浏览器 (musi-browser)

Android TV 电视应用：**开机即播的直播 + 网页浏览/影视点播 + 局域网控制台**，无注册、无登录、无广告、无加固，纯净自用。

![版本](https://img.shields.io/badge/version-2.1-orange) ![平台](https://img.shields.io/badge/platform-Android%20TV%205.0+-green)

## 下载

 releases 页面或仓库根目录的 `慕思浏览器-v2.1.apk`（约 5MB），传到电视安装即可。

## 功能

### 📺 电视直播（打开即播）
- 默认加载中国大陆公开频道源（IPTV-org，145 台：CCTV、各省卫视等）
- 频道数据内置在 APK 里，首次打开零配置直接播放

### 🌐 网页浏览 / 影视点播
- 遥控器**搜索键**进入浏览器模式（输入网址直达，关键词走 Bing 搜索）
- **视频嗅探**：浏览任何网页时自动检测 m3u8 / mp4 等视频流，发现后点「▶ 播放」即可全屏点播
- 内置影视站点面板：YouTube、B站、爱奇艺、腾讯视频、优酷、芒果TV、CCTV直播

### 📡 局域网控制台
APK 启动后自动在 8080 端口开 HTTP 服务。电脑浏览器打开 `http://电视IP:8080`：

- **推送网址** → 电视端浏览器直接打开
- **上传文件** → 保存到电视 `Download/download/` 文件夹（支持中文文件名）
- **查看已上传文件**

### ⚙️ 频道源管理
按 **OK 键** 打开设置，可切换内置源或填自定义 m3u 订阅地址：
- 中国大陆（内置 145 台）
- 香港（内置 18 台）
- 台湾（内置 26 台）
- 国际新闻（内置 1000+ 台）
- 自定义订阅（任意 m3u URL）

## 🎮 遥控器操作

| 按键 | 直播模式 | 浏览器模式 |
|---|---|---|
| 上/下/左/右 | 换台 | 网页滚动/焦点移动 |
| 菜单键(INFO) | 频道列表 | — |
| 搜索键 | 进入浏览器 | — |
| OK 确认键 | 打开设置 | — |
| 数字键 0-9 | 跳到第 N 台 | 输入 |
| 返回键 | 关闭面板 | 网页后退 / 退出浏览器 |

## 从源码编译

见 [BUILD.md](BUILD.md)。

## 技术栈

- 纯 Java + Android SDK（无第三方 UI 框架）
- [Media3 / ExoPlayer](https://github.com/androidx/media) 1.3.1（HLS 直播与点播播放）
- Android [WebView](https://developer.android.com/reference/android/webkit/WebView)（网页浏览与视频流嗅探）
- 内置轻量 HTTP 服务器（`ServerSocket` 手写，零依赖，支持 multipart 文件上传）
- 频道数据源：[IPTV-org](https://github.com/iptv-org/iptv)（开源公开 m3u）

## 目录结构

```
xpglite/
├── app/
│   ├── build.gradle            # 应用模块配置（签名/依赖）
│   ├── mytv.jks                # 发布签名密钥（密码: mytv2026，仅供自用，勿用于分发）
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/mytv/lite/
│       │   ├── MainActivity.java   # 主界面：直播播放器 + 频道面板 + 设置
│       │   ├── BrowserView.java    # WebView 浏览器 + 视频嗅探
│       │   ├── SitesPanel.java     # 内置影视站点面板
│       │   ├── Sources.java        # 频道源管理（内置+自定义订阅）
│       │   └── LiteServer.java     # 局域网 HTTP 服务器（推送/上传）
│       └── assets/                 # 内置频道源 m3u 文件
├── build.gradle / settings.gradle / gradle.properties
```

## 免责声明

- 本项目仅供个人学习与自用，频道数据来自公开的 IPTV-org 项目
- 内置影视站点的内容版权归各平台所有，VIP 内容需自行订阅，本应用不绕过任何付费机制
- 请遵守所在地区的法律法规
