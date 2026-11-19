# 配置项说明 / Configuration Reference

配置文件位于游戏目录 `config/` 下。修改客户端配置后可执行 `/ctthd reload` 热加载；服务端配置在集成单机中即时生效，专用服务器需重启或重载。

## 客户端 `ctt-health-display.json`

### HUD 布局

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `teammateX` | float | `0.01` | 队友面板 X 坐标（屏幕比例 0~1） |
| `teammateY` | float | `0.33` | 队友面板 Y 坐标 |
| `horizontalLayout` | bool | `false` | 队友条是否横向布局 |
| `statsX` | float | `0.005` | 属性面板 X 坐标 |
| `statsY` | float | `0.005` | 属性面板 Y 坐标 |
| `statsColumns` | int | `2` | 属性栏列数 |
| `statsVisibility` | int | `0` | 属性栏显示：`0` 常显 / `1` 背包显示 / `2` 隐藏 |
| `mobBarHalfWidth` | int | `60` | 怪物血条半宽 |
| `teammateBarHalfWidth` | int | `40` | 队友血条半宽 |

### 显示开关

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `showTeammateHeadHP` | bool | `true` | 队友头顶血量 |
| `mobHeadHPMode` | int | `0` | 怪物血量：`0` 全部 / `1` 仅最近 / `2` 关闭 |
| `showShieldCooldown` | bool | `true` | 盾牌冷却条 |
| `showTeammateLayeredHearts` | bool | `true` | 队友四色心叠加外观；`false` 回退多槽老外观 |
| `hidePersonalBar` | bool | `true` | 隐藏自身 Boss 栏 |
| `hideTeamBar` | bool | `true` | 隐藏队友 Boss 栏 |
| `hideMobBars` | bool | `false` | 隐藏怪物 Boss 栏 |
| `hideHudOnBerserk` | bool | `true` | 癫狂状态隐藏全部模组 HUD（不在 ModMenu 暴露，手改 JSON + `/ctthd reload`） |

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
