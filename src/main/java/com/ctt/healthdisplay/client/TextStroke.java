package com.ctt.healthdisplay.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * 窗口文字可读性（第 37 轮：羊皮纸上彻底不铺墨，深色窗里只留"越过正文"的那一笔）。
 *
 * ── 这一轮从用户截图里量出来的东西 ──
 * 进度牌面：底色羊皮纸 #D8C28A（亮度 194），三行字实测：
 *   标题  金 #FFAA00  亮度 176  上一版给它 17% 黑纱 → 纱落在屏幕上 = 亮度 161
 *   描述  深棕 #4A3418 亮度  55  上一版这一笔显不出来，没画
 *   提示  棕 #6A5230  亮度  85  上一版给它 13% 黑纱 → 纱落在屏幕上 = 亮度 168
 * 把"纱"的实际亮度摆到一条线上看就明白糊在哪：
 *   提示行： 字 85 ─────── 纱 168 ── 底 194      纱夹在字和底色中间
 *   标题：   底 194 ─ 纱 161 ─ 字 176            纱只比字暗 15 阶
 * 描边落在正文和底色的**中间**，它就不可能提高对比度 —— 它只是把"字→底色"这个硬边
 * 拆成了"字→纱→底"两个软边，也就是给每条笔画都铺了一层渐变。汉字笔画之间本来就只隔
 * 一个像素，这层渐变把缝填平，看上去就是"黑色描边很糊"。浓淡根本调不出来：
 * 再淡就没用，再浓就是往字上抄一遍。
 *
 * ── 这一版的规则 ──
 * 1. **羊皮纸（浅底）上绝不铺墨**。要救的字一律改字本身：保持色相、往暗压
 *    （金 → 焦金，黄 → 橄榄，青 → 深青），压到和纸面差 80 个色阶为止。
 *    零墨、零渐变，笔画之间的缝一个像素都不填 —— 拿截图真实字形重绘比过，
 *    这条路比任何描边都干净，也比"看得清"这条要求更达标。
 * 2. **深色窗里那一笔必须"越过正文"才算数**：只有比字本身还亮 12 阶以上，
 *    它才是一道真的亮边（把暗字从暗底里托出来）；落在字和底色中间的一律不画。
 *    这条把上一版剩下的那层白纱（暗蓝、暗紫这类饱和深色上 9% 的白）也砍了。
 * 3. **和底色本来就分得开的字，一笔都不加**：亮度差 ≥ 80 直接跳过。
 *    白字、灰字压近黑底属于这类 —— 上一版用户已经认可深色窗，这次只会更轻。
 * 4. 真的贴在一起（色差 < 45）的字，还是先推字色：浅底压暗、深底提亮，保持色相。
 *    §0 纯黑字压在近黑底上会被提到中灰，不靠白边硬救。
 *
 * 几何不变：只抄 1 笔、落在右下对角 (1,1)，和原版文字阴影同一个落点；原版阴影一律关掉。
 */
public final class TextStroke {

    /** 只抄这一笔：右下对角，和原版文字阴影同一个落点。 */
    private static final int DX = 1;
    private static final int DY = 1;

    /** 亮度差大于这个值就算浅底（羊皮纸 194 / 近黑底 7，两边都离这条线很远）。 */
    private static final int LIGHT_BG = 128;

    // ── 推字色：浅底（羊皮纸）——只看亮度差，色相不算分，因为纸上的"淡"就是糊 ──
    /** 差到这个数以下才动手（金 18 / 灰 24 / 黄 41 / 白 61 都要救；棕 109、蓝 90 不动）。 */
    private static final int PAPER_TRIGGER = 70;
    /** 推到这个数才停手（再高就要把字压成黑炭了）。 */
    private static final int PAPER_TARGET = 80;

    // ── 推字色：深底 —— 沿用上一版判据（色相也计分），深色窗用户已经认可，别乱动 ──
    private static final int DARK_TRIGGER = 45;
    private static final int DARK_TARGET = 105;

    // ── 描边那一笔（只在深底可能出现）──
    /** 亮度差 ≥ 这个数：字和底色早就分开了，一笔都不加。 */
    private static final int SEP_SKIP = 80;
    /** 亮度差 ≤ 这个数：这一笔最浓。 */
    private static final int SEP_FULL = 15;
    /** 这一笔必须比正文还亮这么多阶，否则它只是铺在字和底色中间的渐变 = 糊。 */
    private static final int OVERSHOOT = 12;
    /** 这一笔至少要让底色亮这么多阶，否则画了也看不见。 */
    private static final int RING_VISIBLE = 18;
    /** 白纱的 alpha（0~255）：最浓 24 也才 9%，是一层纱不是一笔墨。 */
    private static final int WHITE_MIN = 8;
    private static final int WHITE_MAX = 24;

    private TextStroke() { }

    // ───────────────────────────── 对外入口 ─────────────────────────────

    /**
     * 带描边地画一行字：先垫一笔（多数情况直接省掉），再压正文。
     * x/y 是正文位置，color 取低 24 位当文字色，bg 是这块窗口的底色。
     */
    public static void draw(GuiGraphicsExtractor ctx, Font tr, String text, int x, int y, int color, int bg) {
        int eff = readable(color & 0xFFFFFF, bg);
        int ring = ringFor(eff, bg);
        if (ring != 0) {
            ctx.text(tr, text, x + DX, y + DY, ring, false);
        }
        ctx.text(tr, text, x, y, 0xFF000000 | eff, false);
    }

    /**
     * 这一行字最终会显示成什么颜色（可能因为太贴底色被提亮/压暗过）。
     * 下划线、进度条之类要跟正文同色的东西必须用它，否则线和字会不是一个颜色。
     */
    public static int effective(int color, int bg) {
        return readable(color & 0xFFFFFF, bg);
    }

    // ───────────────────────────── 颜色规则 ─────────────────────────────

    /** 感知亮度（0~255）。 */
    private static int luma(int rgb) {
        int r = rgb >> 16 & 0xFF;
        int g = rgb >> 8 & 0xFF;
        int b = rgb & 0xFF;
        return (r * 299 + g * 587 + b * 114) / 1000;
    }

    /** 红绿蓝三个通道各自差多少，取最大的那个 —— 比亮度更能说明"颜色上分不分得开"。 */
    private static int channelDelta(int a, int b) {
        int dr = Math.abs((a >> 16 & 0xFF) - (b >> 16 & 0xFF));
        int dg = Math.abs((a >> 8 & 0xFF) - (b >> 8 & 0xFF));
        int db = Math.abs((a & 0xFF) - (b & 0xFF));
        return Math.max(dr, Math.max(dg, db));
    }

    /**
     * 这行字和底色分不分得开（0~255）。
     * 浅底只看亮度差：纸上的淡色字（金、黄、青）色相再分明，一样会化在纸里。
     * 深底按老规矩给色相打个折补一点分：暗蓝暗紫压在近黑底上靠颜色还认得出来，别乱改。
     */
    private static int separation(int rgb, int bg) {
        int byLuma = Math.abs(luma(rgb) - luma(bg));
        if (luma(bg) >= LIGHT_BG) {
            return byLuma;
        }
        return Math.max(byLuma, channelDelta(rgb, bg) * 2 / 3);
    }

    /** a 往 b 方向按 percent% 混色（等价于 b 以 percent% 的不透明度盖在 a 上）。 */
    private static int mix(int a, int b, int percent) {
        int ar = a >> 16 & 0xFF;
        int ag = a >> 8 & 0xFF;
        int ab = a & 0xFF;
        int r = ar + ((b >> 16 & 0xFF) - ar) * percent / 100;
        int g = ag + ((b >> 8 & 0xFF) - ag) * percent / 100;
        int bl = ab + ((b & 0xFF) - ab) * percent / 100;
        return clamp(r) << 16 | clamp(g) << 8 | clamp(bl);
    }

    private static int clamp(int v) {
        return v < 0 ? 0 : Math.min(v, 255);
    }

    private static int clamp01(int v) {
        return v < 0 ? 0 : Math.min(v, 100);
    }

    /** 字太贴底色时，按原色相往远离底色的方向推（纸面压暗、深底提亮），比任何描边都干净。 */
    private static int readable(int rgb, int bg) {
        int trig = luma(bg) >= LIGHT_BG ? PAPER_TRIGGER : DARK_TRIGGER;
        int targ = luma(bg) >= LIGHT_BG ? PAPER_TARGET : DARK_TARGET;
        if (separation(rgb, bg) >= trig) {
            return rgb;
        }
        int push = luma(bg) >= LIGHT_BG ? 0x000000 : 0xFFFFFF;
        for (int k = 1; k <= 100; k++) {
            if (separation(mix(rgb, push, k), bg) >= targ) {
                return mix(rgb, push, k);
            }
        }
        return push;
    }

    /**
     * 描边那一笔：浅底一律返回 0（不铺墨），深底只允许"越过正文"的亮边。
     * 返回 0 表示这一笔不画。
     */
    private static int ringFor(int rgb, int bg) {
        int lb = luma(bg);
        if (lb >= LIGHT_BG) {
            return 0;
        }
        int lt = luma(rgb);
        int sep = Math.abs(lt - lb);
        if (sep >= SEP_SKIP) {
            return 0;   // 早就分得开，加了只会加粗
        }
        int need = clamp01((SEP_SKIP - sep) * 100 / (SEP_SKIP - SEP_FULL));
        int alpha = WHITE_MIN + (WHITE_MAX - WHITE_MIN) * need / 100;
        int lr = luma(mix(bg, 0xFFFFFF, alpha * 100 / 255));
        if (lr < lt + OVERSHOOT) {
            return 0;   // 落在字和底色中间 = 给边缘铺渐变 = 糊，宁可不画
        }
        if (lr - lb < RING_VISIBLE) {
            return 0;   // 压在底色上显不出来
        }
        return alpha << 24 | 0xFFFFFF;
    }
}
