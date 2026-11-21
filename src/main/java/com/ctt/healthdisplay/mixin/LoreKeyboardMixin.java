package com.ctt.healthdisplay.mixin;

import com.ctt.healthdisplay.client.LorePopupManager;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public abstract class LoreKeyboardMixin {
    @Inject(method = "keyPress", at = @At("HEAD"))
    private void ctt$pinItemTooltip(long window, int action, KeyEvent event, CallbackInfo ci) {
        if (action != InputConstants.PRESS
                || (event.key() != InputConstants.KEY_LALT && event.key() != InputConstants.KEY_RALT)) return;
        Minecraft client = Minecraft.getInstance();
        if (window == client.getWindow().handle() && client.gui.overlay() == null
                && client.gui.screen() instanceof AbstractContainerScreen<?> screen) {
            LorePopupManager.toggleTooltipPin(screen);
        }
    }
}
