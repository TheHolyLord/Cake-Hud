package com.ctt.healthdisplay.hud;

import com.ctt.healthdisplay.CttHealthDisplay;
import com.ctt.healthdisplay.client.ItemCategoryProbe;
import com.ctt.healthdisplay.config.ModConfig;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.level.GameType;

/** 按手持盾牌的剩余耐久显示冷却进度。 */
public final class ShieldCooldownRenderer {
    private static volatile boolean subtitlesVisible;

    private ShieldCooldownRenderer() {}

    public record Bounds(int left, int top, int width, int height, float scale) {}

    public static void setSubtitlesVisible(boolean visible) {
        subtitlesVisible = visible;
    }

    public static Bounds bounds(int screenWidth, int screenHeight, float x, float y, boolean horizontal, int size) {
        int baseWidth = horizontal ? 42 : 12;
        int baseHeight = horizontal ? 5 : 30;
        float scale = Math.min(size / 100f, Math.min(screenWidth / (float) baseWidth, screenHeight / (float) baseHeight));
        int width = Math.round(baseWidth * scale);
        int height = Math.round(baseHeight * scale);
        int left = Math.clamp(Math.round(screenWidth * x) - width / 2, 0, screenWidth - width);
        int defaultTop = screenHeight / 2 + (horizontal ? 31 : 32);
        int top = Math.clamp(y < 0 ? defaultTop : Math.round(screenHeight * y) - height / 2, 0, screenHeight - height);
        return new Bounds(left, top, width, height, scale);
    }

    public static void render(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
        ModConfig config = ModConfig.INSTANCE;
        if (!config.showShieldCooldown || !CttHealthDisplay.hudEnabled) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.gui.hud.isHidden() || mc.gui.screen() != null) return;
        if (mc.gameMode != null && mc.gameMode.getPlayerMode() == GameType.SPECTATOR) return;
        var shield = ItemCategoryProbe.heldShield(mc);
        if (shield == null || shield.isEmpty()) return;
        float progress = ItemCategoryProbe.shieldProgress(shield);
        if (progress >= 0.999f && subtitlesVisible && config.shieldY < 0) return;
        Bounds bounds = bounds(context.guiWidth(), context.guiHeight(), config.shieldX, config.shieldY,
                config.shieldHorizontal, config.shieldSize);
        draw(context, bounds, config.shieldHorizontal, progress, config.shieldOpacity);
    }

    public static void draw(GuiGraphicsExtractor context, Bounds bounds, boolean horizontal, float progress, int opacity) {
        float p = Math.clamp(progress, 0f, 1f);
        var pose = context.pose();
        pose.pushMatrix();
        pose.translate(bounds.left(), bounds.top());
        pose.scale(bounds.scale(), bounds.scale());
        if (horizontal) drawHorizontal(context, p, opacity);
        else drawVertical(context, p, opacity);
        pose.popMatrix();
    }

    private static void drawHorizontal(GuiGraphicsExtractor context, float p, int opacity) {
        int filled = Math.round(40 * p);
        int color = p <= 0.5f ? interpolate(0xFFE33D3D, 0xFFFFD83D, 2 * p)
                : interpolate(0xFFFFD83D, 0xFF3DDE58, 2 * p - 1);
        context.fill(0, 0, 42, 1, HeadHealthStyle.alpha(0xFF000000, opacity));
        context.fill(0, 4, 42, 5, HeadHealthStyle.alpha(0xFF000000, opacity));
        context.fill(0, 1, 1, 4, HeadHealthStyle.alpha(0xFF000000, opacity));
        context.fill(41, 1, 42, 4, HeadHealthStyle.alpha(0xFF000000, opacity));
        if (filled > 0) context.fill(1, 1, 1 + filled, 4, HeadHealthStyle.alpha(color, opacity));
        if (filled < 40) context.fill(1 + filled, 1, 41, 4, HeadHealthStyle.alpha(0xFF181818, opacity));
    }

    private static void drawVertical(GuiGraphicsExtractor context, float p, int opacity) {
        int filled = Math.round(24 * p);
        int color = p < 0.4f ? 0xFFEC5551 : p < 0.75f ? 0xFFF2C84B : 0xFF66D87D;
        context.fill(1, 0, 11, 2, HeadHealthStyle.alpha(0xFFD9DDE4, opacity));
        context.fill(1, 28, 11, 30, HeadHealthStyle.alpha(0xFF777F8C, opacity));
        context.fill(0, 2, 2, 28, HeadHealthStyle.alpha(0xFFD9DDE4, opacity));
        context.fill(10, 2, 12, 28, HeadHealthStyle.alpha(0xFF777F8C, opacity));
        context.fill(2, 2, 10, 27 - filled, HeadHealthStyle.alpha(0xFF242630, opacity));
        if (filled > 0) {
            context.fillGradient(2, 27 - filled, 10, 27,
                    HeadHealthStyle.alpha(color, opacity), HeadHealthStyle.alpha(HeadHealthStyle.tone(color, 3), opacity));
        }
        context.fill(2, 27, 10, 28, HeadHealthStyle.alpha(0xFF242630, opacity));
    }

    private static int interpolate(int from, int to, float t) {
        int r = Math.round((from >> 16 & 255) * (1 - t) + (to >> 16 & 255) * t);
        int g = Math.round((from >> 8 & 255) * (1 - t) + (to >> 8 & 255) * t);
        int b = Math.round((from & 255) * (1 - t) + (to & 255) * t);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }
}
