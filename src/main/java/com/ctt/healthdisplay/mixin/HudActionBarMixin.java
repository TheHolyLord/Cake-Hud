package com.ctt.healthdisplay.mixin;

import com.ctt.healthdisplay.client.ActionBarCapture;
import net.minecraft.client.gui.Hud;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 被动读取原版 actionbar，不改变其显示结果。 */
@Mixin(Hud.class)
public class HudActionBarMixin {

    @Inject(method = "setOverlayMessage", at = @At("HEAD"))
    private void ctt$onSetOverlayMessage(Component message, boolean aboveHotbar, CallbackInfo ci) {
        ActionBarCapture.onActionBar(message);
    }
}
