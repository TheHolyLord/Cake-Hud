package com.ctt.healthdisplay.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 防止 26.3 在恢复游戏焦点时检查未绑定按键（GLFW_KEY_UNKNOWN = -1）而崩溃。
 *
 * <p>旧版 options.txt 或其他模组都可能留下 -1。26.3 的
 * {@link InputConstants#isKeyDown(int)} 会直接用键值读取原生按键状态缓冲区，
 * 因此必须在进入原方法前把负数按键视为“没有按下”。
 */
@Mixin(InputConstants.class)
public abstract class InputConstantsSafetyMixin {

    @Inject(method = "isKeyDown", at = @At("HEAD"), cancellable = true)
    private static void ctt_ignoreUnknownKey(int keyCode, CallbackInfoReturnable<Boolean> cir) {
        if (keyCode < 0) {
            cir.setReturnValue(false);
        }
    }
}
