package com.ctt.healthdisplay.hud;

import net.minecraft.resources.Identifier;

/**
 * fork 的血条纹理（原版 {@code boss_bar/*}，182×5）调用封装。
 *
 * <p>与 fork 的 {@code BossBarTexture} 一一对应：直接指向
 * {@code textures/gui/sprites/boss_bar/*_background.png} / {@code *_progress.png} 原文件
 * （资源包替换会同步生效），并且按「<b>两端端盖按纹素原尺寸、中段拉伸</b>」切片 ——
 * 182 纹素整条拉伸会把两端 4 纹素的渐晕与圆角压成不到 1 px，条就变成方的了。</p>
 */
public final class ForkBarTexture {

    /** 原版血条纹理尺寸。 */
    public static final int NATIVE_WIDTH = 182;
    public static final int NATIVE_HEIGHT = 5;

    /**
     * 两端「端盖」的宽度（纹素）。
     *
     * <p>实测 {@code boss_bar/*_progress.png}：左端 0~3 列、右端 178~181 列与中间平直段不同
     * （第 0 / 181 列把上下角的不透明度挖成 0，第 1 / 180 列整列压暗……），这就是那条「圆头」。</p>
     */
    public static final int CAP_TEXELS = 4;

    /** 条颜色（名字就是原版 sprite 的前缀）。 */
    public enum Tint { RED, YELLOW, PURPLE, BLUE, GREEN }

    private static final Tint DEFAULT = Tint.RED;

    private ForkBarTexture() {}

    /**
     * 心类 → 条颜色（与 fork / 需求一致）：红心→红、魂心→黄、黑心→紫、蓝心→蓝；玩家固定绿。
     *
     * @param kind {@link ForkHudLayout#RED} … {@link ForkHudLayout#PINK}
     */
    public static Tint tintFor(int kind) {
        return switch (kind) {
            case ForkHudLayout.SOUL -> Tint.YELLOW;
            case ForkHudLayout.BLACK -> Tint.PURPLE;
            case ForkHudLayout.BLUE -> Tint.BLUE;
            default -> Tint.RED;
        };
    }

    /** 空槽纹理 id。 */
    public static Identifier backgroundTexture(Tint tint) {
        return texture(tint == null ? DEFAULT : tint, false);
    }

    /** 已填充段纹理 id。 */
    public static Identifier progressTexture(Tint tint) {
        return texture(tint == null ? DEFAULT : tint, true);
    }

    private static Identifier texture(Tint tint, boolean progress) {
        return Identifier.withDefaultNamespace("textures/gui/sprites/boss_bar/"
                + tint.name().toLowerCase(java.util.Locale.ROOT)
                + (progress ? "_progress" : "_background") + ".png");
    }

    /**
     * 把一段纹理横跨 {@code [xa, xb]} 切成若干个相邻四边形。
     *
     * <p>返回每段 {@code {x0, x1, u0, u1}}（x 是布局像素，u 是<b>纹素</b> 0~182）。</p>
     * <ul>
     *   <li>左端盖：仅当 u 从纹理最左（{@code ua == 0}）开始时才画，宽度固定
     *       {@link #CAP_TEXELS} 纹素 = 同样多的布局像素（1 纹素 : 1 单位）；</li>
     *   <li>右端盖：仅当 u 到纹理最右（{@code ub == 182}）时才画；进度条没满时右边界
     *       应该是直切的（原版 {@code blitSprite} 同样直切）；</li>
     *   <li>中段：整段拉伸。</li>
     * </ul>
     * <p>条太短（{@code xb - xa < 2 × CAP_TEXELS}）时端盖会撞车，退回整段拉伸。</p>
     */
    public static float[][] slices(float xa, float xb, float ua, float ub) {
        final float nw = NATIVE_WIDTH;
        final float cap = CAP_TEXELS;
        final boolean caps = xb - xa >= cap * 2.0f;
        final boolean left = caps && ua <= 0.0f;
        final boolean right = caps && ub >= nw;

        final float xl = left ? xa + cap : xa;
        final float xr = right ? xb - cap : xb;
        final float ul = left ? cap : ua;
        final float ur = right ? nw - cap : ub;

        java.util.List<float[]> out = new java.util.ArrayList<>(3);
        if (left) out.add(new float[]{xa, xa + cap, 0.0f, cap});
        if (xr > xl) out.add(new float[]{xl, xr, ul, ur});
        if (right) out.add(new float[]{xb - cap, xb, nw - cap, nw});
        return out.toArray(new float[0][]);
    }

    /** {@code 0xRRGGBB} + alpha → {@code 0xAARRGGBB}。 */
    public static int argb(int rgb, int alpha) {
        return ((alpha & 0xFF) << 24) | (rgb & 0xFFFFFF);
    }
}
