package com.ctt.healthdisplay.health;

import java.util.Map;

/** 与地图 ViewStats 图标提示使用相同的属性标识。 */
public final class StatLabels {
    private static final Map<String, Integer> ICON_LABELS = Map.ofEntries(
            Map.entry("ClassStat", 8), Map.entry("Strength", 10), Map.entry("AttackSpeed", 12),
            Map.entry("AttackRange", 14), Map.entry("StaminaSpeed", 15), Map.entry("Archery", 16),
            Map.entry("Defence", 17), Map.entry("TrueArmor", 20), Map.entry("Healing", 22),
            Map.entry("TowersRegen", 24), Map.entry("HealPercent", 25), Map.entry("ExtraHealing", 26),
            Map.entry("TrueFireArmor", 27), Map.entry("FireArmor", 28), Map.entry("WaterArmor", 30),
            Map.entry("ElectricArmor", 31), Map.entry("IceArmor", 32), Map.entry("DarkArmor", 33),
            Map.entry("LightArmor", 34), Map.entry("MaxSpeed", 35), Map.entry("ManaPower", 39),
            Map.entry("ManaRechargeSpeed", 41), Map.entry("Jump", 42), Map.entry("DoubleJump", 43),
            Map.entry("ActiveCharge", 44), Map.entry("MaxActiveCharge", 45), Map.entry("ActiveSpeed", 46),
            Map.entry("Summoning", 47), Map.entry("Fishing", 48), Map.entry("BeePoints", 49),
            Map.entry("AntPoints", 50), Map.entry("Bitches", 51), Map.entry("LightKarma", 52),
            Map.entry("NeutralKarma", 53));

    private StatLabels() {}

    public static int iconLabel(String objective) {
        return ICON_LABELS.getOrDefault(objective, 0);
    }
}
