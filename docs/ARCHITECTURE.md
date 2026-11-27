# 架构说明 / Architecture

## 双端入口

| 入口 | 类 | 职责 |
| --- | --- | --- |
| 客户端（`client` entrypoint） | `CttHealthDisplay` | 按键注册、HUD 元素挂载、客户端缓存接收、聊天事件、tick 驱动 |
| 服务端（`main` entrypoint） | `CttStatsServer` | 服务端配置加载、payload 注册、统计探针与广播调度 |
| ModMenu | `ModMenuIntegration` | 仅编译期接入配置界面入口，不依赖 ModMenu 运行 |

## 包划分

```
com.ctt.healthdisplay
├── client/        客户端缓存与观测：血量 / 属性 / 怪物 / 队友心、动作栏捕获、
│                  癫狂与诅咒识别、聊天噪声过滤、Lore 词条弹窗与装饰、统计行标识
│   └── probe/      归属钩子、过滤钩子、粒子观测
├── config/        ModConfig（客户端 JSON）、ServerConfig（服务端 JSON），
│                  以及 F8 设置界面：ConfigScreen、TitleSettingsScreen、
│                  ShieldLayoutScreen、ArrowLayoutScreen、StatFilterScreen、
│                  EnemyHeadOptionsScreen、PlayerHeadOptionsScreen、
│                  ServerConfigScreen 与 PreviewDrag 预览拖动
├── health/        数据模型：HealthData、MobHealthData、StatsData、StatLabels
├── hud/           渲染器与屏幕：血条、属性、怪物条、队友世界渲染、
│                  伤害面板、统计表、弓箭预览、盾牌冷却、关卡名注册表，
│                  以及 CrackedHeartIcon、HeadHealthStyle、HudNumbers
├── mixin/         客户端 Mixin（BossBar、计分板、动作栏、标题字幕与 TitleTextMixin、Lore 界面与键盘等）
├── network/       S2C payload 定义与 codec（含 NpcHealthVisibilityPayload）
└── server/        服务端统计、伤害归属、击杀 / 承伤记录、广播、命令、
    ├── command/   广播开关命令
    ├── filter/    伤害过滤流水线、武器 P95 注册表、滚动窗口
    └── mixin/     服务端计分板 Mixin、文字显示实体背景色访问器
```

## 数据通路

### 1. 属性面板
- **服务端推送优先**：`PlayerStatsPayload`（1 Hz）由服务端拼好属性行，客户端直接应用到 `StatsData`，连接期间不再发 `trigger ViewStats`。
- **本地兜底**：无服务端推送时，客户端按血量下降 / 一次性大回血（≥25）或定时间隔自动 `trigger ViewStats`，从聊天与动作栏解析属性。

### 2. 怪物头顶血条
- **服务端权威**：`MobHealthPayload` 每 5 tick 推送视野内最多 32 条活怪血量，客户端 `ClientMobHealthCache` 维护 5 秒 fresh 窗口。
- **本地兜底**：fresh 失效时解析原版 bossbar，按 `Targetting` → `Boss` → `E` 标签与距离认领实体；实体消失超过 5 秒才清理记录，避免区块抖动丢数据。

### 3. 队友分层心
- `TeamHeartsPayload`（5 Hz）推送全队 Red / Cracked / Soul / Black / Blue 分层心、最大生命与存活数；服务端缺失时回退地图计分板数据。
- 渲染统一走 `HeadHealthStyle`（头顶样式）与 `CrackedHeartIcon`（碎心图标），快捷栏上方、队友列表、队友头顶共用同一套配色与缩放规则。

### 3.1 数字简写
`HudNumbers` 在 `compactHudNumbers` 打开时统一格式化所有大数字（`1.5K` / `2.4M` / `B` / `T`），快捷栏状态、队友列表、属性面板、头顶血量与伤害统计共用同一入口。

### 3.2 NPC 血量可见性
服务端通过 `NpcHealthVisibilityPayload` 下发 NPC 标记，客户端据此隐藏 NPC 头顶血量（`suppressNpcHealth`）。

### 4. 伤害归属链路（服务端）
```
L1 武器 + 火焰
L2 计分板 tick
L3 / L4 3m / 40m 标记
L5 计分板窗口
L6 火焰窗口
L7 最后命中者携带
L8 召唤物兜底
L9 未归属
```

### 5. 关卡分桶
`StageBoundaryDispatcher` 依据计分板变化划分关卡边界；`StageKey`（种类 + 关卡号）作为伤害统计、战绩切片的归集键，`StageNameRegistry` 读取 `stage_name_map.json` 输出本地化关卡名。

### 6. Lore 物品描述弹窗
- `LorePopupManager` 管理悬浮预览、固定窗口、嵌套开窗与拖动状态机，最多同时 6 个窗口，新窗口追加到最上层。
- 背包界面按 `Alt`（`LoreKeyboardMixin` 注入 `KeyboardHandler.keyPress`）调用 `toggleTooltipPin`：固定当前槽位物品描述，再按一次解除；固定状态记住槽位与物品堆叠。
- 窗口位置由 `loreTooltipAtCursor` 控制：背包左侧或跟随鼠标；词条点击可嵌套开窗，点空白 / ESC 关闭。
- 服务端 `TextDisplayBackgroundAccessor` 通过 `@Invoker` 读取文字显示实体背景色，`DamageProbe` 据此识别治疗粒子，辅助伤害 / 治疗归类。

### 7. 设置界面（F8）
`ConfigScreen` 作为主入口，下分标题、盾牌布局、箭矢布局、属性筛选、敌人头顶样式、玩家头顶样式等分页；`PreviewDrag` 提供统一的预览拖动，面板位置改动即时写入 `ModConfig`。

### 8. 屏幕标题
`TitleTextMixin` 拦截原版标题文本绘制，按 `titleOffsetX/Y`、`titleSize`、`titleOpacity`（副标题同名字段）调整；旧配置的共用位置在迁移时拆分到两行。

## 持久化

| 文件 | 写入方 | 内容 |
| --- | --- | --- |
| `config/ctt-health-display.json` | 客户端 | HUD 开关、面板位置、怪物档位、调试开关 |
| `config/ctt-health-display-server.json` | 服务端 | 广播开关、承伤阈值、血量跳变 / 可疑受害者过滤 |
| `ctt-health-display-cdp.json` | 客户端 | 客户端伤害探针快照；退出前 freeze 落盘，启动时恢复 |

## 渲染层级

HUD 元素按 Fabric `HudElementRegistry` 挂载：
- `main_hud`：挂在原版 BossBar **之前**，血条固定在敌人头顶且不被 BossBar 覆盖；
- `shield_cooldown`：`addLast` 独立层，主 HUD 被状态门控压掉时仍绘制；
- `arrow_preview`：挂在原版快捷栏 **之后**，保证物品格对齐；
- 队友世界空间血条走 `LevelRenderEvents.BEFORE_GIZMOS` 每帧收集器。

## 状态门控

`HudRenderGate.shouldSuppressModHud(statsData)` 在癫狂 / 未知诅咒状态下压掉主 HUD；盾牌冷却条不经过该门控。识别结果来自属性面板文本中的 `Berserk` / `癫狂`，以及乱码翻译键形式的诅咒 BossBar。
