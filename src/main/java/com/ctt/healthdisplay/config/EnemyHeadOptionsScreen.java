package com.ctt.healthdisplay.config;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class EnemyHeadOptionsScreen extends Screen {
    private static final String PREFIX = "ctt-health-display.config.";
    private static final String[] PARTS = {"name", "armor", "hearts", "numbers", "bar"};
    private final Screen parent;
    private final ModConfig config = ModConfig.INSTANCE;
    private final boolean[] visible = {config.showEnemyName, config.showEnemyArmor, config.showEnemyExtraHearts,
            config.showEnemyHealthNumbers, config.showEnemyBar};

    EnemyHeadOptionsScreen(Screen parent) {
        super(Component.translatable(PREFIX + "option.enemy_parts"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int buttonWidth = Math.max(1, Math.min(200, width - 16));
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
            config.showEnemyName = visible[0];
            config.showEnemyArmor = visible[1];
            config.showEnemyExtraHearts = visible[2];
            config.showEnemyHealthNumbers = visible[3];
            config.showEnemyBar = visible[4];
            config.save();
            onClose();
        }).bounds(x, y + 4, buttonWidth, 20).build());
    }

    private Component label(int part) {
        return Component.translatable(PREFIX + "enemy_part." + PARTS[part],
                Component.translatable(PREFIX + "value." + (visible[part] ? "on" : "off")));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);
        context.centeredText(font, title, width / 2, 8, 0xFFFFFFFF);
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }
}
