package com.ctt.healthdisplay.mixin;

import com.ctt.healthdisplay.client.LoreTextDecorator;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatScreen.class)
public abstract class LoreChatClickMixin {
    @Inject(method = "handleComponentClicked", at = @At("HEAD"), cancellable = true)
    private void ctt$openLore(Style style, boolean insertion, CallbackInfoReturnable<Boolean> cir) {
        if (!insertion && style != null && LoreTextDecorator.intercept(style.getClickEvent(),
                style.getColor() == null ? 0xFFFFFF : style.getColor().getValue())) {
            cir.setReturnValue(true);
        }
    }
}
