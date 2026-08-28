# wiliwili Android

wiliwili 的 Android 移植，基于 [xfangfang/wiliwili](https://github.com/xfangfang/wiliwili) 与
[xfangfang/borealis](https://github.com/xfangfang/borealis) 自带的 SDL2 Android 后端。

上游 wiliwili 官方并不提供 Android 版本（README 中写明 Android / iOS 不在讨论之内），
本仓库是社区移植：UI 使用 borealis 的 SDL2/GLES 后端，视频使用 mpv-android 预编译的
libmpv，网络使用 NDK 现场编译的 curl(mbedtls TLS)。

## 云端构建（推荐）

推送代码到本仓库后 GitHub Actions 会自动构建 APK：
`.github/workflows/android.yml` → Artifacts `wiliwili-android-apk`。

## 本地构建（可选）

需要 Android SDK + NDK r26 + JDK 17：

```shell
# 1. 拉取代码与子模块
git clone --recursive https://github.com/Tenwang318/wiliwili-android.git
cd wiliwili-android

# 2. 编译宿主机的 libromfs-generator（把资源打包进 so）
bash library/borealis/build_libromfs_generator.sh
cp library/borealis/libromfs-generator ./libromfs-generator

# 3. 下载 libmpv 预编译库与 mpv 头文件
bash .ci/prepare_mpv.sh

# 4. 生成调试签名并编译
cd android-project
./gradlew assembleDebug
# 产物: app/build/outputs/apk/debug/app-debug.apk
```

## 结构说明

- `android-project/` — Android 工程（SDL2 Java 层来自 borealis 模板）
- `android-project/app/jni/CMakeLists.txt` — NDK 构建入口：mbedtls(FetchContent) + SDL2 + wiliwili
- `.ci/prepare_mpv.sh` — 下载 mpv-android 预编译 libmpv(.aar) 并解包出各 ABI 的 .so 与头文件
- `.github/workflows/android.yml` — CI 构建

## 许可

wiliwili 采用 GPL-3.0 协议，本项目同样以 GPL-3.0 发布。
