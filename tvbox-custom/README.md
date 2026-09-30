# 慕思影视 (TVBox 内核版) 定制说明

## 基座
takagen99/TVBoxOSC (开源 TVBox 分支，含完整 catvod Spider 引擎)
- 拉取: git clone --depth 1 https://github.com/takagen99/TVBoxOSC.git
- 工具链: Gradle 7.5 (/opt/gradle-7.5) + AGP 7.4.2 + JDK17 + SDK34
- 构建: gradle assembleArm64GenericNormalRelease / assembleArmeabiGenericNormalRelease
- 打包后需 zipalign + apksigner (密钥 ~/xpglite/app/mytv.jks, pass mytv2026, alias mytv)

## 本目录定制文件（覆盖到源码对应位置）
1. MusiHomeActivity.java -> app/src/main/java/com/github/tvbox/osc/ui/activity/
   卡片式启动主界面：影视点播/电视台直播/配置地址/网盘/历史收藏
   焦点机制：OnFocusChangeListener + 系统焦点（勿手动拦截 DPAD）
2. AndroidManifest.xml -> app/src/main/
   启动入口改为 MusiHomeActivity (LAUNCHER + LEANBACK_LAUNCHER)
3. strings_zh.xml -> app/src/main/res/values-zh/strings.xml
   app_name=慕思影视; app_source 指向云端配置
4. app_icon.png -> app/src/main/res/drawable/app_icon.png (猫logo)

## 内置源（云端配置，改这里不用重装 App）
- https://raw.githubusercontent.com/shejitu/musi-browser/master/musi.json  (48站点)
- .../live_musi.txt  (145个内置电视台频道, TVBox txt 格式: 组名,名称#url)
仓库里有任何变动只需改 ~/xpglite/musi.json / live_musi.txt 并 push。

## 安全说明
本 APK 由上述开源源码本地编译，无第三方 SDK。经全量字符串扫描确认不含
7moor/fs-im-kefu 等客服上报域名（那些出现在网上流传的第三方改版 APK 中）。
