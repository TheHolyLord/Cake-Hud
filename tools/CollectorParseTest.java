import java.util.*;
import java.util.regex.*;

/** 独立复刻 HealthData.extractSuffixText + MobArmorCollector.collect 的纯字符串逻辑，
 *  用 CakeTeamPack 解析后的真实 bossbar 标题验证 8 种护甲段（含两种 🛡）都能被收集。 */
public class CollectorParseTest {

    static final Pattern HP_PATTERN = Pattern.compile("\\((?:HP|生命值|Health)\\s*(-?\\d+)/(-?\\d+)\\)");
    static final Pattern MOB_SUFFIX_PATTERN = Pattern.compile("\\((?:HP|生命值|Health)\\s*-?\\d+/-?\\d+\\)\\s*(.+)$");
    static final Pattern SEGMENT_PATTERN = Pattern.compile(
            "(\uD83D\uDD25|\uD83D\uDEE1|\uD83D\uDC9B|\uD83D\uDC99|\uD83D\uDC9C|\uD83D\uDDA4"
                    + "|[\u2600\u2603\u2620\u2693\u26A1])\\s*(-?\\d+)");

    static int slotOf(String icon, int argb) {
        int r = (argb >> 16) & 0xFF, b = argb & 0xFF;
        if ("\uD83D\uDEE1".equals(icon)) return b > r ? 0 : 5;
        switch (icon) {
            case "\uD83D\uDD25": return 1;
            case "\u2603": return 2;
            case "\u2620": return 3;
            case "\u2600": return 4;
            case "\u2693": return 6;
            case "\u26A1": return 7;
            case "\uD83D\uDC9B": return 8;
            case "\uD83D\uDC99": return 9;
            case "\uD83D\uDC9C": return 10;
            case "\uD83D\uDDA4": return 11;
            default: return -1;
        }
    }

    static String extractSuffix(String full) {
        Matcher m = MOB_SUFFIX_PATTERN.matcher(full);
        return m.find() ? full.substring(m.start(1)) : "";
    }

    /** 模拟 collect：返回 12 槽数值；颜色表按数据包段色（blue/gold/aqua/dark_purple/white/yellow）。 */
    static int[] parse(String suffix, int[] outColors) {
        int[] v = new int[12];
        Matcher m = SEGMENT_PATTERN.matcher(suffix);
        while (m.find()) {
            String icon = m.group(1);
            int value = Integer.parseInt(m.group(2));
            // 用图标推断"数据包给这段的颜色"，模拟 charColors
            int color;
            switch (icon) {
                case "\uD83D\uDEE1": color = (v[0] == 0 && value != 0) ? 0xFF5555FF : 0xFFFFFFFF; break;
                case "\uD83D\uDD25": color = 0xFFFFAA00; break;
                case "\u2603": case "\u2693": color = 0xFF55FFFF; break;
                case "\u2620": color = 0xFFAA00AA; break;
                case "\u2600": color = 0xFFFFFFFF; break;
                case "\u26A1": color = 0xFFFFFF55; break;
                default: color = 0xFFFFFFFF;
            }
            int slot = slotOf(icon, color);
            if (slot >= 0) { v[slot] = value; outColors[slot] = color; }
        }
        return v;
    }

    public static void main(String[] args) {
        String[][] cases = {
            // {ArmorDisplay 阶段, 完整标题(en), 完整标题(zh)}
            {"0", "Skeleton Warrior (HP 125/125) \uD83D\uDEE112", "骷髅勇士 (生命值 125/125) \uD83D\uDEE112"},
            {"1", "Skeleton Warrior (HP 125/125) \uD83D\uDEE112 \uD83D\uDD255", null},
            {"2", "Skeleton Warrior (HP 125/125) \uD83D\uDEE112 \u26038", null},
            {"3", "Skeleton Warrior (HP 125/125) \uD83D\uDEE112 \u26203", null},
            {"4", "Skeleton Warrior (HP 125/125) \uD83D\uDEE112 \u26007", null},
            {"5", "Skeleton Warrior (HP 125/125) \uD83D\uDEE112 \uD83D\uDEE19", null},
            {"6", "Skeleton Warrior (HP 125/125) \uD83D\uDEE112 \u26934", null},
            {"7", "Skeleton Warrior (HP 125/125) \uD83D\uDEE112 \u26A16", null},
        };
        int[] accumulated = new int[12];
        int[] colors = new int[12];
        boolean fail = false;
        for (String[] c : cases) {
            String title = c[2] != null ? c[2] : c[1];
            if (!HP_PATTERN.matcher(title).find()) { System.out.println("HP_PATTERN FAIL: " + title); fail = true; }
            String suffix = extractSuffix(title);
            int[] v = parse(suffix, colors);
            for (int i = 0; i < 12; i++) if (v[i] != 0) accumulated[i] = v[i];
            System.out.printf("stage %s -> suffix=\"%s\" slots=%s%n", c[0], suffix, Arrays.toString(v));
        }
        String[] names = {"Defence","Fire","Ice","Dark","Light","True","Water","Elec","soul","blue","x","black"};
        System.out.println("accumulated:");
        for (int i = 0; i < 12; i++) if (accumulated[i] != 0)
            System.out.printf("  %s=%d%n", names[i], accumulated[i]);
        int[] expect = {12,5,8,3,7,9,4,6,0,0,0,0};
        fail = fail || !Arrays.equals(accumulated, expect);
        System.out.println(fail ? "TEST FAILED" : "TEST PASSED");
        if (fail) System.exit(1);
    }
}
