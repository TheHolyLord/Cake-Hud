# 开发环境与构建 / Build Guide

## 前置要求

| 组件 | 版本 |
| --- | --- |
| JDK | 25（需带 Sources 工具链） |
| Minecraft | 26.3 |
| Fabric Loader | 0.19.5（运行要求 `>=0.19.3`） |
| Fabric API | 0.162.0+26.3 |
| Fabric Loom | 1.17.20 |

> 本仓库源码基于 Mojang 官方映射（26.3）。`tools/rename_yarn_to_mojang.py` 是历史 Yarn → Mojang 批量改名脚本，仅在迁移旧代码时使用。

## 获取依赖

`libs/modmenu-21.0.0.jar` 是 ModMenu 编译期接口，`compileOnly` 引入，**不会打包进最终 jar**；玩家运行时需自行安装 ModMenu（可选，仅配置界面入口使用）。

## 常用命令

```bash
# 编译并打包产物到 build/libs/
./gradlew build

# 启动开发客户端
./gradlew runClient

# 启动开发专用服务器
./gradlew runServer

# 清理构建产物
./gradlew clean
```

Windows 下使用 `gradlew.bat`；若仓库尚未附带 wrapper，可在装有兼容 Gradle 的环境执行 `gradle wrapper --gradle-version <兼容版本>` 生成。

## 产物说明

| 文件 | 说明 |
| --- | --- |
| `build/libs/cake-tower-hud-<version>.jar` | 模组主文件，放入 `.minecraft/mods` |
| `build/libs/cake-tower-hud-<version>-sources.jar` | 源码包，不含可运行编译产物 |

`gradle.properties` 中的 `mod_version` 带内部构建标记（如 `2.0.2-r92`），`build.gradle` 在打包时用 `replaceFirst(/-r\d+$/, '')` 去掉，安装包与游戏内显示保持一致（`2.0.2`）。

## 资源处理注意事项

- `build.gradle` 的 `processResources` 已显式设置 `filteringCharset = 'UTF-8'`，避免中文 description 在容器默认 ASCII 编码下损坏。
- `fabric.mod.json` 中的 `${version}` 按行替换，不使用 Gradle `expand`，防止 JSON 字符串里的 `\n` 被解释成真实换行而损坏文件。

## 调试入口

- 客户端命令：`/ctthd reload` 热加载 `config/ctt-health-display.json`。
- 客户端伤害探针调试聊天开关位于 `ModConfig.clientDamageDebugChat`；击杀日志开关为 `clientKillDebugChat`，默认关闭避免刷屏。
- 服务端广播临时开关：`/ctthd broadcast ...`（见 `BroadcastToggleCommand`）。
