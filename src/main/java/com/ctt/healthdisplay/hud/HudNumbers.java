package com.ctt.healthdisplay.hud;

import com.ctt.healthdisplay.config.ModConfig;

final class HudNumbers {
    private static final String[] UNITS = {"", "K", "M", "B", "T", "Qa", "Qi"};

    private HudNumbers() {}

    static String amount(long value) {
        return amount(value, ModConfig.INSTANCE.compactHudNumbers);
    }

    static String amount(long value, boolean compact) {
        if (!compact || (value > -1000 && value < 1000)) return Long.toString(value);
        double scaled = Math.abs((double) value);
        int unit = 0;
        do {
            scaled /= 1000;
            unit++;
        } while (scaled >= 1000 && unit < UNITS.length - 1);
        long tenths = Math.round(scaled * 10);
        if (tenths == 10000 && unit < UNITS.length - 1) {
            unit++;
            tenths = 10;
        }
        return (value < 0 ? "-" : "") + tenths / 10 + (tenths % 10 == 0 ? "" : "." + tenths % 10) + UNITS[unit];
    }

    static String ratio(long current, long max) {
        return amount(current) + "/" + amount(max);
    }

    static String health(long current, int max, int effectiveMax) {
        if (max <= 0) return amount(current);
        String label = ratio(current, effectiveMax);
        return max == effectiveMax ? label : label + " (" + amount(max) + ")";
    }
}
