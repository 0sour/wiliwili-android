# wiliwili-android 1.6.0 (Android)

首个 Android 修复版 Release，基于 wiliwili v1.6.0。

## 修复

- **动态页 UP 列表恢复**：B 站 `web-dynamic/v1/uplist` 的 `mid` 改为字符串，旧解析会
  抛异常并清空整个左侧列表；现已兼容字符串 `mid`，选中 UP 时安全转回数字。
- **播放器退出/切换崩溃修复**：`VideoView` 重复订阅 MPV 事件会残留悬空回调，可能
  触发 use-after-free；已加重复订阅保护和生命周期令牌。
- **直播页播放器重置时机修复**：`MPVCore::reset()` 移到 `VideoView` 绑定之后，避免
  黑屏或残留上一次播放状态。
- **倍速显示修复**：倍速标签用 6 位有效数字格式化，不再出现 `1.9999999x`。

## Android 构建

- SDL2 / borealis / libmpv Android 构建。
- NDK 26.1.10909125，16 KB 页对齐。
- libmpv AAR 自带 `libc++_shared.so` 优先，避免 r26 缺少符号。
- curl 使用 `poll()`，避免长时间播放后 fd 超过 `FD_SETSIZE`。

## 安装

- Android 8.0+（minSdk 26）
- ABI：arm64-v8a、armeabi-v7a、x86_64
- 可直接覆盖安装 2026-11-08 之前的本地 debug 包（同一签名密钥）。
- 如果之前装过其他签名的 wiliwili-android，请先卸载旧版。

```bash
adb install -r wiliwili-android-1.6.0.apk
```

## 校验

```text
SHA-256: cb4c7b28eb023ef2309db422b112d2d00c95b8c51db25d2e901e1d6c92566a41
```

## 已知问题

- 部分 Adreno 设备长时间播放可能因驱动 fd 泄漏卡死，仍在排查。
- 旧双屏实验代码默认关闭，不在本 Release 中启用。
- 非官方客户端，接口变化/账号风控风险自负。

## 上游与许可证

- 上游：[xfangfang/wiliwili](https://github.com/xfangfang/wiliwili)（GPL-3.0）
- Android 移植基础：[Tenwang318/wiliwili-android](https://github.com/Tenwang318/wiliwili-android)
- 预编译 libmpv：[jarnedemeulemeester/libmpv-android](https://github.com/jarnedemeulemeester/libmpv-android)
- 本项目按 GPL-3.0 发布，源代码见本仓库。
