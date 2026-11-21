package com.ctt.healthdisplay.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractContainerScreen.class)
public interface LoreContainerScreenAccessor {
    @Accessor("hoveredSlot")
    Slot ctt$getHoveredSlot();

    @org.spongepowered.asm.mixin.gen.Invoker("getHoveredSlot")
    Slot ctt$getSlotAt(double mouseX, double mouseY);

    @Accessor("leftPos") int tooltipLoreGuiX();
    @Accessor("topPos") int tooltipLoreGuiY();
    @Accessor("imageWidth") int tooltipLoreGuiWidth();
    @Accessor("imageHeight") int tooltipLoreGuiHeight();

    @org.spongepowered.asm.mixin.gen.Invoker("getTooltipFromContainerItem")
    java.util.List<net.minecraft.network.chat.Component> ctt$getTooltip(net.minecraft.world.item.ItemStack stack);
}
