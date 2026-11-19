package com.ctt.healthdisplay.mixin;

import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.world.BossEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;
import java.util.UUID;

/** 26.3：{@code BossBarHud} 合并进 {@link BossHealthOverlay}，字段名 {@code events}。 */
@Mixin(BossHealthOverlay.class)
public interface BossBarHudAccessor {
    @Accessor("events")
    Map<UUID, BossEvent> getBossBars();
}
