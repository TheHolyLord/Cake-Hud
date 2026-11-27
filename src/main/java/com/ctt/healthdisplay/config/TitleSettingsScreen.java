package com.ctt.healthdisplay.config;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.IntConsumer;

final class TitleSettingsScreen extends Screen {
    private static final String PREFIX = "ctt-health-display.config.";
    private static final int SLIDER_TOP = 42;
    private static final int SLIDER_HEIGHT = 20;
    private static final int ROW_SPACING = 23;
    private static final int COLUMN_GAP = 6;

    private final Screen parent;
    private final LineSettings titleLine;
    private final LineSettings subtitleLine;
    private int columnWidth;
    private int leftX;

    TitleSettingsScreen(Screen parent) {
        super(Component.translatable(PREFIX + "option.title_settings"));
        this.parent = parent;
        ModConfig config = ModConfig.INSTANCE;
        titleLine = new LineSettings(config.titleOffsetX, config.titleOffsetY, config.titleSize, config.titleOpacity);
        subtitleLine = new LineSettings(config.subtitleOffsetX, config.subtitleOffsetY, config.subtitleSize, config.subtitleOpacity);
    }

    @Override protected void init() {
        columnWidth = Math.max(1, Math.min(156, (width - 16 - COLUMN_GAP) / 2));
        leftX = (width - 2 * columnWidth - COLUMN_GAP) / 2;
        int rightX = leftX + columnWidth + COLUMN_GAP;
        addLineSliders(leftX, titleLine);
        addLineSliders(rightX, subtitleLine);

        int buttonY = SLIDER_TOP + 4 * ROW_SPACING;
        addRenderableWidget(Button.builder(Component.translatable(PREFIX + "title_reset"), button -> {
            titleLine.reset();
            subtitleLine.reset();
            rebuildWidgets();
        }).bounds(leftX, buttonY, columnWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable(PREFIX + "btn.done"), button -> {
            ModConfig config = ModConfig.INSTANCE;
            config.titleOffsetX = titleLine.offsetX;
            config.titleOffsetY = titleLine.offsetY;
            config.titleSize = titleLine.size;
            config.titleOpacity = titleLine.opacity / 100.0;
            config.subtitleOffsetX = subtitleLine.offsetX;
            config.subtitleOffsetY = subtitleLine.offsetY;
            config.subtitleSize = subtitleLine.size;
            config.subtitleOpacity = subtitleLine.opacity / 100.0;
            config.save();
            onClose();
        }).bounds(rightX, buttonY, columnWidth, 20).build());
    }

    private void addLineSliders(int x, LineSettings line) {
        int limit = ModConfig.TITLE_OFFSET_LIMIT;
        addSlider(x, SLIDER_TOP, "title_offset_x", -limit, limit, line.offsetX, v -> line.offsetX = v);
        addSlider(x, SLIDER_TOP + ROW_SPACING, "title_offset_y", -limit, limit, line.offsetY, v -> line.offsetY = v);
        addSlider(x, SLIDER_TOP + 2 * ROW_SPACING, "title_size", ModConfig.MIN_TITLE_SIZE,
                ModConfig.MAX_TITLE_SIZE, line.size, v -> line.size = v);
        addSlider(x, SLIDER_TOP + 3 * ROW_SPACING, "title_opacity", 10, 100, line.opacity, v -> line.opacity = v);
    }

    private void addSlider(int x, int y, String key, int min, int max, int initial, IntConsumer setter) {
        String translation = PREFIX + key;
        addRenderableWidget(new AbstractSliderButton(x, y, columnWidth, SLIDER_HEIGHT,
                Component.translatable(translation, initial), (initial - min) / (double) (max - min)) {
            private int selected() {
                return min + (int) Math.round(value * (max - min));
            }

            @Override protected void updateMessage() { setMessage(Component.translatable(translation, selected())); }
            @Override protected void applyValue() { setter.accept(selected()); }
        });
    }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(font, title, width / 2, 8, 0xFFFFFFFF);
        int center = leftX + columnWidth / 2;
        graphics.centeredText(font, Component.translatable(PREFIX + "title_main_heading"),
                center, 28, 0xFFCCCCCC);
        graphics.centeredText(font, Component.translatable(PREFIX + "title_sub_heading"),
                center + columnWidth + COLUMN_GAP, 28, 0xFFCCCCCC);
    }

    @Override public void onClose() { minecraft.gui.setScreen(parent); }

    private static final class LineSettings {
        private int offsetX, offsetY, size, opacity;

        private LineSettings(int offsetX, int offsetY, int size, double opacity) {
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.size = size;
            this.opacity = (int) Math.round(opacity * 100);
        }

        private void reset() {
            offsetX = 0;
            offsetY = 0;
            size = 100;
            opacity = 100;
        }
    }
}
