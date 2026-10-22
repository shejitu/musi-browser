# 慕思影视 2.x（FongMi 内核）构建说明

- 内核：FongMi/TV 5.6.1（commit 11654b0），慕思化改造
- 环境：JDK21 + Gradle 9.7.1 + AGP 9.3.1 + compileSdk 37 + NDK 28.2 + python3.10（chaquopy buildpython，uv python install 3.10）
- 命令：`gradle :app:assembleLeanbackArm64_v8aRelease :app:assembleLeanbackArmeabi_v7aRelease --no-daemon`
- local.properties 自建：sdk.dir、storeFile/keyAlias/storePassword（签名沿用 app/mytv.jks @ xpglite）
- 慕思改动：applicationId com.mytv.musi、label 慕思影视、HomeActivity 空配置自动载 https://raw.githubusercontent.com/shejitu/musi-browser/master/musi.json、MpvPlayer.isAvailable=false（ExoPlayer 唯一引擎）、stub 包 androidx/media3/**（fork 私有类的编译替身）、摘除弹幕/章节/逐字偏移等 fork API
- 产物签名：apksigner verify 已过；app/libs/*.aar 为官方私有件（forcetech/hook/jianpian/thunder/tvbus），缺失时从 FongMi/Release releases 的 module apk 提取
