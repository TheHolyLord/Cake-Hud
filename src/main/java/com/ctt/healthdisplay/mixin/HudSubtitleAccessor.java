package com.ctt.healthdisplay.mixin;

import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.components.SubtitleOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 读取原版 HUD 持有的字幕层，用来判断屏幕上是否有声音字幕。 */
@Mixin(Hud.class)
public interface HudSubtitleAccessor {
    @Accessor("subtitleOverlay")
    SubtitleOverlay ctt$getSubtitleOverlay();
}
