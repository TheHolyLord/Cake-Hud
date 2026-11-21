package com.ctt.healthdisplay.client;

import com.ctt.healthdisplay.config.ModConfig;
import com.ctt.healthdisplay.mixin.LoreContainerScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.network.chat.Component;


import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 弹窗总管家：物品描述预览与固定、嵌套开窗、按住左键即可拖动、
 * 点空白关闭全部、点击粒子和轻响音效。
 * 每个弹窗记住来源界面（owner）：背包里的描述弹窗在关闭背包/按 ESC 时自动收起，
 * 聊天栏里点开的弹窗在关闭聊天栏/按 ESC 时自动收起。
 *
 * 层级：WINDOWS 列表的顺序就是从下到上的叠放顺序，而且 OverlayRenderer 会给每个窗口
 * 单独一段 z（越靠后 = 越靠近摄像机）。新打开的窗口一律追加到末尾（= 最上层），
 * 按在哪个窗口上就把哪个窗口提到最上层，
 * 所以「鼠标最后点的那个永远盖在其他窗口上面」——连窗口里的字也是后点的盖前点的。
 */
public final class LorePopupManager {
    private static final double DRAG_DISTANCE_SQUARED = 1.0; // 至少移动一个界面像素才算拖动
    public static final int MAX_WINDOWS = 6;          // 最多同时存在 6 个窗口
    public static final int MOUSE_GAP_X = 8;          // 「跟着鼠标生成」时窗口相对光标右移的像素
    public static final int MOUSE_GAP_Y = 6;          // 「跟着鼠标生成」时窗口相对光标下移的像素

    // 详情弹窗统一生成在鼠标点击的位置（聊天栏里点词也一样跟着鼠标，不再放屏幕中央上方）。

    public static final List<PopupWindow> WINDOWS = new ArrayList<>();
    private static PopupWindow sticky;
    private static boolean sideTooltipPinned;
    private static PopupWindow hoverPreview;
    private static boolean showHoverPreview;
    private static int hoverSlotId = -1;
    private static ItemStack hoverStack = ItemStack.EMPTY;
    private static boolean hoverAtCursor;
    private static int pinnedSlotId = -1;
    private static ItemStack pinnedStack = ItemStack.EMPTY;
    private static int previewWidth;
    private static long previewRevision;
    private static boolean previewAdvanced;
    private static Screen lastScreen;

    // 按下/拖动状态机
    private static PopupWindow pressWindow;
    private static PopupWindow.Seg pressSeg;
    private static double pressX, pressY, grabDx, grabDy;
    private static boolean dragging;
    private static boolean leftDown;

    public static void setLeftDown(boolean down) { leftDown = down; }

    // 屏幕空间的小粒子（点击反馈）
    private static final class Spark {
        double x, y, vx, vy;
        final long bornNs, lifeNs;
        final int rgb;

        Spark(double x, double y, double vx, double vy, long bornNs, long lifeNs, int rgb) {
            this.x = x; this.y = y; this.vx = vx; this.vy = vy;
            this.bornNs = bornNs; this.lifeNs = lifeNs; this.rgb = rgb;
        }
    }

    private static final List<Spark> SPARKS = new ArrayList<>();
    private static final Random RANDOM = new Random();
    private static long lastFrameNs;

    private LorePopupManager() { }

    public static void startFrame() {
        showHoverPreview = false;
    }

    public static void render(GuiGraphicsExtractor ctx) {
        frameTick(ctx.guiWidth(), ctx.guiHeight());
        LoreAdvancements.render(ctx, leftDown);
        boolean hasPreview = !sideTooltipPinned && showHoverPreview && hoverPreview != null
                && hoverPreview.owner == Minecraft.getInstance().gui.screen();
        if (hasPreview && sticky == null) renderPopup(ctx, hoverPreview);
        for (PopupWindow popup : WINDOWS) {
            renderPopup(ctx, popup);
            if (popup == sticky && hasPreview) renderPopup(ctx, hoverPreview);
        }
        if (hasSparks()) {
            ctx.nextStratum();
            drawSparks(ctx);
        }
    }

    private static void renderPopup(GuiGraphicsExtractor ctx, PopupWindow popup) {
        ctx.nextStratum();
        int x = (int) popup.x, y = (int) popup.y;
        int bg = 0xFF100014;
        ctx.fill(x - 3, y - 3, x + popup.w + 3, y + popup.h + 3, bg);
        ctx.fill(x - 3, y - 3, x + popup.w + 3, y - 2, 0xFF00FFFC);
        ctx.fill(x - 3, y + popup.h + 2, x + popup.w + 3, y + popup.h + 3, 0xFF00FFFC);
        ctx.fill(x - 3, y - 2, x - 2, y + popup.h + 2, 0xFF0000FF);
        ctx.fill(x + popup.w + 2, y - 2, x + popup.w + 3, y + popup.h + 2, 0xFF0000FF);
        drawText(ctx, popup, bg);
    }

    static void drawText(GuiGraphicsExtractor ctx, PopupWindow popup, int bg) {
        int x = (int) popup.x, y = (int) popup.y;
        Font font = Minecraft.getInstance().font;
        ctx.nextStratum();
        for (PopupWindow.Seg seg : popup.segs) {
            TextStroke.draw(ctx, font, seg.text, x + seg.x, y + seg.y, seg.color, bg);
            if (seg.word != null) {
                ctx.fill(x + seg.x, y + seg.y + 8, x + seg.x + seg.w, y + seg.y + 9,
                        0xFF000000 | TextStroke.effective(seg.color, bg));
            }
        }
    }

    public static boolean click(Screen screen, double x, double y) {
        closeWindowsOfClosedScreens();
        PopupWindow hit = peekTopAt(x, y);
        if (hit != null) {
            beginPress(hit, x, y);
            return true;
        }
        if (LoreAdvancements.click(screen, x, y)) return true;
        if (hasWindows()) closeAll();
        return false;
    }

    public static boolean hasWindows() {
        return !WINDOWS.isEmpty();
    }

    public static boolean hasSparks() {
        return !SPARKS.isEmpty();
    }

    /**
     * 1.21.4 起 Mouse.getX()/getY() 返回的是窗口原始像素，不再是缩放后的 GUI 坐标，
     * 原版是在要用的地方自己乘 (缩放宽 / 窗口宽)。本模组所有鼠标位置一律走这里换算，
     * 否则像素值会被当成 GUI 坐标、弹窗被边界钳到屏幕右下角（上一版的 bug 根源）。
     */
    public static double scaledMouseX() {
        Minecraft client = Minecraft.getInstance();
        Window window = client.getWindow();
        return client.mouseHandler.getScaledXPos(window);
    }

    public static double scaledMouseY() {
        Minecraft client = Minecraft.getInstance();
        Window window = client.getWindow();
        return client.mouseHandler.getScaledYPos(window);
    }

    /** 从最上层往下找被点到的窗口（无副作用）。 */
    public static PopupWindow peekTopAt(double mx, double my) {
        for (int i = WINDOWS.size() - 1; i >= 0; i--) {
            PopupWindow w = WINDOWS.get(i);
            if (w.contains(mx, my)) {
                return w;
            }
        }
        return null;
    }

    /** 按在窗口上：记录按压状态并把这个窗口提到最上层（碰过的窗口盖在其他窗口上面）。 */
    public static void beginPress(PopupWindow w, double mx, double my) {
        WINDOWS.remove(w);
        WINDOWS.add(w);
        pressWindow = w;
        pressSeg = w.segAt(mx, my);
        pressX = mx;
        pressY = my;
        grabDx = mx - w.x;
        grabDy = my - w.y;
        dragging = false;
    }

    static boolean movedFrom(double x, double y, double startX, double startY) {
        double dx = x - startX, dy = y - startY;
        return dx * dx + dy * dy >= DRAG_DISTANCE_SQUARED;
    }

    public static void move(double x, double y, int screenW, int screenH) {
        if (!leftDown) return;
        LoreAdvancements.move(x, y, screenW, screenH);
        if (pressWindow == null || pressWindow.owner != Minecraft.getInstance().gui.screen()) return;
        dragging |= movedFrom(x, y, pressX, pressY);
        if (dragging) {
            pressWindow.x = clampD(x - grabDx, 3, screenW - pressWindow.w - 3);
            pressWindow.y = clampD(y - grabDy, 3, screenH - pressWindow.h - 3);
        }
    }

    /** 每帧清理已关闭界面的弹窗，并处理窗口边界和粒子。 */
    private static void frameTick(int screenW, int screenH) {
        closeWindowsOfClosedScreens();
        long now = System.nanoTime();
        for (PopupWindow w : WINDOWS) {
            w.x = clampD(w.x, 3, screenW - w.w - 3);
            w.y = clampD(w.y, 3, screenH - w.h - 3);
        }
        updateSparks(now);
        lastFrameNs = now;
    }

    /**
     * 关闭“来源界面已经不在了”的弹窗：
     * 背包关了（含按 ESC）→ 描述弹窗连同它点开的子弹窗一起收；
     * 聊天栏关了（含按 ESC）→ 聊天点开的弹窗一起收；
     * 没屏幕时点开的弹窗，在玩家打开任意界面（含 ESC 暂停菜单）时收掉。
     */
    private static void closeWindowsOfClosedScreens() {
        if (WINDOWS.isEmpty()) {
            return;
        }
        Screen current = Minecraft.getInstance().gui.screen();
        if (sticky != null && sticky.owner != current) closeSticky();
        WINDOWS.removeIf(w -> {
            if (w.owner == current) {
                return false;
            }
            if (w == pressWindow) {
                clearPress();
            }
            return true;
        });
    }

    /** 在原版绘制物品提示的同一帧准备描述窗，并决定是否替换原版提示。 */
    public static boolean hoverTick(AbstractContainerScreen<?> screen, Slot slot, double mx, double my) {
        Minecraft client = Minecraft.getInstance();
        closeWindowsOfClosedScreens();
        if (screen != lastScreen) {
            lastScreen = screen;
            clearHover();
        }
        showHoverPreview = false;
        // 左侧固定窗关闭前不生成其他物品预览，也不让原版提示盖过它。
        if (sideTooltipPinned) {
            clearHover();
            return true;
        }
        if (client.player == null || !screen.getMenu().getCarried().isEmpty()) {
            clearHover();
            return false;
        }
        if (pressWindow != null || peekTopAt(mx, my) != null) {
            clearHover();
            return true;
        }
        if (slot == null || slot.getItem().isEmpty()) {
            clearHover();
            return false;
        }
        ItemStack item = slot.getItem();
        if (sticky != null && pinnedSlotId == slot.index && ItemStack.matches(pinnedStack, item)) {
            clearHover();
            return true;
        }

        boolean atCursor = ModConfig.INSTANCE.loreTooltipAtCursor;
        if (hoverSlotId != slot.index || hoverAtCursor != atCursor || !ItemStack.matches(hoverStack, item)) {
            hoverSlotId = slot.index;
            hoverStack = item.copy();
            hoverAtCursor = atCursor;
            hoverPreview = null;
        }
        hoverPreview = createTooltip(screen, item, mx, my, false);
        showHoverPreview = hoverPreview != null;
        if (!showHoverPreview) clearHover();
        return showHoverPreview;
    }

    /** Alt 按下时用当前光标重新找物品格，避免快速移动时固定上一帧的物品。 */
    public static boolean toggleTooltipPin(AbstractContainerScreen<?> screen) {
        Minecraft client = Minecraft.getInstance();
        closeWindowsOfClosedScreens();
        if (client.player == null || pressWindow != null) return false;
        // 左侧模式下，再按 Alt 只解除固定，不会直接换成鼠标下的另一件物品。
        if (sideTooltipPinned) {
            closeSticky();
            return true;
        }
        if (!screen.getMenu().getCarried().isEmpty()) return false;
        double mx = scaledMouseX(), my = scaledMouseY();
        Slot slot = ((LoreContainerScreenAccessor) screen).ctt$getSlotAt(mx, my);
        if (sticky != null && (slot == null || slot.getItem().isEmpty()
                || pinnedSlotId == slot.index && ItemStack.matches(pinnedStack, slot.getItem())
                || peekTopAt(mx, my) == sticky)) {
            closeSticky();
            return true;
        }
        if (slot == null || slot.getItem().isEmpty() || peekTopAt(mx, my) != null) return false;
        ItemStack item = slot.getItem();
        boolean sideMode = !ModConfig.INSTANCE.loreTooltipAtCursor;
        boolean reuseSidePreview = sideMode && !hoverAtCursor
                && showHoverPreview && hoverPreview != null && hoverPreview.owner == screen
                && hoverSlotId == slot.index && ItemStack.matches(hoverStack, item);
        // 左侧模式沿用当前预览窗的位置和大小，按 Alt 时窗口不会跳动。
        PopupWindow window = reuseSidePreview ? hoverPreview : createTooltip(screen, item, mx, my, true);
        if (window == null) return false;
        pinTooltip(window, slot, sideMode);
        clearHover();
        return true;
    }

    private static PopupWindow createTooltip(AbstractContainerScreen<?> screen, ItemStack item,
                                             double mx, double my, boolean pinned) {
        Minecraft client = Minecraft.getInstance();
        LoreContainerScreenAccessor acc = (LoreContainerScreenAccessor) screen;
        int screenW = client.getWindow().getGuiScaledWidth();
        int screenH = client.getWindow().getGuiScaledHeight();
        int gx = acc.tooltipLoreGuiX();
        int gy = acc.tooltipLoreGuiY();
        int gw = acc.tooltipLoreGuiWidth();
        int gh = acc.tooltipLoreGuiHeight();
        int leftSpace = gx - 10;
        int rightSpace = screenW - gx - gw - 10;
        boolean atCursor = ModConfig.INSTANCE.loreTooltipAtCursor;
        boolean onLeft = leftSpace >= PopupWindow.MIN_W || rightSpace < PopupWindow.MIN_W;
        int space = atCursor ? Math.max((int) mx, screenW - (int) mx) - MOUSE_GAP_X - 4
                : onLeft ? leftSpace : rightSpace;
        int maxW = Math.min(PopupWindow.MAX_W, Math.max(PopupWindow.MIN_W, Math.min(space, screenW - 8)));
        long revision = LoreRegistry.revision();
        boolean advanced = client.options.advancedItemTooltips;
        PopupWindow window = null;
        if (!pinned && hoverPreview != null && maxW == previewWidth && revision == previewRevision
                && advanced == previewAdvanced) {
            window = hoverPreview;
        }
        if (window == null) {
            List<Component> lines = acc.ctt$getTooltip(item);
            if (lines.isEmpty()) return null;
            String hint = Component.translatable(atCursor && pinned
                    ? "tooltip_lore.hint.lock" : "tooltip_lore.hint.alt_pin").getString();
            String pinnedHint = atCursor ? null : Component.translatable("tooltip_lore.hint.side_pinned").getString();
            window = PopupWindow.fromTooltip(lines, client.font, hint, pinnedHint, maxW);
            window.owner = screen;
            if (!pinned) {
                previewWidth = maxW;
                previewRevision = revision;
                previewAdvanced = advanced;
            }
        }
        if (atCursor) {
            placeNearCursor(window, mx, my, screenW, screenH);
        } else {
            double x = onLeft ? gx - window.w - 8 : gx + gw + 8;
            double y = gy + (gh - window.h) / 2.0;
            window.x = clampD(x, 3, screenW - window.w - 3);
            window.y = clampD(y, 3, screenH - window.h - 3);
        }
        return window;
    }

    private static void pinTooltip(PopupWindow window, Slot slot, boolean sideMode) {
        int at = sticky == null ? 0 : WINDOWS.indexOf(sticky);
        closeSticky();
        if (sideMode) window.showPinnedTooltipHint();
        WINDOWS.add(Math.max(0, at), window);
        sticky = window;
        sideTooltipPinned = sideMode;
        pinnedSlotId = slot.index;
        pinnedStack = slot.getItem().copy();
    }

    private static void clearHover() {
        hoverSlotId = -1;
        hoverStack = ItemStack.EMPTY;
        hoverPreview = null;
        showHoverPreview = false;
    }

    /**
     * 打开某个词的详情窗（支持嵌套：详情里再点词会再开一层）。
     * titleColor = 被点的那个词本身的颜色，详情窗第一行就用它，不再套金色。
     * 位置：一律生成在鼠标点击的位置（右下角一点点；贴边放不下就翻到鼠标左上侧）——
     * 聊天栏里直接点词、物品描述固定窗里点词、进度描述里点词、弹窗里再点词，全部同一套规则。
     * 正文超宽自动换行：先按「鼠标一侧还剩多少地方」定排版宽度，折完行窗口就不会宽过屏幕，
     * 也不会因为太长被钳得满屏跑。
     * 同一处已经有窗口占着就依次错开，看得见也点得到。
     * 新窗口一律追加到列表末尾 → 最后点开的永远在最上层（见 OverlayRenderer 的 z 分层）。
     */
    public static void openEntry(String word, double mx, double my, int titleColor) {
        LoreRegistry.Entry e = LoreRegistry.get(word);
        if (e == null) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            client.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.35f, 1.7f);
        }
        spawnSparks(mx, my);
        int screenW = client.getWindow().getGuiScaledWidth();
        int screenH = client.getWindow().getGuiScaledHeight();
        // 排版宽度：不超过 230，也不超出屏幕；从鼠标右侧放不下就按左侧的可用宽度折行
        int spaceRight = screenW - (int) mx - MOUSE_GAP_X - 4;
        int spaceLeft = (int) mx - MOUSE_GAP_X - 4;
        int space = Math.max(spaceRight, spaceLeft);
        int maxW = Math.min(PopupWindow.MAX_W, Math.max(PopupWindow.MIN_W, Math.min(space, screenW - 8)));
        PopupWindow pw = PopupWindow.fromEntry(word, e.lines(), client.font, titleColor, maxW);
        pw.owner = client.gui.screen(); // 记住来源界面：聊天栏/背包/进度页面关了它也跟着收
        placeNearCursor(pw, mx, my, screenW, screenH);
        double x = pw.x, y = pw.y;
        for (PopupWindow other : WINDOWS) {
            if (Math.abs(other.x - x) < 4 && Math.abs(other.y - y) < 4) {
                x += 14;
                y += 12;
            }
        }
        pw.x = clampD(x, 3, screenW - pw.w - 3);
        pw.y = clampD(y, 3, screenH - pw.h - 3);
        WINDOWS.add(pw); // 追加到末尾 = 最上层
        while (WINDOWS.size() > MAX_WINDOWS) {
            int idx = -1;
            for (int i = 0; i < WINDOWS.size(); i++) {
                if (WINDOWS.get(i) != sticky) {
                    idx = i;
                    break;
                }
            }
            WINDOWS.remove(idx >= 0 ? idx : 0);
        }
    }

    /** 关闭全部弹窗（含固定描述）。 */
    public static void closeAll() {
        closeSticky();
        WINDOWS.clear();
        clearHover();
        clearPress();
    }

    /** 只收起固定描述窗，保留玩家手动点开的词条窗。 */
    private static void closeSticky() {
        if (sticky == null) return;
        if (pressWindow == sticky) clearPress();
        WINDOWS.remove(sticky);
        sticky = null;
        sideTooltipPinned = false;
        pinnedSlotId = -1;
        pinnedStack = ItemStack.EMPTY;
    }

    public static boolean release(double x, double y) {
        if (LoreAdvancements.release(x, y)) return true;
        if (pressWindow == null) return false;
        PopupWindow.Seg hit = pressWindow.segAt(x, y);
        boolean open = pressWindow.owner == Minecraft.getInstance().gui.screen()
                && !dragging && !movedFrom(x, y, pressX, pressY) && hit != null && hit == pressSeg;
        clearPress();
        if (open) openEntry(hit.word, x, y, hit.color);
        return true;
    }

    private static void clearPress() {
        pressWindow = null;
        pressSeg = null;
        dragging = false;
    }

    public static void clientTick(Minecraft client) {
        LoreRegistry.tickReload();
        closeWindowsOfClosedScreens();
        if (lastScreen != client.gui.screen()) {
            lastScreen = null;
            clearHover();
        }
        if (client.level == null) {
            closeAll();
            SPARKS.clear();
        }
    }

    private static void spawnSparks(double mx, double my) {
        long now = System.nanoTime();
        for (int i = 0; i < 14; i++) {
            double a = RANDOM.nextDouble() * Math.PI * 2;
            double sp = 30 + RANDOM.nextDouble() * 70;
            long life = 500_000_000L + (long) (RANDOM.nextDouble() * 350_000_000L);
            int rgb = switch (i % 3) {
                case 0 -> 0x66DDFF;
                case 1 -> 0xFFD75E;
                default -> 0xFF9BD8;
            };
            SPARKS.add(new Spark(mx, my, Math.cos(a) * sp, Math.sin(a) * sp - 20, now, life, rgb));
        }
        if (SPARKS.size() > 240) {
            SPARKS.subList(0, SPARKS.size() - 240).clear();
        }
    }

    private static void updateSparks(long now) {
        SPARKS.removeIf(s -> now - s.bornNs > s.lifeNs);
        double dt = lastFrameNs == 0 ? 0 : Math.min(0.1, (now - lastFrameNs) / 1_000_000_000.0);
        if (dt <= 0) {
            return;
        }
        for (Spark s : SPARKS) {
            s.vy += 140 * dt;
            s.x += s.vx * dt;
            s.y += s.vy * dt;
        }
    }

    public static void drawSparks(GuiGraphicsExtractor ctx) {
        long now = System.nanoTime();
        for (Spark s : SPARKS) {
            double t = (now - s.bornNs) / (double) s.lifeNs;
            if (t < 0 || t >= 1) {
                continue;
            }
            int alpha = (int) (220 * (1 - t));
            int color = (alpha << 24) | (s.rgb & 0xFFFFFF);
            int x = (int) s.x;
            int y = (int) s.y;
            ctx.fill(x, y, x + 2, y + 2, color);
        }
    }

    private static void placeNearCursor(PopupWindow window, double mx, double my, int screenW, int screenH) {
        double x = mx + MOUSE_GAP_X;
        double y = my + MOUSE_GAP_Y;
        if (x + window.w > screenW - 3) x = mx - window.w - MOUSE_GAP_X;
        if (y + window.h > screenH - 3) y = my - window.h - MOUSE_GAP_Y;
        window.x = clampD(x, 3, screenW - window.w - 3);
        window.y = clampD(y, 3, screenH - window.h - 3);
    }

    private static double clampD(double v, double lo, double hi) {
        return hi < lo ? lo : Math.max(lo, Math.min(hi, v));
    }
}
