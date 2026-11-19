package com.ctt.healthdisplay.mixin;

import com.ctt.healthdisplay.client.ClientStageDetector;
import net.minecraft.client.gui.Hud;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * v7.0.14 · 拦截 vanilla title / subtitle 显示，喂给 {@link ClientStageDetector}。
 *
 * <p>{@link Hud#setTitle(Component)} 与 {@link Hud#setSubtitle(Component)}
 * 是 vanilla 客户端在收到 {@code TitleS2CPacket} / {@code SubtitleS2CPacket}
 * 后写入 HUD 状态的入口，所有"屏幕中央关卡名"都会从这里过。
 *
 * <p>{@code @Inject HEAD} 不修改原行为，只做被动观察；detector 内部用正则
 * 区分关卡 title（{@code "Tier-Floor"} 等格式）vs 对话 title。
 */
@Mixin(Hud.class)
public class InGameHudTitleMixin {

    @Inject(method = "setTitle", at = @At("HEAD"))
    private void ctt$onSetTitle(Component title, CallbackInfo ci) {
        ClientStageDetector.onTitle(title);
    }

    @Inject(method = "setSubtitle", at = @At("HEAD"))
    private void ctt$onSetSubtitle(Component subtitle, CallbackInfo ci) {
        ClientStageDetector.onSubtitle(subtitle);
    }
}
