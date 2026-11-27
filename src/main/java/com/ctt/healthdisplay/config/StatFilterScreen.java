package com.ctt.healthdisplay.config;

import com.ctt.healthdisplay.CttHealthDisplay;
import com.ctt.healthdisplay.client.StatRowIdentity;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

final class StatFilterScreen extends Screen {
    private static final String PREFIX = "ctt-health-display.config.";
    private final Screen parent;
    private final Set<String> hidden = new HashSet<>(ModConfig.INSTANCE.hiddenStatRows);
    private final Map<String, Component> rows = new LinkedHashMap<>();
    private int scroll;
    private int capacity;

    StatFilterScreen(Screen parent) {
        super(Component.translatable(PREFIX + "option.stat_filter"));
        this.parent = parent;
        for (Component line : CttHealthDisplay.statsData.getLines()) {
            rows.putIfAbsent(StatRowIdentity.of(line), line);
        }
        for (String key : hidden.stream().sorted().toList()) rows.putIfAbsent(key, StatRowIdentity.fallbackLabel(key));
    }

    @Override
    protected void init() {
        int buttonWidth = Math.min(width - 20, 320);
        int x = (width - buttonWidth) / 2;
        capacity = Math.max(1, (height - 77) / 22);
        scroll = Math.clamp(scroll, 0, Math.max(0, rows.size() - capacity));
        int index = 0;
        for (var row : rows.entrySet()) {
            if (index >= scroll && index < scroll + capacity) {
                String key = row.getKey();
                String label = StatRowIdentity.label(row.getValue());
                if (key.startsWith("hover:")) {
                    String hover = StatRowIdentity.fallbackLabel(key).getString();
                    if (!hover.equals(key.substring("hover:".length()))) label = hover;
                }
                String clipped = font.plainSubstrByWidth(label, Math.max(0, buttonWidth - 45));
                addRenderableWidget(Button.builder(rowText(key, clipped), button -> {
                    if (!hidden.remove(key)) hidden.add(key);
                    button.setMessage(rowText(key, clipped));
                }).bounds(x, 40 + (index - scroll) * 22, buttonWidth, 20).build());
            }
            index++;
        }
        int center = width / 2;
        addRenderableWidget(Button.builder(Component.translatable(PREFIX + "filter_clear"), button -> {
            hidden.clear();
            rebuildWidgets();
        }).bounds(center - 101, height - 28, 99, 20).build());
        addRenderableWidget(Button.builder(Component.translatable(PREFIX + "btn.done"), button -> {
            ModConfig.INSTANCE.hiddenStatRows = new HashSet<>(hidden);
            ModConfig.INSTANCE.save();
            onClose();
        }).bounds(center + 2, height - 28, 99, 20).build());
    }

    private Component rowText(String key, String label) {
        return Component.translatable(PREFIX + (hidden.contains(key) ? "filter_hidden" : "filter_visible"), label);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);
        context.centeredText(font, title, width / 2, 9, 0xFFFFFFFF);
        context.centeredText(font, Component.translatable(PREFIX + (rows.isEmpty() ? "filter_empty" : "filter_hint")),
                width / 2, 23, 0xFFCCCCCC);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (vertical == 0 || rows.size() <= capacity) return super.mouseScrolled(x, y, horizontal, vertical);
        int next = Math.clamp(scroll + (vertical < 0 ? 1 : -1), 0, rows.size() - capacity);
        if (next != scroll) {
            scroll = next;
            rebuildWidgets();
        }
        return true;
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }
}
