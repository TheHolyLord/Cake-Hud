package com.ctt.healthdisplay.mixin;

import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.client.gui.screens.advancements.AdvancementWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AdvancementWidget.class)
public interface LoreAdvancementProgressAccessor {
    @Accessor("progress") AdvancementProgress ctt$getProgress();
}
