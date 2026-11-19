package com.ctt.healthdisplay.client;

import net.minecraft.network.chat.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 纯客户端 actionbar 数据通道。
 *
 * <p>数据包把体力和魔法物品可用次数写在 actionbar 中：体力通常是
 * {@code (Stamina x/y)}，魔法物品通常是单独的 {@code x/y}。这里不改动原版
 * actionbar，只保存最近一次识别到的数值，供 HUD 在下一帧读取。
 */
public final class ActionBarCapture {

    private static final long TTL_MS = 4_000L;
    private static final Pattern STAMINA = Pattern.compile(
            "\\((?:Stamina|体力)\\s*(-?\\d+)\\s*/\\s*(\\d+)\\)");
    // 如果客户端拿到的翻译键尚未展开，仍能识别数据包使用的这个固定翻译键。
    private static final Pattern STAMINA_RAW_KEY = Pattern.compile(
            "Items_RandomAMagic_6_9\\s*(-?\\d+)\\s*/\\s*(\\d+)");
    // Magic actionbar 可能带图标或翻译文字，不再要求整行只有 x/y；
    // 但带 Stamina 的动作条仍只归入体力通道，避免两条数据互相覆盖。
    private static final Pattern SPELL_USES = Pattern.compile(
            "(?<!\\d)(\\d+)\\s*/\\s*(\\d+)");

    private static volatile int stamina;
    private static volatile int maxStamina;
    private static volatile long staminaAt;
    private static volatile int spellUses;
    private static volatile int spellUsesMax;
    private static volatile long spellUsesAt;

    private ActionBarCapture() {}

    /** Hud#setOverlayMessage 的观察入口。 */
    public static void onActionBar(Component message) {
        if (message == null) return;
        String text = message.getString();
        if (text == null) return;

        Matcher staminaMatcher = STAMINA.matcher(text);
        boolean foundStamina = staminaMatcher.find();
        if (!foundStamina) {
            staminaMatcher = STAMINA_RAW_KEY.matcher(text);
            foundStamina = staminaMatcher.find();
        }
        if (foundStamina) {
            publishStamina(staminaMatcher);
        }

        if (!foundStamina) {
            Matcher usesMatcher = SPELL_USES.matcher(text);
            if (usesMatcher.find()) {
                int current = parseInt(usesMatcher.group(1));
                int maximum = parseInt(usesMatcher.group(2));
                if (current >= 0 && maximum > 0) {
                    spellUses = Math.min(current, maximum);
                    spellUsesMax = maximum;
                    spellUsesAt = System.currentTimeMillis();
                }
            }
        }
    }

    private static void publishStamina(Matcher matcher) {
        int current = parseInt(matcher.group(1));
        int maximum = parseInt(matcher.group(2));
        if (maximum <= 0) return;
        stamina = Math.max(0, Math.min(current, maximum));
        maxStamina = maximum;
        staminaAt = System.currentTimeMillis();
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    public static boolean hasFreshStamina() {
        return maxStamina > 0 && System.currentTimeMillis() - staminaAt <= TTL_MS;
    }

    public static int stamina() { return stamina; }
    public static int maxStamina() { return maxStamina; }

    public static boolean hasFreshSpellUses() {
        return spellUsesMax > 0 && System.currentTimeMillis() - spellUsesAt <= TTL_MS;
    }

    public static int spellUses() { return spellUses; }
    public static int spellUsesMax() { return spellUsesMax; }

    /** 断线或切换世界时清掉上一局 actionbar 残留。 */
    public static void reset() {
        stamina = 0;
        maxStamina = 0;
        staminaAt = 0L;
        spellUses = 0;
        spellUsesMax = 0;
        spellUsesAt = 0L;
    }
}
