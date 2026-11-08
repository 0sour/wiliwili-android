# 上游同步记录

本仓库基于 [xfangfang/wiliwili](https://github.com/xfangfang/wiliwili) 的 **v1.6.0**
（提交 `88e5876`），并叠加 [Tenwang318/wiliwili-android](https://github.com/Tenwang318/wiliwili-android)
的 Android 移植。上游的默认分支是 `yoga`，开发分支是 `dev`；Android 维护只同步与
Android/通用逻辑相关的修复，不盲目合并平台相关改动。

## 当前已同步

| 上游 | 内容 | 本仓库改动 |
|---|---|---|
| [#710](https://github.com/xfangfang/wiliwili/pull/710) | 动态 `uplist` 的 `mid` 改为字符串，旧解析会清空整个 UP 列表 | `DynamicUpResult::mid` 改为 `std::string`；选择 UP 时安全转 `uint64_t` |
| [#692](https://github.com/xfangfang/wiliwili/pull/692) | `VideoView::registerMpvEvent()` 重复订阅导致残留悬空回调/UAF | 重复注册直接 `return`；增加 `aliveToken` 生命周期令牌 |
| [#667](https://github.com/xfangfang/wiliwili/pull/667) | 直播页在 `VideoView` 创建前 reset MPV，可能黑屏/残留状态 | reset 移到 `onContentAvailable()`；`video` 改为 `BRLS_BIND` |
| [#711](https://github.com/xfangfang/wiliwili/pull/711) | 倍速标签出现浮点噪声 | `fmt::format("{:.6g}x", ...)` |

## 已评估但未同步

| 上游 | 原因 |
|---|---|
| [#697](https://github.com/xfangfang/wiliwili/pull/697) D3D11 渲染迁移 | Windows/UWP 专用，Android 不走 D3D11 |
| [#698](https://github.com/xfangfang/wiliwili/pull/698) macOS CI | CI/平台专用 |
| [#647](https://github.com/xfangfang/wiliwili/pull/647) 鼠标光标与 OSD | 桌面鼠标逻辑，Android 无鼠标 |
| [#614](https://github.com/xfangfang/wiliwili/pull/614) PlayUrl `-412` 回退 | PR 已关闭且未合并，内容混杂；出现实际 `-412` 问题时再单独评估 |
| #630 视频缓存、#634 UP 主页、#699 一键三连 | 功能 PR，不是修复 |
| #714 PS5、#680 macOS、#669 Wayland | 平台专用 |

## 如何对比上游

```bash
git remote add upstream https://github.com/xfangfang/wiliwili.git
git fetch upstream
git log --oneline HEAD..upstream/dev
git diff HEAD..upstream/dev -- wiliwili/include wiliwili/source
```

注意：

- 上游当前默认分支是 `yoga`；`dev` 可能领先 `yoga`。
- 只 cherry-pick 与 Android 相关的提交，避免把 D3D11、Wayland、PS5、macOS 的改动
  带进来。
- 修改 `wiliwili/source/view/video_view.cpp`、`live_player_activity.cpp`、
  `player_base_activity.cpp` 前，先检查本仓库的 Android/双屏历史改动，避免覆盖。

## Android 专属改动（不在上游）

这些提交只存在于本仓库/Android 移植，不是上游补丁：

- SDL2 Android 构建、`ALooper_pollAll` 兼容（NDK r26）。
- 16 KB 页对齐、libmpv AAR 的 `libc++_shared.so` 优先。
- curl 强制 `HAVE_POLL_FINE`，避免 fd 超过 `FD_SETSIZE`。
- `android-project/build_local_win.sh`：ASCII 路径镜像构建。
- `diag_logger`：`{configDir}/logs/wiliwili_*.log` 健康/卡顿日志。
- 旧双屏 `Presentation`/JNI 桥实验代码（默认 `DUAL_SCREEN=false`，已废弃）。

## 发布版本对应

- 上游 v1.6.0 = 本仓库 `versionName 1.6.0`、`versionCode 10600`。
- Android 修复版 tag 建议：`v1.6.0-android.1`、`v1.6.0-android.2`……
- 每次发 Release 前更新 `android-project/app/build.gradle` 的 `versionCode`/`versionName`，
  并同步更新本文件/Release Notes 中的修复列表。

## 许可证

上游 wiliwili 与本仓库均为 GPL-3.0。同步/移植上游代码时保留版权与许可证；不要把
GPL 代码复制进不兼容许可证的项目。
