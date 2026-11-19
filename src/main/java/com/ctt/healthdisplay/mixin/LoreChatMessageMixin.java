package com.ctt.healthdisplay.mixin;

import com.ctt.healthdisplay.client.LoreTextDecorator;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(value = ChatComponent.class, priority = 1200)
public abstract class LoreChatMessageMixin {
    // ChatPlus adds its URL styles at HEAD and reads the constructed message at RETURN.
    @ModifyArg(method = "addMessage", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/chat/GuiMessage;<init>(ILnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V"), index = 1)
    private Component ctt$decorateLore(Component message) {
        return LoreTextDecorator.decorate(message);
    }
}
