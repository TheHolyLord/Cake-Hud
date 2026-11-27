package com.ctt.healthdisplay.client;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.util.Optional;

/** 属性行标识不随数值刷新而变。倍率、状态等有翻译键的行也不随游戏语言变。 */
public final class StatRowIdentity {
    private StatRowIdentity() {}

    public static String of(Component row) {
        String translation = translation(row);
        if (translation != null) return "translate:" + translation;
        String hint = hoverKey(row);
        if (hint != null) {
            if (hint.equals("view_stats_19")) hint = "view_stats_20";
            return "hover:" + hint;
        }
        String label = label(row);
        int color = row.visit((style, text) -> {
            if (style.getColor() == null || text.isBlank()) return Optional.empty();
            return Optional.of(style.getColor().getValue());
        }, Style.EMPTY).orElse(0xFFFFFF);
        // 颜色区分同形的红心、魂心、黑心、蓝心，以及物防与真甲。
        return "text:" + label + (label.equals("❤") || label.equals("\uE026") || label.equals("🛡")
                ? "#" + Integer.toHexString(color) : "");
    }

    public static String label(Component row) {
        String result = row.getString().replace("\uFE0F", "")
                .replaceAll("[+−-]?\\d+(?:[.,]\\d+)?", "").strip();
        return result.isEmpty() ? row.getString() : result;
    }

    public static Component fallbackLabel(String key) {
        if (key.startsWith("translate:")) return Component.translatable(key.substring("translate:".length()));
        if (key.startsWith("hover:")) return Component.translatable(key.substring("hover:".length()));
        return Component.literal(key.startsWith("text:") ? key.substring("text:".length()) : key);
    }

    private static String hoverKey(Component row) {
        return row.visit((style, text) -> {
            if (style.getHoverEvent() instanceof net.minecraft.network.chat.HoverEvent.ShowText hint) {
                String key = translation(hint.value());
                if (key != null) return Optional.of(key);
            }
            return Optional.<String>empty();
        }, Style.EMPTY).orElse(null);
    }

    private static String translation(Component part) {
        if (part.getContents() instanceof TranslatableContents translation) return translation.getKey();
        for (Component child : part.getSiblings()) {
            String key = translation(child);
            if (key != null) return key;
        }
        return null;
    }
}
