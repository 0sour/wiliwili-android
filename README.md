# wiliwili-android

[wiliwili](https://github.com/xfangfang/wiliwili) 的 Android 移植与修复版，面向手柄/掌机用户，
以 **单屏 APK** 的形式维护和发布。

[![Build Android APK](https://github.com/0sour/wiliwili-android/actions/workflows/android.yml/badge.svg)](https://github.com/0sour/wiliwili-android/actions/workflows/android.yml)
[![Latest release](https://img.shields.io/github/v/release/0sour/wiliwili-android)](https://github.com/0sour/wiliwili-android/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/0sour/wiliwili-android/total)](https://github.com/0sour/wiliwili-android/releases)
[![License: GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-blue.svg)](LICENSE)

> **非官方客户端。** 本项目与哔哩哔哩（Bilibili）官方无关，使用自己的账号登录，风险自负。
> 本项目基于 GPL-3.0 的 wiliwili，源代码按 GPL-3.0 提供。

## 下载与安装

- **最新 APK**：<https://github.com/0sour/wiliwili-android/releases/latest>
- 系统要求：Android 8.0+（minSdk 26）
- 架构：`arm64-v8a`、`armeabi-v7a`、`x86_64`
- 安装：下载 APK 后允许“安装未知来源应用”，或：

```bash
adb install -r wiliwili-android-1.6.0.apk
```

Release APK 使用项目固定密钥签名，证书 SHA-256：

```text
70:45:F3:24:1E:63:98:10:33:CF:77:8E:D2:B1:95:20:92:3E:70:B2:0B:FE:A7:64:18:D1:8D:D6:66:DD:6E:62
```

> 证书 DN 是 `CN=Android Debug`：为了让 2026-11-08 之前安装的本地 debug 包可以直接覆盖升级，
> 项目密钥沿用了原 debug 密钥的密钥材料。它只用于本项目，请勿复用到其他应用。
> 如果你之前装过其他签名的 wiliwili-android，需要先卸载旧版一次。

## 这个仓库是什么

- 上游：[xfangfang/wiliwili](https://github.com/xfangfang/wiliwili)（GPL-3.0），基于
  **v1.6.0**。
- Android 移植基础：[Tenwang318/wiliwili-android](https://github.com/Tenwang318/wiliwili-android)。
- 本仓库继续维护 **Android 单屏版**：修复 Android 构建/运行问题、跟进 B 站接口变化、
  发布可直接安装的 APK。
- 双屏适配不再放在本仓库的主线中。旧的 wiliwili 双屏实验代码仍保留在仓库里，但默认
  `DUAL_SCREEN=false`，不参与 Release 构建；双屏产品已拆分为独立项目 **bili-duo**。

## 当前版本：1.6.0-android.1

相对上游 v1.6.0 和早期 Android 包，本版本包含：

- **动态页 UP 列表恢复**：B 站把 `uplist` 接口的 `mid` 从数字改成字符串，旧解析会导致
  整个左侧 UP 列表被清空；现已兼容字符串 `mid`。
- **播放器退出崩溃修复**：`VideoView` 重复订阅 MPV 事件会残留悬空回调，退出/切换视频
  时可能触发 use-after-free；已加订阅去重和生命周期令牌。
- **直播页播放器重置时机修复**：把 `MPVCore::reset()` 从视图创建前移到
  `onContentAvailable()`，避免黑屏/上次播放状态残留。
- **倍速显示修复**：倍速标签不再出现 `1.9999999x` 这类浮点噪声。
- Android 构建修复：NDK r26 / SDL2 `ALooper_pollAll`、16 KB 页对齐、libmpv AAR
  的 libc++ 兼容、curl `poll()` 而不是 `select()`（避免 fd 超过 1024 后网络请求失败）、
  非 ASCII 路径构建等。

完整的上游同步记录见 [docs/UPSTREAM.md](docs/UPSTREAM.md)。

## 功能

wiliwili 的核心功能在 Android 上可用：

- 首页推荐、热门、追番、影视、直播
- 搜索、视频详情、分 P、合集、相关推荐
- 评论、弹幕、字幕、画质切换、倍速、进度记忆
- 动态、关注、历史、收藏、追剧、稍后再看
- 手柄操作（Android 手柄/掌机）、键盘快捷键
- 自定义主题、自定义字体/图标（与上游一致的配置目录）

Android 版是手柄优先的 UI，触摸支持取决于设备和 SDL 输入；建议搭配手柄使用。

## Android 版改动 / 与上游的差异

- SDL2 + borealis + libmpv 的 Android 构建链；预编译 libmpv 来自
  [jarnedemeulemeester/libmpv-android](https://github.com/jarnedemeulemeester/libmpv-android)。
- 使用 NDK **26.1.10909125**（SDL 2.28 依赖 `ALooper_pollAll`），并强制 16 KB
  页对齐。
- 保留 libmpv AAR 自带的 `libc++_shared.so`，避免 NDK r26 缺少新符号导致
  `dlopen` 失败。
- curl 强制启用 `HAVE_POLL_FINE`，避免 Adreno 设备长时间播放后 fd 超过
  `FD_SETSIZE` 导致所有请求失败。
- 构建脚本 `android-project/build_local_win.sh` 会把源码镜像到 ASCII 路径再构建，
  因为 AGP/CMake 不能可靠处理含中文的路径。
- 诊断日志：`{configDir}/logs/wiliwili_*.log`，包含 fd/内存/卡顿 watchdog 信息，
  便于排查 Android 长时间播放问题。
- 旧的第二屏 `Presentation` 代码保留但默认关闭；Release 不包含双屏 UI。

## 构建

### 依赖

- JDK 17
- Android SDK Platform 34、Build-Tools 34.0.0
- Android NDK **26.1.10909125**
- CMake 3.22.1
- Linux/macOS：`ninja-build`、`pkg-config`、host C/C++ 编译器
- Windows：MinGW gcc/g++（用于 host 工具 `libromfs-generator`）

### 克隆

```bash
git clone --recursive https://github.com/0sour/wiliwili-android.git
cd wiliwili-android
```

如果已克隆但子模块为空：

```bash
git submodule update --init --recursive
```

应用 Android 子模块补丁（幂等，已应用会跳过）：

```bash
bash scripts/apply_android_patches.sh
```

### 准备 libmpv 与 mpv 头文件

```bash
bash .ci/prepare_mpv.sh
```

该脚本下载预编译 libmpv AAR 和 mpv 0.41.0 头文件；生成的
`android-project/app/libs/` 与 `mpv-deps/` 已被 `.gitignore` 忽略。

### Linux / macOS

```bash
# 先生成 host 工具
cd library/borealis
bash build_libromfs_generator.sh
cp libromfs-generator ../../libromfs-generator
cd ../..

bash .ci/prepare_mpv.sh

cd android-project
./gradlew assembleRelease --no-daemon
```

产物：

```text
android-project/app/build/outputs/apk/release/app-release.apk
```

### Windows

项目路径含中文时，直接用 Gradle 会失败。使用仓库自带的镜像构建脚本：

```bash
bash android-project/build_local_win.sh assembleRelease
```

默认镜像目录是 `D:\wiliwili-build`，可用 `WL_BUILD_DIR` 覆盖。脚本会：
1. 应用 Android 子模块补丁（`scripts/apply_android_patches.sh`，幂等）；
2. 把源码镜像到 ASCII 路径；
3. 准备 mbedtls、host `libromfs-generator`、MinGW 运行库；
4. 调用 Gradle 构建；
5. 把 APK 复制回 `android-project/app/build/outputs/apk/<variant>/`。

首次下载依赖需要网络；脚本默认使用 `WL_PROXY=http://127.0.0.1:7877`，可按需覆盖。
常用环境变量：`JAVA_HOME`、`ANDROID_SDK_ROOT`、`WL_BUILD_DIR`、`WL_MINGW_BIN`。

### 签名

Release 签名使用仓库内的 `android-project/app/wiliwili.jks`：

| 项 | 值 |
|---|---|
| storePassword | `wiliwili` |
| keyAlias | `wiliwili` |
| keyPassword | `wiliwili` |

这是**公开的项目密钥**，只用于本项目，目的是让所有 Release 和本地构建可以互相覆盖安装。
不要用它签其他应用，也不要把它当成安全密钥。只从本仓库的 Releases 下载 APK，并核对上面的
证书指纹。

## 发布 Release

1. 修改 `android-project/app/build.gradle` 的 `versionCode` / `versionName`（当前
   `10600` / `1.6.0`）。
2. 构建 `assembleRelease`，确认 APK 可安装。
3. 计算校验和：

```bash
sha256sum android-project/app/build/outputs/apk/release/app-release.apk
```

4. 创建 tag 和 Release（示例）：

```bash
cp android-project/app/build/outputs/apk/release/app-release.apk /tmp/wiliwili-android.apk
gh release create v1.6.0-android.2 \
  /tmp/wiliwili-android.apk#wiliwili-android-1.6.0.apk \
  --title "wiliwili-android 1.6.0 (Android)" \
  --notes-file RELEASE_NOTES.md
```

GitHub Actions 会在 push `main` 时构建 release APK 并上传为 artifact；Release 由维护者
按上面的步骤发布。tag 建议使用 `v<上游版本>-android.<序号>`，例如
`v1.6.0-android.1`、`v1.6.0-android.2`。

## 上游同步

- 当前基线：上游 **v1.6.0**（`88e5876`）+ Android 移植提交。
- 已同步的上游修复：动态 UP 列表解析、播放器 UAF、直播 MPV 重置、倍速显示。
- Windows D3D11、macOS/Wayland、PS5 等平台改动不适用于 Android，未同步。
- 详细对比方法和未同步列表见 [docs/UPSTREAM.md](docs/UPSTREAM.md)。

## 已知问题

- **Adreno 长时间播放**：部分 Retroid/Adreno 设备在长时间播放后可能因驱动
  `sync_file` fd 泄漏触发卡死/网络请求失败；日志在 `{configDir}/logs/`，仍在排查。
- **双屏代码**：仓库里的旧双屏 `Presentation` 代码是实验性的，默认关闭；双屏产品
  已拆分为 bili-duo，不在本仓库发布。
- **B 站接口变化**：B 站会调整 Web API；如果首页/动态/播放出现异常，请带日志提 Issue。
- **非官方客户端**：账号风控、接口不可用等风险由使用者自行承担。

## 项目结构

```text
wiliwili/              核心 UI / Activity / Presenter / View
resources/             XML 布局、i18n、图片、字体
android-project/       Android Gradle 工程与 JNI 包装
library/               第三方子模块（borealis、cpr、mongoose 等）
.ci/prepare_mpv.sh     下载 libmpv AAR 与 mpv 头文件
docs/                  上游说明、双屏历史文档、发布/同步记录
```

## 上游与致谢

- [xfangfang/wiliwili](https://github.com/xfangfang/wiliwili) — 原始项目，GPL-3.0
- [Tenwang318/wiliwili-android](https://github.com/Tenwang318/wiliwili-android) — Android
  移植基础
- [jarnedemeulemeester/libmpv-android](https://github.com/jarnedemeulemeester/libmpv-android)
  — 预编译 libmpv AAR
- [xfangfang/borealis](https://github.com/xfangfang/borealis) — UI 框架
- [libsdl-org/SDL](https://github.com/libsdl-org/SDL) — SDL2
- [mpv-player/mpv](https://github.com/mpv-player/mpv) — 播放器
- [SocialSisterYi/bilibili-API-collect](https://github.com/SocialSisterYi/bilibili-API-collect)
  — B 站 API 文档
- 以及 wiliwili 依赖的 cpr、mongoose、OpenCC、lunasvg、pystring 等子模块作者

上游 README 的完整功能/平台说明保留在 [docs/UPSTREAM_README.md](docs/UPSTREAM_README.md)。

## License

本项目按 **GPL-3.0** 发布，见 [LICENSE](LICENSE)。由于基于 wiliwili 的 GPL 代码，
分发修改版时必须提供对应源代码。Bilibili 名称、图标和服务的权利归其权利人所有。
