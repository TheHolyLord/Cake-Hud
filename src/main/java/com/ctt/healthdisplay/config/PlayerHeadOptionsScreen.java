package com.ctt.healthdisplay.config;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class PlayerHeadOptionsScreen extends Screen {
    private static final String PREFIX = "ctt-health-display.config.";
    private static final String[] PARTS = {"bar", "numbers", "hearts", "cracked", "effective_max"};
    private final Screen parent;
    private final ModConfig config = ModConfig.INSTANCE;
    private final boolean[] visible = {config.showPlayerHeadBar, config.showPlayerHeadNumbers,
            config.showPlayerHeadHeartCounts, config.showPlayerHeadCrackedCount, config.showPlayerHeadEffectiveMax};

    PlayerHeadOptionsScreen(Screen parent) {
        super(Component.translatable(PREFIX + "option.player_parts"));
        this.parent = parent;
    }

    @Override protected void init() {
        int buttonWidth = Math.max(1, Math.min(220, width - 16));
        int x = (width - buttonWidth) / 2;
        int spacing = Math.clamp((height - 55) / PARTS.length, 20, 24);
        int y = 24;
        for (int i = 0; i < PARTS.length; i++) {
            int part = i;
            addRenderableWidget(Button.builder(label(part), button -> {
                visible[part] = !visible[part];
                button.setMessage(label(part));
            }).bounds(x, y, buttonWidth, 20).build());
            y += spacing;
        }
        addRenderableWidget(Button.builder(Component.translatable(PREFIX + "btn.done"), button -> {
            config.showPlayerHeadBar = visible[0];
            config.showPlayerHeadNumbers = visible[1];
            config.showPlayerHeadHeartCounts = visible[2];
            config.showPlayerHeadCrackedCount = visible[3];
            config.showPlayerHeadEffectiveMax = visible[4];
            config.save();
            onClose();
        }).bounds(x, y + 4, buttonWidth, 20).build());
    }

    private Component label(int part) {
        return Component.translatable(PREFIX + "player_part." + PARTS[part],
                Component.translatable(PREFIX + "value." + (visible[part] ? "on" : "off")));
    }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(font, title, width / 2, 8, 0xFFFFFFFF);
    }

    @Override public void onClose() { minecraft.gui.setScreen(parent); }
}
