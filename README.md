# Cake Tower HUD

> 蛋糕塔 Chapter 3（Cake Team Towers）专用的 Fabric HUD 模组：自绘血量条、属性面板、队友与敌人头顶血条、战斗统计，按 `F8` 打开设置，面板可在预览中拖动。
>
> A HUD mod built for **Cake Team Towers Chapter 3**: layered health bars, stats panel, party and enemy overhead bars, combat tables. Press `F8` to configure; panels are drag-to-position.

![Mod icon](src/main/resources/assets/ctt-health-display/icon.png)

## 模组信息

| 项目 | 内容 |
| --- | --- |
| Mod ID | `ctt-health-display` |
| 当前版本 | `2.0.2`（构建标记 `2.0.2-r92`） |
| Minecraft | `~26.3` |
| Fabric Loader | `>= 0.19.3`（构建使用 `0.19.5`） |
| Fabric API | `0.162.0+26.3` |
| Java | `>= 25` |
| Loom | `1.17.20` |
| 环境 | 双端（客户端 HUD + 服务端统计推送） |
| 许可证 | MIT |
| 作者 | （前）麒麟 (Kirin0321) （现）节斗圣主（MCHolyLord）|
| 贡献者 | OnlyScoutCat（赞助 · MC Server `sc4.i9idc.com:12439`） |

## 功能特性

### 核心 HUD
- **自定义血量条**：快捷栏上方、队友列表、队友头顶统一使用红心 / 碎心 / 魂心 / 黑心 / 蓝心分层配色；有碎心时显示 `200/150 (200)`（当前 / 碎心后上限 / 原上限）。
- **属性面板**：可分别控制背包关闭时 / 背包打开时是否显示，列数与左右对齐可调，支持隐藏单项属性；支持服务端属性推送与客户端定时 `trigger ViewStats` 两条取数路径。
- **队友头顶血条**：抬到名字上方，世界空间渲染，每行最多四组、超出向上换行；长度用 30–100 滑块调整，心形按默认长度 80 绘制。
- **敌人头顶血条**：带轮廓与高光样式，额外心用黄色并跟随血量变化；名称、护甲抗性、额外心、血量数字、血条可分别开关，长度固定不随数字位数变化，可隐藏 NPC 血量。
- **数字简写**：`1500 → 1.5K`、`2400000 → 2.4M`，更大值可达 `B` / `T`，覆盖快捷栏状态、队友列表、属性面板、头顶血量与伤害统计。
- **盾牌冷却**：可选横向长条或竖向指示器，大小、透明度可调；持弓时显示下一支箭预览并可拖动位置。
- **屏幕标题**：标题与副标题各有水平位置、垂直位置、大小和透明度；旧配置的共用位置会沿用并拆分。
- **设置界面**：按 `F8` 打开，队友列表、属性面板、盾牌冷却、箭矢提示均可在预览中拖动。

### 战斗统计（实验性）
> ⚠ 伤害统计 / 攻击者归属 / 伤害分配 / K·D·A 统计 / 每关战绩广播仍在调优中：数值可能不准、归属可能误判、聊天广播默认关闭。

- **伤害分配面板（F24）**：整局（SESSION）与当前关（CURRENT_STAGE）两种数据切片，紧凑行 / 详情展开两种视图。
- **统计表（N）**：总表 / 分关表，点击列头排序、滚轮滚动；分关表优先显示进入地牢时屏幕中央的副标题，并按实际宽度裁剪过长名称。
- **攻击者归属链路**：武器+火焰 → 计分板 tick → 3m / 40m 标记 → 计分板窗口 → 火焰窗口 → 最后命中者 → 召唤物兜底 → 未归属。
- **服务端统计广播**：伤害 / 击杀 / 承伤 / 每关战绩可在聊天栏广播；承伤可设阈值。
- **客户端伤害探针（CDP）**：本地观测伤害增量、击杀与 5 秒 DPS，数据落盘 `ctt-health-display-cdp.json`，断线前自动冻结保存。

### 状态识别与兼容处理
- **癫狂（Berserk）/ 未知诅咒（Curse of the Unknown）**：识别后隐藏本模组全部 UI，避免乱码状态条干扰。
- **团队 Boss 栏自动切换**：检测到个人 Boss 栏后自动 `trigger TogglePartyBossbar`，带 3 秒冷却与连续失败 10 分钟熔断，防止被服务端限流踢出。
- **聊天噪声过滤**：屏蔽本模组自发命令回显与地图固定字面噪声行；Lore 词条点击 / 悬浮 / 固定窗口交互，背包界面按 `Alt` 可固定 / 解除物品描述，窗口可选择跟随鼠标或贴在背包左侧。
- **关卡名本地化表**：`stage_name_map.json` 映射关卡 ID 到中英文名，统计表与战绩标题共用。

## 默认按键

| 按键 | 功能 |
| --- | --- |
| `F8` | 打开设置界面（可拖动面板预览） |
| `H` | 切换 CTT 血条显示 / 隐藏 |
| `J` | 切换背包关闭时的属性栏显示 / 隐藏 |
| `K` | 切换 Boss 栏（自身 / 队友 / 怪物）显示 |
| `F24` | 显示 / 隐藏伤害分配面板 |
| `N` | 打开 / 关闭统计表格面板 |
| `Alt`（背包界面） | 固定 / 解除当前物品描述窗口 |

所有按键均可在「选项 → 控制 → 蛋糕塔 HUD 增强」中重新绑定。

## 快速开始

### 环境要求
- JDK **25**（带 Sources 工具链）
- Gradle（推荐使用仓库内 wrapper；若 wrapper 缺失，使用本地兼容 Gradle 版本）
- 已安装 Fabric Loader 与 Fabric API 的 Minecraft 26.3 客户端 / 服务端

### 构建
```bash
# 在仓库根目录执行
./gradlew build          # Windows: gradlew.bat build
```
产物位于 `build/libs/`（构建脚本会去掉 `-rNN` 内部标记，只留正式版本号）：
- `cake-tower-hud-2.0.2.jar`：模组主文件，放入 `.minecraft/mods`。
- `cake-tower-hud-2.0.2-sources.jar`：源码包。

### 开发运行
```bash
./gradlew runClient      # 启动带模组的客户端
./gradlew runServer      # 启动专用服务器
```

### 配置文件
- `config/ctt-health-display.json`：客户端 HUD、面板位置、怪物血条档位、调试开关等。
- `config/ctt-health-display-server.json`：服务端广播开关、承伤阈值、初始血量跳变过滤、可疑受害者大数过滤等。
- 客户端命令 `/ctthd reload` 热加载客户端配置，无需重启游戏。

## 仓库结构

```
Cake-Tower-HUD-Plus/
├── build.gradle                  # Fabric Loom 构建脚本（发布版号去掉 -rNN）
├── settings.gradle               # 项目名与插件仓库
├── gradle.properties             # MC / Loader / API / 版本等属性
├── libs/
│   └── modmenu-21.0.0.jar        # ModMenu 编译期接口（不打包进 jar）
├── src/main/
│   ├── java/com/ctt/healthdisplay/
│   │   ├── CttHealthDisplay.java  # 客户端入口与按键注册
│   │   ├── ModMenuIntegration.java
│   │   ├── client/               # 客户端缓存、探针、识别器、统计行标识
│   │   ├── config/               # 配置模型、设置主界面与各分页（标题 / 盾牌 / 属性筛选 / 头顶样式 / 预览拖动）
│   │   ├── health/              # 血量 / 属性 / 怪物血量数据模型、属性标签
│   │   ├── hud/                  # 各 HUD 渲染器、碎心图标、头顶样式、数字简写
│   │   ├── mixin/               # 客户端 Mixin（标题文本、Lore 交互等）
│   │   ├── network/              # S2C 网络 payload 与 codec（含 NPC 血量可见性）
│   │   └── server/              # 服务端统计、归属、广播、命令与服务端 Mixin
│   └── resources/
│       ├── fabric.mod.json
│       ├── ctt-health-display.mixins.json
│       ├── ctt-health-display-server.mixins.json
│       ├── pack.mcmeta
│       ├── stage_name_map.json   # 关卡 ID → 本地化名称表
│       ├── weapon_damage_seed.json
│       ├── META-INF/cake-tower-hud/  # 随 jar 分发的 CHANGELOG / RELEASE 文档
│       └── assets/ctt-health-display/
│           ├── icon.png
│           ├── lang/             # zh_cn.json / en_us.json
│           ├── lore/entries/     # Lore 词条数据
│           └── textures/custom/  # 自定义图标贴图
├── tools/                        # 一次性维护脚本与独立测试
│   ├── CollectorParseTest.java
│   ├── rename_yarn_to_mojang.py
│   └── render_pixel_art.py
├── docs/                          # 设计说明与架构文档
├── .github/ISSUE_TEMPLATE/       # Issue 模板
├── CHANGELOG.md
├── CONTRIBUTING.md
└── LICENSE
```

## 网络数据包

模组通过以下 S2C payload 与服务端通信；两端都注册类型后才能完成握手：

| Payload | 方向 | 用途 |
| --- | --- | --- |
| `StagePayload` | S2C | 关卡位置 / 阶段信息 |
| `StatsSnapshotPayload` | S2C | 属性全量快照（专用服务器 / 远程客户端） |
| `MobHealthPayload` | S2C | 视野内怪物血量（HP / 上限 / 是否被锁定） |
| `PlayerStatsPayload` | S2C | 服务端推送的属性面板内容 |
| `TeamHeartsPayload` | S2C | 全队四色心与最大生命摘要 |
| `NpcHealthVisibilityPayload` | S2C | NPC 头顶血量是否可见 |

集成服务器单 JVM 下任意一端先注册即可，重复注册抛出的 `IllegalArgumentException` 会被吞掉；纯专用客户端 / 远端客户端必须在客户端侧完成注册。

## 反馈与发布

- CurseForge 页面：<https://www.curseforge.com/minecraft/mc-mods/cake-tower-hud>
- 源码仓库：<https://github.com/TheHolyLord/Cake-Hud>

## 许可

[MIT](LICENSE)。Minecraft、Fabric Loader 及其他第三方组件保留各自原有许可证。
