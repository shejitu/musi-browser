# 慕思影视（TVBox 内核版）— Bug 记录与解决方案汇总

> 面向接手的 AI 协作者。基座：takagen99/TVBoxOSC（见 tvbox-custom/README.md 的构建说明）。
> 当前版本 v2.2。包名 com.github.tvbox.osc.tk（注意：曾计划改 com.mytv.musi，v1.4 起沿用原包名避免升级冲突）。

---

## 已修复（历轮 bug → 根因 → 解法）

### 1. 开机闪退（v1.3 修复）
- 现象：装上即闪退，无法进入。
- 根因：启动入口从 HomeActivity 改为 MusiHomeActivity 后，`ApiConfig.loadConfig()` 调用 `HomeActivity.getRes()`，静态 `res` 仅在 HomeActivity.onCreate 赋值 → NPE。
- 解法：`HomeActivity.getRes()` 增加 `if (res == null) res = App.getInstance().getResources();`。
- 教训：**改动启动 Activity 时必须排查所有静态单例的初始化依赖**。

### 2. 崩溃无日志难定位（v1.4 加固）
- 解法：
  - App.onCreate 注册全局 UncaughtExceptionHandler → 写 `外部存储/Android/data/<pkg>/files/musi_crash.txt`。
  - MusiHomeActivity.onCreate 整体 try/catch，异常自动降级进 TVBox 原生 HomeActivity。
- 位置：base/App.java、ui/activity/MusiHomeActivity.java。

### 3. 多仓配置无法识别（v1.8 修复）
- 现象：选 `{"urls":[{name,url}...]}` 格式（挺好GYCK/小盒子多仓）后无法加载，或直接报错。
- 根因：takagen 的 `ApiConfig.parseJson()` 只认 `sites`/`video.sites`，遇到只有 `urls` 的多仓配置会 NPE。
- 解法：parseJson 开头识别「有 urls 无 sites」→ 填充 `ApiConfig.multiRepoList`（public 字段）→ return；HomeActivity 在 loadConfig success 回调里检测 multiRepoList 非空则弹子仓选择列表，选中后写回 HawkConfig.API_URL 并递归 initData()。
- 已实测：挺好GYCK 32 子仓、小盒子多仓 18 子仓可用。
- 已确认死亡地址（勿再内置）：cnb.cool/fish2018/xs（仓库冻结）、xmbjm.fh4u.org（域名失效）。

### 4. 多仓子仓列表可看不可选（v1.9 修复）
- 根因：`AlertDialog.setItems` 的列表在电视遥控器上焦点行为不完整。
- 解法：自建 ListView + ArrayAdapter + setOnItemClickListener，dlg.show() 后 `lv.requestFocus(); lv.setSelection(0);`。
- 教训：**电视 UI 一律显式 requestFocus，勿依赖系统对话框默认焦点**。

### 5. 选仓库必须退回主界面才弹子仓（v2.0→v2.1 修复）
- 现象：配置地址确定后无事发生，回主界面才弹子仓选择；且此流程下确定/切换子仓会**闪退**。
- 根因 A（v2.0 流程缺陷）：弹窗逻辑只写在 HomeActivity；ApiDialog 确定后未预载。
- 根因 B（v2.0 闪退）：ApiDialog 在 `dismiss()` **之后**的回调里调用 `getContext()`——Dialog dismiss 后返回 null → NPE。
- 解法（v2.1）：ApiDialog 确定按钮内：先 `Context ctx = getContext()` 捕获 → dismiss() → `loadConfig()` 预载 → 回调里全部用捕获的 Activity 引用（actFinal）建 ListView 弹子仓选择；子仓选中后 `listener.onchange(subUrl)` 通知 SettingActivity 保存。
- 教训：**Dialog 异步回调中严禁使用 getContext()/getDialog() 等生命周期方法，必须在 dismiss 前捕获**。

### 6. 电脑端网页上传文件失败（v2.0 修复）
- 现象：`http://电视IP:9978/` 选择文件后没有上传动作。
- 根因：重写的 index.html 沿用原版 `onchange="uploadTip()"` 流程，但原版确认弹窗 `#uploadTip` 在新页面不存在 → doUpload 永远不执行。
- 解法：改为 `onchange="doUploadDirect()"`，选完文件立即 FormData POST `/upload`（path=Download），页面显示成功/失败。
- 教训：**替换内嵌网页时必须核对每个 JS 函数的依赖链（DOM 元素、后续函数）**。

### 7. 配置弹窗推荐按钮遮挡输入区（v1.6 修复）
- 根因：推荐列表被 addView 到横向主容器（二维码旁），挤压右栏。
- 解法：插入到右栏内部（tvAddress 之后），dialog_api.xml 高度 vs_460→vs_560（dimens.xml 需新增 vs_560）。

### 8. 其它已交付功能（非 bug，定位备查）
- MusiHomeActivity 卡片式启动页（影视点播/电视台直播/配置地址/网盘/历史）。
- 内置 145 频道直播：GitHub `live_musi.txt`（TVBox txt 格式 `组名,名称#url`），经 musi.json lives.proxy:// 引用。
- musi.json（云端配置）= 俊佬 top98 完整内容 + 内置直播；dxawi 源因 spider JAR 托管在 zooho 被 429 限流而弃用。
- 电脑控制台（9978）慕思风格重写：仓库一键切换/推送、直播源、EPG、socks 代理、搜索、推送播放、上传。
- WebController.kt 新增 `/api/pushConfig?url=` 与 `/api/pushProxy?proxy=`。
- 推荐仓库顺序（每行4个）：挺好GYCK、小盒子多仓、慕思聚合、俊佬。
- 「文件」入口自动注册 Download 目录为本地驱动（DriveActivity.initData）。
- socks 代理：配置弹窗可选接口填 `IP:端口`，ExoMediaPlayer 播放失败自动走 socks 重试（takagen 原生能力）。

---

## 已知待查（交接给下一个 AI）

### A. v2.1 子仓切换仍需验证
上面第 5 条的 v2.1 修复逻辑已编译通过，但**用户尚未确认真机不再闪退**。若仍闪退，读取 musi_crash.txt：
`adb shell cat /storage/emulated/0/Android/data/com.github.tvbox.osc.tk/files/musi_crash.txt`
或让用户通过电视文件管理器取出。重点怀疑：loadConfig 回调线程（OkGo 回调在子线程）里 runOnUiThread 的 Activity 状态、以及 SettingActivity.onchange 里是否又触发一次 loadConfig 冲突。

### B. Spider 源播放成功率
俊佬源的 csp_XXX 站点走引擎解析（非嗅探），部分站需代理或已失效——属源本身质量问题，非 App bug。可尝试：不同子仓、或代理环境下测试。

### C. 多仓嵌套
子仓本身又是多仓格式时（罕见），当前逻辑：载入子仓→再次识别 urls→再弹选择，理论已支持但未实测。

---

### 6. 控制台"推送"点了没反应（v2.2 修复）
- 现象：9978 控制台点"推送仓库到电视"、"推送直播源/EPG/代理"，电视端毫无反应。
- 根因：`ControlManager` 发出 `RefreshEvent(TYPE_API_URL_CHANGE/LIVE/EPG/PROXYS_CHANGE)` 后，**没有任何订阅者真正处理**——只有 ApiDialog 在打开时把地址填进输入框。
- 解法：
  - `App.onCreate` 注册 EventBus，新增 `onConsolePush()`：API_URL 推送→存 Hawk+历史+Toast；LIVE/EPG/代理→存 Hawk+Toast（代理下次播放生效）。
  - `HomeActivity.refresh()` 收到 TYPE_API_URL_CHANGE 时若正在点播页就地 `initData()` 重载（多仓会自动弹子仓选择）。
  - 12345 的 `/api/pushConfig`、`/api/pushProxy` 走同一事件，同步生效。
- 另修上游 bug：`TYPE_PROXYS_CHANGE` 与 `TYPE_SET_DANMU_SETTINGS` **值撞车（都是 18）**——弹幕开关 post 的是 Boolean，会被误读成代理地址。已将前者改为 100。

### 7. ApiDialog 提交普通单仓后设置页不刷新（v2.2 修复，v2.1 回归）
- 现象：v2.1 重构后，提交普通单仓 URL 只存 Hawk，不调 `listener.onchange()`（基座原版会调），设置页显示的地址还是旧的。
- 解法：预载 success 回调里非多仓分支补 `listener.onchange(newApi)`；error 回调加 Toast（之前预载失败静默，用户不知道地址没生效）。

### 8. 存的多仓地址下次进点播页无处理（v2.2 修复）
- 现象：多仓地址提交后若取消子仓选择，API_URL 存的是多仓地址；下次进"影视点播"时 parseJson 提前 return，页面空数据且无任何提示。
- 解法：`HomeActivity` loadConfig success 回调里检测 `multiRepoList` 非空→弹子仓选择（与 ApiDialog 同款 ListView）；取消则清空标记后照常初始化。

### 9. MusiHomeActivity 无默认焦点（v2.2 修复）
- 现象：卡片页启动后遥控器首次按键焦点乱跳。
- 解法：buildUi 末尾 `grid.getChildAt(0).requestFocus()`。

### 10. 控制台上传接口问题（v2.2 加固）
- `RemoteServer` `/upload` 等接口异常被吞掉仍回 "OK"，前端误以为成功→现回 500 便于排查。
- 路径穿越防护：`path`/`fn` 来自局域网客户端，现做 canonical 路径校验，逃逸则跳过。
- `unzip()` zip-slip 防护：跳过 `../` 逃逸条目。

### 11. aapt2 34 构建兼容（v2.2）
- `res/values/strings.xml`、`values-zh/strings.xml` 里有 `//` 行注释（基座原样），aapt2 34 报错 "plain text not allowed"→已转为标准 `<!-- -->` 注释。
- 教训：基座升级后留意构建工具链的严格性变化。

### 12. 定制目录结构化（v2.2）
- 之前"直接改在源码里"的修复（getRes 空保护、ApiConfig 多仓识别）没有收进 tvbox-custom，导致换机器/重拉基座时丢失。
- 解法：tvbox-custom 改为按源码树组织（`app/src/main/...` 一一对应），应用时 `cp -r tvbox-custom/app/* app/` 即可，不再需要手动映射表。

## 构建速查
```
git clone --depth 1 https://github.com/takagen99/TVBoxOSC.git
# 覆盖 tvbox-custom/ 下的定制文件到源码对应位置（README 有映射表）
cd TVBoxOSC && export ANDROID_HOME=~/android-sdk
/opt/gradle-7.5/bin/gradle assembleArm64GenericNormalRelease   # 或 assembleArmeabiGenericNormalRelease
zipalign -f -p 4 <apk> out.apk
apksigner sign --ks ~/xpglite/app/mytv.jks --ks-pass pass:mytv2026 --ks-key-alias mytv --out final.apk out.apk
```
工具链：JDK17 + Gradle 7.5(/opt/gradle-7.5) + AGP 7.4.2 + SDK34。Gradle 8.5 不兼容此项目。
