package com.ctt.healthdisplay.hud;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;

import java.util.function.Predicate;

/**
 * 在快捷栏旁显示弓下一次会使用的箭。
 *
 * <p>检测顺序与玩家使用弓时取弹药的习惯保持一致：先看副手，再按背包槽位
 * 0 到 35 顺序查找。图标直接走原版物品渲染，所以会保留物品模型、附魔光效
 * 和堆叠数量显示。
 */
public final class ArrowPreviewRenderer {

    /** 原版快捷栏在 GUI 坐标中的标准尺寸：182 像素宽、9 个 20 像素槽位。 */
    private static final int VANILLA_HOTBAR_WIDTH = 182;
    private static final int VANILLA_HOTBAR_SLOT_SIZE = 20;
    private static final int SLOT_GAP = 2;
    private static final int ITEM_INSET = 2;
    private static final int SLOT_BORDER = 0xFF8A8A8A;
    private static final int SLOT_HIGHLIGHT = 0xFF555555;
    private static final int SLOT_FILL = 0xFF202020;

    private ArrowPreviewRenderer() {}

    public static void render(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null || client.gui == null) return;
        // 这里故意不检查 HudRenderGate：癫狂 / 未知诅咒只隐藏本模组的普通信息 HUD，
        // 弓箭预览属于快捷栏辅助信息，必须始终保留。
        if (client.gui.hud.isHidden() || client.gui.screen() != null) return;
        if (!isHoldingBow(client.player.getMainHandItem())) return;

        ItemStack nextArrow = findNextArrow(client.player, client.player.getMainHandItem());
        if (nextArrow.isEmpty()) return;

        HotbarLayout hotbar = hotbarLayout(context);
        // 独立物品格紧贴快捷栏右侧，不占用 0~8 号快捷栏槽位。
        int slotX = hotbar.right + hotbar.gap;
        int slotY = hotbar.top;
        drawSlot(context, client.font, nextArrow, slotX, slotY,
                hotbar.slotSize, hotbar.itemInset);
    }

    private static boolean isHoldingBow(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof BowItem;
    }

    private static ItemStack findNextArrow(net.minecraft.world.entity.player.Player player, ItemStack bowStack) {
        BowItem bow = (BowItem) bowStack.getItem();
        Predicate<ItemStack> supported = bow.getAllSupportedProjectiles();

        ItemStack offhand = player.getOffhandItem();
        if (isSupportedArrow(offhand, supported)) {
            return offhand;
        }

        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < 36; slot++) {
            ItemStack candidate = inventory.getItem(slot);
            if (isSupportedArrow(candidate, supported)) {
                return candidate;
            }
        }
        return ItemStack.EMPTY;
    }

    private static boolean isSupportedArrow(ItemStack stack, Predicate<ItemStack> supported) {
        return !stack.isEmpty() && stack.is(ItemTags.ARROWS) && supported.test(stack);
    }

    /**
     * 根据当前 GUI 的快捷栏实际宽度定位，而不是把图标写死在某个屏幕分辨率上。
     * 原版 GUI 坐标会随 GUI Scale 改变，所以这里直接使用当前画布宽度和原版 182 像素
     * 快捷栏尺寸；屏幕变宽只改变左右留白，不会把快捷栏本身错误放大。
     */
    private static HotbarLayout hotbarLayout(GuiGraphicsExtractor context) {
        int width = context.guiWidth();
        int height = context.guiHeight();
        int left = width / 2 - VANILLA_HOTBAR_WIDTH / 2;
        int top = height - 23;
        return new HotbarLayout(left, top, left + VANILLA_HOTBAR_WIDTH,
                VANILLA_HOTBAR_SLOT_SIZE, SLOT_GAP, ITEM_INSET);
    }

    private static void drawSlot(GuiGraphicsExtractor context, Font font, ItemStack stack,
                                 int x, int y, int slotSize, int itemInset) {
        context.fill(x, y, x + slotSize, y + slotSize, SLOT_BORDER);
        context.fill(x + 1, y + 1, x + slotSize - 1, y + slotSize - 1, SLOT_HIGHLIGHT);
        context.fill(x + 2, y + 2, x + slotSize - 2, y + slotSize - 2, SLOT_FILL);

        int itemX = x + itemInset;
        int itemY = y + itemInset;
        context.item(stack, itemX, itemY);
        // 原版物品渲染会按 GUI 坐标裁切；数量仍沿用原版装饰绘制。
        context.itemDecorations(font, stack, itemX, itemY, Integer.toString(stack.getCount()));
    }

    private record HotbarLayout(int left, int top, int right, int slotSize, int gap, int itemInset) {
    }
}
