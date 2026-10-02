# 慕思影视 (TVBox 内核版) 定制说明

## 基座
takagen99/TVBoxOSC (开源 TVBox 分支，含完整 catvod Spider 引擎)
- 拉取: git clone --depth 1 https://github.com/takagen99/TVBoxOSC.git
- 工具链: Gradle 7.5 + AGP 7.4.2 + JDK17 + SDK34
- 构建: gradle assembleArm64GenericNormalRelease / assembleArmeabiGenericNormalRelease
- 打包后需 zipalign + apksigner (密钥 ~/xpglite/app/mytv.jks, pass mytv2026, alias mytv)

## 本目录结构（v2.2 起：按源码树组织，直接覆盖）
`app/` 下的文件与 TVBoxOSC 源码的 `app/` 目录结构一一对应，应用定制时直接覆盖：
```
cd TVBoxOSC
cp -r /path/to/tvbox-custom/app/* app/
```
文件清单（20 个）：
- `app/src/main/AndroidManifest.xml` — 启动入口改为 MusiHomeActivity (LAUNCHER + LEANBACK_LAUNCHER)
- `app/src/main/java/com/github/tvbox/osc/ui/activity/MusiHomeActivity.java` — 卡片式启动主界面
- `app/src/main/java/com/github/tvbox/osc/ui/activity/HomeActivity.java` — v1.3 getRes 空保护；v2.2 多仓子仓选择、推送仓库就地重载
- `app/src/main/java/com/github/tvbox/osc/ui/activity/DriveActivity.java` — 自动注册 Download 目录为本地驱动
- `app/src/main/java/com/github/tvbox/osc/ui/dialog/ApiDialog.java` — 确定后预载+子仓选择；v2.2 恢复单仓通知、失败 Toast
- `app/src/main/java/com/github/tvbox/osc/ui/fragment/ModelSettingFragment.java` — 配置地址为默认焦点
- `app/src/main/java/com/github/tvbox/osc/api/ApiConfig.java` — v1.8 多仓识别 multiRepoList
- `app/src/main/java/com/github/tvbox/osc/base/App.java` — 全局崩溃日志；v2.2 控制台推送落盘
- `app/src/main/java/com/github/tvbox/osc/event/RefreshEvent.java` — v2.2 TYPE_PROXYS_CHANGE 改值（原与弹幕开关撞值）
- `app/src/main/java/com/github/tvbox/osc/server/RemoteServer.java` — v2.2 上传路径穿越/zip-slip 防护、失败回 500
- `app/src/main/java/com/github/tvbox/osc/server/WebController.kt` — /api/pushConfig、/api/pushProxy
- `app/src/main/java/com/github/tvbox/osc/viewmodel/SourceViewModel.java` — v2.2 构建兼容：`LinkedHashMap.Entry` 加 `Map.` 限定（JDK17 编译需要）
- `app/src/main/res/values-zh/strings.xml` — app_name=慕思影视；app_source 指向云端配置
- `app/src/main/res/values/strings.xml` — v2.2 `//` 注释转标准 XML 注释（aapt2 34 要求）
- `app/src/main/res/values/dimens.xml` — 新增 vs_560
- `app/src/main/res/layout/dialog_api.xml` — 配置弹窗定制（含快捷仓库按钮）
- `app/src/main/res/drawable/musi_rec_btn.xml` / `app_icon.png` — 按钮样式 / 猫 logo
- `app/src/main/res/raw/index.html` / `script.js` — 9978 电视控制台页面

> v2.2 起不再使用扁平文件+手动映射：之前"直接改在源码里"的修复（getRes、多仓识别等）
> 曾因没收进定制目录而丢失，本次已全部收录。旧版扁平文件已移除。

## 内置源（云端配置，改这里不用重装 App）
- https://raw.githubusercontent.com/shejitu/musi-browser/master/musi.json  (48站点)
- .../live_musi.txt  (145个内置电视台频道, TVBox txt 格式: 组名,名称#url)
仓库里有任何变动只需改 ~/xpglite/musi.json / live_musi.txt 并 push。

## 安全说明
本 APK 由上述开源源码本地编译，无第三方 SDK。经全量字符串扫描确认不含
7moor/fs-im-kefu 等客服上报域名（那些出现在网上流传的第三方改版 APK 中）。

## 版本历史
- v2.2 (2026-10-02): 推送链路修复（控制台推送真正生效）、上传安全加固、多仓流程补完、
  ApiDialog 单仓通知恢复、MusiHome 默认焦点、资源目录结构化。详见 BUGFIXES.md。
- v2.1 / v2.0 / v1.x: 见 BUGFIXES.md。
