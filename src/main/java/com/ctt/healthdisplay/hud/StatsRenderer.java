package com.ctt.healthdisplay.hud;

import com.ctt.healthdisplay.config.ModConfig;
import com.ctt.healthdisplay.health.StatsData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

public class StatsRenderer {

    private static final int LINE_HEIGHT = 10;
    public static final int COLUMN_GAP = 6;
    // 属性数值由服务端或地图发来，和图标、翻译文字一起组成带样式的文本。
    private static final Pattern STAT_NUMBER = Pattern.compile("(?<![\\p{IsLatin}\\d_.])[-+]?\\d{4,}(?![\\p{IsLatin}\\d_]|\\.\\d)");
    private static Language cachedLanguage;
    private static List<Component> cachedStats = List.of();
    private static List<Component> cachedCompactStats = List.of();

    public static boolean isPlayerInventory(Screen screen) {
        return screen instanceof InventoryScreen || screen instanceof CreativeModeInventoryScreen;
    }

    public static void render(GuiGraphicsExtractor context, StatsData data) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;

        Font tr = client.font;
        int screenW = client.getWindow().getGuiScaledWidth();
        int screenH = client.getWindow().getGuiScaledHeight();
        ModConfig config = ModConfig.INSTANCE;
        int x = (int) (config.statsX * screenW);
        int y = (int) (config.statsY * screenH);

        if (!data.hasData()) {
            Component hint = data.isGameNotStarted()
                    ? Component.translatable("ctt-health-display.stats.hint.game_not_started")
                    : Component.translatable("ctt-health-display.stats.hint.use_view_stats");
            int hintX = config.statsAlignRight ? Math.max(0, x - tr.width(hint)) : x;
            context.text(tr, hint, hintX, y, HeadHealthStyle.alpha(0xFF888888, config.statsOpacity), true);
            return;
        }

        List<Component> lines = data.getLines();
        if (config.hiddenStatRows.isEmpty()) {
            if (config.compactHudNumbers) lines = compactLines(lines);
        } else {
            List<Component> displayed = config.compactHudNumbers ? compactLines(lines) : lines;
            List<Component> visible = new ArrayList<>();
            for (int i = 0; i < lines.size(); i++) {
                if (!config.hiddenStatRows.contains(com.ctt.healthdisplay.client.StatRowIdentity.of(lines.get(i)))) {
                    visible.add(displayed.get(i));
                }
            }
            lines = visible;
        }
        if (lines.isEmpty()) return;
        int textColor = HeadHealthStyle.alpha(0xFFFFFFFF, config.statsOpacity);

        int columns = Math.clamp(config.statsColumns, 1, ModConfig.MAX_STATS_COLUMNS);

        int rows = Math.ceilDiv(lines.size(), columns);
        int colWidth = 0;
        if (config.statsAlignRight || columns > 1) {
            for (Component line : lines) {
                colWidth = Math.max(colWidth, tr.width(line));
            }
        }
        int columnStep = colWidth + COLUMN_GAP;
        if (config.statsAlignRight) {
            int usedColumns = Math.ceilDiv(lines.size(), rows);
            x = Math.min(screenW - 1, Math.max(x, colWidth + (usedColumns - 1) * columnStep));
        }
        for (int i = 0; i < lines.size(); i++) {
            Component line = lines.get(i);
            int lineWidth = config.statsAlignRight ? tr.width(line) : 0;
            int lineX = columnX(x, i / rows, columnStep, lineWidth, config.statsAlignRight);
            context.text(tr, line, lineX, y + (i % rows) * LINE_HEIGHT, textColor, true);
        }
    }

    public static int columnX(int edge, int column, int step, int textWidth, boolean alignRight) {
        return alignRight ? edge - column * step - textWidth : edge + column * step;
    }

    private static List<Component> compactLines(List<Component> lines) {
        Language language = Language.getInstance();
        if (language != cachedLanguage || !cachedStats.equals(lines)) {
            cachedCompactStats = lines.stream().map(StatsRenderer::compactLine).toList();
            cachedStats = List.copyOf(lines);
            cachedLanguage = language;
        }
        return cachedCompactStats;
    }

    private static Component compactLine(Component line) {
        var result = Component.empty();
        line.visit((style, text) -> {
            var matcher = STAT_NUMBER.matcher(text);
            String formatted = matcher.replaceAll(match -> {
                String number = match.group();
                try {
                    String amount = HudNumbers.amount(Long.parseLong(number), true);
                    return number.startsWith("+") ? "+" + amount : amount;
                } catch (NumberFormatException ignored) {
                    return number;
                }
            });
            result.append(Component.literal(formatted).withStyle(style));
            return Optional.empty();
        }, Style.EMPTY);
        return result;
    }
}
