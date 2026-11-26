package com.ctt.healthdisplay.hud;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 「CTT HUD - Fork」头顶排版的<b>逐像素复刻</b>（纯计算，不引用任何 Minecraft 类）。
 *
 * <p>本类等价于 fork 的 {@code HudLayout} + {@code HeartCounts} + {@code HeartCycle} +
 * {@code ArmourCycle} 四件套，只把其中依赖 fork 内部类型（{@code EnemyData}）的常量
 * 内联进来，因此可以在上游 mod 里原样复用。坐标系与 fork 完全一致：
 * <b>原点 = 原版名牌锚点，y 向下为正，1 像素 = 0.025 格</b>。</p>
 *
 * <h2>固定排版</h2>
 * <pre>
 *   敌人：  护甲行（🛡 防御 + 真盔甲 + 2 项元素护甲，🛡 与元素之间两个空格）   top = -28
 *           ❤当前/上限 + 一种轮换心（魂/黑/蓝/碎/粉）                        top = -18
 *   ───────── 血条（-7 ~ -2）─────────
 *   名称（原版名牌，0 ~ 9）
 *
 *   玩家：  碎心/粉心 + 蓝心 + 魂心/黑心（同时显示）                        top = -28
 *           ❤当前/上限 + 一种轮换心（魂 / 黑）                              top = -18
 *   ───────── 血条 ─────────
 *   名称
 * </pre>
 *
 * <p>行距 10 像素：原版名牌背景框是 {@code y-1 ~ y+9}，比字体行高多 1 像素，
 * 行距必须是 10，否则上行的底边会压住下行的顶边 1 像素（两层 25% 黑叠成暗带）。</p>
 */
public final class ForkHudLayout {

    // ── 几何常量（与 fork 逐字一致） ──

    /** 最下面一行文字的顶部（布局像素）。 */
    public static final int TEXT_TOP = -18;
    /** 血条顶部（布局像素）。血条下沿 = -2，正好留出名字背景框的上沿。 */
    public static final int BAR_TOP = -7;
    /** 血条高度 = 原版 bossbar 纹理高度。 */
    public static final int BAR_HEIGHT = 5;
    /** 名称行顶部（与原版「没有 below_name 分数时」的位置逐像素一致）。 */
    public static final int NAME_TOP = 0;
    /** 名称行高度（原版字体行高）。 */
    public static final int NAME_HEIGHT = 9;
    /** 一行文字的背景框高度（{@code y-1 ~ y+9}）。 */
    public static final int TEXT_BOX_HEIGHT = NAME_HEIGHT + 1;
    /** 行距：往上一行抬这么多像素。必须 ≥ {@link #TEXT_BOX_HEIGHT}。 */
    public static final int ROW_STEP = TEXT_BOX_HEIGHT;

    /** 血条长度下限（原版名牌像素）。 */
    public static final int BAR_WIDTH_MIN = 50;
    /** 血条长度上限。 */
    public static final int BAR_WIDTH_MAX = 150;
    /** 血条长度默认值（fork 1.18.0 起的默认 75）。 */
    public static final int BAR_WIDTH_DEFAULT = 75;

    /** 同一行内各段之间的间隔。 */
    public static final String HEART_GAP = " ";
    /** 「护甲 ↔ 元素护甲」之间的间隔：两个空格。 */
    public static final String ARMOUR_GAP = "  ";
    /** 元素护甲一页最多显示几项。 */
    public static final int ARMOUR_PAGE_SIZE = 2;

    // ── 心类（下标与 fork 的 HeartCounts 一致） ──

    public static final int RED = 0;
    public static final int SOUL = 1;
    public static final int BLACK = 2;
    public static final int BLUE = 3;
    public static final int CRACKED = 4;
    public static final int PINK = 5;
    public static final int KIND_COUNT = 6;

    /** 数值未知（不是 0）。 */
    public static final int UNKNOWN = Integer.MIN_VALUE;

    /** ❤ */
    public static final String HEART_GLYPH = "\u2764";
    /** 地图字体里的裂心（碎 / 粉共用，颜色区分）；没有资源包时退回 ❤。 */
    public static final String CRACKED_GLYPH = "\uE026";

    /** 红=红、魂=黄、黑=紫、蓝=蓝、碎=暗红、粉=亮紫。 */
    public static final int[] HEART_COLORS = {
            0xFF5555, 0xFFFF55, 0xAA00AA, 0x5555FF, 0xAA0000, 0xFF55FF
    };

    // ── 元素护甲（下标与数据包 ArmorDisplay 一致） ──

    /** 元素护甲字形：火 / 冰 / 暗 / 光 / 真盔甲 / 水 / 电。 */
    public static final String[] ARMOUR_ICONS = {
            "\uD83D\uDD25", "\u2603", "\u2620", "\u2600", "\uD83D\uDEE1", "\u2693", "\u26A1"
    };
    /** gold / aqua / dark_purple / white / white / aqua / yellow。 */
    public static final int[] ARMOUR_COLORS = {
            0xFFAA00, 0x55FFFF, 0xAA00AA, 0xFFFFFF, 0xFFFFFF, 0x55FFFF, 0xFFFF55
    };
    /** 真盔甲下标：常驻显示，不参与翻页。 */
    public static final int TRUE_ARMOUR_INDEX = 4;
    /** 🛡 防御的颜色。 */
    public static final int DEFENCE_COLOR = 0x5555FF;

    /** 护甲翻页 / 心类轮换的节拍（毫秒）。 */
    public static final long CYCLE_MS = 2_000L;

    /** 大数值缩写阈值：1000 起用 K / M / B。 */
    public static final int NUM_EXACT_LIMIT = 1_000;

    /** 文字宽度测量器（游戏里是真 Font，测试里可以是假实现）。 */
    public interface WidthMeasurer {
        int width(String text);
    }

    /** 一帧布局需要的开关。 */
    public static final class Options {
        public boolean showNumbers = true;
        public boolean showHearts = true;
        public boolean showArmor = true;
        public boolean showBar = true;
        /** 固定条长（名牌像素），夹在 {@link #BAR_WIDTH_MIN} ~ {@link #BAR_WIDTH_MAX}。 */
        public double barWidth = BAR_WIDTH_DEFAULT;
        /** 元素护甲当前第几页（0 起）。 */
        public int armourPage = 0;
        /** 心数行里「除红心外要显示的那一类心」，-1 = 不显示。 */
        public int cycleKind = -1;
        /** true = 玩家排版（碎/粉/蓝/魂/黑固定在最上面一行）。 */
        public boolean playerStyle = false;
        /** 地图资源包的裂心字形是否可用（不可用退回 ❤）。 */
        public boolean crackedGlyph = false;
    }

    /** 一段带颜色的文字。 */
    public static final class Line {
        public final String text;
        public final int rgb;

        Line(String text, int rgb) {
            this.text = text;
            this.rgb = rgb;
        }
    }

    /** 一整行（若干段首尾相接），多行时各自居中。 */
    public static final class Row {
        public final List<Line> lines;
        /** 本行总宽（布局像素）。 */
        public final int width;
        /** 本行文字顶部（布局像素，向上为负）。 */
        public final int top;

        Row(List<Line> lines, int width, int top) {
            this.lines = lines;
            this.width = width;
            this.top = top;
        }
    }

    /** 布局结果。 */
    public static final class Result {
        /** 从上到下的可见行；最后一个是最靠近血条那行。 */
        public final List<Row> rows;
        /** 最宽那一行文字的像素宽。 */
        public final int textWidth;
        /** 解析后的血条长度（已夹进范围；showBar=false 时为 0）。 */
        public final int barWidth;
        /** 最下面一行的文字顶部。 */
        public final int textTop;
        /** 血条顶部的相对 y。 */
        public final int barTop;

        Result(List<Row> rows, int textWidth, int barWidth, int textTop, int barTop) {
            this.rows = rows;
            this.textWidth = textWidth;
            this.barWidth = barWidth;
            this.textTop = textTop;
            this.barTop = barTop;
        }

        public boolean hasText() {
            return textWidth > 0;
        }

        public boolean isEmpty() {
            return textWidth <= 0 && barWidth <= 0;
        }
    }

    private ForkHudLayout() {}

    // ── 心数数据（对应 fork 的 HeartCounts） ──

    /** 一个数都不知道 —— 界面上退回「当前/最大」。 */
    public static int[] unknownHearts() {
        int[] h = new int[KIND_COUNT];
        Arrays.fill(h, UNKNOWN);
        return h;
    }

    /**
     * 全部心类都已知。碎心按数据包口径拆两栏：{@code cracked > 0} 记碎心，
     * {@code cracked < 0} 取绝对值记粉心。
     */
    public static int[] hearts(int red, int soul, int black, int blue, int cracked) {
        int[] h = new int[KIND_COUNT];
        h[RED] = Math.max(0, red);
        h[SOUL] = Math.max(0, soul);
        h[BLACK] = Math.max(0, black);
        h[BLUE] = Math.max(0, blue);
        h[CRACKED] = Math.max(0, cracked);
        h[PINK] = cracked < 0 ? (int) Math.min(Integer.MAX_VALUE, -(long) cracked) : 0;
        return h;
    }

    /** 敌人：只有红心与魂心，两者都精确（{@code 红 = min(全部, 上限)}）。 */
    public static int[] mobHearts(int allHearts, int maxHp) {
        int all = Math.max(0, allHearts);
        int cap = Math.max(0, maxHp);
        int red = Math.min(all, cap);
        int[] h = new int[KIND_COUNT];
        h[RED] = red;
        h[SOUL] = Math.max(0, all - red);
        return h;
    }

    /** 这一类心的数量；未知返回 {@link #UNKNOWN}。 */
    public static int heart(int[] hearts, int kind) {
        return hearts != null && kind >= 0 && kind < KIND_COUNT ? hearts[kind] : UNKNOWN;
    }

    /** 这一类心存在（数值已知且 &gt; 0）。 */
    public static boolean has(int[] hearts, int kind) {
        int v = heart(hearts, kind);
        return v != UNKNOWN && v > 0;
    }

    /** 至少有类心能画。 */
    public static boolean anyHeart(int[] hearts) {
        if (hearts == null) return false;
        for (int v : hearts) {
            if (v != UNKNOWN && v > 0) return true;
        }
        return false;
    }

    /** 某一类心实际要写的字形：碎 / 粉在没有资源包时退回 ❤。 */
    public static String icon(int kind, boolean crackedGlyph) {
        if (kind == CRACKED || kind == PINK) {
            return crackedGlyph ? CRACKED_GLYPH : HEART_GLYPH;
        }
        return HEART_GLYPH;
    }

    // ── 布局 ──

    /**
     * 计算一帧的布局。
     *
     * @param hp          当前血量（{@code AllHearts}）
     * @param maxHp       最大血量（{@code MaxHP}，红心上限）
     * @param percentOnly true = 只有百分比，没有绝对值
     * @param defence     防御值，0 = 不画
     * @param armour      七档元素护甲，0 = 不画
     * @param hearts      各类心的数量，null / 全未知 = 退回「当前/最大」
     */
    public static Result compute(int hp, int maxHp, boolean percentOnly,
                                 int defence, int[] armour, int[] hearts,
                                 Options options, WidthMeasurer measurer) {
        Options opt = options != null ? options : new Options();
        WidthMeasurer w = measurer != null ? measurer : String::length;

        // ① 最上面那一行：敌人只有护甲；玩家是碎/粉 + 蓝 + 魂/黑。
        List<Line> top = new ArrayList<>(8);
        if (opt.showArmor && hasArmor(defence, armour)) {
            if (defence != 0) {
                top.add(new Line("\uD83D\uDEE1" + num(defence), DEFENCE_COLOR));
            }
            int trueArmor = trueArmorValue(armour);
            if (trueArmor != 0) {
                if (!top.isEmpty()) top.add(new Line(HEART_GAP, 0xFFFFFF));
                top.add(new Line(ARMOUR_ICONS[TRUE_ARMOUR_INDEX] + num(trueArmor),
                        ARMOUR_COLORS[TRUE_ARMOUR_INDEX]));
            }
            List<Line> elements = armourPage(armour, opt.armourPage);
            if (!elements.isEmpty()) {
                if (!top.isEmpty()) top.add(new Line(ARMOUR_GAP, 0xFFFFFF));
                for (int i = 0; i < elements.size(); i++) {
                    if (i > 0) top.add(new Line(HEART_GAP, 0xFFFFFF));
                    top.add(elements.get(i));
                }
            }
        }

        // ② 心数行：红心 + 一种其它心（按时间轮换）。
        List<Line> heartRow = new ArrayList<>(4);
        if (opt.showNumbers) {
            if (opt.showHearts && anyHeart(hearts)) {
                if (opt.playerStyle) {
                    addKinds(top, hearts, opt, SOUL, BLACK, BLUE);
                    if (has(hearts, RED)) {
                        heartRow.add(redLine(heart(hearts, RED), maxHp, opt));
                    }
                    addKinds(heartRow, hearts, opt, CRACKED, PINK);
                } else {
                    if (has(hearts, RED)) {
                        heartRow.add(redLine(heart(hearts, RED), maxHp, opt));
                    }
                    if (opt.cycleKind >= 0 && has(hearts, opt.cycleKind)) {
                        if (!heartRow.isEmpty()) heartRow.add(new Line(HEART_GAP, 0xFFFFFF));
                        heartRow.add(kindLine(opt.cycleKind, heart(hearts, opt.cycleKind), opt));
                    }
                }
            } else if (percentOnly) {
                heartRow.add(new Line(icon(RED, opt.crackedGlyph), HEART_COLORS[RED]));
                heartRow.add(new Line(HEART_GAP, 0xFFFFFF));
                heartRow.add(new Line(percentText(hp, maxHp), 0xFFFFFF));
            } else {
                heartRow.add(new Line(icon(RED, opt.crackedGlyph), HEART_COLORS[RED]));
                heartRow.add(new Line(HEART_GAP, 0xFFFFFF));
                heartRow.add(new Line(num(Math.max(0, hp)) + "/" + num(Math.max(0, maxHp)), 0xFFFFFF));
            }
        }

        int barWidth = opt.showBar ? resolveBarWidth(opt.barWidth) : 0;

        // 从上到下：最上面那一行 → 心数行。最下面那行贴 TEXT_TOP。
        List<List<Line>> groups = new ArrayList<>(2);
        if (!top.isEmpty()) groups.add(top);
        if (!heartRow.isEmpty()) groups.add(heartRow);

        int bottomTop = TEXT_TOP;
        int count = groups.size();
        List<Row> rows = new ArrayList<>(count);
        int textWidth = 0;
        for (int i = 0; i < count; i++) {
            List<Line> group = groups.get(i);
            int width = width(group, w);
            rows.add(new Row(group, width, bottomTop - ROW_STEP * (count - 1 - i)));
            textWidth = Math.max(textWidth, width);
        }
        return new Result(rows, textWidth, barWidth, bottomTop, BAR_TOP);
    }

    /** 当前页的元素护甲段（最多 {@link #ARMOUR_PAGE_SIZE} 项，不带分隔空格）。 */
    private static List<Line> armourPage(int[] armor, int page) {
        List<Line> out = new ArrayList<>(ARMOUR_PAGE_SIZE);
        if (armor == null) return out;
        int current = clampArmourPage(page, armourPageCount(armor));
        int seen = 0;
        int n = Math.min(armor.length, ARMOUR_ICONS.length);
        for (int i = 0; i < n; i++) {
            if (i == TRUE_ARMOUR_INDEX) continue;
            int v = armor[i];
            if (v == 0) continue;
            int itemPage = seen / ARMOUR_PAGE_SIZE;
            seen++;
            if (itemPage != current) continue;
            out.add(new Line(ARMOUR_ICONS[i] + num(v), ARMOUR_COLORS[i]));
        }
        return out;
    }

    /** 红心段：{@code ❤当前/红心上限}；上限未知（≤ 0）时只写当前值。 */
    private static Line redLine(int red, int maxHp, Options opt) {
        String icon = icon(RED, opt.crackedGlyph);
        String cur = num(Math.max(0, red));
        String text = maxHp > 0 ? icon + cur + "/" + num(maxHp) : icon + cur;
        return new Line(text, HEART_COLORS[RED]);
    }

    /** 某一类心的一段：图标 + 数量。 */
    private static Line kindLine(int kind, int value, Options opt) {
        return new Line(icon(kind, opt.crackedGlyph) + num(value), HEART_COLORS[kind]);
    }

    /** 「73%」：纯客户端只有 HealthPercent 时的数字行。 */
    static String percentText(int hp, int maxHp) {
        int percent;
        if (maxHp <= 0) {
            percent = 100;
        } else {
            percent = Math.round(Math.max(0, hp) * 100.0f / maxHp);
        }
        return Math.max(0, Math.min(100, percent)) + "%";
    }

    private static int width(List<Line> lines, WidthMeasurer w) {
        int total = 0;
        for (Line line : lines) {
            total += Math.max(0, w.width(line.text));
        }
        return total;
    }

    /** 把指定几类心各写成一段，追加到 {@code lines}，只写存在的类型。 */
    private static void addKinds(List<Line> lines, int[] hearts, Options opt, int... kinds) {
        for (int kind : kinds) {
            if (!has(hearts, kind)) continue;
            if (!lines.isEmpty()) lines.add(new Line(HEART_GAP, 0xFFFFFF));
            lines.add(kindLine(kind, heart(hearts, kind), opt));
        }
    }

    // ── 大数值缩写 ──

    /**
     * 数字的显示文本：{@code < 1000} 原样，再大改写成 {@code 123.4K / 12.3M / 2.1B}
     * （小数截断，不四舍五入）。
     */
    public static String num(int value) {
        long n = Math.abs((long) value);
        String sign = value < 0 ? "-" : "";
        if (n < NUM_EXACT_LIMIT) return sign + n;
        if (n < 1_000_000L) return sign + scaled(n, 1_000L) + "K";
        if (n < 1_000_000_000L) return sign + scaled(n, 1_000_000L) + "M";
        return sign + scaled(n, 1_000_000_000L) + "B";
    }

    private static String scaled(long n, long unit) {
        long whole = n / unit;
        long tenth = (n % unit) / (unit / 10L);
        if (tenth == 0) return Long.toString(whole);
        return whole + "." + tenth;
    }

    // ── 元素护甲翻页 ──

    /** 元素护甲一共几页；没有护甲时 0 页。{@code 🛡 防御} 不计入。 */
    public static int armourPageCount(int[] armor) {
        if (armor == null) return 0;
        int items = 0;
        int n = Math.min(armor.length, ARMOUR_ICONS.length);
        for (int i = 0; i < n; i++) {
            if (i == TRUE_ARMOUR_INDEX) continue;
            if (armor[i] != 0) items++;
        }
        return (items + ARMOUR_PAGE_SIZE - 1) / ARMOUR_PAGE_SIZE;
    }

    /** 真盔甲的值；数组不够长时 0。 */
    static int trueArmorValue(int[] armor) {
        if (armor == null || armor.length <= TRUE_ARMOUR_INDEX) return 0;
        return armor[TRUE_ARMOUR_INDEX];
    }

    /** 页号夹到 {@code [0, pages)}；页数为 0 时返回 0。 */
    public static int clampArmourPage(int page, int pages) {
        if (pages <= 0) return 0;
        return Math.max(0, Math.min(pages - 1, page));
    }

    /** 当前该显示第几页护甲（{@code salt} 用实体 id 错开，避免满屏同时翻页）。 */
    public static int page(int[] armor, long nowMs, int salt) {
        int pages = armourPageCount(armor);
        if (pages <= 1) return 0;
        long step = Math.floorDiv(Math.max(0L, nowMs), CYCLE_MS);
        return (int) Math.floorMod(step + salt, (long) pages);
    }

    // ── 心类轮换 ──

    /** 敌人除红心外的轮换顺序。 */
    public static final int[] ENEMY_ORDER = {SOUL, BLACK, BLUE, CRACKED, PINK};
    /** 玩家只在魂 / 黑之间轮换。 */
    public static final int[] PLAYER_ORDER = {SOUL, BLACK};

    /** 轮换顺序。 */
    public static int[] order(boolean player) {
        return player ? PLAYER_ORDER : ENEMY_ORDER;
    }

    /** 该实体有几种可轮换的非红心血类。 */
    public static int presentCount(int[] hearts, boolean player) {
        if (hearts == null) return 0;
        int count = 0;
        for (int kind : order(player)) {
            if (has(hearts, kind)) count++;
        }
        return count;
    }

    /** 当前该显示哪一类心；没有任何可轮换的心时返回 -1。 */
    public static int cycleKind(int[] hearts, boolean player, long nowMs, int salt) {
        if (hearts == null) return -1;
        int count = presentCount(hearts, player);
        if (count == 0) return -1;
        long step = Math.floorDiv(Math.max(0L, nowMs), CYCLE_MS);
        int slot = (int) Math.floorMod(step + salt, (long) count);
        for (int kind : order(player)) {
            if (!has(hearts, kind)) continue;
            if (slot == 0) return kind;
            slot--;
        }
        return -1;
    }

    // ── 血条 ──

    /**
     * 血条要画多长（像素）：{@code ratio <= 0} 时 0，超小比例（残血）至少 1 px
     * （fork 1.5.2：地图分数是整数，{@code 1/325} 乘条宽会被 round 成 0）。
     */
    public static int fillPixels(int width, float ratio) {
        if (width <= 0) return 0;
        float r = Math.max(0.0f, Math.min(1.0f, ratio));
        int fill = Math.round(width * r);
        if (fill <= 0 && r > 0.0f) fill = 1;
        return Math.min(width, fill);
    }

    /** 血条长度：夹在 [MIN, MAX]，非法值回落默认值。 */
    public static int resolveBarWidth(double configured) {
        if (Double.isNaN(configured)) return BAR_WIDTH_DEFAULT;
        int width = (int) Math.round(configured);
        return Math.max(BAR_WIDTH_MIN, Math.min(BAR_WIDTH_MAX, width));
    }

    /** 这一实体有没有护甲段要画。 */
    public static boolean hasArmor(int defence, int[] armor) {
        if (defence != 0) return true;
        if (armor == null) return false;
        for (int v : armor) {
            if (v != 0) return true;
        }
        return false;
    }
}
