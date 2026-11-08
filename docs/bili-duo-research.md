# bili-duo 调研与双屏架构决策

日期：2026-11-03

## 结论

**双屏项目从 wiliwili 的“上屏原生 UI + 下屏 Java Presentation”方案中拆出，改名为
`bili-duo`，以 Kotlin + Jetpack Compose 重做 UI 层；`wiliwili-android` 保留为单屏
Android 修复版，不再作为双屏产品的主线。**

这不是“把现有双屏代码换个名字”，而是承认当前双屏交互的结构性问题，换一个
能同时满足以下三点的架构：

1. 下屏必须是完整的主 UI，而不是上屏 UI 的遥控器/复刻版。
2. 上屏只显示视频，没有 OSD、没有可聚焦控件。
3. 手柄和触摸都只面对一套 UI、一套焦点状态。

在 Retroid Pocket Duo 上，前两点决定了 app 的 UI 必须运行在**下屏的 Presentation**
里。wiliwili 的原生 UI 跑在上屏的 SDL window 里，无法搬进 Presentation；继续在
它旁边补一套 Java UI，就永远会有两套 UI、两套焦点、两份状态。这是当前“操作逻辑
有很大问题”的根因，不是某个按钮映射没调好。

## 调研范围

### 第三方 B 站客户端

| 项目 | 技术栈 / 许可证 | 状态 | 可借鉴之处 |
|---|---|---|---|
| [BBLL](https://github.com/xiaye13579/BBLL) | 仓库只有发布物，无源码 | 活跃，17k stars | TV/遥控器优先的交互、成熟的 B 站功能取舍；不能作为代码基座 |
| [PiliPala](https://github.com/guozhigq/pilipala) | Flutter / GPL-3.0 | 已归档 | 移动端功能和 API 设计完整；Flutter 不适合直接宿主 Android Presentation |
| [bilimiao2](https://github.com/10miaomiao/bilimiao2) | Kotlin Multiplatform + Compose / GPL-3.0 | 活跃，3k stars | API、登录、播放、弹幕、直播、番剧、动态都很完整；可作为功能参考/移植来源 |
| [bilibili-pure-apk](https://github.com/yearzen1/bilibili-pure-apk) | Kotlin + Compose + Media3 / MIT | 活跃 | 登录、搜索、详情、DASH 播放、评论、历史、收藏、字幕、下载、WBI 签名；代码小、结构清楚，适合作为新项目起点 |
| [BiliCompose](https://github.com/suzhelan/BiliCompose) | Compose Multiplatform / Apache-2.0 | 早期 | Compose 跨端结构参考，功能尚少 |
| [wiliwili](https://github.com/xfangfang/wiliwili) | C++17 + borealis / GPL-3.0 | 活跃，5.6k stars | 播放器、直播、番剧、弹幕、手柄交互成熟；UI 是主机/PC 形态，不能直接作为下屏 Presentation UI |

### 双屏/双显示项目

| 项目 | 做法 | 对我们的意义 |
|---|---|---|
| [xVideoPlayer](https://github.com/MaiconErick/xVideoPlayer) | 上屏视频 + 下屏 `Presentation` 里的 Compose 播放控制；手柄留在上屏 `Activity.dispatchKeyEvent`，下屏以触摸为主 | 最接近“上屏视频、下屏 UI”的成熟形态；证明下屏 Compose Presentation 可行 |
| [EmuLnk](https://github.com/EmuLnk/emulnk) | 上屏游戏，下屏 WebView 仪表盘；数据通过 UDP 推送，下屏触摸交互 | 下屏是 companion/dashboard，不抢游戏手柄；单 UI 栈 |
| [Wayfinder](https://github.com/Thor-Wayfinder/thor-wayfinder) | 系统级双屏工具；显式“手柄焦点切到上/下屏”，用 root/app_process 调 `focusTopTask(displayId)` | 说明 Android 的焦点是按 display 的，未 root 时普通 app 无法可靠地把系统手柄焦点切到下屏；也说明“显式模式切换”比“全量转发”更符合用户预期 |
| [Jarngreipr](https://github.com/BrianJr03/Jarngreipr) | 双屏 launcher | 双屏首页/启动器交互参考 |
| [Heimdall](https://github.com/mastercook777/Heimdall-AYN-Thor-Assistant) | 下屏辅助工具 | 下屏常驻信息/工具的产品形态参考 |
| [Dual-Screen-Games](https://github.com/codm2000/Dual-Screen-Games) | 双屏游戏/应用目录 | 生态现状：没有 B 站客户端，bili-duo 仍是空白 |

### 关键事实

1. **Duo 的下屏不能放普通 Activity。**
   `dumpsys display` 显示下屏 `canHostTasks=false`，并且带 `FLAG_PRESENTATION`。
   `ActivityOptions.setLaunchDisplayId` 对下屏无效，必须使用 `Presentation`
   （或把 Surface 交给下屏）。这意味着“把整个 app 的 Activity 挪到下屏”不可行，
   但“把整个 Compose UI 放进下屏 Presentation”可行。
2. **手柄按键默认跟着上屏焦点。**
   未 root 的 Duo 上，app 只能在上屏 Activity 里收到手柄事件，再自己转发给下屏。
   Wayfinder 能把焦点真正切到下屏，是因为它用了 root/Shizuku 级别的
   `focusTopTask`。所以任何方案都绕不开“上屏接收、下屏消费”的转发，但可以做到
   **只有一个 UI 消费这些事件**。
3. **成熟双屏应用不把下屏做成第二套全手柄 UI。**
   xVideoPlayer 和 EmuLnk 的下屏都是触摸优先；手柄留给上屏内容。Wayfinder 即使
   支持手柄切屏，也把它做成显式动作和状态，而不是默认把所有按键都灌到下屏。
   我们当前“进入视频后所有按键全部转发、上屏 OSD 全部隐藏”的做法是异类。
4. **当前 wiliwili 双屏实现是 bolt-on。**
   上屏是完整的 borealis app，下屏是 Java/XML 复刻的控制台；两边都维护播放状态，
   焦点在 SDL 与 Presentation 之间来回抢。HOME 页、直播、视频、overlay 又各有一套
   焦点列表。每增加一个功能都要做两遍，操作逻辑会越来越难预测。

## 决策

### 选择：重新开始 bili-duo 的 UI，而不是继续优化 wiliwili 双屏

理由：

- 下屏必须是主 UI。wiliwili 的原生 UI 只能在上屏；要让它成为下屏主 UI，等于把
  borealis 渲染/输入整体搬进 Presentation，成本和重写相当，且会破坏上游可维护性。
- 重写 UI 层可以只保留一套状态、一套焦点、一套导航；这是当前问题的直接解。
- 继续在现有双屏代码上修补，会保留两套 UI 的根因；即使这一轮调顺，下一次加功能
  仍会回到同样的问题。

### 不选择：直接 fork wiliwili 做双屏

wiliwili 的 C++/borealis UI 与 SDL window 绑定，不能直接作为下屏 Presentation 的
内容。可行的做法只剩“headless 播放器/API 服务 + 新的下屏 UI”，这实际上已经是
重写 UI，还要额外维护 JNI 数据桥；不如直接用 Kotlin/Compose 的 B 站客户端基座。

### 基座：bilibili-pure-apk（MIT）

选择它而不是 bilimiao2 的原因：

- MIT，代码量小（67 个 Kotlin 文件），结构清楚，适合改成双屏专用 UI。
- 已有登录、搜索、视频详情、DASH 播放（视频/音频合流）、评论、历史、收藏、字幕、
  下载、WBI 签名，正好覆盖 bili-duo 的第一阶段。
- 使用 Media3/ExoPlayer，视频 Surface 与 Compose UI 解耦相对容易：上屏放
  ` PlayerView`，下屏 Presentation 放 Compose。
- 缺少首页推荐、弹幕、直播、番剧；这些按阶段补。弹幕/直播/番剧的 API 与实现可参考
  bilimiao2 和 wiliwili（注意 GPL 代码的许可证边界）。

## bili-duo 目标架构

```
┌─────────────────────────────────────────────┐
│ 上屏 Activity（1920×1080）                   │
│  · 只放视频 Surface（Media3 PlayerView）     │
│  · 无 OSD、无按钮、无焦点控件                │
│  · 捕获手柄事件 → InputRouter                │
└─────────────────────────────────────────────┘
                    │ 共享 ViewModel / PlayerController
┌─────────────────────────────────────────────┐
│ 下屏 Presentation（1280×960）                │
│  · 整个 app UI（Compose）                    │
│  · 首页 / 搜索 / 视频信息 / 评论 / 相关推荐   │
│  · 播放控制 / 弹幕 / 清晰度 / 设置 / 我的     │
│  · 唯一焦点系统；触摸和手柄共用               │
└─────────────────────────────────────────────┘
```

### 输入模型（核心）

只允许一个输入所有者：

- `TOP_PLAYER`（默认，播放中）：手柄控制播放。A 播放/暂停，B 返回，X 弹幕，
  左右 seek，上下音量，LB/RB 上一个/下一个，LT/RT 倍速，START 清晰度，
  BACK 打开下屏信息/评论。
- `BOTTOM_UI`：手柄全部交给下屏 UI。方向键移动焦点，A 确认，B 返回/关闭，
  X/Y 上下文操作，LB/RB 切页，LT/RT 切 tab/翻页，START 菜单，BACK 信息。
- 切换：按 Y（或触摸下屏任意位置）进入 `BOTTOM_UI`；在下屏按 B（无 overlay 时）
  回到 `TOP_PLAYER`。上屏不显示任何焦点提示；下屏底部常驻显示当前模式与按键提示。
- 所有 overlay（清晰度、倍速、音量、更多、退出确认）都是下屏的模态层，打开时
  独占输入，B 只关闭 overlay，不退出播放。
- 首页/搜索页没有视频时默认 `BOTTOM_UI`，手柄直接导航下屏。

这样解决的正是当前三个问题：上屏 OSD 不再参与、下屏成为唯一可见 UI、手柄不会
同时驱动两套焦点。

### 状态与生命周期

- 一个 `PlayerController`（或 Activity-scoped ViewModel）持有 ExoPlayer 和播放状态；
  上屏 Activity 只负责 Surface 与生命周期，下屏 Presentation 只读状态、发命令。
- Presentation 没有自带 lifecycle，需要像 xVideoPlayer 那样提供
  `LifecycleOwner/ViewModelStoreOwner/SavedStateRegistryOwner`。
- Activity `onPause` 时保留播放器或按策略暂停；`onStop` 时 dismiss Presentation；
  `onResume` 重建并重新绑定。注意 Presentation 的创建/销毁与会话 ID，避免旧回调
  覆盖新实例（现有 wiliwili 桥已经踩过这个坑）。

## 迁移阶段

- **Phase 0**：以 pure-apk 为基座，改包名/应用名/版本；建立 `bili-duo` 仓库；
  验证下屏 Presentation 能承载 Compose，上屏可以是黑屏/占位。
- **Phase 1**：把 pure-apk 的导航和页面整体搬进下屏 Presentation；上屏只留视频宿主。
- **Phase 2**：拆播放器：上屏 PlayerView 只渲染；下屏接收播放/进度/清晰度命令；
  去掉上屏所有控制。
- **Phase 3**：实现 `TOP_PLAYER ↔ BOTTOM_UI` 输入状态机、触摸/手柄共用焦点、
  模态 overlay、模式提示。
- **Phase 4**：补首页推荐、弹幕；再按需补直播、番剧、动态。
- **Phase 5**：在 Duo 上做真实流程测试（进入/退出、后台恢复、直播、评论分页、
  overlay、手柄/触摸混用），再打包。

## 风险

- Media3 播放器与两个 window 的 Surface 绑定/解绑；Presentation 重建后的恢复。
- 手柄事件从 Activity 转发到 Presentation 的焦点系统，需要一套明确的 key map 和
  防重复（raw/nav 双事件）逻辑；但比现在两套 UI 互相抢要简单。
- B 站接口/WBI/登录风控变化；pure-apk 已有 WBI 与登录，但仍需维护。
- 功能缺口：首页推荐、弹幕、直播、番剧需要补；不要在第一阶段同时追求功能对等。
- 许可证：pure-apk 是 MIT，保留 LICENSE/NOTICE；若移植 bilimiao2（GPL-3.0）代码，
  bili-duo 需按 GPL 分发。

## 参考

- xVideoPlayer 双屏实现：<https://github.com/MaiconErick/xVideoPlayer/blob/main/app/src/main/java/com/app/xvideoplayer/ui/player/DualScreen.kt>
- xVideoPlayer 输入/双屏挂载：<https://github.com/MaiconErick/xVideoPlayer/blob/main/app/src/main/java/com/app/xvideoplayer/ui/player/PlayerScreen.kt>
- EmuLnk：<https://github.com/EmuLnk/emulnk>
- Wayfinder 焦点切换：<https://github.com/Thor-Wayfinder/thor-wayfinder/blob/main/app/src/main/java/app/wayfinder/DisplayFocus.kt>
- bilibili-pure-apk：<https://github.com/yearzen1/bilibili-pure-apk>
- bilimiao2：<https://github.com/10miaomiao/bilimiao2>
- wiliwili：<https://github.com/xfangfang/wiliwili>
- B 站 API 文档：<https://github.com/SocialSisterYi/bilibili-API-collect>

## 当前状态（2026-11-03）

`bili-duo` 已从 `bilibili-pure-apk` 建立独立仓库：

- 路径：`D:\项目\bili-duo`
- applicationId / namespace：`cn.xfangfang.biliduo`
- 应用名：`bili-duo`
- 版本：`0.1.0`（versionCode 1）
- 基座构建已验证通过（`./gradlew assembleDebug`）；产物：
  `app/build/outputs/apk/debug/app-debug.apk`
- 已移除基座的上游自动更新入口（原来指向 BiliPure 的 release），避免 bili-duo
  被提示安装成另一个应用。
- 方向锁定改为 landscape；后续的完整 UI 会放在下屏 Presentation，上屏只保留视频。

尚未实现：下屏 Presentation、上屏视频宿主、输入状态机、首页/弹幕。下一步按上面的
Phase 0 → Phase 3 顺序推进。旧 wiliwili 双屏代码与 APK 保留在
`wiliwili-android` 的 `dist/` 和 `dist/legacy/`，仅作参考。

