package com.ctt.healthdisplay.mixin;

import net.minecraft.client.gui.components.LerpingBossEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * v8.5.3 · 读取 bossbar 的【目标】进度（未插值的原始 value/max 比值）。
 *
 * <p>datapack 用 {@code store result bossbar ... value/max} 把
 * RedHearts/MaxHP 或 SoulHearts/MaxSoulHearts 写进锁定怪的血条；
 * {@link LerpingBossEvent#getProgress()} 返回的是渲染插值中的值，
 * 直接拿来反推魂心数会抖。{@code targetPercent} 是服务端下发后的
 * 真实比值（26.3 字段名，protected），这里开一个只读访问器。
 */
@Mixin(LerpingBossEvent.class)
public interface LerpingBossEventAccessor {

    @Accessor("targetPercent")
    float ctt$getTargetPercent();
}
