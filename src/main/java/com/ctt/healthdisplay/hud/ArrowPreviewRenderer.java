package com.ctt.healthdisplay.hud;

import com.ctt.healthdisplay.config.ModConfig;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;

/** 显示主手弓下一次会使用的箭，弹药顺序由游戏决定。 */
public final class ArrowPreviewRenderer {
    public static final int SIZE = 20;
    private static final int HOTBAR_HALF_WIDTH = 91;

    private ArrowPreviewRenderer() {}

    public static void render(GuiGraphicsExtractor graphics, DeltaTracker tickCounter) {
        Minecraft client = Minecraft.getInstance();
        ModConfig config = ModConfig.INSTANCE;
        if (!config.showArrowPreview || client.player == null || client.level == null
                || client.gui.hud.isHidden() || client.gui.screen() != null) return;

        ItemStack bow = client.player.getMainHandItem();
        if (!(bow.getItem() instanceof BowItem)) return;
        ItemStack arrow = client.player.getProjectile(bow);
        if (arrow.isEmpty()) return;

        draw(graphics, client.font, arrow, bounds(graphics.guiWidth(), graphics.guiHeight(),
                config.arrowPreviewX, config.arrowPreviewY));
    }

    public static Bounds bounds(int width, int height, float x, float y) {
        int left = x < 0 ? width / 2 + HOTBAR_HALF_WIDTH + 2 : Math.round(x * width) - SIZE / 2;
        int top = y < 0 ? height - 23 : Math.round(y * height) - SIZE / 2;
        return new Bounds(Math.clamp(left, 0, Math.max(0, width - SIZE)),
                Math.clamp(top, 0, Math.max(0, height - SIZE)));
    }

    public static void draw(GuiGraphicsExtractor graphics, Font font, ItemStack stack, Bounds bounds) {
        int x = bounds.left();
        int y = bounds.top();
        graphics.fill(x, y, x + SIZE, y + SIZE, 0xFF8A8A8A);
        graphics.fill(x + 1, y + 1, x + SIZE - 1, y + SIZE - 1, 0xFF555555);
        graphics.fill(x + 2, y + 2, x + SIZE - 2, y + SIZE - 2, 0xFF202020);
        graphics.item(stack, x + 2, y + 2);
        graphics.itemDecorations(font, stack, x + 2, y + 2, Integer.toString(stack.getCount()));
    }

    public record Bounds(int left, int top) {}
}
