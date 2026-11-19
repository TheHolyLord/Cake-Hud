package com.ctt.healthdisplay.mixin;

import com.ctt.healthdisplay.hud.ShieldCooldownRenderer;
import net.minecraft.client.gui.components.SubtitleOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 在原版字幕层完成本帧筛选后，把“当前确实有字幕”状态交给盾牌冷却条。 */
@Mixin(SubtitleOverlay.class)
public class SubtitleOverlayMixin {

    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void ctt$clearSubtitleState(net.minecraft.client.gui.GuiGraphicsExtractor context, CallbackInfo ci) {
        ShieldCooldownRenderer.setSubtitlesVisible(false);
    }

    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void ctt$publishSubtitleState(net.minecraft.client.gui.GuiGraphicsExtractor context, CallbackInfo ci) {
        try {
            SubtitleOverlayAccessor accessor = (SubtitleOverlayAccessor) (Object) this;
            var subtitles = accessor.ctt$getAudibleSubtitles();
            boolean enabled = net.minecraft.client.Minecraft.getInstance().options.showSubtitles().get();
            ShieldCooldownRenderer.setSubtitlesVisible(enabled && subtitles != null && !subtitles.isEmpty());
        } catch (Throwable ignored) {
            ShieldCooldownRenderer.setSubtitlesVisible(false);
        }
    }
}
