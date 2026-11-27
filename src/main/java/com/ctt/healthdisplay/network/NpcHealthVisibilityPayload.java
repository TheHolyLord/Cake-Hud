package com.ctt.healthdisplay.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** 独立通道，保留旧版血量数据包的兼容性。 */
public record NpcHealthVisibilityPayload(Set<UUID> entities) implements CustomPacketPayload {
    public static final int MAX_ENTITIES = 128;
    public static final Type<NpcHealthVisibilityPayload> ID = new Type<>(
            Identifier.fromNamespaceAndPath("ctt-health-display", "npc_health_visibility"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NpcHealthVisibilityPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.entities.size());
                for (UUID id : payload.entities) buf.writeUUID(id);
            }, buf -> {
                int size = buf.readVarInt();
                if (size < 0 || size > MAX_ENTITIES || size > buf.readableBytes() / 16) {
                    buf.skipBytes(buf.readableBytes());
                    return new NpcHealthVisibilityPayload(Set.of());
                }
                Set<UUID> ids = new HashSet<>();
                for (int i = 0; i < size; i++) ids.add(buf.readUUID());
                return new NpcHealthVisibilityPayload(Set.copyOf(ids));
            });

    @Override
    public Type<NpcHealthVisibilityPayload> type() {
        return ID;
    }
}
