package com.ctt.healthdisplay.mixin;

import com.ctt.healthdisplay.health.HealthData;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.world.BossEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 客户端侧隐藏指定 boss bar 的实现。
 *
 * <p>历史（v8.2.0 之前）：在 render 两头把要隐藏的 bar 从 map 里 remove 再塞回，
 * 三个副作用导致队友 bossbar 与队友 HUD 闪烁；v8.2.0 起改用 {@link Redirect}
 * 拦截迭代源，返回过滤掉隐藏 UUID 的快照 List，原 Map 从不被修改。
 *
 * <p>26.3 适配：{@code BossBarHud} 已并入 {@link BossHealthOverlay}，渲染入口从
 * {@code render(DrawContext)} 改为 {@code extractRenderState(GuiGraphicsExtractor)}，
 * 字段 {@code bossBars} 更名为 {@code events}（值类型 {@link BossEvent}）。
 * Redirect 语义不变：仍只拦截 {@code Map.values()} 一次调用，隐藏 UUID 的 bar
 * 不进入绘制循环，原 Map 永不被修改。
 */
@Mixin(BossHealthOverlay.class)
public abstract class BossBarHudRenderMixin {

    @Shadow
    @Final
    private Map<UUID, BossEvent> events;

    @Redirect(
            method = "extractRenderState",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/Map;values()Ljava/util/Collection;"
            )
    )
    private Collection<BossEvent> ctt_filterBossBars(Map<UUID, BossEvent> map) {
        Set<UUID> hidden = HealthData.getHiddenBarUUIDs();
        if (hidden.isEmpty()) {
            // 快路径：没有要隐藏的，直接返回原集合，零额外分配。
            return map.values();
        }
        List<BossEvent> filtered = new ArrayList<>(Math.max(0, map.size() - hidden.size()));
        for (Map.Entry<UUID, BossEvent> entry : map.entrySet()) {
            if (!hidden.contains(entry.getKey())) {
                filtered.add(entry.getValue());
            }
        }
        return filtered;
    }
}
