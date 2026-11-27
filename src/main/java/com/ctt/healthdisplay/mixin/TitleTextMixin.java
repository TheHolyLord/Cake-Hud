package com.ctt.healthdisplay.mixin;

import com.ctt.healthdisplay.config.ModConfig;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/** 分别调整 /title 的两行，保留原版显示时间和淡入淡出。 */
@Mixin(Hud.class)
public abstract class TitleTextMixin {
    private static final String EXTRACT_TITLE =
            "extractTitle(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V";
    private static final String TEXT_WITH_BACKDROP =
            "Lnet/minecraft/client/gui/GuiGraphicsExtractor;textWithBackdrop("
                    + "Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIII)V";

    @WrapOperation(method = EXTRACT_TITLE, at = @At(value = "INVOKE", target = TEXT_WITH_BACKDROP, ordinal = 0))
    private void ctt$drawTitle(GuiGraphicsExtractor graphics, Font font, Component text,
                               int x, int y, int textWidth, int argb, Operation<Void> original) {
        ctt$drawTitleLine(graphics, font, text, x, y, textWidth, argb, original, false, 4f);
    }

    @WrapOperation(method = EXTRACT_TITLE, at = @At(value = "INVOKE", target = TEXT_WITH_BACKDROP, ordinal = 1))
    private void ctt$drawSubtitle(GuiGraphicsExtractor graphics, Font font, Component text,
                                  int x, int y, int textWidth, int argb, Operation<Void> original) {
        ctt$drawTitleLine(graphics, font, text, x, y, textWidth, argb, original, true, 2f);
    }

    @Unique
    private static void ctt$drawTitleLine(GuiGraphicsExtractor graphics, Font font, Component text,
                                          int x, int y, int textWidth, int argb, Operation<Void> original,
                                          boolean subtitle, float vanillaScale) {
        ModConfig config = ModConfig.INSTANCE;
        double opacity = subtitle ? config.subtitleOpacity : config.titleOpacity;
        int alpha = (int) Math.round((argb >>> 24) * opacity);
        if (alpha == 0) return;
        int color = (alpha << 24) | (argb & 0x00FFFFFF);
        float size = (subtitle ? config.subtitleSize : config.titleSize) / 100f;
        float offsetX = config.titleOffsetForWidth(graphics.guiWidth(), subtitle) / vanillaScale;
        float offsetY = config.titleOffsetForHeight(graphics.guiHeight(), subtitle) / vanillaScale;

        // 此处原版已将矩阵缩放，位移先换算回文字坐标，再绕文字行中心单独缩放。
        var pose = graphics.pose();
        pose.pushMatrix();
        try {
            float centerY = y + font.lineHeight / 2f;
            pose.translate(offsetX, offsetY + centerY * (1 - size));
            pose.scale(size, size);
            original.call(graphics, font, text, x, y, textWidth, color);
        } finally {
            pose.popMatrix();
        }
    }
}
