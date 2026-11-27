package com.ctt.healthdisplay.config;

import com.ctt.healthdisplay.hud.ArrowPreviewRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

final class ArrowLayoutScreen extends Screen implements PreviewDrag {
    private static final String PREFIX = "ctt-health-display.config.";
    private static final int SIZE = ArrowPreviewRenderer.SIZE;
    private final Screen parent;
    private final ItemStack sample = new ItemStack(Items.ARROW, 32);
    private float x = ModConfig.INSTANCE.arrowPreviewX;
    private float y = ModConfig.INSTANCE.arrowPreviewY;
    private boolean held;
    private long pressedAt;
    private double startX, startY;
    private int startLeft, startTop;
    private Button resetButton, doneButton;

    ArrowLayoutScreen(Screen parent) {
        super(Component.translatable(PREFIX + "arrow_layout"));
        this.parent = parent;
    }

    @Override protected void init() {
        held = false;
        int buttonWidth = Math.max(1, Math.min(99, width / 2 - 4));
        resetButton = addRenderableWidget(Button.builder(Component.translatable(PREFIX + "btn.reset_position"), button -> {
            x = y = -1f;
            placeButtons();
        }).bounds(width / 2 - buttonWidth - 2, 0, buttonWidth, 20).build());
        doneButton = addRenderableWidget(Button.builder(Component.translatable(PREFIX + "btn.done"), button -> {
            var config = ModConfig.INSTANCE;
            config.arrowPreviewX = x;
            config.arrowPreviewY = y;
            config.save();
            onClose();
        }).bounds(width / 2 + 2, 0, buttonWidth, 20).build());
        placeButtons();
    }

    private ArrowPreviewRenderer.Bounds bounds() {
        return ArrowPreviewRenderer.bounds(width, height, x, y);
    }

    private void placeButtons() {
        int buttonY = bounds().top() + SIZE + 4 > height - 26 ? 36 : height - 26;
        resetButton.setY(buttonY);
        doneButton.setY(buttonY);
    }

    private boolean canDrag() {
        return held && System.nanoTime() - pressedAt >= 250_000_000L;
    }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (canDrag()) movePreviewDrag(mouseX, mouseY);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(font, title, width / 2, 8, 0xFFFFFFFF);
        graphics.centeredText(font, Component.translatable(PREFIX + "arrow_layout_hint"), width / 2, 22, 0xFFCCCCCC);
        var preview = bounds();
        graphics.outline(preview.left() - 2, preview.top() - 2, SIZE + 4, SIZE + 4,
                canDrag() ? 0xFF72D68A : 0xFF83929D);
        ArrowPreviewRenderer.draw(graphics, font, sample, preview);
    }

    @Override public boolean beginPreviewDrag(double mouseX, double mouseY) {
        var preview = bounds();
        if (mouseX < preview.left() || mouseX >= preview.left() + SIZE
                || mouseY < preview.top() || mouseY >= preview.top() + SIZE) return false;
        held = true;
        pressedAt = System.nanoTime();
        startX = mouseX;
        startY = mouseY;
        startLeft = preview.left();
        startTop = preview.top();
        return true;
    }

    @Override public void movePreviewDrag(double mouseX, double mouseY) {
        if (!canDrag()) return;
        int left = Math.clamp(startLeft + (int) Math.round(mouseX - startX), 0, Math.max(0, width - SIZE));
        int top = Math.clamp(startTop + (int) Math.round(mouseY - startY), 0, Math.max(0, height - SIZE));
        var current = bounds();
        if (left == current.left() && top == current.top()) return;
        x = (left + SIZE / 2f) / width;
        y = (top + SIZE / 2f) / height;
        placeButtons();
    }

    @Override public void endPreviewDrag() { held = false; }

    @Override public void onClose() { minecraft.gui.setScreen(parent); }
}
