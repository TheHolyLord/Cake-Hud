package com.ctt.healthdisplay.server.mixin;

import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Display.TextDisplay.class)
public interface TextDisplayBackgroundAccessor {
    @Invoker("getBackgroundColor")
    int ctt$getBackgroundColor();
}
