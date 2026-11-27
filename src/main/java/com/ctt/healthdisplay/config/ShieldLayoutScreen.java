package com.ctt.healthdisplay.config;

import com.ctt.healthdisplay.hud.ShieldCooldownRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class ShieldLayoutScreen extends Screen implements PreviewDrag {
    private static final String PREFIX = "ctt-health-display.config.";
    private final Screen parent;
    private final ModConfig config = ModConfig.INSTANCE;
    private float x = config.shieldX;
    private float y = config.shieldY;
    private boolean dragging;
    private int dragLeft;
    private int dragTop;
    private double mouseStartX;
    private double mouseStartY;
    private Button resetButton;
    private Button doneButton;

    ShieldLayoutScreen(Screen parent) {
        super(Component.translatable(PREFIX + "shield_layout"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        dragging = false;
        int buttonWidth = Math.max(1, Math.min(99, width / 2 - 4));
        resetButton = addRenderableWidget(Button.builder(Component.translatable(PREFIX + "btn.reset_position"), button -> {
            x = ModConfig.DEFAULT_SHIELD_X;
            y = ModConfig.DEFAULT_SHIELD_Y;
            positionButtons(bounds());
        }).bounds(width / 2 - buttonWidth - 2, height - 26, buttonWidth, 20).build());
        doneButton = addRenderableWidget(Button.builder(Component.translatable(PREFIX + "btn.done"), button -> {
            config.shieldX = x;
            config.shieldY = y;
            config.save();
            onClose();
        }).bounds(width / 2 + 2, height - 26, buttonWidth, 20).build());
        positionButtons(bounds());
    }

    private ShieldCooldownRenderer.Bounds bounds() {
        return ShieldCooldownRenderer.bounds(width, height, x, y, config.shieldHorizontal, config.shieldSize);
    }

    private void positionButtons(ShieldCooldownRenderer.Bounds preview) {
        // 预览拖到底部时，把按钮移到上方，保留整个屏幕作为可摆放区域。
        int buttonY = preview.top() + preview.height() + 5 > height - 26 ? 36 : height - 26;
        resetButton.setY(buttonY);
        doneButton.setY(buttonY);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);
        context.centeredText(font, title, width / 2, 8, 0xFFFFFFFF);
        context.centeredText(font, Component.translatable(PREFIX + "shield_layout_hint"), width / 2, 22, 0xFFCCCCCC);
        var preview = bounds();
        context.outline(preview.left() - 2, preview.top() - 2, preview.width() + 4,
                preview.height() + 4, dragging ? 0xFF72D68A : 0xFF83929D);
        ShieldCooldownRenderer.draw(context, preview, config.shieldHorizontal, 0.65f, config.shieldOpacity);
    }

    @Override
    public boolean beginPreviewDrag(double mouseX, double mouseY) {
        if (children().stream().anyMatch(child -> child.isMouseOver(mouseX, mouseY))) return false;
        var preview = bounds();
        if (mouseX < preview.left() - 5 || mouseX > preview.left() + preview.width() + 5
                || mouseY < preview.top() - 5 || mouseY > preview.top() + preview.height() + 5) return false;
        dragging = true;
        dragLeft = preview.left();
        dragTop = preview.top();
        mouseStartX = mouseX;
        mouseStartY = mouseY;
        return true;
    }

    @Override
    public void movePreviewDrag(double mouseX, double mouseY) {
        if (!dragging || width <= 0 || height <= 0 || (mouseStartX == mouseX && mouseStartY == mouseY && y < 0)) return;
        var preview = bounds();
        int left = Math.clamp(dragLeft + (int) Math.round(mouseX - mouseStartX), 0, width - preview.width());
        int top = Math.clamp(dragTop + (int) Math.round(mouseY - mouseStartY), 0, height - preview.height());
        x = (left + preview.width() / 2) / (float) width;
        y = (top + preview.height() / 2) / (float) height;
        positionButtons(bounds());
    }

    @Override
    public void endPreviewDrag() {
        dragging = false;
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }
}
