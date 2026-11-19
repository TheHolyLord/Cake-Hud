package com.ctt.healthdisplay.client;

import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.advancements.AdvancementWidget;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/** Shared advancement card for both advancement screens. */
public final class LoreAdvancements {
    private static final int PAPER = 0xFFD8C28A;
    private static final int EDGE = 0xFF2A1D0D;
    private static Screen owner;
    private static Object tab;
    private static boolean tabSeen;
    private static Node hovered, pinned, shown, cachedNode;
    private static boolean cachedPinned, pressing;
    private static int cachedWidth, cachedPercent;
    private static String cachedFraction;
    private static PopupWindow card;
    private static long cachedRevision;

    private LoreAdvancements() {}

    private record Node(AdvancementWidget widget, AdvancementProgress progress, int x, int y) {}

    public static void startFrame(Screen screen) {
        if (owner != screen) {
            owner = screen;
            tab = null;
            reset();
        }
        tabSeen = false;
        hovered = null;
    }

    public static void tab(Object selected) {
        if (!(owner instanceof AdvancementsScreen)) return;
        if (tab != selected) reset();
        tab = selected;
        tabSeen = true;
    }

    public static boolean capture(GuiGraphicsExtractor ctx, AdvancementWidget widget,
                                  AdvancementProgress progress, int ox, int oy) {
        if (!tabSeen || widget.getDisplay() == null) return false;
        hovered = locate(ctx, widget, progress, ox, oy);
        return true;
    }

    public static void trackPinned(GuiGraphicsExtractor ctx, AdvancementWidget widget,
                                   AdvancementProgress progress, int ox, int oy) {
        if (pinned != null && pinned.widget() == widget) pinned = locate(ctx, widget, progress, ox, oy);
    }

    private static Node locate(GuiGraphicsExtractor ctx, AdvancementWidget widget,
                               AdvancementProgress progress, int ox, int oy) {
        int pad = widget.getClass().getName().startsWith("de.dafuqs.paginatedadvancements.") ? 3 : 0;
        var pos = ctx.pose().transformPosition(ox + widget.getX() + pad, oy + widget.getY(), new org.joml.Vector2f());
        return new Node(widget, progress, Math.round(pos.x), Math.round(pos.y));
    }

    public static void render(GuiGraphicsExtractor ctx, double mx, double my, boolean down) {
        if (!tabSeen) {
            reset();
            return;
        }
        if (!down) pressing = false;
        Node next = pinned != null ? pinned : !down ? hovered : null;
        // Keep the card under the pointer, including the one-pixel gap above the node.
        if (pinned == null && !down && overCard(mx, my, 1)) next = shown;
        shown = next;
        if (next == null) {
            card = null;
            return;
        }
        var display = next.widget().getDisplay();
        var progress = next.progress();
        int pct = progress == null ? -1 : Math.round(progress.getPercent() * 100);
        Component fraction = progress == null ? null : progress.getProgressText();
        String fractionText = fraction == null ? null : fraction.getString();
        int width = Math.max(90, Math.min(216, ctx.guiWidth() - 12));
        long revision = LoreRegistry.revision();
        if (card == null || !next.equals(cachedNode) || cachedPinned != (pinned != null)
                || cachedWidth != width || cachedPercent != pct || cachedRevision != revision
                || !java.util.Objects.equals(cachedFraction, fractionText)) {
            card = PopupWindow.fromAdvancement(display.title(), display.description(), fractionText,
                    Component.translatable(pinned == null ? "tooltip_lore.hint.adv_hover" : "tooltip_lore.hint.adv_pinned").getString(),
                    Minecraft.getInstance().font, width, 32, 28, EDGE, 0xFF4A3418, 0xFF6A5230);
            card.icon = display.icon().create();
            card.accent = switch (display.type()) {
                case CHALLENGE -> 0xFFB16F50;
                case GOAL -> 0xFFACB4B9;
                default -> 0xFFD7BC5C;
            };
            card.progressPct = pct;
            cachedNode = next;
            cachedPinned = pinned != null;
            cachedPercent = pct;
            cachedWidth = width;
            cachedRevision = revision;
            cachedFraction = fractionText;
        }
        int x = next.x() - PopupWindow.PAD_X;
        int y = next.y() - card.h - 1;
        if (y < 2) y = next.y() + 27;
        if (x + card.w > ctx.guiWidth() - 2) x = next.x() + 26 - card.w;
        card.x = Math.clamp(x, 2, Math.max(2, ctx.guiWidth() - card.w - 2));
        card.y = Math.clamp(y, 2, Math.max(2, ctx.guiHeight() - card.h - 2));
        x = (int) card.x;
        y = (int) card.y;
        ctx.nextStratum();
        ctx.fill(x, y, x + card.w, y + card.h, PAPER);
        ctx.outline(x, y, card.w, card.h, EDGE);
        ctx.outline(x + 1, y + 1, card.w - 2, card.h - 2, 0xFFB59865);
        int ix = x + 4, iy = y + 4;
        ctx.fill(ix, iy, ix + 26, iy + 26, EDGE);
        ctx.outline(ix + 1, iy + 1, 24, 24, card.accent);
        ctx.fakeItem(card.icon, ix + 5, iy + 5);
        if (pct > 0 && pct < 100) ctx.fill(ix + 2, iy + 23, ix + 2 + Math.max(1, 22 * pct / 100), iy + 24, card.accent);
        LorePopupManager.drawText(ctx, card, PAPER);
    }

    private static boolean overCard(double x, double y, int margin) {
        return card != null && x >= card.x - margin && x < card.x + card.w + margin
                && y >= card.y - margin && y < card.y + card.h + margin;
    }

    public static boolean click(Screen screen, double x, double y) {
        if (owner != screen || !tabSeen) return false;
        if (overCard(x, y, 0)) {
            var segment = card.segAt(x, y);
            if (segment != null) LorePopupManager.openEntry(segment.word, x, y, segment.color);
            if (pinned == null) pin(shown);
            pressing = true;
            return true;
        }
        if (hovered != null && x >= hovered.x() - 2 && x <= hovered.x() + 28
                && y >= hovered.y() - 2 && y <= hovered.y() + 28) {
            pin(pinned != null && pinned.widget() == hovered.widget() ? null : hovered);
            pressing = true;
            LorePopupManager.closeAll();
            return true;
        }
        pin(null);
        shown = null;
        card = null;
        return false;
    }

    public static boolean release() {
        boolean consumed = pressing;
        pressing = false;
        return consumed;
    }

    private static void pin(Node node) {
        if (pinned == node) return;
        pinned = node;
        var player = Minecraft.getInstance().player;
        if (player != null) player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.3f, 1.4f);
    }

    private static void reset() {
        hovered = pinned = shown = cachedNode = null;
        card = null;
        cachedFraction = null;
        pressing = false;
    }
}
