package com.ctt.healthdisplay.mixin;

import com.ctt.healthdisplay.client.LorePopupManager;
import com.ctt.healthdisplay.config.ConfigScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class LoreInputMixin {
    @Unique private ConfigScreen ctt$previewDrag;

    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void ctt$click(long window, MouseButtonInfo button, int action, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (window != mc.getWindow().handle() || button.button() != InputConstants.MOUSE_BUTTON_LEFT) return;
        double x = LorePopupManager.scaledMouseX(), y = LorePopupManager.scaledMouseY();
        if (action == InputConstants.RELEASE) {
            LorePopupManager.setLeftDown(false);
            if (ctt$previewDrag != null) {
                if (ctt$previewDrag == mc.gui.screen()) ctt$previewDrag.movePreviewDrag(x, y);
                ctt$previewDrag.endPreviewDrag();
                ctt$previewDrag = null;
                ci.cancel();
            } else if (LorePopupManager.release(x, y)) {
                ci.cancel();
            }
        } else if (action == InputConstants.PRESS) {
            LorePopupManager.setLeftDown(true);
            if (mc.gui.overlay() != null) return;
            if (mc.gui.screen() instanceof ConfigScreen config && config.beginPreviewDrag(x, y)) {
                ctt$previewDrag = config;
                ci.cancel();
            } else if (LorePopupManager.click(mc.gui.screen(), x, y)) {
                ci.cancel();
            }
        }
    }

    @Inject(method = "onMove", at = @At("TAIL"))
    private void ctt$move(long window, double x, double y, double deltaX, double deltaY, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (window == 0 || window != mc.getWindow().handle() || !mc.isWindowActive()) return;
        if (ctt$previewDrag != null && ctt$previewDrag == mc.gui.screen()) {
            ctt$previewDrag.movePreviewDrag(LorePopupManager.scaledMouseX(), LorePopupManager.scaledMouseY());
        }
        LorePopupManager.move(LorePopupManager.scaledMouseX(), LorePopupManager.scaledMouseY(),
                mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
    }
}
