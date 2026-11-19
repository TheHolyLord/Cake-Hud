package com.ctt.healthdisplay.mixin;

import com.ctt.healthdisplay.client.LoreAdvancements;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "de.dafuqs.paginatedadvancements.client.PaginatedAdvancementTab", remap = false)
public abstract class LorePaginatedTabMixin {
    @Inject(method = "drawWidgetTooltip(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIIIII)V",
            at = @At("HEAD"), require = 0)
    private void ctt$tab(GuiGraphicsExtractor ctx, int mx, int my, int x, int y, int right, int bottom, CallbackInfo ci) {
        LoreAdvancements.tab(this);
    }
}
