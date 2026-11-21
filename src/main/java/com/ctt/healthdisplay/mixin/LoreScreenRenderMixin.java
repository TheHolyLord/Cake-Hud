package com.ctt.healthdisplay.mixin;

import com.ctt.healthdisplay.client.LorePopupManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import com.ctt.healthdisplay.client.LoreAdvancements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class LoreScreenRenderMixin {
    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void ctt$beginLoreFrame(DeltaTracker delta, boolean hud, boolean screens, CallbackInfo ci) {
        Gui gui = (Gui) (Object) this;
        LorePopupManager.startFrame();
        LoreAdvancements.startFrame(gui.screen());
    }

    @com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;applyCursor(Lcom/mojang/blaze3d/platform/Window;)V"))
    private void ctt$renderLorePopup(GuiGraphicsExtractor ctx, com.mojang.blaze3d.platform.Window window,
                                    com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> original) {
        Gui gui = (Gui) (Object) this;
        if (gui.overlay() == null) LorePopupManager.render(ctx);
        original.call(ctx, window);
    }
}
