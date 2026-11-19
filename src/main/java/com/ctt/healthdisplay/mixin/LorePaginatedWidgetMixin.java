package com.ctt.healthdisplay.mixin;

import com.ctt.healthdisplay.client.LoreAdvancements;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.advancements.AdvancementWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "de.dafuqs.paginatedadvancements.client.PaginatedAdvancementWidget", remap = false)
public abstract class LorePaginatedWidgetMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;II)V", at = @At("HEAD"), require = 0)
    private void ctt$trackPinned(GuiGraphicsExtractor ctx, int ox, int oy, CallbackInfo ci) {
        AdvancementWidget widget = (AdvancementWidget) (Object) this;
        LoreAdvancements.trackPinned(ctx, widget, ((LoreAdvancementProgressAccessor) widget).ctt$getProgress(), ox, oy);
    }

    @Inject(method = "extractHover(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIFIII)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void ctt$hover(GuiGraphicsExtractor ctx, int ox, int oy, float alpha,
                           int left, int top, int width, CallbackInfo ci) {
        AdvancementWidget widget = (AdvancementWidget) (Object) this;
        if (LoreAdvancements.capture(ctx, widget, ((LoreAdvancementProgressAccessor) widget).ctt$getProgress(), ox, oy)) {
            ci.cancel();
        }
    }
}
