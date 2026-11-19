package com.ctt.healthdisplay.mixin;

import com.ctt.healthdisplay.client.LoreAdvancements;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.advancements.AdvancementTab;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AdvancementTab.class)
public abstract class LoreAdvancementTabMixin {
    @Inject(method = "extractTooltips", at = @At("HEAD"))
    private void ctt$tab(GuiGraphicsExtractor ctx, int x, int y, CallbackInfo ci) {
        LoreAdvancements.tab(this);
    }
}
