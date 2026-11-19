package com.ctt.healthdisplay.client;

import com.ctt.healthdisplay.mixin.LoreContainerScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.network.chat.Component;


import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 弹窗总管家：悬停锁定（0.5 秒）、嵌套开窗、左键长按 0.2 秒拖动、
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
    public static final long LOCK_MS = 500L;          // 悬停多久锁定描述
    public static final long DRAG_NS = 200_000_000L;  // 左键按住多久进入拖动
    public static final int MAX_WINDOWS = 6;          // 最多同时存在 6 个窗口
    public static final long RELOCK_GRACE_MS = 1500L; // 点空白关闭后，同一物品短暂不再自动锁定
    public static final int MOUSE_GAP_X = 8;          // 「跟着鼠标生成」时窗口相对光标右移的像素
    public static final int MOUSE_GAP_Y = 6;          // 「跟着鼠标生成」时窗口相对光标下移的像素

    // 详情弹窗统一生成在鼠标点击的位置（聊天栏里点词也一样跟着鼠标，不再放屏幕中央上方）。

    public static final List<PopupWindow> WINDOWS = new ArrayList<>();
    private static PopupWindow sticky;
    private static int hoverSlotId = -1;
    private static net.minecraft.world.item.ItemStack hoverStack = net.minecraft.world.item.ItemStack.EMPTY;
    private static long hoverStartMs;
    private static boolean lockedForHoverSlot;
    private static int dismissedSlotId = -1;
    private static long dismissedAtMs;
    private static Screen lastScreen;

    // 按下/拖动状态机
    private static PopupWindow pressWindow;
    private static PopupWindow.Seg pressSeg;
    private static long pressStartNs;
    private static double grabDx, grabDy;
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

    public static void render(GuiGraphicsExtractor ctx) {
        double x = scaledMouseX(), y = scaledMouseY();
        frameTick((int) x, (int) y, ctx.guiWidth(), ctx.guiHeight());
        LoreAdvancements.render(ctx, x, y, leftDown);
        for (PopupWindow popup : WINDOWS) {
            ctx.nextStratum();
            int px = (int) popup.x, py = (int) popup.y;
            int bg = 0xFF100014;
            ctx.fill(px - 3, py - 3, px + popup.w + 3, py + popup.h + 3, bg);
            ctx.fill(px - 3, py - 3, px + popup.w + 3, py - 2, 0xFF00FFFC);
            ctx.fill(px - 3, py + popup.h + 2, px + popup.w + 3, py + popup.h + 3, 0xFF00FFFC);
            ctx.fill(px - 3, py - 2, px - 2, py + popup.h + 2, 0xFF0000FF);
            ctx.fill(px + popup.w + 2, py - 2, px + popup.w + 3, py + popup.h + 2, 0xFF0000FF);
            drawText(ctx, popup, bg);
        }
        if (hasSparks()) {
            ctx.nextStratum();
            drawSparks(ctx);
        }
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
        pressStartNs = System.nanoTime();
        grabDx = mx - w.x;
        grabDy = my - w.y;
        dragging = false;
    }

    /** 每帧调用：先清掉“来源界面已关闭”的弹窗，再处理拖动、松手点击、边界、粒子。 */
    public static void frameTick(int mouseX, int mouseY, int screenW, int screenH) {
        closeWindowsOfClosedScreens();
        long now = System.nanoTime();
        boolean down = leftDown;
        if (pressWindow != null) {
            if (down && !dragging && now - pressStartNs >= DRAG_NS) {
                dragging = true;
            }
            if (down && dragging) {
                pressWindow.x = clampD(mouseX - grabDx, 2, screenW - pressWindow.w - 2);
                pressWindow.y = clampD(mouseY - grabDy, 2, screenH - pressWindow.h - 2);
            }
            if (!down) release(mouseX, mouseY);
        }
        for (PopupWindow w : WINDOWS) {
            w.x = clampD(w.x, 2, Math.max(2, screenW - w.w - 2));
            w.y = clampD(w.y, 2, Math.max(2, screenH - w.h - 2));
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
        WINDOWS.removeIf(w -> {
            if (w.owner == current) {
                return false;
            }
            if (w == sticky) {
                sticky = null;
                lockedForHoverSlot = false;
            }
            if (w == pressWindow) {
                clearPress();
            }
            return true;
        });
    }

    /** 每帧调用（有屏幕时）：悬停超过 0.5 秒就把物品描述固定成弹窗（位置贴在背包左侧）。 */
    public static void hoverTick(AbstractContainerScreen<?> hs) {
        if (Minecraft.getInstance().player == null) return;
        if (hs != lastScreen) {
            lastScreen = hs;
            closeSticky();
            hoverSlotId = -1;
            dismissedSlotId = -1;
        }
        Slot slot = ((LoreContainerScreenAccessor) hs).ctt$getHoveredSlot();
        if (slot == null || slot.getItem().isEmpty() || !hs.getMenu().getCarried().isEmpty()
                || pressWindow != null || peekTopAt(scaledMouseX(), scaledMouseY()) != null) {
            hoverSlotId = -1;
            return;
        }
        long now = System.currentTimeMillis();
        if (hoverSlotId != slot.index || !net.minecraft.world.item.ItemStack.matches(hoverStack, slot.getItem())) {
            hoverSlotId = slot.index;
            hoverStack = slot.getItem().copy();
            hoverStartMs = now;
            lockedForHoverSlot = false;
        }
        if (lockedForHoverSlot) {
            return;
        }
        if (dismissedSlotId == slot.index && now - dismissedAtMs < RELOCK_GRACE_MS) {
            return;
        }
        if (now - hoverStartMs < LOCK_MS) {
            return;
        }
        lockTooltip(hs, slot);
    }

    public static boolean hidesTooltip(AbstractContainerScreen<?> screen, double x, double y) {
        return sticky != null && sticky.owner == screen
                && (lockedForHoverSlot && hoverSlotId >= 0 || peekTopAt(x, y) != null);
    }

    private static void lockTooltip(AbstractContainerScreen<?> hs, Slot slot) {
        Minecraft client = Minecraft.getInstance();
        Font tr = client.font;
        List<Component> lines = ((LoreContainerScreenAccessor) hs).ctt$getTooltip(slot.getItem());
        if (lines.isEmpty()) {
            return;
        }
        // 先量一侧能放多宽，再按这个上限去排版（超长描述自动换行），
        // 这样描述窗几乎总能待在背包界面的左侧 —— 以前不换行，描述一长就宽过屏幕，
        // 左边放不下只能整个挪到右边去，这就是「不是所有物品都在物品栏左侧」的原因。
        LoreContainerScreenAccessor acc = (LoreContainerScreenAccessor) hs;
        int screenW = client.getWindow().getGuiScaledWidth();
        int screenH = client.getWindow().getGuiScaledHeight();
        int gx = acc.tooltipLoreGuiX();
        int gy = acc.tooltipLoreGuiY();
        int gw = acc.tooltipLoreGuiWidth();
        int gh = acc.tooltipLoreGuiHeight();
        int leftSpace = gx - 10;                       // 背包左边能用的宽度
        int rightSpace = screenW - gx - gw - 10;       // 右边能用的宽度
        boolean onLeft = leftSpace >= PopupWindow.MIN_W || rightSpace < PopupWindow.MIN_W;
        int space = Math.max(onLeft ? leftSpace : rightSpace, PopupWindow.MIN_W);
        int maxW = Math.min(Math.min(space, PopupWindow.MAX_W), Math.max(screenW - 8, PopupWindow.MIN_W));
        PopupWindow w = PopupWindow.fromTooltip(lines, tr,
                Component.translatable("tooltip_lore.hint.lock").getString(), maxW);
        w.sticky = true;
        w.owner = hs; // 关背包 / 按 ESC 时随界面一起收起
        // 贴在背包界面的界面一侧（左边放不下才放右侧），不再跟着鼠标跑：
        // 描述窗在一边、鼠标在另一边，互不遮挡，也彻底避开屏幕右下角的聊天栏。
        double x = onLeft ? gx - w.w - 8 : gx + gw + 8;
        double y = gy + (gh - w.h) / 2.0;
        w.x = clampD(x, 2, Math.max(2, screenW - w.w - 2));
        w.y = clampD(y, 2, Math.max(2, screenH - w.h - 2));
        // 换物品时固定窗是「原地换内容」，不是新开的窗口：插回它原来那一层，
        // 免得重新生成的固定窗跳到最上面，把玩家点开的详情窗压住（截图里就是这个毛病）。
        int at = WINDOWS.size();
        if (sticky != null) {
            int old = WINDOWS.indexOf(sticky);
            WINDOWS.remove(sticky);
            if (old >= 0) {
                at = Math.min(old, WINDOWS.size());
            }
        }
        WINDOWS.add(at, w);
        sticky = w;
        lockedForHoverSlot = true;
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
        // 跟着鼠标：出现在点击位置的右下方，光标不会压住标题；
        // 右下放不下（贴屏幕边缘）就翻到左下 / 右上 / 左上
        double x = mx + MOUSE_GAP_X;
        double y = my + MOUSE_GAP_Y;
        if (x + pw.w > screenW - 2) {
            x = mx - pw.w - MOUSE_GAP_X;
        }
        if (y + pw.h > screenH - 2) {
            y = my - pw.h - MOUSE_GAP_Y;
        }
        if (x < 2) {
            x = 2;
        }
        if (y < 2) {
            y = 2;
        }
        for (PopupWindow other : WINDOWS) {
            if (Math.abs(other.x - x) < 4 && Math.abs(other.y - y) < 4) {
                x += 14;
                y += 12;
            }
        }
        pw.x = clampD(x, 2, screenW - pw.w - 2);
        pw.y = clampD(y, 2, screenH - pw.h - 2);
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

    /** 点空白：关闭全部弹窗（含固定描述），并在短暂宽限期内不自动重锁。 */
    public static void closeAll() {
        if (sticky != null) {
            if (hoverSlotId >= 0) {
                dismissedSlotId = hoverSlotId;
                dismissedAtMs = System.currentTimeMillis();
            }
            sticky = null;
            lockedForHoverSlot = false;
        }
        WINDOWS.clear();
        clearPress();
    }

    /** 只收起固定描述窗（例如切换屏幕 / 关闭背包时），保留玩家手动点开的词条窗。 */
    private static void closeSticky() {
        if (sticky != null) {
            WINDOWS.remove(sticky);
            sticky = null;
            lockedForHoverSlot = false;
        }
    }

    public static boolean release(double x, double y) {
        if (LoreAdvancements.release()) return true;
        if (pressWindow == null) return false;
        PopupWindow.Seg hit = pressWindow.segAt(x, y);
        boolean open = !dragging && System.nanoTime() - pressStartNs < DRAG_NS && hit != null && hit == pressSeg;
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
            hoverSlotId = dismissedSlotId = -1;
            hoverStack = net.minecraft.world.item.ItemStack.EMPTY;
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

    private static double clampD(double v, double lo, double hi) {
        return hi < lo ? lo : Math.max(lo, Math.min(hi, v));
    }
}
