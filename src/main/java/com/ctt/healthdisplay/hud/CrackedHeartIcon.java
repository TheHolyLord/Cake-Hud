package com.ctt.healthdisplay.hud;

/** 用矩形画出带深色边缘和裂纹的碎心。HUD 和头顶都能直接使用。 */
final class CrackedHeartIcon {
    static final int WIDTH = 9;
    static final int HEIGHT = 8;
    private static final String[] PIXELS = {
            ".##...##.",
            "#++#.#++#",
            "#+++#+++#",
            "#++#++++#",
            ".#+##++#.",
            "..#+#+#..",
            "...#+#...",
            "....#...."
    };

    private CrackedHeartIcon() {}

    static void draw(int color, HeadHealthStyle.RowConsumer draw) {
        int outline = (color & 0xFF000000) | 0x140909;
        for (int y = 0; y < HEIGHT; y++) {
            String row = PIXELS[y];
            for (int start = 0; start < WIDTH; ) {
                char pixel = row.charAt(start);
                int end = start + 1;
                while (end < WIDTH && row.charAt(end) == pixel) end++;
                if (pixel != '.') draw.accept(start, y, end, pixel == '#' ? outline : color);
                start = end;
            }
        }
    }
}
