# wiliwili 双屏主页设计（Retroid Pocket Duo）

记录日期：2026-10-30  
设备：上屏 1920×1080，下屏 1280×960 / 320dpi（`FLAG_PRESENTATION`，displayId 2，touch EXTERNAL）  
现状（2026-10-30 更新）：播放器已经完整使用下屏（评论/推荐/分集/信息 + 图标控制条 + 更多菜单 + 手柄焦点导航 + 提示区），双屏播放时上屏不再绘制 OSD；主界面（MainActivity）已接入 **Phase 0 HOME 仪表盘**，下屏在非播放状态显示快捷入口而不是空白。搜索/设置等独立页面打开时下屏会暂时关闭，返回主界面后恢复。Phase 1 的“继续观看/推荐”数据尚未接入。

> 实现状态：Phase 0 已完成（`Mode::HOME`、`MainActivity::startHomeSession()`、`second_home` 布局、首页/动态/我的/搜索/设置/刷新/关闭下屏快捷入口）。播放器 → HOME 通过 session id 保护直接切换，不会先 OFF 再重建。Phase 1（历史/推荐数据、封面卡片）和 Phase 2（上屏焦点详情）仍是后续工作。手柄在主页仍是“上屏焦点驱动”，下屏为触摸操作；只有播放器阶段由 native 转发手柄按键给下屏。

---

## 1. 目标与原则

1. **上屏负责“看”**：主页、搜索、动态、我的等内容浏览保持在上屏，尽量少改动现有交互。
2. **下屏负责“上下文 + 快捷操作”**：不是简单镜像，而是显示当前选中内容的详情、继续观看、快捷入口，减少返回和跳转。
3. **播放时沿用现有控制台**：打开播放器后下屏切回评论/控制布局，关闭播放器后回到主页布局。
4. **触摸优先，手柄不抢焦点**：主页阶段手柄仍然控制上屏的焦点/点击；下屏只做“输出”，不接收手柄按键，避免和原版按键映射冲突（见 §7）。
5. **复用现有资产**：主题色、封面/头像/表情加载器、RecyclerGrid 数据源、播放器的分集/评论组件尽量复用。
6. **低风险渐进**：先做“伴随面板”，再做“焦点详情”，最后才考虑把导航搬到下屏。

---

## 2. 推荐体验（按阶段）

| 模式 | 上屏 | 下屏 |
|---|---|---|
| 默认（无播放） | 现有主页/搜索/动态/我的 | **Companion Dashboard**：继续观看 + 快捷入口 + 为你推荐 |
| 选中内容（Phase 2） | 现有网格，焦点/长按选中卡片 | **Detail Preview**：封面、标题、UP、数据、简介、分集/评论预览、播放/收藏按钮 |
| 播放视频 | 纯视频 | 现有 Player Console（评论/推荐/分集/信息 + 控制） |
| 播放直播 | 纯视频 | 现有直播弹幕/信息 + 控制 |
| Phase 3（可选） | 纯内容网格（隐藏左侧栏/主页 Tab） | 导航中心：首页/动态/我的 + 直播/推荐/热门/追番/影视 |

推荐先做 **Dashboard → Focus Detail → 可选导航中心**。  
理由：Dashboard 不改变上屏交互、没有输入冲突、立即可用；Focus Detail 是双屏的“招牌体验”；导航中心改动最大，等前两步稳定后再评估。

---

## 3. 状态机与生命周期

下屏模式（Java `SecondScreenManager`）：

```text
MODE_HOME_DASHBOARD   // 无选中内容
MODE_HOME_DETAIL      // 选中/长按/聚焦某个卡片（Phase 2）
MODE_VIDEO            // 播放器（现有）
MODE_LIVE             // 直播（现有）
```

生命周期建议：

- `WiliwiliActivity onResume`：若“双屏模式”开启且有 Presentation display → 显示 `MODE_HOME_*`。
- 打开播放器：native `openSession(VIDEO/LIVE)` 覆盖 HOME 会话，下屏切到播放器布局。
- 关闭播放器：native 重新进入 HOME（受 session id 保护，避免旧播放器关掉新会话），或由 `SecondScreenManager` 在收到 `MODE_OFF` 时回退到 HOME。
- `onPause` / `detach`：dismiss，释放 Surface；回到前台再重建。
- 设置开关：复用 `SettingItem::PLAYER_SECOND_SCREEN` 作为总开关，界面文案从“播放器双屏”改为“双屏模式”；默认 Android=1。后续可加“首页也使用下屏”子选项。

**关键点**：HOME 会话要主动调用一次 `pushTheme()`（以及新增的 keymap/swap 信息），否则首次启动（还没进过播放器）下屏拿不到颜色。

---

## 4. 下屏主页布局（640×480dp）

### 4.1 Dashboard（Phase 1）

```text
┌──────────────────────────────────────────────┐
│ 继续观看                            全部 ›   │
│ ┌────────────┐ ┌────────────┐ ┌────────────┐ │
│ │  cover 16:9│ │  cover     │ │  cover     │ │
│ │  标题      │ │  标题      │ │  标题      │ │
│ │  UP · 进度 │ │  UP · 进度 │ │  UP · 进度 │ │
│ └────────────┘ └────────────┘ └────────────┘ │
│                                              │
│ 快捷入口                                     │
│ [搜索] [历史] [收藏] [稍后再看]              │
│ [直播] [番剧] [动态] [设置]                  │
│                                              │
│ 为你推荐                                     │
│ [封面] 标题 …                    [播放]      │
│ [封面] 标题 …                    [播放]      │
├──────────────────────────────────────────────┤
│ 手柄  A 打开  B 返回  X 刷新  Y 搜索  LB/RB 切页 │
└──────────────────────────────────────────────┘
```

- “继续观看”：历史记录前 3～6 条，16:9 封面 + 标题 + UP + 进度条；点击直接 `Intent::openBV(bvid, 0, progress)` 续播。
- 快捷入口：2×4 大按钮，图标 + 文字；搜索/收藏/设置走现有 Intent；历史/直播/番剧/动态需要新增 Tab 切换事件（见 §5.4）。
- “为你推荐”：取主页推荐列表前 4～6 条，横排卡片；“播放”直接打开视频。
- 未登录时：隐藏“继续观看”，快捷入口只留搜索/设置/直播，推荐照常。
- 视觉：沿用 wiliwili 浅/深主题、B 站粉强调色、圆角封面、底部手柄提示条；列表项高度 ≥44dp。

### 4.2 Detail Preview（Phase 2）

```text
┌──────────────────────────────────────────────┐
│ [16:9 封面] 标题（最多 2 行）                 │
│              UP · 播放 123.4万 · 弹幕 4567    │
│ [播放] [收藏] [稍后再看] [UP 主页]           │
│ 简介 …（可展开）                              │
│ ──────────────────────────────────────────── │
│ [分集] [评论] [相关]                          │
│ 分集列表 / 评论前几条 / 相关视频              │
├──────────────────────────────────────────────┤
│ 手柄  A 播放  B 返回  X 刷新  Y 搜索          │
└──────────────────────────────────────────────┘
```

- 视频：复用播放器的 `分集` / `评论` / `相关` 数据推送与适配器；下屏只读预览，播放仍在上屏。
- 番剧：显示季封面、简介、剧集列表（episodeList），点集播放。
- 直播：封面/主播/在线人数/最近弹幕，按钮“进入直播间”。
- 动态：正文 + 图片缩略图 + 评论预览（Phase 3）。
- 触发方式：
  - **手柄**：上屏卡片获得焦点时推送详情（`View::getFocusEvent()` / `onFocusGained`），A 打开。
  - **触摸**：单击仍直接播放（不改变习惯）；**长按**卡片预览详情（需要给卡片加长按手势），或卡片角标“详情”。
  - B / 返回：回到 Dashboard，而不是退出应用。

---

## 5. 数据与 JNI 设计

### 5.1 新 Mode / Event

- `wiliwili::second_screen::Mode` 增加 `HOME = 3`（或 `HOME_DASHBOARD / HOME_DETAIL` 由 Java 内部区分，native 只发 `HOME`）。
- 新事件：
  - `EVENT_HOME_DASHBOARD = 11`：`{ history: [...], recommends: [...] }`
  - `EVENT_HOME_DETAIL = 12`：`{ type, id, title, cover, up, desc, stats..., episodes..., commentsPreview... }`
  - `EVENT_HOME_HINTS = 13`（可选）：home 专用的手柄提示；也可以沿用 theme/keymap 由 Java 按 mode 选择。
- `EVENT_THEME` 增加 `swapABXY` 字段（见 §7）。

### 5.2 数据结构（native 侧）

```cpp
struct HomeHistoryItem {
    std::string bvid, title, cover, up;
    int64_t progress = 0, duration = 0;
};
struct HomeRecommendItem {
    std::string bvid, title, cover, up;
    int64_t view = 0, duration = 0;
};
struct HomeDashboard {
    std::vector<HomeHistoryItem> history;
    std::vector<HomeRecommendItem> recommends;
};
```

Phase 2 的 `HomeDetail` 可复用 `VideoInfo / EpisodeItem / CommentItem / RelatedItem` 的字段，并加 `type`（video/season/live/dynamic）与 `id`。

### 5.3 数据来源与钩子

| 数据 | 现有代码 | 建议钩子 |
|---|---|---|
| 继续观看 | `MineHistoryRequest` / `HistoryVideoResult` | 在 `MainActivity` 里新建一个轻量 history presenter，启动/回前台时请求一次，推前 N 条 |
| 为你推荐 | `HomeRecommends::onRecommendVideoList` | 列表更新后 `APP_E` 发一个拷贝事件，`MainActivity` 订阅并推下屏（避免重复请求） |
| 番剧/影视 | `HomeBangumiRequest` / `HomeCinemaRequest`、`PGCItemResult` | Phase 2 详情按 `season_id` 拉 `seasonInfo/episodeList` |
| 直播 | `HomeLiveRequest` / `LiveVideoResult` | Phase 2 详情用 roomid 拉主播/在线/历史弹幕 |
| 历史详情 | `HistoryVideoResult`（bvid/progress/cover/title） | Dashboard 直接用 |
| 动态 | `DynamicTab` / `dynamic_*` presenter | Phase 3 |

注意：`HomeRecommends` 只在上屏首次进入推荐页时才请求；如果要让下屏在启动后立刻有推荐，需要 MainActivity 主动请求一次，或等上屏推荐页加载完成后再推送。推荐列表建议只取前 4～6 条，避免大 JSON。

### 5.4 命令（Java → native）

在现有 `Command` 基础上新增：

| 命令 | 参数 | 动作 |
|---|---|---|
| `OPEN_BV_PROGRESS` | text=bvid, arg=progress 秒 | `Intent::openBV(bvid, 0, progress)`（继续观看） |
| `OPEN_ROOM` | arg=roomid | `Intent::openLive(roomid)` |
| `OPEN_SEARCH` | text=keyword（可空） | `Intent::openSearch(keyword)` |
| `OPEN_SETTINGS` | – | `Intent::openSetting()` |
| `OPEN_FAVORITES` | – | `Intent::openCollection(userMid)`（未登录提示） |
| `REFRESH_HOME` | – | 触发上屏主页刷新 / 重新拉历史 |
| `MAIN_TAB` | arg=0/1/2（首页/动态/我的） | 通过新增 `APP_E` 事件让 `MainActivity` 切 `main/tabFrame` |
| `HOME_SUB_TAB` | arg=0..4（直播/推荐/热门/追番/影视） | 通过 `APP_E` 让 `HomeTab` 切子 Tab |
| `OPEN_HISTORY` | – | 新增 `Intent::openHistory()`：打开主界面→我的→历史（或直接进入 MineHistory） |

现有 `OPEN_BV`、`OPEN_SEARCH` 等能力可以复用；`Intent::openBV` 已支持 progress 参数（`activity_helper.hpp:12`）。

### 5.5 Presentation 内部

- 现有 `presentation_second_screen.xml` 根布局已经是一个 `FrameLayout`（承载弹窗遮罩），可以在里面再加一个 `home_container`，与 `second_main`（播放器容器）同级。
- `SecondScreenPresentation.setMode(MODE_HOME_*)`：隐藏播放器容器与底部控制条，显示 home 容器；保留最底部的手柄提示区（提示内容按模式切换）。
- 现有玩家数据/适配器不销毁，切回播放器时直接复用，避免闪烁。
- Home 列表建议用 `ListView` + 复用 `ImageLoader`/`CoverLoader`（已有 8MB LRU + 并发合并），不要新开一套网络栈。

---

## 6. 视觉与交互细节

- 下屏背景/面板/文字/强调色继续用 `SecondScreenTheme`（native 推送，浅/深色）。
- 封面 16:9、圆角 6dp；继续观看卡片底部叠加进度条。
- 快捷入口用现有图标风格（圆形/胶囊 + 图标），点击有 pressed 反馈。
- 详情页的“播放”是粉色主按钮；收藏/稍后再看为浅色胶囊。
- 底部手柄提示区保留，Home 模式显示：`A 打开 / B 返回 / X 刷新 / Y 搜索 / LB/RB 切页`；播放器模式显示现有播放映射。
- 列表空态：未登录/无历史显示“登录后同步观看记录”，推荐为空显示“稍后再试”。
- 性能：下屏只渲染可见项；封面 URL 统一加 `@320w_180h`；避免在滚动时频繁推 JNI。

---

## 7. 手柄输入（结合当前 bug）

已记录的 bug：**下屏手柄适配不起作用**，用户怀疑与原版按钮设置冲突。代码核对结果：

1. Java 下屏代码没有任何 `dispatchKeyEvent/onKeyDown/KeyEvent` 处理，下屏是纯触摸。
2. Android 按键只会送到默认显示器的焦点窗口；display 2 的 Presentation 收不到。
3. 底部提示区是静态展示，只读取 `KEYMAP`（xbox/ps/keyboard），**没有读取 `APP_SWAP_ABXY`**。
4. 原生播放器绑定仍然生效：B 返回、LB/RB 快退快进、X 弹幕、Y OSD、START 清晰度、BACK 详情、RT 音量；`APP_SWAP_ABXY` 会交换 A/B、X/Y。

**设计结论（推荐）**：

- **主页阶段：手柄继续控制上屏，下屏只做输出。**
  - 上屏焦点移动 → 下屏显示对应卡片详情（Phase 2）。
  - A 播放/打开，B 返回；下屏不抢焦点，不需要按键转发，因此不会和原版绑定冲突。
- **播放阶段：手柄继续控制播放器（现状），下屏触摸操作。**
  - 底部提示条展示真实绑定，并读取 `APP_SWAP_ABXY` 修正 A/B、X/Y 图标与文案。
- **如果以后要用手柄操作下屏控件**（明确需求再做）：
  - 增加“下屏控制”模态：native 把 D-pad/A/B 转发给 Java（新增 `pushKey` 事件），同时**屏蔽**对应播放器动作，避免双触发；B 或专用键退出模态。
  - 提示条随模态切换，并在设置里说明。
  - 这一步需要显式的输入所有权（PLAYER / LOWER_UI），不能与现有 `registerAction` 并存。

---

## 8. 分阶段计划

### Phase 0：HOME 壳（0.5～1 天）
- `Mode::HOME`、Java `MODE_HOME_*`、Presentation 增加 home 容器。
- 生命周期：resume 显示 HOME，播放器覆盖，关闭播放器回 HOME；`pushTheme`/keymap 在 HOME 启动时推送。
- 占位 Dashboard（标题 + 快捷入口），验证上下屏切换无闪烁。

### Phase 1：Dashboard（1～2 天）
- 历史 presenter + 推荐列表事件，推 `EVENT_HOME_DASHBOARD`。
- 继续观看卡片（封面/标题/进度）→ `OPEN_BV_PROGRESS`。
- 快捷入口：搜索/收藏/设置/刷新；历史/直播/番剧/动态的 Tab 切换事件。
- Home 模式手柄提示；验证未登录空态。

### Phase 2：Focus Detail（2～3 天）
- 上屏卡片 `focusEvent` → `pushHomeDetail`（节流，只在索引变化时推）。
- 触摸长按预览（或卡片“详情”按钮）。
- 视频/番剧/直播详情布局；播放/收藏/稍后再看；分集/评论/相关预览。
- B 返回 Dashboard。

### Phase 3：导航中心（可选，2～3 天）
- 下屏主导航：首页/动态/我的 + 直播/推荐/热门/追番/影视。
- 上屏隐藏左侧栏/主页 Tab，变成纯内容网格（`main.xml` / `home_tab` 改造）。
- 手柄在主页上的焦点/翻页与下屏导航联动。

### Phase 4：下屏手柄模态（可选）
- 按键转发 + 动作屏蔽 + 提示切换；需要真机长时间验证。

---

## 9. 验收标准

- 应用回前台后下屏 HOME 在 300ms 内出现；打开/关闭播放器无黑屏闪烁。
- Dashboard 不额外重复请求推荐；历史只请求一次；封面缓存命中。
- 触摸：继续观看可续播；快捷入口能打开对应页面；未登录有提示。
- 手柄（主页）：焦点移动时下屏详情跟随；A 播放；B 返回；提示与实际按键一致（含 A/B 交换）。
- 播放器行为与现在完全一致；下屏触摸控制不受影响。
- RP Duo 内置手柄 + 外接手柄各验证一次。

---

## 10. 待确认（不阻塞 Phase 0/1）

1. 主页下屏默认是 Dashboard 还是直接显示“选中卡片详情”？（推荐 Dashboard）
2. 触摸单击是继续“直接播放”，还是改成“先预览、再播放”？（推荐保持直接播放 + 长按预览）
3. 是否要把主导航搬到下屏（Phase 3），让上屏变成纯网格？
4. 是否需要“手柄操作下屏控件”的模态（Phase 4）？

---

## 11. 涉及文件（预估）

- native：
  - `wiliwili/include/utils/second_screen.hpp` / `second_screen.cpp`：Mode HOME、HOME 事件、命令。
  - `wiliwili/source/activity/main_activity.cpp` / `home_tab` / `home_recommends`：HOME 数据推送、Tab 切换事件。
  - `wiliwili/include/presenter/`：新增 HomeCompanion/History 轻量 presenter（或复用 `MineHistoryRequest`）。
  - `wiliwili/include/utils/activity_helper.hpp`：`openHistory()` 等导航入口。
- Java：
  - `SecondScreenManager`：MODE_HOME、dashboard/detail 模型、HOME 回退、EVENT_HOME_*。
  - `SecondScreenPresentation`：home 容器、Dashboard/Detail 视图、模式切换、home 提示。
  - 新适配器：ContinueWatchingAdapter、HomeRecommendAdapter（可复用 CoverLoader）。
  - `res/layout/presentation_home.xml` 或合并进现有布局；`res/drawable` 图标。
- 资源/i18n：快捷入口、空态、模式提示；`setting.app.playback.second_screen` 文案改为“双屏模式”。
