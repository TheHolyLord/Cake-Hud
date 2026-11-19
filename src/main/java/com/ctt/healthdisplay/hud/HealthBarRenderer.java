package com.ctt.healthdisplay.hud;

import com.ctt.healthdisplay.client.ClientDamageProbe;
import com.ctt.healthdisplay.client.ItemCategoryProbe;
import com.ctt.healthdisplay.config.ModConfig;
import com.ctt.healthdisplay.health.HealthData;
import com.ctt.healthdisplay.health.StatsData;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

import java.util.UUID;

public class HealthBarRenderer {

    private static final Identifier TOWER_TOKEN_TEXTURE = Identifier.fromNamespaceAndPath("ctt-health-display", "textures/custom/tower_token.png");
    private static final int COIN_ICON_SIZE = 9;

    // 果冻斯旺动量图标，直接复用地图资源包里 Swan Jelly 的 "air_dash" 贴图（16x16 红色双箭头）。
    // 与 Coins 图标渲染规模保持一致：屏幕上画成 9x9，匹配 8px 文字行基线。
    // 纹理本身保持原色（不使用 setShaderColor 染色），仅文字采用粉色 light_purple。
    private static final Identifier VELOCITY_ICON_TEXTURE = Identifier.fromNamespaceAndPath("ctt-health-display", "textures/custom/velocity_icon.png");
    private static final int VELOCITY_ICON_SIZE = 9;
    private static final int VELOCITY_ICON_SRC = 16;

    private static final int BAR_WIDTH = 81;
    private static final int BAR_HEIGHT = 5;

    private static final int MATE_BAR_WIDTH = 80;
    private static final int MATE_BAR_HEIGHT = 7;
    private static final int HEAD_SIZE = 8;
    private static final int MATE_ENTRY_SPACING = 20;
    private static final int H_ENTRY_WIDTH = 100;
    // v6.6 · M3 嵌入式 HUD：每个 stats 行（关: / 局:）在 bar 下方占 9px（8 字号 + 1 padding）。
    private static final int STATS_ROW_HEIGHT = 9;

    private static final int HP_FULL = 0xFFE84040;
    private static final int HP_HIGH = 0xFFD83030;
    private static final int HP_MID  = 0xFFCC6620;
    private static final int HP_LOW  = 0xFFAA1515;

    private static final int MANA_FULL = 0xFF3090DD;
    private static final int MANA_HIGH = 0xFF2878C0;
    private static final int MANA_MID  = 0xFF2060A0;
    private static final int MANA_LOW  = 0xFF184880;

    // v8.6.1 · Stamina 条配色：用户拍板 #00ff17（亮绿），全程纯色不做渐变。
    private static final int STAMINA_COLOR = 0xFF00FF17;

    // v8.6.2 · Joey 鲜血统一配色 #9c2727（用户拍板）：条体纯色、顶栏文字同色、图标 💧。
    // 主 HP 条是亮红 (E84040)；这里特意压暗到暗红，一眼和 HP 区分开。
    private static final int BLOOD_COLOR = 0xFF9C2727;

    // v8.5.2：全部改完全不透明（旧 0xA0 边框 / 0x50 空槽 / 0xB0~0xC0 心层会透出后方方块）。
    // 分层顺序仍是 红→魂→黑→蓝，后画的不透明层直接覆盖先画的，不再靠半透明混色。
    private static final int BAR_BORDER = 0xFF000000;
    private static final int EMPTY_FILL = 0xFF181818;

    private static final int COLOR_RED_HEART     = 0xFFE84040;
    // v8.7.2 · 碎心血条颜色：用户指定 #881B1B。
    private static final int COLOR_CRACKED_HEART = 0xFF881B1B;
    // 粉心只显示在血条左侧数量处，不绘制独立血条；颜色与数据包 light_purple 一致。
    private static final int COLOR_PINK_HEART = 0xFFFF55FF;
    // 数据包 view_stats.mcfunction 使用的碎心图标字形（U+E026）。直接复用该字形，
    // 确保 HUD 图标与属性栏中显示的碎心图案一致，而不是另画一个近似图标。
    private static final String CRACKED_HEART_GLYPH = "\uE026";
    private static final int CRACKED_INDICATOR_GAP = 4;
    private static final int HEART_INDICATOR_GAP = 2;
    private static final int COLOR_SOUL_HEART    = 0xFFDDCC22;
    private static final int COLOR_BLACK_HEART   = 0xFF550088;
    private static final int COLOR_BLUE_HEART    = 0xFF4488EE;

    private static final int TEXT_WHITE  = 0xFFFFFFFF;
    private static final int TEXT_RED    = 0xFFFF6666;
    private static final int TEXT_YELLOW = 0xFFFFDD44;
    private static final int TEXT_GREEN  = 0xFF66FF66;
    private static final int TEXT_AQUA   = 0xFF55DDFF;
    // v5.3.3：自己在队友面板里的名字色，金色 (§6 gold)，和真队友（白）拉开区分。
    private static final int TEXT_GOLD   = 0xFFFFAA00;
    // 果冻斯旺 (ClassPassive=14) 动量顶行文字颜色。取 bossbar 原生色 light_purple (#FF55FF)，与 HUD 语义同步。
    private static final int TEXT_PINK   = 0xFFFF55FF;
    private static final int ICON_HEART  = 0xFFFF4444;

    // v6.6 · M3 嵌入式 HUD 配色（设计 §5.3 · 图标固定色，数字按身份变色）
    private static final int STATS_PREFIX_GREY = 0xFFAAAAAA; // "关:" / "局:" 前缀
    private static final int STATS_DEALT_ICON  = 0xFFFFFFFF; // ⚔ 白
    private static final int STATS_TAKEN_ICON  = 0xFFCCCCCC; // ⛨ 灰白
    private static final int STATS_KILL_ICON   = 0xFFFF8080; // ☠ 淡红
    private static final int STATS_OFFLINE     = 0x80AAAAAA; // 离线整行 50% 灰

    private static long lastFlashTime = 0;
    private static boolean flashOn = false;

    private static StatsData currentStatsData;

    public static void render(GuiGraphicsExtractor context, DeltaTracker tickCounter, HealthData data, StatsData statsData) {
        currentStatsData = statsData;
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        if (client.gui.hud.isHidden()) return;

        data.update();
        if (!data.isAvailable()) return;

        int screenWidth = client.getWindow().getGuiScaledWidth();
        int screenHeight = client.getWindow().getGuiScaledHeight();

        int leftX = screenWidth / 2 - 91;
        int rightX = screenWidth / 2 + 10;
        int barY = screenHeight - 39;

        if (data.isPersonalAvailable()) {
            renderHealthBar(context, client.font, data, leftX, barY);

            // v8.6.2 · 右侧条统一规则（用户拍板）：
            //   · 手持"耗体力武器"（Melee / Dagger 分类，或特殊武器龙之重剑 Dragonspire）
            //     且体力数据可得 → 画绿色 Stamina 条；
            //   · 否则法力可得 → 画蓝色法力条（SwanJelly 的法力值 = Magic 物品可用次数，
            //     由 actionbar "3/3" 通道提供，见 HealthData）；
            //   · 纯客户端拿不到法力时 SwanJelly 退回体力条、
            //     其余职业退回旧鲜血条，保证"至少有一条、数值不骗人"。
            boolean staminaWeapon = ItemCategoryProbe.holdingStaminaWeapon(client);
            boolean magicItem = ItemCategoryProbe.holdingMagic(client);
            byte knightMode = com.ctt.healthdisplay.client.ClientMobHealthCache.knightResourceMode();
            boolean serverKnightMode = com.ctt.healthdisplay.client.ClientMobHealthCache.isFresh()
                    && knightMode != com.ctt.healthdisplay.network.MobHealthPayload.KNIGHT_MODE_NONE;

            // Knight 的职业与手持 Magic 判断完全由服务端完成：
            //   1 = 常驻体力；2 = 服务端确认主手为 Magic 后显示法力。
            // 纯客户端没有这个服务端模式时，才继续走下面原有的本地兼容逻辑。
            if (serverKnightMode && knightMode == com.ctt.healthdisplay.network.MobHealthPayload.KNIGHT_MODE_STAMINA) {
                if (data.hasStamina()) {
                    renderStaminaBar(context, client.font, data, rightX, barY);
                }
            } else if (serverKnightMode && knightMode == com.ctt.healthdisplay.network.MobHealthPayload.KNIGHT_MODE_MANA) {
                if (data.hasMana()) {
                    renderManaBar(context, client.font, data, rightX, barY);
                }
            } else if (data.hasVelocity && magicItem && com.ctt.healthdisplay.client.ActionBarCapture.hasFreshSpellUses()) {
                // SwanJelly 手持 Magic 时仍保留原有的物品可用次数显示。
                renderMagicUsesBar(context, client.font, data, rightX, barY);
            } else if (data.hasVelocity && data.hasStamina()) {
                renderStaminaBar(context, client.font, data, rightX, barY);
            } else if (staminaWeapon && data.hasStamina()) {
                renderStaminaBar(context, client.font, data, rightX, barY);
            } else if (data.hasMana()) {
                renderManaBar(context, client.font, data, rightX, barY);
            } else if (data.hasStamina()) {
                renderStaminaBar(context, client.font, data, rightX, barY);
            } else if (data.hasBlood) {
                renderBloodBar(context, client.font, data, rightX, barY);
            }
        }

        if (!data.teammates.isEmpty()) {
            renderTeammates(context, client.font, data, client, screenWidth, screenHeight);
        }
    }

    // ── Main health bar ──

    private static void renderHealthBar(GuiGraphicsExtractor context, Font textRenderer, HealthData data, int x, int y) {
        // 分母分档（v5.1.12 起）：maxHP ≤ 50 锁 50；50 < maxHP ≤ 100 锁 100；maxHP > 100 用真实值。
        // 低 maxHP 时让血条更"灵敏"（每点血都占更长一截像素）；≤ 0 / 负数情况也走最低档 50，避免除零且显示合理。
        // 红/灵魂/黑/蓝四层彩色条共用同一个 effectiveMax（见 drawLayeredBar）。
        int effectiveMax = computeEffectiveMax(data.maxHP);

        context.fill(x - 1, y - 1, x + BAR_WIDTH + 1, y + BAR_HEIGHT + 1, BAR_BORDER);
        context.fill(x, y, x + BAR_WIDTH, y + BAR_HEIGHT, EMPTY_FILL);

        if (currentStatsData != null && currentStatsData.hasHeartData()) {
            drawLayeredBar(context, x, y, effectiveMax,
                    data.allHearts,
                    data.crackedHearts,
                    currentStatsData.getSoulHearts(),
                    currentStatsData.getBlackHearts(),
                    currentStatsData.getBlueHearts());
        } else {
            float pct = Math.min(100f, (float) data.allHearts / effectiveMax * 100f);
            int fillPx = Math.max(0, Math.min(BAR_WIDTH, Math.round(pct / 100f * BAR_WIDTH)));
            if (fillPx > 0) {
                int pctInt = Math.round(pct);
                int color = getHealthColor(pctInt);
                context.fill(x, y, x + fillPx, y + BAR_HEIGHT, color);
                int highlight = (color & 0x00FFFFFF) | 0x30000000;
                context.fill(x, y, x + fillPx, y + 1, highlight);
            }
            // 没有完整心型数据时仍显示碎心层，避免纯客户端通道下碎心只改数字、不改血条颜色。
            drawCrackedLayer(context, x, y, effectiveMax, data.crackedHearts);
        }

        drawHealthText(context, textRenderer, data, x, y);
    }

    private static void drawLayeredBar(GuiGraphicsExtractor context, int x, int y, int maxHP,
                                       int allHearts, int cracked, int soul, int black, int blue) {
        // 覆盖顺序保持为：红心 → 碎心 → 魂心 → 黑心 → 蓝心；后画的层覆盖前画的层。
        drawBarLayer(context, x, y, Math.min(100f, (float) allHearts / maxHP * 100f), COLOR_RED_HEART);
        drawCrackedLayer(context, x, y, maxHP, cracked);
        drawBarLayer(context, x, y, Math.min(100f, (float) soul / maxHP * 100f), COLOR_SOUL_HEART);
        drawBarLayer(context, x, y, Math.min(100f, (float) black / maxHP * 100f), COLOR_BLACK_HEART);
        drawBarLayer(context, x, y, Math.min(100f, (float) blue / maxHP * 100f), COLOR_BLUE_HEART);
    }

    private static void drawCrackedLayer(GuiGraphicsExtractor context, int x, int y, int maxHP, int cracked) {
        if (maxHP <= 0 || cracked <= 0) return;
        drawBarLayer(context, x, y,
                Math.min(100f, (float) cracked / maxHP * 100f), COLOR_CRACKED_HEART);
    }

    private static void drawBarLayer(GuiGraphicsExtractor context, int x, int y, float pct, int color) {
        if (pct <= 0) return;
        int fillPx = Math.max(0, Math.min(BAR_WIDTH, Math.round(pct / 100f * BAR_WIDTH)));
        if (fillPx > 0) {
            context.fill(x, y, x + fillPx, y + BAR_HEIGHT, color);
        }
    }

    private static void renderManaBar(GuiGraphicsExtractor context, Font textRenderer, HealthData data, int x, int y) {
        float manaPct = data.getDisplayMana() / Math.max(1, data.getDisplayMaxMana()) * 100f;
        drawBar(context, x, y, BAR_WIDTH, BAR_HEIGHT, manaPct, data.getManaPercent(), BarKind.MANA);
        drawManaText(context, textRenderer, data, x, y);
        // v8.6.1 · 条内数值从 drawManaText 挪到条渲染器自己画：同一段顶栏文字
        // （❤lives / 🪙coins / ✦段）现在会被法力条、体力条、鲜血条共用，
        // 条内数字必须跟着"当前画的是哪种条"走。
        drawBarLabel(context, textRenderer, data.mana + "/" + data.maxMana, x, y);
    }

    /**
     * v8.6.1 · Stamina 条（纯色 #00ff17，用户拍板）。手持近战物品时替换法力条，
     * 数据来源：bossbar (Stamina x/y) 段（SwanJelly）或服务端 payload（其它职业 /
     * SwanJelly 法力通道）。顶栏文字段照常由 drawManaText 画（Joey 画鲜血、
     * SwanJelly 画动量、其它职业画法力）。
     */
    private static void renderStaminaBar(GuiGraphicsExtractor context, Font textRenderer, HealthData data, int x, int y) {
        float pct = data.maxStamina > 0 ? (float) data.stamina / data.maxStamina * 100f : 0f;
        drawBar(context, x, y, BAR_WIDTH, BAR_HEIGHT, pct, data.getStaminaPercent(), BarKind.STAMINA);
        drawManaText(context, textRenderer, data, x, y);
        drawBarLabel(context, textRenderer, data.stamina + "/" + data.maxStamina, x, y);
    }

    /** SwanJelly 手持 Magic 时显示物品可用次数，颜色沿用法力条蓝色。 */
    private static void renderMagicUsesBar(GuiGraphicsExtractor context, Font textRenderer, HealthData data, int x, int y) {
        int uses = com.ctt.healthdisplay.client.ActionBarCapture.spellUses();
        int maxUses = com.ctt.healthdisplay.client.ActionBarCapture.spellUsesMax();
        float pct = maxUses > 0 ? (float) uses / maxUses * 100f : 0f;
        drawBar(context, x, y, BAR_WIDTH, BAR_HEIGHT, pct, Math.round(pct), BarKind.MANA);
        drawManaText(context, textRenderer, data, x, y);
        drawBarLabel(context, textRenderer, uses + "/" + maxUses, x, y);
    }

    // Joey 专属：下方条渲染为鲜血条，上方文字行仍然走 drawManaText（Lives + Coins + ✦Mana/MaxMana），
    // 所以玩家仍然能在同一个位置看到法力值，只是下方槽位从"蓝条 + 蓝数字"换成了"深红条 + 红色 Blood 数字"。
    private static void renderBloodBar(GuiGraphicsExtractor context, Font textRenderer, HealthData data, int x, int y) {
        int denom = Math.max(1, data.maxBlood);
        float bloodPct = (float) data.blood / denom * 100f;
        int rawPct = Math.max(0, Math.min(100, Math.round(bloodPct)));
        drawBar(context, x, y, BAR_WIDTH, BAR_HEIGHT, bloodPct, rawPct, BarKind.BLOOD);

        // 条内数值：直接写 blood/maxBlood，白字描边 + 黑阴影，排版与法力条保持一致。
        drawManaText(context, textRenderer, data, x, y);
        drawBarLabel(context, textRenderer, data.blood + "/" + data.maxBlood, x, y);
    }

    /** 条内居中数值（白字四向黑描边），法力 / 体力 / 鲜血条共用。 */
    private static void drawBarLabel(GuiGraphicsExtractor context, Font textRenderer, String label, int x, int y) {
        int labelW = textRenderer.width(label);
        drawOutlinedText(context, textRenderer, label, x + (BAR_WIDTH - labelW) / 2, y + (BAR_HEIGHT - 8) / 2, TEXT_WHITE);
    }

    // ── Teammate panel ──

    private static void renderTeammates(GuiGraphicsExtractor context, Font textRenderer, HealthData data, Minecraft client, int screenWidth, int screenHeight) {
        ModConfig config = ModConfig.INSTANCE;
        int x = Math.max(0, Math.min(screenWidth - MATE_BAR_WIDTH, (int) (config.teammateX * screenWidth)));
        int y = Math.max(0, Math.min(screenHeight - MATE_ENTRY_SPACING, (int) (config.teammateY * screenHeight)));

        // v7.0.0 · P0 客户端探针：HUD 顶部聚合行（⚔ 全局 · ⚔ 当前关 ⚡ 5sDPS/s）。
        // 依据 CLIENT_SIDE_STATS_PROPOSAL §X "P0 客户端探针骨架"。
        // 仅在垂直布局 + 配置开关 + 探针有数据时绘制；绘制后 y 下移 STATS_ROW_HEIGHT 让队友行不重叠。
        if (config.clientDamageHudHeader && !config.horizontalLayout
                && ClientDamageProbe.INSTANCE.hasAnyData()) {
            drawCdpHeaderRow(context, textRenderer, x, y);
            y += STATS_ROW_HEIGHT;
        }

        // v6.6 · M3：水平布局下 stats 行被强制隐藏（横排队友本来就挤）。
        boolean statsAllowed = !config.horizontalLayout && config.embeddedHudMode != ModConfig.EMBED_OFF;

        for (HealthData.TeammateData mate : data.teammates) {
            int pct = mate.getPercent();

            // Row 1: Head + Name + Lives hearts
            drawPlayerHead(context, client, mate.name, x, y);

            int textX = x + HEAD_SIZE + 2;
            context.text(textRenderer, mate.name, textX, y, mate.isSelf ? TEXT_GOLD : TEXT_WHITE, true);

            {
                String livesStr = "\u2764" + mate.lives;
                int livesW = textRenderer.width(livesStr);
                context.text(textRenderer, livesStr, x + MATE_BAR_WIDTH - livesW, y, TEXT_GREEN, true);
            }

            // Row 2: Health bar with HP text inside
            int barY = y + HEAD_SIZE + 1;
            // v8.4.0 · 服务端 TeamHeartsBroadcaster 推过四色心 + 客户端开关打开 → 走 layered，
            // 与玩家自己主血条 drawLayeredBar 视觉一致；否则旧 OVERFLOW_COLORS 单色多槽。
            if (ModConfig.INSTANCE.showTeammateLayeredHearts && mate.hasLayeredHearts()) {
                drawTeammateBarLayered(context, x, barY, MATE_BAR_WIDTH, MATE_BAR_HEIGHT, mate);
            } else {
                drawTeammateBar(context, x, barY, MATE_BAR_WIDTH, MATE_BAR_HEIGHT, mate.hp, mate.maxHP);
            }

            String hpStr = mate.maxHP > 0 ? mate.hp + "/" + mate.maxHP : String.valueOf(mate.hp);
            int hpW = textRenderer.width(hpStr);
            drawOutlinedText(context, textRenderer, hpStr,
                    x + (MATE_BAR_WIDTH - hpW) / 2,
                    barY + (MATE_BAR_HEIGHT - 8) / 2,
                    TEXT_WHITE);

            // Row 3 / 4: v6.6 · M3 嵌入式 HUD stats 行
            int statsRows = 0;
            if (statsAllowed) {
                UUID uuid = resolveUuid(client, mate.name);
                boolean offline = (client.getConnection() == null
                        || client.getConnection().getPlayerInfo(mate.name) == null);
                int rowY = barY + MATE_BAR_HEIGHT + 1;

                // v6.6.6 · 自动隐藏空行：配置允许该行 + 该行有数据（dealt/taken/kills/assists 任一非 0）才画。
                // 大厅 / 第一关刚开打但还没造成伤害 / 上一关被清空（重置 stats）等场景下，
                // 自动收回行高，队友面板回到只有名字 + HP 条的紧凑形态；其余场景按配置原样显示。
                // v6.6.6 · 自动隐藏空行：配置允许该行 + 该行有数据（dealt/taken/kills/assists 任一非 0）才画。
                // 大厅 / 第一关刚开打但还没造成伤害 / 上一关被清空（重置 stats）等场景下，
                // 自动收回行高，队友面板回到只有名字 + HP 条的紧凑形态；其余场景按配置原样显示。
                // v6.6.7 · 去掉 "关:" / "局:" 前缀（用户决策：图标已足够区分语义，节省横向空间）。
                int mode = config.embeddedHudMode;
                if (mode == ModConfig.EMBED_ONLY_STAGE || mode == ModConfig.EMBED_BOTH) {
                    TeammateStatsLine.Line line = TeammateStatsLine.ofStage(uuid);
                    if (line.hasData()) {
                        // v6.6.7 · 关行末尾追加 · <dps>/s（DPS=0 也画占位，保持视觉宽度稳定）
                        drawStatsRow(context, textRenderer, "", line, x, rowY, mate.isSelf, offline, true);
                        rowY += STATS_ROW_HEIGHT;
                        statsRows++;
                    }
                }
                if (mode == ModConfig.EMBED_ONLY_SESSION || mode == ModConfig.EMBED_BOTH) {
                    TeammateStatsLine.Line line = TeammateStatsLine.ofSession(uuid);
                    if (line.hasData()) {
                        drawStatsRow(context, textRenderer, "", line, x, rowY, mate.isSelf, offline, false);
                        statsRows++;
                    }
                }
            }

            if (config.horizontalLayout) {
                x += H_ENTRY_WIDTH;
            } else {
                y += MATE_ENTRY_SPACING + statsRows * STATS_ROW_HEIGHT;
            }
        }
    }

    /**
     * v6.6 · M3：通过玩家名字解析 UUID（HealthData.TeammateData 不含 UUID，用 PlayerInfo 反查）。
     * 玩家断线 / PlayerList 缺项时返回 null，stats 行会显示 0。
     */
    private static UUID resolveUuid(Minecraft client, String name) {
        if (client.getConnection() == null) return null;
        PlayerInfo entry = client.getConnection().getPlayerInfo(name);
        return entry == null ? null : entry.getProfile().id();
    }

    /**
     * v6.6 · M3：画一行 stats 行 {@code [⚔ 12.3k ⛨ 4.5k ☠ 7/3]}。
     *
     * <ul>
     *   <li>前缀（v6.6.7 默认空字符串；保留参数以便未来回归"关:/局:"标签）灰色</li>
     *   <li>图标 ⚔/⛨/☠ 固定色</li>
     *   <li>数字本体：自己 → 金；离线 → 半透明灰；普通队友 → 白</li>
     *   <li>行宽固定贴齐 {@link #MATE_BAR_WIDTH}，超长会被 {@code ⚔}/{@code ⛨}/{@code ☠} 自然换行截断
     *       （compact 数字格式确保 4-5 字符以内 → 实测 80px 足够容纳两段 4 位 + 一段 K/A）</li>
     *   <li>v6.6.7 · {@code showDps=true}（关行）末尾追加 {@code · <dps>/s}；DPS=0 也画占位
     *       保持视觉宽度稳定（用户拍板：方案 b）。session 行传 false 不画。</li>
     * </ul>
     */
    private static void drawStatsRow(GuiGraphicsExtractor context, Font textRenderer,
                                     String prefix, TeammateStatsLine.Line line,
                                     int x, int y, boolean isSelf, boolean offline,
                                     boolean showDps) {
        int valueColor = offline ? STATS_OFFLINE : (isSelf ? TEXT_GOLD : TEXT_WHITE);
        int prefixColor = offline ? STATS_OFFLINE : STATS_PREFIX_GREY;
        int dealtIcon   = offline ? STATS_OFFLINE : STATS_DEALT_ICON;
        int takenIcon   = offline ? STATS_OFFLINE : STATS_TAKEN_ICON;
        int killIcon    = offline ? STATS_OFFLINE : STATS_KILL_ICON;

        int curX = x;
        // 前缀（v6.6.7 起默认为空 → 跳过绘制和 2px 间距，节省横向空间）
        if (prefix != null && !prefix.isEmpty()) {
            context.text(textRenderer, prefix, curX, y, prefixColor, true);
            curX += textRenderer.width(prefix) + 2;
        }

        // ⚔ <dealt>
        String swordIcon = "\u2694";
        context.text(textRenderer, swordIcon, curX, y, dealtIcon, true);
        curX += textRenderer.width(swordIcon) + 1;
        String dealtStr = TeammateStatsLine.compact(line.dealt());
        context.text(textRenderer, dealtStr, curX, y, valueColor, true);
        curX += textRenderer.width(dealtStr) + 3;

        // ⛨ <taken>（用 U+26E8 黑十字盾，更通用的字体覆盖；与设计 §5.3 一致语义）
        String shieldIcon = "\u26E8";
        context.text(textRenderer, shieldIcon, curX, y, takenIcon, true);
        curX += textRenderer.width(shieldIcon) + 1;
        String takenStr = TeammateStatsLine.compact(line.taken());
        context.text(textRenderer, takenStr, curX, y, valueColor, true);
        curX += textRenderer.width(takenStr) + 3;

        // ☠ <K>/<A>
        String skullIcon = "\u2620";
        context.text(textRenderer, skullIcon, curX, y, killIcon, true);
        curX += textRenderer.width(skullIcon) + 1;
        String kaStr = line.kills() + "/" + line.assists();
        context.text(textRenderer, kaStr, curX, y, valueColor, true);
        curX += textRenderer.width(kaStr) + 3;

        // v6.6.7 · 关行末尾 · <dps>/s（最近 5 秒造成伤害量 / 5）
        // DPS=0 也画占位，保持视觉宽度稳定（与设计决策 b 一致）。
        if (showDps) {
            String sep = "\u00b7";  // 中点
            context.text(textRenderer, sep, curX, y, prefixColor, true);
            curX += textRenderer.width(sep) + 1;
            String dpsStr = TeammateStatsLine.compact(line.dps()) + "/s";
            context.text(textRenderer, dpsStr, curX, y, valueColor, true);
        }
    }

    /**
     * v7.0.0 · P0 客户端探针 HUD 顶部聚合行。
     * <p>v7.1.0 · 布局：{@code ⚔ <本层伤害> ☠ <本层击杀> ⚡ <5sDPS>/s}。
     * <ul>
     *   <li>⚔ 本层伤害：{@link ClientDamageProbe#getStageTotal()}</li>
     *   <li>☠ 本层击杀：{@link ClientDamageProbe#getStageKills()}（始终显示）</li>
     *   <li>⚡ 5s DPS：{@link ClientDamageProbe#getRecent5sDps()}</li>
     * </ul>
     * 全局数字仍可在 K 表 / 面板查看。绘制配色与"特殊聚合行"语义对齐：⚔ 金、☠ 淡红、⚡ 黄。
     */
    private static void drawCdpHeaderRow(GuiGraphicsExtractor context, Font tr, int x, int y) {
        ClientDamageProbe probe = ClientDamageProbe.INSTANCE;
        String stageStr  = TeammateStatsLine.compact(probe.getStageTotal());
        String killStr   = TeammateStatsLine.compact(probe.getStageKills());
        String dpsStr    = TeammateStatsLine.compact(probe.getRecent5sDps()) + "/s";

        int cx = x;
        // ⚔ <本层伤害>
        String swordIcon = "\u2694";
        context.text(tr, swordIcon, cx, y, STATS_DEALT_ICON, true);
        cx += tr.width(swordIcon) + 1;
        context.text(tr, stageStr, cx, y, TEXT_GOLD, true);
        cx += tr.width(stageStr) + 2;

        // ☠ <本层击杀>
        String killIcon = "\u2620";
        context.text(tr, killIcon, cx, y, STATS_KILL_ICON, true);
        cx += tr.width(killIcon) + 1;
        context.text(tr, killStr, cx, y, TEXT_GOLD, true);
        cx += tr.width(killStr) + 2;

        // ⚡ <DPS>/s
        String boltIcon = "\u26A1";
        context.text(tr, boltIcon, cx, y, TEXT_YELLOW, true);
        cx += tr.width(boltIcon);
        context.text(tr, dpsStr, cx, y, TEXT_YELLOW, true);
    }

    private static void drawPlayerHead(GuiGraphicsExtractor context, Minecraft client, String playerName, int x, int y) {
        try {
            if (client.getConnection() == null) {
                drawFallbackHead(context, x, y);
                return;
            }
            PlayerInfo entry = client.getConnection().getPlayerInfo(playerName);
            if (entry == null) {
                drawFallbackHead(context, x, y);
                return;
            }

            PlayerSkin textures = entry.getSkin();
            Identifier texture = textures.body().texturePath();

            context.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 8.0f, 8.0f, HEAD_SIZE, HEAD_SIZE, 64, 64);
            context.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 40.0f, 8.0f, HEAD_SIZE, HEAD_SIZE, 64, 64);
        } catch (Exception e) {
            drawFallbackHead(context, x, y);
        }
    }

    private static void drawFallbackHead(GuiGraphicsExtractor context, int x, int y) {
        context.fill(x, y, x + HEAD_SIZE, y + HEAD_SIZE, 0xFF808080);
    }

    // ── Shared bar drawing ──

    private static void drawTeammateBar(GuiGraphicsExtractor context, int x, int y, int w, int h, int hp, int maxHP) {
        context.fill(x - 1, y - 1, x + w + 1, y + h + 1, BAR_BORDER);
        context.fill(x, y, x + w, y + h, EMPTY_FILL);

        if (hp <= 0 || maxHP <= 0) return;

        int topIdx = (hp - 1) / maxHP;
        int hpInTop = hp - topIdx * maxHP;
        int topFillPx = Math.max(1, Math.min(w, Math.round((float) hpInTop / maxHP * w)));

        if (topIdx > 0 && topFillPx < w) {
            int bgColorIdx = Math.min(topIdx - 1, OVERFLOW_COLORS.length - 1);
            context.fill(x + topFillPx, y, x + w, y + h, OVERFLOW_COLORS[bgColorIdx]);
        }

        int topColorIdx = Math.min(topIdx, OVERFLOW_COLORS.length - 1);
        context.fill(x, y, x + topFillPx, y + h, OVERFLOW_COLORS[topColorIdx]);
    }

    /**
     * v8.4.0 · 队友 4 色心叠加版血条（与玩家自己主血条 {@link #drawLayeredBar} 视觉一致）。
     *
     * <p>仅在 {@link com.ctt.healthdisplay.client.ClientTeamHeartsCache} fresh 且服务端
     * 推过该玩家的 soul/black/blue 之一非零时调用。无 mod 服务端 / 队友未参与本局时
     * {@code mate.hasLayeredHearts()} 返回 false，渲染入口会退回 {@link #drawTeammateBar}
     * 老路径。
     *
     * <p>层级顺序 = {@code drawLayeredBar} 同款 = datapack {@code view_stats.mcfunction} 同款：
     * Red → Soul → Black → Blue。每层独立 {@code min(100%, value/maxHP)} 填充。
     */
    private static void drawTeammateBarLayered(GuiGraphicsExtractor context, int x, int y, int w, int h,
                                               HealthData.TeammateData mate) {
        context.fill(x - 1, y - 1, x + w + 1, y + h + 1, BAR_BORDER);
        context.fill(x, y, x + w, y + h, EMPTY_FILL);

        if (mate.maxHP <= 0) return;

        // mate.hp 来自 bossbar（vanilla 实时），等价于 RedHearts 当前层 —— 不用 mate.redHeartsServer。
        // mate.redHeartsServer 仅在 hasLayeredHearts() 判定 / 未来扩展时用得到。
        drawBarLayer(context, x, y, w, h, Math.min(100f, (float) mate.hp          / mate.maxHP * 100f), COLOR_RED_HEART);
        drawBarLayer(context, x, y, w, h, Math.min(100f, (float) mate.soulHearts  / mate.maxHP * 100f), COLOR_SOUL_HEART);
        drawBarLayer(context, x, y, w, h, Math.min(100f, (float) mate.blackHearts / mate.maxHP * 100f), COLOR_BLACK_HEART);
        drawBarLayer(context, x, y, w, h, Math.min(100f, (float) mate.blueHearts  / mate.maxHP * 100f), COLOR_BLUE_HEART);
    }

    /** v8.4.0 · 通用尺寸版本（替代旧 {@link #drawBarLayer(GuiGraphicsExtractor, int, int, float, int)}
     *  的硬编码 BAR_WIDTH/BAR_HEIGHT 写死），让 layered 渲染可重用于队友条任意宽高。 */
    private static void drawBarLayer(GuiGraphicsExtractor context, int x, int y, int w, int h, float pct, int color) {
        if (pct <= 0) return;
        int fillPx = Math.max(0, Math.min(w, Math.round(pct / 100f * w)));
        if (fillPx > 0) {
            context.fill(x, y, x + fillPx, y + h, color);
        }
    }

    private enum BarKind { HEALTH, MANA, BLOOD, STAMINA }

    private static void drawBar(GuiGraphicsExtractor context, int x, int y, int w, int h, float displayPct, int rawPct, BarKind kind) {
        context.fill(x - 1, y - 1, x + w + 1, y + h + 1, BAR_BORDER);
        context.fill(x, y, x + w, y + h, EMPTY_FILL);

        int fillPx = Math.max(0, Math.min(w, Math.round(displayPct / 100f * w)));
        if (fillPx > 0) {
            int barColor = switch (kind) {
                case HEALTH -> getHealthColor(rawPct);
                case MANA -> getManaColor(rawPct);
                case BLOOD -> getBloodColor(rawPct);
                case STAMINA -> STAMINA_COLOR; // v8.6.1 · #00ff17 纯色，不做百分比分档
            };
            context.fill(x, y, x + fillPx, y + h, barColor);
            int highlight = (barColor & 0x00FFFFFF) | 0x30000000;
            context.fill(x, y, x + fillPx, y + 1, highlight);
        }
    }

    // ── Text overlays ──

    private static void drawHealthText(GuiGraphicsExtractor context, Font textRenderer, HealthData data, int barX, int barY) {
        int pct = data.getEffectivePercent();
        int textY = barY - 10;

        // 粉心和碎心共用同一个左侧槽位：CrackedHearts 正数显示碎心，负数显示粉心。
        drawHeartIndicators(context, textRenderer, data, barX, barY);

        if (currentStatsData != null && currentStatsData.hasHeartData()) {
            drawMultiHeartText(context, textRenderer, data, barX, textY);
        } else {
            int heartColor = pct <= 25 ? TEXT_RED : pct <= 50 ? TEXT_YELLOW : ICON_HEART;
            int textColor;
            if (pct <= 25) {
                long now = Util.getMillis();
                if (now - lastFlashTime > 400) { flashOn = !flashOn; lastFlashTime = now; }
                textColor = flashOn ? 0xFFFF2222 : TEXT_RED;
            } else if (pct <= 50) {
                textColor = TEXT_YELLOW;
            } else {
                textColor = TEXT_WHITE;
            }

            String heart = "\u2764";
            context.text(textRenderer, heart, barX, textY, heartColor, true);
            int heartW = textRenderer.width(heart);

            if (data.hasRawValues()) {
                String hpValue = " " + data.allHearts + "/" + data.maxHP;
                context.text(textRenderer, hpValue, barX + heartW, textY, textColor, true);
            } else {
                context.text(textRenderer, " " + pct + "%", barX + heartW, textY, textColor, true);
            }
        }

        if (data.hasRawValues()) {
            // 碎心存在时：主分母显示碎心后的有效上限，括号保留原始最大血上限。
            // 例如 200 上限 + 50 碎心：从旧版 200/200 (150) 改为 200/150 (200)。
            String barLabel = data.allHearts + "/" + displayMaxHealth(data) + crackedOriginalMaxSuffix(data);
            int labelW = textRenderer.width(barLabel);
            drawOutlinedText(context, textRenderer, barLabel, barX + (BAR_WIDTH - labelW) / 2, barY + (BAR_HEIGHT - 8) / 2, TEXT_WHITE);
        }
    }

    private static void drawHeartIndicators(GuiGraphicsExtractor context, Font textRenderer,
                                            HealthData data, int barX, int barY) {
        // 两种心共用一个槽位，不能同时绘制；粉心优先只是负值状态的颜色切换。
        int amount = data.pinkHearts > 0 ? data.pinkHearts : data.crackedHearts;
        int color = data.pinkHearts > 0 ? COLOR_PINK_HEART : COLOR_CRACKED_HEART;
        drawHeartIndicator(context, textRenderer, amount,
                color, barX - CRACKED_INDICATOR_GAP, barY);
    }

    /** 在指定右边界绘制一组“数量 + 心图标”，返回该组左边界。 */
    private static int drawHeartIndicator(GuiGraphicsExtractor context, Font textRenderer,
                                           int amount, int color, int rightEdge, int barY) {
        if (amount <= 0) return rightEdge;
        String count = String.valueOf(amount);
        int countWidth = textRenderer.width(count);
        int glyphWidth = textRenderer.width(CRACKED_HEART_GLYPH);
        int totalWidth = countWidth + 1 + glyphWidth;
        int indicatorX = rightEdge - totalWidth;
        int indicatorY = barY + (BAR_HEIGHT - 8) / 2;
        drawOutlinedText(context, textRenderer, count, indicatorX, indicatorY, color);
        drawOutlinedText(context, textRenderer, CRACKED_HEART_GLYPH,
                indicatorX + countWidth + 1, indicatorY, color);
        return indicatorX;
    }

    private static int displayMaxHealth(HealthData data) {
        if (data.crackedHearts <= 0 || data.maxHP <= 0) return data.maxHP;
        return Math.max(0, data.maxHP - data.crackedHearts);
    }

    private static String crackedOriginalMaxSuffix(HealthData data) {
        if (data.crackedHearts <= 0 || data.maxHP <= 0) return "";
        return " (" + data.maxHP + ")";
    }

    private static void drawMultiHeartText(GuiGraphicsExtractor context, Font textRenderer, HealthData data, int barX, int textY) {
        String heart = "\u2764";
        int curX = barX;

        int red = currentStatsData.getRedHearts();
        int soul = currentStatsData.getSoulHearts();
        int black = currentStatsData.getBlackHearts();
        int blue = currentStatsData.getBlueHearts();

        curX = drawHeartEntry(context, textRenderer, String.valueOf(red), heart, 0xFFFF4444, curX, textY);
        if (soul > 0) {
            curX = drawHeartEntry(context, textRenderer, String.valueOf(soul), heart, 0xFFFFDD44, curX, textY);
        }
        if (black > 0) {
            curX = drawHeartEntry(context, textRenderer, String.valueOf(black), heart, 0xFF7722AA, curX, textY);
        }
        if (blue > 0) {
            curX = drawHeartEntry(context, textRenderer, String.valueOf(blue), heart, 0xFF5599FF, curX, textY);
        }
    }

    private static int drawHeartEntry(GuiGraphicsExtractor context, Font textRenderer,
                                      String value, String icon, int color, int x, int y) {
        context.text(textRenderer, value, x, y, color, true);
        int valW = textRenderer.width(value);
        context.text(textRenderer, icon, x + valW, y, color, true);
        int iconW = textRenderer.width(icon);
        return x + valW + iconW + 2;
    }

    private static final int[] OVERFLOW_COLORS = {
        0xFFE84040, // 1x red
        0xFFFF8800, // 2x orange
        0xFFFFDD00, // 3x yellow
        0xFF44CC44, // 4x green
        0xFF4488EE, // 5x blue
        0xFFAA44DD  // 6x+ purple
    };

    private static final int COLOR_COIN = 0xFFFFAA00;

    private static void drawManaText(GuiGraphicsExtractor context, Font textRenderer, HealthData data, int barX, int barY) {
        int textY = barY - 10;
        int rightEdge = barX + BAR_WIDTH;

        // Lives: left-aligned, always visible
        String livesIcon = "\u2764";
        context.text(textRenderer, livesIcon, barX, textY, TEXT_GREEN, true);
        int iconW = textRenderer.width(livesIcon);
        String livesVal = String.valueOf(data.lives);
        context.text(textRenderer, livesVal, barX + iconW, textY, TEXT_GREEN, true);
        int livesEndX = barX + iconW + textRenderer.width(livesVal);

        // 右上角：
        //   · 默认：`✦mana/maxMana`（AQUA，纯文本，✦ 图标以字符形式嵌在文本里）
        //   · 果冻斯旺 (hasVelocity)：左边一张 9x9 air_dash 贴图（原色不染色），右边粉色数值 `velocity/maxVelocity`
        // 双者都和左侧 `❤lives` 保持"图标 + 数值"的左右对称视觉，宽度由对应分支独立计算后整体右对齐。
        //
        // v8.5.9 · 大数字防重叠：数字超过 3~4 位时 "✦1234/5678" 的宽度会顶穿 81px 条宽，
        // 旧版仍把它右对齐到条尾 → 文字左端压到 ❤lives / 硬币段上（用户截图重叠）。
        // 新版先按"lives + 硬币段 + 右段"顺序排一遍，放不下就整体向右扩——右段起点
        // 越过条尾也照常画（HUD 条右侧是空白屏幕区），保证三段永远互不压字。
        String coinVal = String.valueOf(data.coins);
        int coinTotalW = COIN_ICON_SIZE + 1 + textRenderer.width(coinVal);
        int minGap = 3;

        int manaX;
        int rightTextW;
        if (data.hasVelocity) {
            String velText = data.velocity + "/" + data.maxVelocity;
            rightTextW = VELOCITY_ICON_SIZE + 1 + textRenderer.width(velText);
            manaX = rightEdge - rightTextW;
            int minX = livesEndX + minGap + coinTotalW + minGap;
            if (manaX < minX) manaX = minX;
            int iconX = manaX;
            context.blit(RenderPipelines.GUI_TEXTURED, VELOCITY_ICON_TEXTURE,
                    iconX, textY - 1, 0, 0,
                    VELOCITY_ICON_SIZE, VELOCITY_ICON_SIZE,
                    VELOCITY_ICON_SRC, VELOCITY_ICON_SRC,
                    VELOCITY_ICON_SRC, VELOCITY_ICON_SRC);
            context.text(textRenderer, velText, iconX + VELOCITY_ICON_SIZE + 1, textY, TEXT_PINK, true);
        } else if (data.hasBlood) {
            // v8.6.1 · Joey：右上角段不再写法力，改写当前鲜血 / 鲜血上限
            //（用户拍板："把图3中的法力值换成当前的 Blood/Max Blood"）。
            // v8.6.2 · 图标 ✦ → 💧（U+1F4A7），配色 #9c2727（用户拍板），数值不变。
            String rightText = "\uD83D\uDCA7" + data.blood + "/" + data.maxBlood;
            rightTextW = textRenderer.width(rightText);
            manaX = rightEdge - rightTextW;
            int minX = livesEndX + minGap + coinTotalW + minGap;
            if (manaX < minX) manaX = minX;
            context.text(textRenderer, rightText, manaX, textY, BLOOD_COLOR, true);
        } else {
            String rightText = "\u2726" + data.mana + "/" + data.maxMana;
            rightTextW = textRenderer.width(rightText);
            manaX = rightEdge - rightTextW;
            int minX = livesEndX + minGap + coinTotalW + minGap;
            if (manaX < minX) manaX = minX;
            context.text(textRenderer, rightText, manaX, textY, TEXT_AQUA, true);
        }

        // Coins: centered between lives and mana
        int valW = textRenderer.width(coinVal);
        int totalW = COIN_ICON_SIZE + 1 + valW;
        int coinX = (livesEndX + manaX - totalW) / 2;
        context.blit(RenderPipelines.GUI_TEXTURED, TOWER_TOKEN_TEXTURE,
                coinX, textY - 1, 0, 0, COIN_ICON_SIZE, COIN_ICON_SIZE, COIN_ICON_SIZE, COIN_ICON_SIZE);
        context.text(textRenderer, coinVal, coinX + COIN_ICON_SIZE + 1, textY, COLOR_COIN, true);

        // v8.6.1 · 条内数值改由各条渲染器（renderManaBar / renderStaminaBar /
        // renderBloodBar）自己画，这里不再画 mana 标签——否则体力条上会叠出法力数字。
    }

    // ── Utility ──

    private static void drawOutlinedText(GuiGraphicsExtractor context, Font textRenderer, String text, int x, int y, int color) {
        int shadow = 0xFF000000;
        context.text(textRenderer, text, x + 1, y, shadow, false);
        context.text(textRenderer, text, x - 1, y, shadow, false);
        context.text(textRenderer, text, x, y + 1, shadow, false);
        context.text(textRenderer, text, x, y - 1, shadow, false);
        context.text(textRenderer, text, x, y, color, false);
    }

    private static int computeEffectiveMax(int maxHP) {
        if (maxHP <= 50) return 50;
        if (maxHP <= 100) return 100;
        return maxHP;
    }

    private static int getHealthColor(int percent) {
        if (percent <= 25) return HP_LOW;
        if (percent <= 50) return interpolateColor(HP_LOW, HP_MID, (percent - 25) / 25f);
        if (percent <= 75) return interpolateColor(HP_MID, HP_HIGH, (percent - 50) / 25f);
        return interpolateColor(HP_HIGH, HP_FULL, (percent - 75) / 25f);
    }

    private static int getManaColor(int percent) {
        if (percent <= 25) return MANA_LOW;
        if (percent <= 50) return interpolateColor(MANA_LOW, MANA_MID, (percent - 25) / 25f);
        if (percent <= 75) return interpolateColor(MANA_MID, MANA_HIGH, (percent - 50) / 25f);
        return interpolateColor(MANA_HIGH, MANA_FULL, (percent - 75) / 25f);
    }

    private static int getBloodColor(int percent) {
        // v8.6.2 · 用户拍板：鲜血条纯色 #9c2727，不再按百分比分档渐变。
        return BLOOD_COLOR;
    }

    private static int interpolateColor(int colorA, int colorB, float t) {
        t = Math.max(0, Math.min(1, t));
        int rA = (colorA >> 16) & 0xFF, gA = (colorA >> 8) & 0xFF, bA = colorA & 0xFF;
        int rB = (colorB >> 16) & 0xFF, gB = (colorB >> 8) & 0xFF, bB = colorB & 0xFF;
        int r = (int) (rA + (rB - rA) * t);
        int g = (int) (gA + (gB - gA) * t);
        int b = (int) (bA + (bB - bA) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
