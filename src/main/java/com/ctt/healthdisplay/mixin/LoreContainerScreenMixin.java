package com.ctt.healthdisplay.mixin;

import com.ctt.healthdisplay.client.LorePopupManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 记录背包悬停状态；词条弹窗锁定后隐藏原版物品提示，避免两层文字重叠。 */
@Mixin(AbstractContainerScreen.class)
public abstract class LoreContainerScreenMixin {
    @Inject(method = "extractTooltip", at = @At("HEAD"), cancellable = true)
    private void ctt$trackTooltip(GuiGraphicsExtractor context, int mouseX, int mouseY, CallbackInfo ci) {
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        var slot = ((LoreContainerScreenAccessor) screen).ctt$getHoveredSlot();
        if (LorePopupManager.hoverTick(screen, slot, LorePopupManager.scaledMouseX(), LorePopupManager.scaledMouseY())) {
            ci.cancel();
        }
    }
}
