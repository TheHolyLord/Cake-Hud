package com.ctt.healthdisplay.mixin;

import com.ctt.healthdisplay.client.LoreAdvancements;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.advancements.AdvancementWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AdvancementWidget.class)
public abstract class LoreAdvancementWidgetMixin {
    @Shadow private AdvancementProgress progress;

    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void ctt$trackPinned(GuiGraphicsExtractor ctx, int ox, int oy, CallbackInfo ci) {
        LoreAdvancements.trackPinned(ctx, (AdvancementWidget) (Object) this, progress, ox, oy);
    }

    @Inject(method = "extractHover", at = @At("HEAD"), cancellable = true)
    private void ctt$hover(GuiGraphicsExtractor ctx, int ox, int oy, float alpha,
                           int left, int top, int width, CallbackInfo ci) {
        AdvancementWidget widget = (AdvancementWidget) (Object) this;
        if (LoreAdvancements.capture(ctx, widget, progress, ox, oy)) ci.cancel();
    }
}
