# 构建与发布

## 环境

| 项 | 版本 |
|---|---|
| JDK | 17 |
| Gradle | 8.5 |
| AGP | 8.2.2 |
| compileSdk | 34 |
| build-tools | 34.0.0 |

## 云端（推荐）

push 到 `master` 即触发 `.github/workflows/build.yml`：

1. JDK 17 + `android-actions/setup-android` + `sdkmanager` 安装 platform-34 / build-tools 34
2. `gradle assembleRelease`
3. 用 `app/mytv.jks` 签名，产物 `MusiBrowser-v4.1.apk`
4. 上传 artifact `musi-apk`，同时强推 `apk` 分支（无 artifact 通道时按二进制取回）

> `apk` 分支是构建产物分支，会被每次构建 `-f` 覆盖，不要在上面改代码。

## 本地

```bash
gradle assembleRelease
ls app/build/outputs/apk/release/
```

首次构建需要联网拉 AGP 与 AndroidX 依赖（media3 1.3.1、webkit 1.8.0、okhttp 4.12.0）。

## 签名

```gradle
signingConfigs {
    release {
        storeFile file("mytv.jks")
        storePassword "mytv2026"
        keyAlias "mytv"
        keyPassword "mytv2026"
    }
}
```

- **不要换 key**：换了之后新包无法覆盖旧版，必须卸载重装（本地设置会丢）。
- 当前 jks 与密码在公开仓库里，任何人可签同签名包。要收紧：把 jks base64 存 GitHub Secrets，
  CI 里还原到 `app/mytv.jks`，然后 `git rm --cached app/mytv.jks` 并从仓库删除。
  代价是新旧包签名不同，用户需卸载重装一次。

## 版本号

- `versionName`：`4.1` 形式，功能变更 +0.1
- `versionCode`：`41` 形式（×10 + 小版本），**只增不减**
- 新包文件名带版本：`MusiBrowser-v4.1.apk`，不要覆盖旧版本文件

## 自检清单（发布前）

1. 主页 7 张卡片都能进；
2. 直播能出画面，▲▼ 换台正常；
3. 浏览器打开一个影视站 → 按菜单键 → 轻站面板出现海报/选集；
4. 点一集能播，返回键回面板，再返回退出；
5. 设置 → 轻站规则管理 → 拉取官方规则成功；
6. 局域网 `http://电视IP:8080` 推送网址可用。
