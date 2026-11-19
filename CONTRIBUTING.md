# 贡献指南 / Contributing

感谢你愿意为 **Cake Tower HUD Plus** 提交反馈或代码！请先阅读本文件，确保改动符合项目约定。

## 开发环境

- JDK 25
- Minecraft 26.3 / Fabric Loader 0.19.5 / Fabric API 0.162.0+26.3
- Fabric Loom 1.17.20
- 推荐 IntelliJ IDEA（导入 Gradle 项目）或 VS Code + Java 扩展包

## 拉取与构建

```bash
git clone https://github.com/Kirin-0321/Cake-Tower-HUD-Plus.git
cd Cake-Tower-HUD-Plus
./gradlew build
```

首次构建会下载 Minecraft 与依赖；`build/libs/` 下会生成模组 jar 与源码包。

## 代码约定

- **包结构**：客户端代码放 `client/`，服务端代码放 `server/`，数据模型放 `health/`，渲染器与屏幕放 `hud/`，网络协议放 `network/`，配置放 `config/`，Mixins 单独放 `mixin/`。
- **编码**：所有 Java 源文件与 JSON 资源使用 UTF-8；Gradle `processResources` 已显式声明 UTF-8，不要改回平台默认编码。
- **注释**：关键行为、历史坑点、版本来历保留中文说明；新增对外可读的文案必须同步 `lang/zh_cn.json` 与 `lang/en_us.json`。
- **网络 payload**：新增 S2C 包时，服务端 `CttStatsServer` 与客户端 `CttHealthDisplay` 都要注册 `PayloadType`，并处理集成服务器单 JVM 下重复注册抛出的 `IllegalArgumentException`。
- **配置兼容**：新增配置字段要在 `ModConfig.migrate()` / `ServerConfig` 中做旧 JSON 迁移与取值范围校验。
- **Mixin**：客户端 Mixin 写入 `ctt-health-display.mixins.json`，服务端 Mixin 写入 `ctt-health-display-server.mixins.json`，不要混放。

## 提交前自检

```bash
./gradlew build          # 编译 + 资源处理通过
./gradlew runClient      # 进游戏确认 HUD、按键、配置界面正常
```

重点检查：
- 切服 / 断线后无悬挂的上一局数据；
- 癫狂、未知诅咒状态下 HUD 屏蔽符合预期；
- 旧版配置文件加载不崩溃；
- 中英文语言键齐全，无漏翻、中英混合。

## 提交信息与分支

- 提交信息建议使用 `类型: 简述`，类型参考：`feat`（新功能）、`fix`（修复）、`refactor`（重构）、`docs`（文档）、`i18n`（本地化）、`chore`（构建 / 杂项）。
- 功能开发从 `main` 切出分支，例如 `feat/damage-panel-sort`、`fix/mob-bar-mismatch`。
- 合并前请更新 `CHANGELOG.md` 对应小节。

## 报告问题

提交 Issue 前请先确认：
- 模组版本、MC 版本、Loader / Fabric API 版本；
- 是单机集成服务器、专用服务器还是 LAN 远程客户端；
- 复现步骤、截图 / 日志、相关配置文件内容（可隐去账号信息）。

## 许可证

提交即表示你同意你的贡献在 [MIT](LICENSE) 许可证下发布。
