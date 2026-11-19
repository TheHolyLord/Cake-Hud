package com.ctt.healthdisplay.mixin;

import net.minecraft.client.gui.components.SubtitleOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/** 读取原版字幕层当前正在显示的声音字幕。 */
@Mixin(SubtitleOverlay.class)
public interface SubtitleOverlayAccessor {
    @Accessor("audibleSubtitles")
    List<?> ctt$getAudibleSubtitles();
}
