# 配置项说明 / Configuration Reference

配置文件位于游戏目录 `config/` 下。修改客户端配置后可执行 `/ctthd reload` 热加载；服务端配置在集成单机中即时生效，专用服务器需重启或重载。

## 客户端 `ctt-health-display.json`

### HUD 布局

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `teammateX` | float | `0.01` | 队友面板 X 坐标（屏幕比例 0~1） |
| `teammateY` | float | `0.33` | 队友面板 Y 坐标 |
| `horizontalLayout` | bool | `false` | 队友条是否横向布局 |
| `teammateAlignRight` | bool | `false` | 队友列表右对齐（`false` 左对齐） |
| `statsX` | float | `0.005` | 属性面板 X 坐标 |
| `statsY` | float | `0.005` | 属性面板 Y 坐标 |
| `statsAlignRight` | bool | `false` | 属性面板右对齐（`false` 左对齐） |
| `statsColumns` | int | `2` | 属性栏列数（上限 `MAX_STATS_COLUMNS = 3`） |
| `showStatsOutsideInventory` | bool | `true` | 背包关闭时是否显示属性栏 |
| `showStatsInInventory` | bool | `false` | 背包打开时是否显示属性栏 |
| `statsOpacity` | int | `100` | 属性面板不透明度（0–100） |
| `loreTooltipAtCursor` | bool | `false` | 物品描述窗口位置：`false` 背包左侧 / `true` 跟随鼠标 |
| `enemyBarLength` | int | `36` | 敌人头顶血条长度（固定像素，不随数字位数变化） |
| `teammateBarHalfWidth` | int | `40` | 队友头顶血条半宽；界面上以 30–100 的长度滑块调整（存半宽） |

### 屏幕标题

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `titleOffsetX` / `titleOffsetY` | int | `0` | 主标题水平 / 垂直偏移 |
| `subtitleOffsetX` / `subtitleOffsetY` | int | `0` | 副标题水平 / 垂直偏移 |
| `titleSize` | int | `100` | 主标题缩放（百分比） |
| `subtitleSize` | int | `100` | 副标题缩放（百分比） |
| `titleOpacity` | double | `1.0` | 主标题不透明度（0–1） |
| `subtitleOpacity` | double | `1.0` | 副标题不透明度（0–1） |

旧配置中标题与副标题共用的位置字段在升级时迁移并沿用到两行，之后可分别调节。

### 显示开关

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `showTeammateHeadHP` | bool | `true` | 队友头顶血量 |
| `showPlayerHeadBar` | bool | `true` | 玩家头顶血条 |
| `showPlayerHeadNumbers` | bool | `true` | 玩家头顶血量数字 |
| `showPlayerHeadHeartCounts` | bool | `true` | 玩家头顶各类心数量 |
| `showPlayerHeadCrackedCount` | bool | `true` | 玩家头顶碎心数量 |
| `showPlayerHeadEffectiveMax` | bool | `true` | 玩家头顶碎心后上限 |
| `showMobHeadHP` | bool | `true` | 敌人头顶血量总开关 |
| `mobHeadHPMode` | int | `0` | 敌人血量：`0` 全部 / `1` 仅最近 / `2` 关闭 |
| `showEnemyName` / `showEnemyArmor` / `showEnemyExtraHearts` | bool | `true` | 敌人名字 / 护甲抗性 / 额外心 |
| `showEnemyHealthNumbers` / `showEnemyBar` | bool | `true` | 敌人血量数字 / 血条 |
| `showEnemyHealthDuringEffects` | bool | `true` | 癫狂 / 诅咒期间仍显示敌人血量 |
| `suppressNpcHealth` | bool | `true` | 隐藏 NPC 头顶血量 |
| `enemyHeadDistance` / `playerHeadDistance` | int | `48` | 敌人 / 玩家头顶血条可见距离（格） |
| `enemyBarOpacity` / `enemyTextOpacity` | int | `65` / `100` | 敌人血条 / 文字不透明度 |
| `playerBarOpacity` / `playerTextOpacity` | int | `65` / `100` | 玩家血条 / 文字不透明度 |
| `compactHudNumbers` | bool | `true` | 所有数据中的大数字简写（`1500 → 1.5K`、`2400000 → 2.4M`，支持 `B` / `T`） |
| `showTeammateLayeredHearts` | bool | `true` | 队友分层心外观（红 / 碎 / 魂 / 黑 / 蓝） |
| `hidePersonalBar` | bool | `true` | 隐藏自身 Boss 栏 |
| `hideTeamBar` | bool | `true` | 隐藏队友 Boss 栏 |
| `hideMobBars` | bool | `false` | 隐藏怪物 Boss 栏 |
| `hideHudOnBerserk` | bool | `true` | 癫狂状态隐藏全部模组 HUD（不在 ModMenu 暴露，手改 JSON + `/ctthd reload`） |

### 盾牌冷却与箭矢预览

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `showShieldCooldown` | bool | `true` | 盾牌冷却提示 |
| `shieldHorizontal` | bool | `false` | `true` 横向长条 / `false` 竖向指示器 |
| `shieldSize` | int | `100` | 指示器尺寸（百分比） |
| `shieldOpacity` | int | `65` | 不透明度（0–100） |
| `shieldX` / `shieldY` | float | 默认位置 | 预览中可拖动 |
| `showArrowPreview` | bool | `true` | 持弓时显示下一支箭 |
| `arrowPreviewX` / `arrowPreviewY` | float | `-1` | 箭矢提示位置；`-1` 表示使用默认（跟随快捷栏） |

### 属性刷新

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `autoRefreshStats` | bool | `true` | 无服务端推送时自动刷新属性 |
| `autoRefreshIntervalSeconds` | int | `30` | 定时刷新间隔（秒）；过短会高频触发 `trigger ViewStats`，可能被反作弊踢线 |

### 伤害面板（实验性）

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `damagePanelHudVisible` | bool | `false` | HUD 上持续渲染伤害分配面板 |
| `damagePanelX` | float | `0.005` | 面板 X 坐标 |
| `damagePanelY` | float | `0.20` | 面板 Y 坐标 |
| `damagePanelDetailed` | bool | `true` | 详情展开；`false` 为紧凑行 |
| `damagePanelStageScope` | int | `0` | 切片范围：`0` 整局 / `1` 当前关 |
| `embeddedHudMode` | int | `1` | 队友面板伤害击杀行：`0` 关 / `1` 仅本关 / `2` 仅本局 / `3` 双行 |

### 调试

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `clientDamageDebugChat` | bool | `false` | 把每条客户端伤害观测打到本地聊天 |
| `clientDamageHudHeader` | bool | `true` | 队友 HUD 顶部绘制全队伤害 / 击杀 / DPS 聚合行 |
| `clientKillDebugChat` | bool | `false` | 击杀报告写入本地聊天 |

> 旧字段 `showMobHeadHP` 仅用于旧 JSON 迁移：`false` 会映射到 `mobHeadHPMode = 2`。
> 旧字段 `statsVisibility`（0 常显 / 1 背包显示 / 2 隐藏）在首次加载时迁移：`0` → `showStatsOutsideInventory = true`，`1` → `showStatsInInventory = true`；新代码不再读取该字段。

## 服务端 `ctt-health-display-server.json`

| 字段 | 说明 |
| --- | --- |
| `broadcastDamageInChat` | 聊天栏广播伤害 |
| `broadcastKillsInChat` | 聊天栏广播击杀 |
| `broadcastTakenInChat` | 聊天栏广播承伤 |
| `broadcastStageReportInChat` | 每关战绩广播 |
| `broadcastTakenThreshold` | 承伤广播阈值（≥ 该值才广播） |
| `useRedHeartsTally` | 以 RedHearts 作为主数据源 |
| `filterInitHpJumps` | 过滤初始血量跳变 |
| `initHpJumpValues` | 初始血量列表 |
| `filterSuspectVictims` | 过滤可疑受害者大数 |
| `suspectVictims` | 可疑受害者名单 |
| `suspectVictimDamageThreshold` | 可疑受害者阈值 |
| `collectMagumTrials` | Magum Trials 分关采集 |

旧客户端配置中的服务端字段会在首次启动时迁移到本文件，残留 key 被 GSON 静默丢弃。
