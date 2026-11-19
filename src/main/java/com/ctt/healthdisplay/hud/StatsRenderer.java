package com.ctt.healthdisplay.hud;

import com.ctt.healthdisplay.config.ModConfig;
import com.ctt.healthdisplay.health.StatsData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;

import java.util.List;

public class StatsRenderer {

    private static final int LINE_HEIGHT = 10;
    private static final int TITLE_COLOR = 0xFFFFAA00;

    public static void render(GuiGraphicsExtractor context, StatsData data) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;

        int vis = ModConfig.INSTANCE.statsVisibility;
        if (vis == 2) return;
        if (vis == 1 && !(client.gui.screen() instanceof InventoryScreen)) return;
        if (vis == 0 && client.gui.hud.isHidden()) return;

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
            context.text(tr, hint, x, y, 0xFF888888, true);
            return;
        }

        List<Component> lines = data.getLines();
        if (lines.isEmpty()) return;

        int columns = Math.max(1, Math.min(2, config.statsColumns));

        if (columns == 1) {
            int lineY = y;
            for (Component line : lines) {
                context.text(tr, line, x, lineY, 0xFFFFFFFF, true);
                lineY += LINE_HEIGHT;
            }
        } else {
            int colWidth = 0;
            for (Component line : lines) {
                colWidth = Math.max(colWidth, tr.width(line));
            }
            colWidth += 6;

            int rows = (lines.size() + 1) / 2;
            for (int i = 0; i < lines.size(); i++) {
                int col = i / rows;
                int row = i % rows;
                context.text(tr, lines.get(i), x + col * colWidth, y + row * LINE_HEIGHT, 0xFFFFFFFF, true);
            }
        }
    }
}
