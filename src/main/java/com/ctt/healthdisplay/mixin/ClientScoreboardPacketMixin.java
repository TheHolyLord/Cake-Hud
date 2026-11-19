package com.ctt.healthdisplay.mixin;

import com.ctt.healthdisplay.client.ClientScorePackets;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
abstract class ClientScoreboardPacketMixin implements ClientScorePackets.Access {
    @Unique
    private final ClientScorePackets ctt$scorePackets = new ClientScorePackets();

    @Override
    public ClientScorePackets ctt$getScorePackets() {
        return ctt$scorePackets;
    }

    @Inject(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V",
            at = @At("HEAD"))
    private void ctt$observeScorePacket(ChannelHandlerContext context, Packet<?> packet, CallbackInfo ci) {
        Connection connection = (Connection) (Object) this;
        if (ClientScorePackets.isScorePacket(packet) && connection.isConnected()
                && connection.getPacketListener() instanceof ClientPacketListener listener) {
            ctt$scorePackets.accept(listener, packet);
        }
    }
}
