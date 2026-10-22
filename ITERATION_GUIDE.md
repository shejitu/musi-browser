# 慕思影视 2.x — 逆向分析与迭代指导书（AI 协作必读）

> 本文档写给未来的 AI 助手和开发者本人：解释慕思影视 2.x 是怎么来的、逆向了什么、改了哪些、以后迭代从哪下手。
> 更新于 2026-10-22（V2.1 / 261022 版）。

---

## 一、这个项目是怎么来的（逆向思路总纲）

目标：为自家电视做一个**纯净、免费、免注册**的影视应用，能力对标"小苹果影视盒子"。

逆向方法论：**REA 式（Decompose → Understand → Recreate），只拆结构、学原理，不脱壳、不破解、不盗用商用组件。**

### 1. 被逆向对象：小苹果影视盒子（com.phoenix.tv v1.6.2，35MB）

手工拆解（unzip + aapt2 + strings，未用 Ghidra，因为 360 加固的 dex 无法反编译）：

| 拆出的事实 | 结论 |
|---|---|
| 启动 Activity = `com.github.tvbox.osc.ui.activity.SplashActivity` | 血统是 TVBoxOSC 开源项目的魔改 |
| `libjiagu.so` + StubApp | 360 加固，dex 加密 —— **明确不碰**，反正骨架已知 |
| `libquickjs.so` | JS 爬虫引擎（TVBox spider 体系） |
| `assets/chaquopy/` + `app.imy`（zip）内 app.py/runner.py/base/spider.py **未加密全读出** | Python 爬虫：标准 TVBox spider 接口（homeContent/detailContent/playerContent），Chaquopy 3.8 |
| ijkplayer 全家桶 + librtmp | 播放内核 |
| `libp2p.so` + `libxl_thunder_sdk.so` | 迅雷 P2P —— **商用 SDK 不盗用**，开源等价物替代 |
| `epg_data.json`（51zmt 台标 EPG）+ ua.db | 公开数据，可直接复用 |

**核心判断：小苹果 = TVBoxOSC 骨架 + 双脚本引擎（JS/Python）+ 现代 UI。所以升级路线不是自己写播放器，而是换一个"TVBoxOSC 血统的现代化顶配开源内核"——FongMi/TV。**

### 2. 为什么选 FongMi/TV 5.6.1（commit 11654b0）

- 官方 master（5.7.9）依赖 **私有 media3 fork + mpvplayer AAR**（仓库里 `.gitignore` 了 `lib-*.aar`，GitHub 无处下载，官方 APK 被 R8 混淆无法反提）→ **编译死路**；
- 5.6.1 是 **没有 mpv 之前的最后稳定版**：media3 可用官方版替代，功能仍保留 JS+Python 双爬虫、现代 leanback UI；
- 版本定位：`git log` 反查 versionName 5.6.1 的 commit（`11654b066e196c0242d6601c0b289455d3da4428`），浅克隆指定 SHA。

## 二、慕思化改造清单（改了哪些、怎么改的）

源码树：仓库 `musi-tv-fongmi/`（= /tmp/fongmi561 的去历史精简版，重建说明见 `musi-tv-fongmi/BUILD_MUSI.md`）。

### A. 身份与配置（必改三件套）
1. **包名**：`app/build.gradle` 里 `applicationId "com.mytv.musi"`（`namespace` 保留 `com.fongmi.android.tv` 不动——源码包名引用极广，改它代价巨大且无收益）；
2. **应用名**：`app/src/main/res/values/strings.xml` 的 `app_name` → 慕思影视；
3. **云端配置**：`app/src/leanback/.../HomeActivity.java` 的 `initConfig()`：
   ```java
   if (VodConfig.get().getConfig() == null || VodConfig.get().getConfig().isEmpty())
       VodConfig.load(Config.find("https://raw.githubusercontent.com/shejitu/musi-browser/master/musi.json", 0), getCallback());
   ```
   首开无配置时自动拉云端 musi.json —— **改源不重装**的机制所在。要换源站点改 musi.json，不要动 APK。

### B. 播放引擎（最大的一块工程）
FongMi 5.6.1 源码 import 了它私有 media3 fork 里的类。解法 = **官方 media3 1.11.1 + 手写 stub 包**：

1. `app/build.gradle` 加 12 条 `androidx.media3:*:1.11.1` 依赖（exoplayer/hls/dash/rtsp/ui/session/datasource-okhttp/database/extractor/effect/common）；
2. 新建 stub 包 `app/src/main/java/androidx/media3/`，补齐 fork 专有类（全部空实现/最小实现）：
   - `mpvplayer/`：MpvPlayer（**isAvailable() 恒 false** → MPV 引擎永不启用）、PlayerBase（Player 接口 122 个抽象方法的 no-op 基类）、MpvPlayerConfig/MpvAndroidOptions/MpvSubtitleOptions/MpvAudioFilter/MpvVideoEqualizer；
   - `common/`：MediaChapter、MediaEdition、DolbyVisionOutputPolicy；
   - `common/audio/`：AudioChannelMix、AudioChannelMode（含 clamp）、AudioEffectParameters（Builder 全套）、AudioEffectProcessor（extends BaseAudioProcessor，queueInput 透传）；
   - `effect/`：VideoAdjustment（Builder）、ColorToneAdjustEffect、DetailAdjustEffect；
   - `ui/`：PlayerSeekView（extends PlayerView + getTimeBar()）、`ui/danmaku/DanmakuConfig`（常量+公开字段）；
   - `exoplayer/`：source/preload/DiskPreloadManager（Builder+Options）、trackselection/DecodeTrackSelector（extends DefaultTrackSelector）、audio/AudioEqualizerController；
3. **源码摘除 fork API**（编译期修）：
   - `PlayerManager`：删 onMediaChaptersChanged/onMediaEditionsChanged 两个 override；getCurrentMediaChapters/Editions → 空 List；selectChapter/Edition → no-op；audio/text offset → 返回 0；
   - `PlaybackActivity`：删 danmaku 系列（setDanmaku*/sendDanmaku/setDanmakuSource）与 toggleDebugView/isDebugViewVisible 调用（对应 Listener override 一并删）；
   - `ExoPlayerEffect`：ExoPlayer.VIDEO_EFFECTS_*/AUDIO_PROCESSING_* switch → 直接 true/0；isSkipSilenceSupported → true；
   - `ExoUtil`：删 setRendererDecodePreferences、setDolbyVisionOutputPolicy；ERROR_CODE_PARSING_MANIFEST_* 收敛；
   - `ExoDiskPreload`：Options.builder() → new Options.Builder()；
   - `MediaItemFactory`：删 .setAdblock()；
   - `SubtitleSetting`：CaptionStyleCompat 8 参构造 → 官方 6 参；删 SubtitleView.reset()/setBottomPosition()/setTextSizeScale()；
   - `DanmakuSetting.getConfig()` → 返回 DanmakuConfig.DEFAULT；
   - `PlayerEngine`：`C.DECODE_SOFTWARE/HARDWARE`（fork 常量）→ 自定义 0/1；
4. **效果**：MPV 引擎禁用，ExoPlayer 唯一引擎。播放/选集/字幕/倍速/画面效果全正常；弹幕、章节/多版本菜单是明面功能损失（stub 空转，UI 入口保留但不生效）。

### C. 版本命名（本人定的规矩）
- 文件名：`慕思影视-YYMMDD-V主.minor`（如 慕思影视-261022-V2.1）；
- 系统内：versionName = V 后的号（2.1），versionCode 递增（201）；
- 每出新版自动删服务器上的旧版 APK，只留最新。

## 三、构建配方（重建环境时照抄）

```bash
# 环境
sudo apt install openjdk-21-jdk-headless   # FongMi 必须 JDK21
uv python install 3.10 && ln -s $(uv python find 3.10) ~/.local/bin/python3.10  # chaquopy 需要
# Gradle 9.7.1 装在 /opt/gradle-9.7.1；ANDROID_HOME=~/android-sdk（platform-37 + NDK 28.2 + cmake 3.22.1）

# 源码
git clone --depth 1 https://github.com/shejitu/musi-browser.git ~/xpglite
cp ~/xpglite/musi-tv-fongmi /tmp/fongmi561 -r
cat > /tmp/fongmi561/local.properties << EOF
sdk.dir=/home/agentuser/android-sdk
storeFile=/home/agentuser/xpglite/app/mytv.jks
keyAlias=mytv
storePassword=mytv2026
EOF

# 构建（arm64 为主力，v7a 为旧盒子）
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
cd /tmp/fongmi561
/opt/gradle-9.7.1/bin/gradle :app:assembleLeanbackArm64_v8aRelease :app:assembleLeanbackArmeabi_v7aRelease --no-daemon
# 产物：app/build/outputs/apk/leanbackArm64_v8a/release/、leanbackArmeabi_v7a/release/

# 签名验证
~/android-sdk/build-tools/34.0.0/apksigner verify <apk>
```

坑位备忘：
- chaquopy 报 "Couldn't find Python 3.10" → 装 python3.10 且 PATH 可见；
- `assembleArm64Release`/`assembleLeanbackRelease` 任务不存在 → 用上面带 abi 的全名；
- `app/libs/*.aar`（forcetech/hook/jianpian/thunder/tvbus）是 FongMi 官方私有件，已随源码入库；若丢失可从 FongMi/Release 的 module apk 提取。

## 四、未来迭代路线图（想优化时从这里挑）

| 方向 | 怎么做 | 难度 |
|---|---|---|
| 换源/加源 | 改 GitHub 上的 `musi.json`（48 站）与 `live_musi.txt`，不动 APK | ★ |
| 界面配色/启动页 | `app/src/leanback/res/` 的 layout/drawable；注意焦点用系统机制，别手动 DPAD 索引 | ★★ |
| 加内置仓库列表 | HomeActivity 或 ConfigDialog 里加推荐源数组（参照小苹果的多仓模式） | ★★ |
| 补回弹幕 | stub DanmakuConfig 换成真正的开源弹幕库（如 danmaku-flame-master 移植） | ★★★ |
| EPG 节目单（对标小苹果） | 把 51zmt 的 epg_data.json 内置进 assets + 直播页接 EPG 解析 | ★★★ |
| 补 MPV 内核 | 等官方公开 AAR，或自编 libmpv（libmpv-android 开源项目）替换 stub | ★★★★ |
| 版本发布 | 改 versionName/versionCode → 按命名规范出 APK → 推 GitHub Release | ★ |

## 五、边界与原则（不可违反）

1. **不脱壳、不破解**：360 加固（libjiagu/StubApp）、商用 SDK（迅雷 P2P）一律不碰；
2. 爬虫走公开采集源，**不碰付费版权内容的破解**；源失效换源，不做破解补丁；
3. 外部 AI 声称的"已修复"必须 git/源码核验（98cd7fb 教训：声称修了浏览器 v4.0，实际一行没进库）；
4. 遥控器交互一律系统焦点机制，**禁止手动 DPAD 索引**（v3.6 焦点 bug 教训）；
5. GitHub token 每次临时生成、用完撤销；本文件与任何提交中不得出现 token 明文。

## 六、相关文档索引

- `README.md` — 项目总览
- `BUILD.md` — 浏览器版构建
- `ARCHITECTURE.md` — 浏览器版架构
- `musi-tv-fongmi/BUILD_MUSI.md` — 影视版构建速查
- `musi-tv-fongmi/docs/{CONFIG,LIVE,LOCAL}.md` — FongMi 原生功能文档（配置/直播/本地播放）
- `tvbox-custom/BUGFIXES.md` — 旧 v2.1-takagen 线的修复史（已停更，仅参考）
- 云端配置：`musi.json`（点播 48 站）+ `live_musi.txt`（145 频道）
