package com.ctt.healthdisplay.hud;

import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class HeadHealthStyle {
    private static final int TRACK = 0x382D39;

    private HeadHealthStyle() {}

    public static int alpha(int color, int percent) {
        return (color & 0xFFFFFF) | (Math.round((color >>> 24) * percent / 100f) << 24);
    }

    static int dim(int rgb) {
        return shade(rgb, 0.8);
    }

    static final int COLOR_RED_HEART = 0xFFE84040;
    static final int COLOR_CRACKED_HEART = 0xFF881B1B;
    static final int COLOR_SOUL_HEART = 0xFFDDCC22;
    static final int COLOR_BLACK_HEART = 0xFF550088;
    static final int COLOR_BLUE_HEART = 0xFF4488EE;

    private static final int[] HEART_LAYER_COLORS = {
            COLOR_RED_HEART, COLOR_CRACKED_HEART, COLOR_SOUL_HEART, COLOR_BLACK_HEART, COLOR_BLUE_HEART
    };

    static int healthBarCapacity(int maxHp) {
        if (maxHp <= 50) return 50;
        return Math.max(100, maxHp);
    }

    /** 按红→碎→魂→黑→蓝覆盖左侧。将重叠的前缀分成不相交的色段，避免半透明时混色。 */
    static void playerSpans(int width, int maxHp, long total, int cracked, int soul, int black, int blue,
                            SpanConsumer draw) {
        int capacity = healthBarCapacity(maxHp);
        long[] layers = {total, cracked, soul, black, blue};
        int start = 0;
        for (int i = layers.length - 1; i >= 0; i--) {
            double fraction = Math.clamp((double) layers[i] / capacity, 0, 1);
            int end = (int) Math.round(width * fraction);
            if (end > start) {
                draw.accept(start, end, HEART_LAYER_COLORS[i]);
                start = end;
            }
        }
        if (width > start) draw.accept(start, width, -1);
    }

    static void drawEnemy(GuiGraphicsExtractor context, int x, int y, int width, int total, int max,
                          int extra, int extraMax, int redColor, int yellowColor, int opacity) {
        int filled = pixels(width, (double) total / Math.max(1, max));
        int yellow = Math.min(filled, pixels(width, (double) extra / Math.max(1, extraMax)));
        int red = pixels(width, (double) (total - extra) / Math.max(1, max));
        filled = Math.min(filled, yellow + red);
        if (yellow > 0) drawSpan(context, x, y, width, 0, yellow, yellowColor, opacity);
        if (filled > yellow) drawSpan(context, x, y, width, yellow, filled, redColor, opacity);
        if (width > filled) drawSpan(context, x, y, width, filled, width, -1, opacity);
    }

    private static void drawSpan(GuiGraphicsExtractor context, int x, int y, int width,
                                 int start, int end, int color, int opacity) {
        rows(x, y, width, (left, top, right, track) -> {
            int from = Math.max(left, x + start);
            int to = Math.min(right, x + end);
            if (to > from) context.fill(from, top, to, top + 1,
                    alpha(color == -1 ? track : tone(color, top - y), opacity));
        });
    }

    private static int pixels(int width, double ratio) {
        return ratio > 0 ? Math.max(1, (int) Math.round(width * Math.min(1, ratio))) : 0;
    }

    static int tone(int rgb, int row) {
        return shade(rgb, switch (row) {
            case 0 -> 0.58;
            case 1 -> 1.22;
            case 3 -> 0.83;
            case 4 -> 0.49;
            default -> 1.0;
        });
    }

    private static int shade(int rgb, double factor) {
        int r = Math.min(255, Math.round((float) ((rgb >> 16 & 255) * factor)));
        int g = Math.min(255, Math.round((float) ((rgb >> 8 & 255) * factor)));
        int b = Math.min(255, Math.round((float) ((rgb & 255) * factor)));
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    static void rows(int x, int y, int width, RowConsumer draw) {
        for (int row = 0; row < 5; row++) {
            int inset = row == 0 || row == 4 ? 1 : 0;
            draw.accept(x + inset, y + row, x + width - inset, tone(TRACK, row));
        }
    }

    @FunctionalInterface
    interface RowConsumer {
        void accept(int left, int top, int right, int trackColor);
    }

    @FunctionalInterface
    interface SpanConsumer {
        void accept(int start, int end, int color);
    }
}
