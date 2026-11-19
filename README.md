# Cake Tower HUD Plus

> 蛋糕塔 Chapter 3（Cake Team Towers）专用的 Fabric HUD 模组：自绘血量条、属性面板、队友头顶血条、怪物追踪，面板可拖拽，并接入 ModMenu 配置界面。
>
> A client-side HUD mod built for **Cake Team Towers Chapter 3**: custom health bars, stats panel, teammate overhead bars and mob tracking. Panels are drag-to-position and configurable through ModMenu.

![Mod icon](src/main/resources/assets/ctt-health-display/icon.png)

## 模组信息

| 项目 | 内容 |
| --- | --- |
| Mod ID | `ctt-health-display` |
| 当前版本 | `1.0.0-r66` |
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
- **自定义血量条**：分层绘制红心 / 魂心 / 黑心 / 蓝心 / 碎心，支持多色心叠加。
- **属性面板**：常显 / 背包显示 / 隐藏三档切换，可调整列数与位置；支持服务端属性推送与客户端定时 `trigger ViewStats` 两条取数路径。
- **队友头顶血条**：世界空间渲染，支持四色心叠加外观与老版多槽溢出条两种风格，可切换横向 / 纵向布局。
- **怪物追踪血条**：全部显示 / 仅最近目标 / 关闭三档，按 `Targetting`、`Boss`、`E` 标签与距离认领怪物数据，避免同名怪物串血条。
- **盾牌冷却条**：独立渲染层，癫狂等状态压掉主 HUD 时仍可显示。
- **弓箭预览**：紧跟原版快捷栏绘制，与物品格对齐。
- **可拖拽面板**：伤害分配面板、队友面板、属性面板均可在游戏内拖动位置，并可重置。

### 战斗统计（实验性）
> ⚠ 伤害统计 / 攻击者归属 / 伤害分配 / K·D·A 统计 / 每关战绩广播仍在调优中：数值可能不准、归属可能误判、聊天广播默认关闭。

- **伤害分配面板（L / F24）**：整局（SESSION）与当前关（CURRENT_STAGE）两种数据切片，紧凑行 / 详情展开两种视图。
- **统计表（N）**：总表 / 分关表，点击列头排序、滚轮滚动，含客户端可见伤害（无归属）聚合行。
- **攻击者归属链路**：武器+火焰 → 计分板 tick → 3m / 40m 标记 → 计分板窗口 → 火焰窗口 → 最后命中者 → 召唤物兜底 → 未归属。
- **服务端统计广播**：伤害 / 击杀 / 承伤 / 每关战绩可在聊天栏广播；承伤可设阈值。
- **客户端伤害探针（CDP）**：本地观测伤害增量、击杀与 5 秒 DPS，数据落盘 `ctt-health-display-cdp.json`，断线前自动冻结保存。

### 状态识别与兼容处理
- **癫狂（Berserk）/ 未知诅咒（Curse of the Unknown）**：识别后隐藏本模组全部 UI，避免乱码状态条干扰。
- **团队 Boss 栏自动切换**：检测到个人 Boss 栏后自动 `trigger TogglePartyBossbar`，带 3 秒冷却与连续失败 10 分钟熔断，防止被服务端限流踢出。
- **聊天噪声过滤**：屏蔽本模组自发命令回显与地图固定字面噪声行；Lore 词条点击 / 悬浮 / 固定窗口交互。
- **关卡名本地化表**：`stage_name_map.json` 映射关卡 ID 到中英文名，统计表与战绩标题共用。

## 默认按键

| 按键 | 功能 |
| --- | --- |
| `H` | 切换 CTT 血条显示 / 隐藏 |
| `J` | 切换属性面板：常显 → 背包显示 → 隐藏 |
| `K` | 切换 Boss 栏（自身 / 队友 / 怪物）显示 |
| `F24` | 显示 / 隐藏伤害分配面板 |
| `N` | 打开 / 关闭统计表格面板 |

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
产物位于 `build/libs/`：
- `fabric_upgrade-<version>.jar`：模组主文件，放入 `.minecraft/mods`。
- `fabric_upgrade-<version>-sources.jar`：源码包。

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
├── build.gradle                  # Fabric Loom 构建脚本
├── settings.gradle               # 项目名与插件仓库
├── gradle.properties             # MC / Loader / API / 版本等属性
├── libs/
│   └── modmenu-21.0.0.jar        # ModMenu 编译期接口（不打包进 jar）
├── src/main/
│   ├── java/com/ctt/healthdisplay/
│   │   ├── CttHealthDisplay.java  # 客户端入口
│   │   ├── ModMenuIntegration.java
│   │   ├── client/               # 客户端缓存、探针、识别器、聊天过滤
│   │   ├── config/              # 客户端 / 服务端配置与配置界面
│   │   ├── health/              # 血量 / 属性 / 怪物血量数据模型
│   │   ├── hud/                  # 各 HUD 渲染器与屏幕
│   │   ├── mixin/               # 客户端 Mixin
│   │   ├── network/              # S2C 网络 payload 与 codec
│   │   └── server/              # 服务端统计、归属、广播、命令与服务端 Mixin
│   └── resources/
│       ├── fabric.mod.json
│       ├── ctt-health-display.mixins.json
│       ├── ctt-health-display-server.mixins.json
│       ├── pack.mcmeta
│       ├── stage_name_map.json   # 关卡 ID → 本地化名称表
│       ├── weapon_damage_seed.json
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

集成服务器单 JVM 下任意一端先注册即可，重复注册抛出的 `IllegalArgumentException` 会被吞掉；纯专用客户端 / 远端客户端必须在客户端侧完成注册。

## 反馈与发布

- CurseForge 页面：<https://www.curseforge.com/minecraft/mc-mods/cake-tower-hud>
- 源码仓库：<https://github.com/TheHolyLord/Cake-Hud>

## 许可

[MIT](LICENSE)。Minecraft、Fabric Loader 及其他第三方组件保留各自原有许可证。
