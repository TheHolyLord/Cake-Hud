package com.ctt.healthdisplay.mixin;

import com.ctt.healthdisplay.client.LorePopupManager;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class LoreInputMixin {
    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void ctt$click(long window, MouseButtonInfo button, int action, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (window != mc.getWindow().handle() || button.button() != InputConstants.MOUSE_BUTTON_LEFT) return;
        if (action == InputConstants.RELEASE) {
            LorePopupManager.setLeftDown(false);
            if (LorePopupManager.release(LorePopupManager.scaledMouseX(), LorePopupManager.scaledMouseY())) ci.cancel();
        } else if (action == InputConstants.PRESS) {
            LorePopupManager.setLeftDown(true);
            if (mc.gui.overlay() == null && LorePopupManager.click(mc.gui.screen(),
                    LorePopupManager.scaledMouseX(), LorePopupManager.scaledMouseY())) ci.cancel();
        }
    }
}
