# 编译说明

## 环境要求

| 组件 | 版本 | 说明 |
|---|---|---|
| JDK | 17 | `sudo apt install openjdk-17-jdk-headless` |
| Android SDK | Platform 34 + Build-Tools 34.0.0 | 通过 cmdline-tools 安装 |
| Gradle | 8.5 | [下载地址](https://services.gradle.org/distributions/gradle-8.5-bin.zip) |

## 1. 安装 Android SDK

```bash
mkdir -p ~/android-sdk/cmdline-tools && cd /tmp
curl -sL -o cmdtools.zip https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
unzip -q cmdtools.zip -d ~/android-sdk/cmdline-tools
mv ~/android-sdk/cmdline-tools/cmdline-tools ~/android-sdk/cmdline-tools/latest

export ANDROID_HOME=~/android-sdk
yes | ~/android-sdk/cmdline-tools/latest/bin/sdkmanager --sdk_root=$ANDROID_HOME \
    "platform-tools" "platforms;android-34" "build-tools;34.0.0"
```

## 2. 安装 Gradle

```bash
cd /tmp
curl -sL -o gradle.zip https://services.gradle.org/distributions/gradle-8.5-bin.zip
sudo unzip -q gradle.zip -d /opt
/opt/gradle-8.5/bin/gradle --version   # 应显示 JVM: 17.x
```

## 3. 生成签名密钥（首次编译前）

```bash
keytool -genkeypair -v -keystore app/mytv.jks -alias mytv \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -storepass mytv2026 -keypass mytv2026 \
    -dname "CN=MyTV,O=Personal,C=CN"
```

> 仓库里已带一个自用密钥 `app/mytv.jks`（密码同为 mytv2026）。**仅供自用调试**；如要正式分发，请重新生成自己的密钥并更换密码。

## 4. 编译

```bash
export ANDROID_HOME=~/android-sdk
cd xpglite
/opt/gradle-8.5/bin/gradle assembleRelease --no-daemon
```

产物：`app/build/outputs/apk/release/app-release.apk`

## 5. 验证（可选）

```bash
~/android-sdk/build-tools/34.0.0/aapt dump badging \
    app/build/outputs/apk/release/app-release.apk | head -3
```

应看到 `package: name='com.mytv.lite' ... application-label:'慕思浏览器'`

## 常见问题

**Q: mergeReleaseResources 报 Duplicate resources**
同一资源名同时存在 .xml 和 .png。检查 `res/drawable/banner.*` 只保留一个。

**Q: shouldInterceptRequest 报错**
该方法的返回类型是 `WebResourceResponse`（不是 boolean），返回 `null` 表示不拦截。

**Q: 电视上安装失败（解析包错误）**
确认电视是 armeabi-v7a 或以上（本 APK 未限定 ABI，通用）；Android 版本 ≥ 5.0。

## 添加新功能后的发布流程

1. 改 `app/build.gradle` 里的 `versionCode`（+1）和 `versionName`
2. 重新 `gradle assembleRelease`
3. 拷贝 `app-release.apk` 改名带上版本号
