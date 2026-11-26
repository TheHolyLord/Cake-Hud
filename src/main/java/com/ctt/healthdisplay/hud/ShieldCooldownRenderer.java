package com.ctt.healthdisplay.hud;

import com.ctt.healthdisplay.client.ItemCategoryProbe;
import com.ctt.healthdisplay.config.ModConfig;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.GameType;
import net.minecraft.world.item.ItemStack;

/**
 * v8.6.1 · 盾牌冷却条（用户拍板的新机制）。
 *
 * <p>手上（主手优先、其次副手）拿着 Shield 分类物品时，在准星下方画一条耐久进度条：
 * 进度 = 剩余耐久比（数据包用盾牌耐久表达冷却，挡一次掉一截、修复后回满）。
 *
 * <h2>位置：为什么是 准星 + 27</h2>
 * <p>26.3 原版准星攻击冷却指示器（{@code Hud.extractCrosshair}，字节码核实）画在
 * {@code x = w/2 - 8, y = h/2 + 9}：进度态 16x4，"满蓄力/有目标"态用 16x16 的 FULL
 * sprite，最坏占到 {@code h/2 + 25}。本条顶边取 {@code h/2 + 27}，两种状态都压不到；
 * 全部坐标基于 GUI 缩放后的屏幕中心，任何分辨率 / GUI Scale 下相对间距恒定，
 * 低分辨率也不会和原版指示器或物品名（热栏上方 h-41 一带）重叠。
 *
 * <h2>显示门槛</h2>
 * <p>与 {@link com.ctt.healthdisplay.client.HudRenderGate} 无关——用户明确要求
 * 癫狂（Berserk）/ Curse of the Unknown 期间本条照常显示（那两个状态只是把
 * bossbar 文本换成装饰内容，物品耐久通道不受影响）。F1 隐藏 HUD、打开界面、
 * 旁观模式时跟随原版准星一起消失。
 */
public final class ShieldCooldownRenderer {

    private static final int BAR_W = 40;
    private static final int BAR_H = 3;
    /** 顶边相对屏幕中心的偏移；见类注释的位置推导（避开 16x16 满蓄力指示器）。 */
    // v8.6.3 · 再向下留出字幕安全间距。
    private static final int OFFSET_Y = 32;

    private static final int BAR_BORDER = 0xFF000000;
    private static final int EMPTY_FILL = 0xFF181818;
    // v8.6.2 · 冷却进度颜色：0=红，0.5=黄，1=绿。
    private static final int COOLDOWN_RED = 0xFFE33D3D;
    private static final int COOLDOWN_YELLOW = 0xFFFFD83D;
    private static final int COOLDOWN_GREEN = 0xFF3DDE58;

    private static volatile boolean subtitlesVisible;

    /** 移植自本 fork：四张 34×86 的盾牌计量贴图（按剩余耐久分四档）。 */
    private static final int TEX_W = 34;
    private static final int TEX_H = 86;
    private static final Identifier[] METER = {
            Identifier.fromNamespaceAndPath("ctt-health-display", "textures/gui/meter_0.png"),
            Identifier.fromNamespaceAndPath("ctt-health-display", "textures/gui/meter_1.png"),
            Identifier.fromNamespaceAndPath("ctt-health-display", "textures/gui/meter_2.png"),
            Identifier.fromNamespaceAndPath("ctt-health-display", "textures/gui/meter_3.png"),
    };

    private ShieldCooldownRenderer() {}

    /** 由原版 SubtitleOverlay.extractRenderState 返回点更新，避免读取时机落后一帧。 */
    public static void setSubtitlesVisible(boolean visible) {
        subtitlesVisible = visible;
    }

    public static void render(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
        if (!ModConfig.INSTANCE.showShieldCooldown) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.gui == null) return;
        // 全局 HUD 开关（按键切换）关掉时本条也关；但本条不参与
        // HudRenderGate 的癫狂 / Curse of the Unknown 抑制（用户拍板）。
        if (!com.ctt.healthdisplay.CttHealthDisplay.hudEnabled) return;
        if (mc.gui.hud.isHidden()) return;
        if (mc.gui.screen() != null) return;
        if (mc.gameMode != null && mc.gameMode.getPlayerMode() == GameType.SPECTATOR) return;

        ItemStack shield = ItemCategoryProbe.heldShield(mc);
        if (shield == null || shield.isEmpty()) return;

        float progress = ItemCategoryProbe.shieldProgress(shield);
        // 冷却已完成时，字幕优先：原版字幕通常位于准星下方，暂时不画这条
        // 绿色满条；一旦盾牌重新受损进入冷却，条立即恢复，即使字幕还在。
        if (progress >= 0.999f && subtitlesVisible) return;

        int w = context.guiWidth();
        int h = context.guiHeight();
        // 本 fork：允许在设置界面拖动位置；未设置时保持上游的「准星下方居中」。
        boolean custom = ModConfig.INSTANCE.shieldX >= 0 && ModConfig.INSTANCE.shieldY >= 0;
        int cx = custom ? ModConfig.INSTANCE.shieldX : w / 2;
        int baseTop = custom ? ModConfig.INSTANCE.shieldY : h / 2 + OFFSET_Y;
        int top = custom ? baseTop - BAR_H / 2 : baseTop;
        int left = cx - BAR_W / 2;

        // 可选的「贴图仪表」样式（默认关闭，保持上游原来的小条）。
        if (ModConfig.INSTANCE.shieldTextureStyle) {
            // fork 的四档贴图只有 4 张（按磨损比 damage/maxDamage 分档），「完全就绪」这一态
            // 它不是画第 5 张图，而是**整块不渲染**（fork: {@code if (damage <= 0) return;}）。
            // 所以磨损 = 0（耐久满 / 无耐久概念）时直接返回，和 fork 一致。
            float wear = 1.0f - Math.max(0f, Math.min(1f, progress));
            if (wear <= 0.0f) return;
            // fork 的档位：磨损越多档位越低（0 = 刚用完/空槽，3 = 已基本回满/满格）。
            // 这里的 progress 是**剩余比**，必须先换算成磨损比再取档 ——
            // 直接把剩余比喂进 fork 的阈值会让档位整个反过来
            // （表现：冷却完成时反而显示空槽）。
            int stage = wear < 0.4f ? 3 : wear < 0.6f ? 2 : wear < 0.8f ? 1 : 0;
            double scale = Math.max(0.25, Math.min(3.0, ModConfig.INSTANCE.shieldScale));
            int tw = Math.max(1, (int) Math.round(TEX_W * scale));
            int th = Math.max(1, (int) Math.round(TEX_H * scale));
            int alpha = (int) Math.round(255.0
                    * Math.max(0.0, Math.min(1.0, ModConfig.INSTANCE.shieldOpacity)));
            int texY = custom ? baseTop - th / 2 : baseTop;
            context.blit(RenderPipelines.GUI_TEXTURED, METER[stage], cx - tw / 2, texY,
                    0.0f, 0.0f, tw, th, TEX_W, TEX_H, TEX_W, TEX_H, (alpha << 24) | 0xFFFFFF);
            return;
        }

        double opacity = Math.max(0.0, Math.min(1.0, ModConfig.INSTANCE.shieldOpacity));
        context.fill(left - 1, top - 1, left + BAR_W + 1, top + BAR_H + 1, withAlpha(BAR_BORDER, opacity));
        context.fill(left, top, left + BAR_W, top + BAR_H, withAlpha(EMPTY_FILL, opacity));
        int fillPx = Math.round(BAR_W * progress);
        if (fillPx > 0) {
            context.fill(left, top, left + fillPx, top + BAR_H,
                    withAlpha(cooldownColor(progress), opacity));
        }
    }


    private static int withAlpha(int argb, double opacity) {
        int a = (int) Math.round(255.0 * Math.max(0.0, Math.min(1.0, opacity)));
        return (argb & 0x00FFFFFF) | (a << 24);
    }

    private static int cooldownColor(float progress) {
        float p = Math.max(0f, Math.min(1f, progress));
        if (p <= 0.5f) {
            return interpolate(COOLDOWN_RED, COOLDOWN_YELLOW, p * 2f);
        }
        return interpolate(COOLDOWN_YELLOW, COOLDOWN_GREEN, (p - 0.5f) * 2f);
    }

    private static int interpolate(int from, int to, float amount) {
        float t = Math.max(0f, Math.min(1f, amount));
        int r = Math.round(((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t);
        int g = Math.round(((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t);
        int b = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
