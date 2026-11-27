package com.ctt.healthdisplay.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public class ModConfig {

    public static ModConfig INSTANCE = new ModConfig();

    public static final int MOB_HP_MODE_ALL = 0;
    public static final int MOB_HP_MODE_NEAREST = 1;
    public static final int MOB_HP_MODE_OFF = 2;

    public float teammateX = 0.01f;
    public float teammateY = 0.33f;
    public boolean horizontalLayout = false;
    public boolean teammateAlignRight = false;
    public float statsX = 0.005f;
    public float statsY = 0.005f;
    public boolean statsAlignRight = false;
    public boolean autoRefreshStats = true;
    public int autoRefreshIntervalSeconds = 30;
    public boolean showTeammateHeadHP = true;
    public boolean showPlayerHeadBar = true;
    public boolean showPlayerHeadNumbers = true;
    public boolean showPlayerHeadHeartCounts = true;
    public boolean showPlayerHeadCrackedCount = true;
    public boolean showPlayerHeadEffectiveMax = true;
    // 旧版字段，仅用于读旧 JSON 做迁移；新代码不直接读它，而是读 mobHeadHPMode。
    public boolean showMobHeadHP = true;
    // 新三档开关：0=ALL 全部显示；1=NEAREST 仅最近（bossbar 锁定的那只）；2=OFF 关。
    public int mobHeadHPMode = MOB_HP_MODE_ALL;
    public static final int MIN_ENEMY_BAR_LENGTH = 12;
    public static final int MAX_ENEMY_BAR_LENGTH = 160;
    public static final int MIN_TEAMMATE_BAR_LENGTH = 30;
    public static final int MAX_TEAMMATE_BAR_LENGTH = 100;
    public static final int DEFAULT_TEAMMATE_BAR_LENGTH = 80;
    public int enemyBarLength = 36;
    public int teammateBarHalfWidth = DEFAULT_TEAMMATE_BAR_LENGTH / 2;
    public static final int MAX_STATS_COLUMNS = 3;
    public int statsColumns = 2;
    public boolean showStatsOutsideInventory = true;
    public boolean showStatsInInventory = false;
    public boolean loreTooltipAtCursor = false;
    public boolean hidePersonalBar = true;
    public boolean hideTeamBar = true;
    public boolean hideMobBars = false;
    public boolean showArrowPreview = true;
    // 负值表示跟随快捷栏，拖动后保存为屏幕上的相对位置。
    public float arrowPreviewX = -1f;
    public float arrowPreviewY = -1f;
    public boolean showShieldCooldown = true;
    public static final float DEFAULT_SHIELD_X = 0.5f;
    public static final float DEFAULT_SHIELD_Y = -1f;
    public float shieldX = DEFAULT_SHIELD_X;
    public float shieldY = DEFAULT_SHIELD_Y;
    public int shieldOpacity = 65;
    public boolean shieldHorizontal = false;
    public static final int MIN_SHIELD_SIZE = 50;
    public static final int MAX_SHIELD_SIZE = 200;
    public int shieldSize = 100;
    public int enemyBarOpacity = 65;
    public boolean showEnemyName = true;
    public boolean showEnemyArmor = true;
    public boolean showEnemyExtraHearts = true;
    public boolean showEnemyHealthNumbers = true;
    public boolean showEnemyBar = true;
    public int enemyTextOpacity = 100;
    public int playerBarOpacity = 65;
    public int playerTextOpacity = 100;
    public int enemyHeadDistance = 48;
    public int playerHeadDistance = 48;
    public static final int TITLE_OFFSET_LIMIT = 300;
    public static final int MIN_TITLE_SIZE = 25;
    public static final int MAX_TITLE_SIZE = 200;
    private static final int TITLE_REFERENCE_WIDTH = 640;
    private static final int TITLE_REFERENCE_HEIGHT = 360;
    public int titleOffsetX;
    public int titleOffsetY;
    public int subtitleOffsetX;
    public int subtitleOffsetY;
    public int titleSize = 100;
    public int subtitleSize = 100;
    public double titleOpacity = 1.0;
    public double subtitleOpacity = 1.0;
    public int statsOpacity = 100;
    @com.google.gson.annotations.SerializedName(value = "compactHudNumbers", alternate = "compactHeadHealth")
    public boolean compactHudNumbers = true;
    public boolean showEnemyHealthDuringEffects = true;
    public boolean suppressNpcHealth = true;
    public java.util.Set<String> hiddenStatRows = new java.util.HashSet<>();

    /**
     * v8.4.0 · 队友头顶 / 侧栏 HUD 血条是否使用与玩家自己一致的 4 色心叠加（Red/Soul/Black/Blue）。
     *
     * <p>默认 {@code true} —— 视觉直观区分队友抗性堆叠 (Soul/Black/Blue 多寡)，
     * 与主血条 {@link com.ctt.healthdisplay.hud.HealthBarRenderer#drawLayeredBar} 风格一致。
     * 依赖服务端 {@link com.ctt.healthdisplay.server.TeamHeartsBroadcaster} 推过四色心数据
     * （{@link com.ctt.healthdisplay.client.ClientTeamHeartsCache#isFresh} fresh 时生效），
     * 否则自动 fallback 到 v8.3.x 的 OVERFLOW_COLORS 多槽溢出条。
     * <p>设为 {@code false} 强制使用 v8.3.x 老外观（{@code 1× 红 / 2× 橙 / 3× 黄 ...} 多槽），
     * 适合追求"老风格"或服务端推送有故障时手动切回。
     */
    public boolean showTeammateLayeredHearts = true;

    // ===== v6.3.0 · 伤害分配面板（测试版）=====
    /**
     * 是否在 HUD 上持续渲染面板（非交互模式的只读展示）。关闭后仅按 L 弹 Screen 时可见。
     * <p>v6.5.30 起默认 false：面板属于实验性功能且开发者向，新装用户不应默认看到。
     */
    public boolean damagePanelHudVisible = false;
    /** 面板左上角屏幕坐标百分比（0~1）。默认左上 1%/20%，便于和左侧血条避让。 */
    public float damagePanelX = 0.005f;
    public float damagePanelY = 0.20f;
    /** 详情展开：否则紧凑行；Screen 模式下可通过按钮切换。 */
    public boolean damagePanelDetailed = true;

    // ===== v6.6.0 · M1 关卡分桶切片 =====
    /**
     * L 键面板的数据切片范围。
     * <ul>
     *   <li>0 = SESSION：整局累计（既有行为，session 总）</li>
     *   <li>1 = CURRENT_STAGE：仅当前关累计（{@link com.ctt.healthdisplay.server.PlayerDamageStats#snapshotOf(com.ctt.healthdisplay.server.StageKey)}）</li>
     * </ul>
     * 由 [C] / [S] 按钮在 Screen 模式下切换。
     */
    public int damagePanelStageScope = 0;
    public static final int STAGE_SCOPE_SESSION = 0;
    public static final int STAGE_SCOPE_CURRENT = 1;

    // ===== v6.6.0 · M3 嵌入式 HUD（队友面板伤害/承伤/击杀紧凑双行）=====
    /**
     * 嵌入式 HUD 模式（设计 §5.4 / §9）。控制队友面板第二/三/四行的显示：
     * <ul>
     *   <li>0 = OFF：完全关掉，回到 v5.3.3 单行</li>
     *   <li>1 = ONLY_STAGE：只显示"关:"行（当前/上一关分桶切片）（默认）</li>
     *   <li>2 = ONLY_SESSION：只显示"局:"行（整局累计）</li>
     *   <li>3 = BOTH：两行都显示</li>
     * </ul>
     * 在大厅 / Game Over kind 下整组 stats 行强制隐藏（设计 §5.6）。
     */
    public int embeddedHudMode = EMBED_ONLY_STAGE;
    public static final int EMBED_OFF          = 0;
    public static final int EMBED_ONLY_STAGE   = 1;
    public static final int EMBED_ONLY_SESSION = 2;
    public static final int EMBED_BOTH         = 3;

    // ===== v6.7.x · P0 客户端伤害探针（CLIENT_SIDE_STATS_PROPOSAL §X）=====
    /**
     * 客户端 DamageShower 粒子探针——是否把每条增量观测以 {@code [CDP] tick=N +D ...}
     * 形式打到本地聊天。仅本地可见（不走 networking），调试用。默认关闭以避免刷屏。
     *
     * <p>详见 {@link com.ctt.healthdisplay.client.ClientDamageProbe}。
     */
    public boolean clientDamageDebugChat = false;

    /**
     * 是否在队友 HUD 顶部绘制"客户端可见伤害"聚合行：
     * {@code ⚔ <本层> ☠ <本层击杀> ⚡ <5sDPS>/s}。仅当 {@link com.ctt.healthdisplay.client.ClientDamageProbe#hasAnyData()}
     * 为 true 时生效——避免空 HUD 占位。默认开启。
     */
    public boolean clientDamageHudHeader = true;

    /**
     * v7.1.0 · 客户端击杀报告聊天日志。开启后每次检测到死亡 → 本地聊天打 {@code [CDP/KILL] tick=N ☠ <名> uuid=...}。
     * 与 {@link #clientDamageDebugChat}（粒子流水）独立，两个开关互不影响。默认关闭以避免刷屏。
     *
     * <p>v7.1.0 · 客户端击杀计数本身始终开启（无配置开关）——开销低（每 tick 一次活体扫描），
     * 关闭只会让 HUD/K 表击杀段数字恒为 0，体验上反而更乱。本字段只控制"是否往聊天打日志"。
     */
    public boolean clientKillDebugChat = false;

    /**
     * v8.4.5 · 癫狂（Berserk）状态时是否隐藏本模组全部 HUD（血条 / 属性 / 队友头顶条 /
     * 怪物条 / 伤害面板等）。默认 {@code true}。
     *
     * <p>癫狂判定：属性面板数据行中出现 {@code Berserk} 翻译键或其本地化文本
     *（英文 {@code Berserk}、中文 {@code 癫狂}）。不在 ModMenu 配置界面暴露，
     * 仅通过 {@code config/ctt-health-display.json} 手改 + {@code /ctthd reload} 热加载。
     */
    public boolean hideHudOnBerserk = true;

    // ===== v6.6.4 · M5 · 服务端字段已迁出 =====
    // 以下字段从 v6.6.4 起搬到 {@link ServerConfig}（独立 JSON 文件
    // {@code config/ctt-health-display-server.json}），本类不再持有：
    //   - broadcastDamageInChat / broadcastKillsInChat / broadcastTakenInChat / broadcastTakenThreshold
    //   - useRedHeartsTally
    //   - filterInitHpJumps / initHpJumpValues
    //   - filterSuspectVictims / suspectVictims / suspectVictimDamageThreshold
    // 老用户的 ctt-health-display.json 里若仍残留这些 key，由 ServerConfig.load()
    // 在首次启动时迁移到新文件，旧 key 在反序列化时被 GSON 静默丢弃，不影响行为。

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance()
            .getConfigDir().resolve("ctt-health-display.json");

    public void save() {
        try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
            GSON.toJson(this, writer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void load() {
        if (Files.exists(CONFIG_PATH)) {
            try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
                JsonObject settings = GSON.fromJson(reader, JsonObject.class);
                if (settings != null) {
                    ModConfig loaded = GSON.fromJson(settings, ModConfig.class);
                    loaded.migrate(settings);
                    INSTANCE = loaded;
                    return;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        INSTANCE = new ModConfig();
    }

    /**
     * 从磁盘重新读取 {@code config/ctt-health-display.json} 并替换 {@link #INSTANCE}。
     * 供 {@code /ctthd reload} 客户端命令调用；修改 hideHudOnBerserk 等隐藏项后无需重启游戏。
     *
     * @return 是否成功读到已有配置文件（文件不存在时使用默认值，仍返回 {@code true}）
     */
    public static boolean reload() {
        boolean existed = Files.exists(CONFIG_PATH);
        load();
        return existed;
    }

    /**
     * 兼容 v5.2.0 以前只有 boolean `showMobHeadHP` 的旧 config：
     * 旧 JSON 里 `showMobHeadHP=false` 而新字段 `mobHeadHPMode` 仍是默认值 0（ALL）时，
     * 翻译为 {@code MOB_HP_MODE_OFF}，保留用户"关闭"的意图。
     * 旧 JSON 里 `showMobHeadHP=true` 保持默认 ALL，不动。
     */
    private void migrate(JsonObject settings) {
        statsColumns = Math.clamp(statsColumns, 1, MAX_STATS_COLUMNS);
        enemyBarLength = Math.clamp(enemyBarLength, MIN_ENEMY_BAR_LENGTH, MAX_ENEMY_BAR_LENGTH);
        enemyBarOpacity = Math.clamp(enemyBarOpacity, 10, 100);
        enemyTextOpacity = Math.clamp(enemyTextOpacity, 10, 100);
        playerBarOpacity = Math.clamp(playerBarOpacity, 10, 100);
        playerTextOpacity = Math.clamp(playerTextOpacity, 10, 100);
        statsOpacity = Math.clamp(statsOpacity, 20, 100);
        shieldOpacity = Math.clamp(shieldOpacity, 10, 100);
        shieldSize = Math.clamp(shieldSize, MIN_SHIELD_SIZE, MAX_SHIELD_SIZE);
        enemyHeadDistance = Math.clamp(enemyHeadDistance, 4, 200);
        playerHeadDistance = Math.clamp(playerHeadDistance, 4, 200);
        teammateBarHalfWidth = Math.clamp(teammateBarHalfWidth, MIN_TEAMMATE_BAR_LENGTH / 2, MAX_TEAMMATE_BAR_LENGTH / 2);
        shieldX = Float.isFinite(shieldX) ? Math.clamp(shieldX, 0, 1) : DEFAULT_SHIELD_X;
        shieldY = Float.isFinite(shieldY) && shieldY >= 0 ? Math.clamp(shieldY, 0, 1) : DEFAULT_SHIELD_Y;
        arrowPreviewX = normalizedArrowPosition(arrowPreviewX);
        arrowPreviewY = normalizedArrowPosition(arrowPreviewY);
        titleOffsetX = Math.clamp(titleOffsetX, -TITLE_OFFSET_LIMIT, TITLE_OFFSET_LIMIT);
        titleOffsetY = Math.clamp(titleOffsetY, -TITLE_OFFSET_LIMIT, TITLE_OFFSET_LIMIT);
        // 旧配置让两行一起移动，首次读取时沿用该位置。
        subtitleOffsetX = settings.has("subtitleOffsetX")
                ? Math.clamp(subtitleOffsetX, -TITLE_OFFSET_LIMIT, TITLE_OFFSET_LIMIT) : titleOffsetX;
        subtitleOffsetY = settings.has("subtitleOffsetY")
                ? Math.clamp(subtitleOffsetY, -TITLE_OFFSET_LIMIT, TITLE_OFFSET_LIMIT) : titleOffsetY;
        titleSize = Math.clamp(titleSize, MIN_TITLE_SIZE, MAX_TITLE_SIZE);
        subtitleSize = Math.clamp(subtitleSize, MIN_TITLE_SIZE, MAX_TITLE_SIZE);
        titleOpacity = sanitizedTitleOpacity(titleOpacity);
        subtitleOpacity = sanitizedTitleOpacity(subtitleOpacity);
        if (hiddenStatRows == null) hiddenStatRows = new java.util.HashSet<>();
        hiddenStatRows.remove(null);
        var oldStatsVisibility = settings.get("statsVisibility");
        if (oldStatsVisibility != null && oldStatsVisibility.isJsonPrimitive()
                && oldStatsVisibility.getAsJsonPrimitive().isNumber()) {
            int oldMode = oldStatsVisibility.getAsInt();
            if (!settings.has("showStatsOutsideInventory")) showStatsOutsideInventory = oldMode == 0;
            if (!settings.has("showStatsInInventory")) showStatsInInventory = oldMode == 1;
        }
        if (!showMobHeadHP && mobHeadHPMode == MOB_HP_MODE_ALL) {
            mobHeadHPMode = MOB_HP_MODE_OFF;
        }
        if (mobHeadHPMode < MOB_HP_MODE_ALL || mobHeadHPMode > MOB_HP_MODE_OFF) {
            mobHeadHPMode = MOB_HP_MODE_ALL;
        }
        // v7.1.2 · embeddedHudMode 范围校验（防手改 JSON 出非法值导致循环按钮 (x+1)%4 异常）
        if (embeddedHudMode < EMBED_OFF || embeddedHudMode > EMBED_BOTH) {
            embeddedHudMode = EMBED_ONLY_STAGE;
        }
    }

    private static float normalizedArrowPosition(float value) {
        return Float.isFinite(value) && value >= 0 ? Math.min(value, 1f) : -1f;
    }

    private static double sanitizedTitleOpacity(double value) {
        return Double.isFinite(value) ? Math.clamp(value, 0.10, 1.0) : 1.0;
    }

    public int titleOffsetForWidth(int width, boolean subtitle) {
        return Math.round((subtitle ? subtitleOffsetX : titleOffsetX) * (float) width / TITLE_REFERENCE_WIDTH);
    }

    public int titleOffsetForHeight(int height, boolean subtitle) {
        return Math.round((subtitle ? subtitleOffsetY : titleOffsetY) * (float) height / TITLE_REFERENCE_HEIGHT);
    }

    public boolean isMobHeadHPEnabled() { return mobHeadHPMode != MOB_HP_MODE_OFF; }
    public boolean isMobHeadHPNearestOnly() { return mobHeadHPMode == MOB_HP_MODE_NEAREST; }
}
