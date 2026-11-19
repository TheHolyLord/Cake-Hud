package com.ctt.healthdisplay.client;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * v8.5.0 · 纯客户端护甲聚合器（不走服务端）。
 *
 * <h2>为什么不能直接读 scoreboard</h2>
 * <p>vanilla 服务端只把"挂在展示槽位（侧栏等）上的 objective"的分数同步给客户端
 * （{@code ServerScoreboard.onScoreChanged} 里 {@code trackedObjectives.contains} 门控）。
 * CTT 地图的 FireArmor/IceArmor/… 是内部数据、不上侧栏，客户端 scoreboard 里根本没有。
 * 客户端唯一能看到护甲的通道是 <b>bossbar 标题后缀</b>——datapack 按 ArmorDisplay=1..7
 * 轮换，每帧只带一种元素护甲。
 *
 * <h2>聚合策略</h2>
 * <p>每帧解析后缀里的全部 {@code 图标+数值} 段（图标颜色区分蓝🛡=Defence、白🛡=TrueArmor），
 * 按怪 UUID 累积到 12 个槽位（0..7=八种护甲，8..11=心形段 💛/💙/💜/🖤，供血条分层）；
 * 渲染时把"非 0 且 TTL 内见过"的槽位一次性全部画出——
 * 轮换在幕后继续，玩家看到的是一行完整护甲清单而不是翻页。
 *
 * <ul>
 *   <li>槽位 TTL {@value #SLOT_TTL_MS} ms（&gt; 16 s 轮换一圈）：某档护甲连续一整个
 *       轮换周期没再出现 → 视为已消失（v8.5.4 起不再做"Defence 变动即清档"，
 *       战斗中 Defence 逐 tick 磨损掉字会让元素段反复闪断）。</li>
 *   <li>条目 30 s 没被任何帧 touch → 整体移除。</li>
 * </ul>
 *
 * <p>只在渲染线程调用（{@code TeammateWorldRenderer.renderMob}），map 用并发容器纯防御。
 *
 * <p>v8.5.1：解析前剔除变体选择符等隐形字符，修复 {@code 🛡️12}（盾牌带 U+FE0F）
 * 匹配不上、蓝盾 Defence 段永远不显示的问题。
 *
 * <p>v8.5.3：修复 {@link #SEGMENT_PATTERN} 代理对字符类缺陷——旧写法让所有
 * emoji（🛡🔥💛💙🖤）段匹配失败，护甲行只剩 BMP 符号（☃☠☀⚓⚡），
 * 两种 🛡（蓝 Defence / 白 TrueArmor）永远不显示。现改为码点字面量交替匹配。
 * 已对照 CakeTeamPack 源码核实：bossbar 标题里的 score 段是【服务端解析】后
 * 下发的纯文本（{@code BossBarCommands → ComponentArgument.getResolvedComponent}），
 * 客户端 scoreboard 确实没有 Defence/FireArmor 等目标（未上展示槽不广播），
 * 后缀文本仍是唯一可用通道。
 *
 * <p>v8.5.4：对照数据包 {@code bossbars_universal.mcfunction} 重算轮换节拍——
 * {@code ArmorDisplay} 每 40 tick 进一档，8 档全有护甲时一整圈是 <b>16 s</b>；
 * 旧 {@code SLOT_TTL_MS=12 s} 比一整圈还短，🔥☃☠☀🛡(白)⚓⚡ 这些元素段会在
 * 两次刷新之间集体过期 → 护甲行每隔几秒整段闪断。TTL 提到 18 s。
 * 同时删除"Defence 数值一变就清空元素槽"的旧逻辑：数据包里 Defence 会随穿甲
 * 磨损逐 tick 掉字（{@code health.mcfunction} 的 WearArmor/ArmorSubtract 段），
 * 战斗中每次掉 1 点都清档会让元素段反复消失重收，比不过期更糟；条目本就按
 * 实体 UUID 隔离，某档护甲真正消失交给 TTL 处理即可。
 */
public final class MobArmorCollector {

    /** 单段护甲：图标+数值文本、ARGB 颜色、原始数值。 */
    public static final class Segment {
        public final String text;
        public final int argb;

        Segment(String text, int argb) {
            this.text = text;
            this.argb = argb;
        }
    }

    /** 护甲段数量（槽 0..7）；槽 8..11 预留给出现在后缀里的心形段。 */
    public static final int ARMOR_SLOTS = 8;
    /** 心形槽相对 values[] 下标的偏移：8=💛魂 9=💙蓝 10=🟪预留 11=🖤黑。 */
    public static final int HEART_BASE = 8;
    /** hearts 数组下标：0=魂 1=蓝 2=预留 3=黑。 */
    public static final int HEART_SOUL = 0;
    public static final int HEART_BLUE = 1;
    public static final int HEART_BLACK = 3;
    public static final int HEART_COUNT = 4;

    // 槽位顺序与 datapack ArmorDisplay 模板一致：0=Defence(蓝🛡) 1=🔥 2=☃ 3=☠ 4=☀
    // 5=TrueArmor(白🛡) 6=⚓ 7=⚡；8..11 为心形段（魂/蓝/预留/黑）。
    // v8.5.5 起血条不再按心色分层，心形段改为 💛🖤💙 数量图标，经
    // {@link #currentWithHearts} 与护甲同排显示在怪物头顶。
    private static final String[] ICONS = {
            "\uD83D\uDEE1", // 🛡 Defence
            "\uD83D\uDD25", // 🔥 Fire
            "\u2603",       // ☃ Ice
            "\u2620",       // ☠ Dark
            "\u2600",       // ☀ Light
            "\uD83D\uDEE1", // 🛡 True（与 Defence 同图标，靠颜色区分）
            "\u2693",       // ⚓ Water
            "\u26A1",       // ⚡ Electric
            "\uD83D\uDC9B", // 💛 魂心
            "\uD83D\uDC99", // 💙 蓝心
            "\uD83D\uDC9C", // 💜 预留
            "\uD83D\uDDA4", // 🖤 黑心
    };
    private static final int SLOT_DEFENCE = 0;
    private static final int SLOT_TRUE = 5;

    /**
     * 后缀段匹配：BMP 内 U+26xx 符号 + 代理对 emoji（🔥/🛡/💛/💙/💜/🖤），后跟可选空白和整数。
     *
     * <p>v8.5.3 关键修复：Java 正则把代理对当作<b>单个码点</b>处理——字符类
     * {@code [\uDEE1]} 里放低位代理只会匹配"孤立的低位代理字符"，对真实的
     * {@code \uD83D\uDEE1}（U+1F6E1 🛡）永远匹配不上。旧版用
     * {@code \uD83D[\uDD25\uDEE1...]} 导致 🛡Defence/🛡TrueArmor/🔥Fire/💛/💙/🖤
     * 这些代理对 emoji 段【全部】解析失败，只有 BMP 的 ☃☠☀⚓⚡ 能显示——
     * 正是用户反馈"两种🛡都不显示"的根因。代理对必须写成字面量交替
     * （{@code \uD83D\uDEE1|\uD83D\uDD25|...}）才能匹配。
     */
    private static final Pattern SEGMENT_PATTERN = Pattern.compile(
            "(\uD83D\uDD25|\uD83D\uDEE1|\uD83D\uDC9B|\uD83D\uDC99|\uD83D\uDC9C|\uD83D\uDDA4"
                    + "|[\u2600\u2603\u2620\u2693\u26A1])\\s*(-?\\d+)");

    /**
     * v8.5.4：数据包 ArmorDisplay 每 40 tick 进一档、8 档一圈 = 16 s，
     * TTL 必须大于一整圈，否则元素段在两次刷新之间集体过期（护甲行闪断）。
     */
    private static final long SLOT_TTL_MS = 18_000L;
    private static final long ENTRY_PURGE_MS = 30_000L;

    private static final class Entry {
        final int[] values = new int[12];
        final int[] colors = new int[12];
        final long[] seenMs = new long[12];
        long lastTouchMs;
    }

    private static final Map<UUID, Entry> ENTRIES = new ConcurrentHashMap<>();
    private static long lastPurgeMs = 0L;

    private MobArmorCollector() {}

    /** 每帧喂一次当前 bossbar 后缀 Component；解析出哪些段就刷新哪些槽位。 */
    public static void collect(UUID id, Component suffix) {
        if (id == null || suffix == null) return;
        String probe = suffix.getString();
        if (probe.isEmpty()) return; // 无护甲后缀的怪不建条目，避免全世界实体常驻缓存
        Entry entry = ENTRIES.computeIfAbsent(id, k -> new Entry());
        entry.lastTouchMs = System.currentTimeMillis();
        purgeStaleIfNeeded();

        // 逐字符记录颜色：Component 是带样式的 run 序列，段图标取 run 的颜色。
        // 同时剔除变体选择符（VS15/VS16）与零宽字符：datapack 里的 🛡 几乎总是写成
        // U+1F6E1 + U+FE0F（"🛡️"），隐形字符夹在图标和数字中间会让 SEGMENT_PATTERN
        // 永远匹配不上 → 蓝盾 Defence 段整段丢失（v8.5.1 修的 BUG）。
        StringBuilder buf = new StringBuilder();
        List<Integer> charColors = new ArrayList<>();
        try {
            suffix.visit((style, part) -> {
                int color = resolveColor(style);
                for (int i = 0; i < part.length(); i++) {
                    char c = part.charAt(i);
                    if (isInvisibleMark(c)) continue;
                    buf.append(c);
                    charColors.add(color);
                }
                return Optional.empty();
            }, Style.EMPTY);
        } catch (Exception ignored) {
            return;
        }
        String raw = buf.toString();
        if (raw.isEmpty()) return;

        Matcher m = SEGMENT_PATTERN.matcher(raw);
        while (m.find()) {
            String icon = m.group(1);
            int value;
            try {
                value = Integer.parseInt(m.group(2));
            } catch (NumberFormatException e) {
                continue;
            }
            int color = charColors.get(m.start(1));
            int slot = slotOf(icon, color);
            if (slot < 0) continue;
            entry.values[slot] = value;
            entry.colors[slot] = color;
            entry.seenMs[slot] = entry.lastTouchMs;
        }
    }

    /** 当前应显示的护甲段（0 除外、TTL 内），按固定槽位顺序，只含 8 种护甲槽。 */
    public static List<Segment> current(UUID id) {
        Entry entry = ENTRIES.get(id);
        if (entry == null) return List.of();
        long now = System.currentTimeMillis();
        List<Segment> out = new ArrayList<>(ARMOR_SLOTS);
        for (int s = 0; s < ARMOR_SLOTS; s++) {
            if (entry.values[s] != 0 && now - entry.seenMs[s] <= SLOT_TTL_MS) {
                out.add(new Segment(ICONS[s] + entry.values[s], entry.colors[s]));
            }
        }
        return out;
    }

    // v8.5.5：心形数量段的固定配色（💛 黄 / 🖤 黑 / 💙 蓝，💜 预留位淡紫）。
    // 血条本身不再按心色分层，这些数字段与护甲同排显示在怪物头顶。
    private static final int HEART_SEG_SOUL  = 0xFFFFDD44;
    private static final int HEART_SEG_BLACK = 0xFF2A2A2A;
    private static final int HEART_SEG_BLUE  = 0xFF5599FF;
    private static final int HEART_SEG_PURPLE = 0xFFAA55FF;

    /**
     * v8.5.5 · 护甲段 + 心形数量段合并清单（头顶护甲行一次画全）。
     *
     * <p>心形数值取"后缀段与 bossbar 推导"的较大者：
     * {@code soul = max(💛后缀段, soulOverride)}、{@code black = max(🖤后缀段, blackOverride)}；
     * 蓝心 / 紫心没有推导通道，只看后缀段。顺序：护甲在前，魂 → 黑 → 蓝 → 紫在后。
     *
     * @param soulOverride   {@link com.ctt.healthdisplay.health.MobHealthData#soulHearts}
     * @param blackOverride  {@link com.ctt.healthdisplay.health.MobHealthData#blackHearts}
     */
    public static List<Segment> currentWithHearts(UUID id, int soulOverride, int blackOverride) {
        return currentWithHearts(id, soulOverride, blackOverride, 0);
    }

    public static List<Segment> currentWithHearts(UUID id, int soulOverride, int blackOverride, int blueOverride) {
        List<Segment> out = new ArrayList<>(current(id));
        int[] h = new int[HEART_COUNT];
        hearts(id, h);
        int soul = Math.max(h[HEART_SOUL], Math.max(0, soulOverride));
        int black = Math.max(h[HEART_BLACK], Math.max(0, blackOverride));
        int blue = Math.max(h[HEART_BLUE], Math.max(0, blueOverride));
        int purple = h[2];
        if (soul > 0) out.add(new Segment(ICONS[HEART_BASE + HEART_SOUL] + soul, HEART_SEG_SOUL));
        if (black > 0) out.add(new Segment(ICONS[HEART_BASE + HEART_BLACK] + black, HEART_SEG_BLACK));
        if (blue > 0) out.add(new Segment(ICONS[HEART_BASE + HEART_BLUE] + blue, HEART_SEG_BLUE));
        if (purple > 0) out.add(new Segment(ICONS[HEART_BASE + 2] + purple, HEART_SEG_PURPLE));
        return out;
    }

    /**
     * 心形护甲段（💛魂 / 💙蓝 / 💜预留 / 🖤黑）：hearts[0..3]，TTL 内、无数据为 0。
     * v8.5.5 起供 {@link #currentWithHearts} 与护甲同排显示数量（血条本身恒为红心色）。
     */
    public static void hearts(UUID id, int[] out4) {
        java.util.Arrays.fill(out4, 0);
        Entry entry = ENTRIES.get(id);
        if (entry == null) return;
        long now = System.currentTimeMillis();
        for (int h = 0; h < HEART_COUNT; h++) {
            int s = HEART_BASE + h;
            if (entry.values[s] != 0 && now - entry.seenMs[s] <= SLOT_TTL_MS) {
                out4[h] = Math.max(0, entry.values[s]);
            }
        }
    }

    /** DISCONNECT 时清空，避免跨局残留。 */
    public static void reset() {
        ENTRIES.clear();
    }

    // ── 内部 ──

    /** 🛡 蓝色归 Defence、其它色（白）归 TrueArmor；其余护甲图标按表直配；心形图标归 8..11。 */
    private static int slotOf(String icon, int argb) {
        int r = (argb >> 16) & 0xFF;
        int b = argb & 0xFF;
        if ("\uD83D\uDEE1".equals(icon)) {
            return b > r ? SLOT_DEFENCE : SLOT_TRUE;
        }
        return switch (icon) {
            case "\uD83D\uDD25" -> 1;
            case "\u2603" -> 2;
            case "\u2620" -> 3;
            case "\u2600" -> 4;
            case "\u2693" -> 6;
            case "\u26A1" -> 7;
            case "\uD83D\uDC9B" -> HEART_BASE + HEART_SOUL;  // 💛 魂心
            case "\uD83D\uDC99" -> HEART_BASE + HEART_BLUE;  // 💙 蓝心
            case "\uD83D\uDC9C" -> HEART_BASE + 2;           // 💜 预留
            case "\uD83D\uDDA4" -> HEART_BASE + HEART_BLACK; // 🖤 黑心
            default -> -1;
        };
    }

    /** 变体选择符与零宽格式字符：不影响语义，但会打断 {@link #SEGMENT_PATTERN} 匹配。 */
    private static boolean isInvisibleMark(char c) {
        return c == '\uFE0E' || c == '\uFE0F'                                   // VS15 / VS16
            || c == '\u200B' || c == '\u200C' || c == '\u200D'                   // ZWSP / ZWNJ / ZWJ
            || c == '\u2060' || c == '\uFEFF';                                   // WJ / BOM
    }

    private static int resolveColor(Style style) {
        TextColor tc = style == null ? null : style.getColor();
        if (tc == null) return 0xFFFFFFFF;
        return 0xFF000000 | (tc.getValue() & 0x00FFFFFF);
    }

    private static void purgeStaleIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - lastPurgeMs < 5_000L) return;
        lastPurgeMs = now;
        ENTRIES.entrySet().removeIf(e -> now - e.getValue().lastTouchMs > ENTRY_PURGE_MS);
    }
}
