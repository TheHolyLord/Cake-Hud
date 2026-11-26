package com.ctt.healthdisplay.mixin;

import com.ctt.healthdisplay.CttHealthDisplay;
import com.ctt.healthdisplay.client.HudRenderGate;
import com.ctt.healthdisplay.config.ModConfig;
import com.ctt.healthdisplay.health.HealthData;
import com.ctt.healthdisplay.health.MobHealthData;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.EntityType;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.UUID;

/**
 * 26.3 重构说明：vanilla 名牌渲染已改为延迟提交架构——
 * {@code EntityRenderer.renderLabelIfPresent(...)}（立即模式 VertexConsumer）不复存在，
 * 取而代之的是 {@code EntityRenderer.submitNameDisplay(EntityRenderState, PoseStack,
 * SubmitNodeCollector, CameraRenderState)}。
 *
 * <p>本 Mixin 因此只保留"取消 vanilla 名牌"的语义（怪物头顶血量 / 队友数字条场景），
 * 不再在这里就地画血条：世界空间里的队友 / 怪物头顶条统一由
 * {@link com.ctt.healthdisplay.hud.TeammateWorldRenderer} 通过官方 gizmos 通道绘制
 * （注册在 {@code LevelRenderEvents.BEFORE_GIZMOS}），避免 1.21.4 时代
 * "mixin + WorldRenderer 双渲染源"的重复绘制。
 */
@Mixin(EntityRenderer.class)
public abstract class TeammateHealthMixin<S extends EntityRenderState> {

    @Inject(
            method = "submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;"
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/SubmitNodeCollector;"
                    + "Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At("HEAD"), cancellable = true
    )
    private void ctt_onNameSubmit(EntityRenderState state, PoseStack poseStack,
                                  SubmitNodeCollector collector, CameraRenderState cameraState,
                                  CallbackInfo ci) {
        if (HudRenderGate.shouldSuppressModHud(CttHealthDisplay.statsData)) return;

        Component nameTag = state.nameTag;
        if (nameTag == null) return;
        String label = nameTag.getString().trim();
        if (label.isEmpty()) return;

        // 三档 mob 头顶血量：
        //   OFF       - 不碰 vanilla 标签；
        //   ALL       - 找到匹配的 MobHealthData 就 cancel 原 vanilla 标签（CTT 会自己画）；
        //   NEAREST   - 只对 targetted（bossbar 锁定的那只）cancel；其他同名 mob 保留 vanilla 名字。
        if (ModConfig.INSTANCE.isMobHeadHPEnabled()) {
            MobHealthData matched = findMobWithHealth(label);
            if (matched != null) {
                boolean shouldCancel = !ModConfig.INSTANCE.isMobHeadHPNearestOnly() || matched.targetted;
                if (shouldCancel) {
                    ci.cancel();
                    return;
                }
            }
        }

        // 队友头顶血量开启时，隐藏 vanilla 的 "12❤️" 数字名牌（CTT 自己的四色条接管显示）。
        if (ModConfig.INSTANCE.showTeammateHeadHP
                && label.matches("\\d+\\s*[❤♥\u2764\u2665].*")) {
            ci.cancel();
        }
    }

    private static MobHealthData findMobWithHealth(String displayName) {
        Map<UUID, MobHealthData> mobMap = HealthData.getMobHealthMap();
        if (mobMap.isEmpty()) return null;

        for (MobHealthData mob : mobMap.values()) {
            String mobName = mob.name();
            if (mobName.equalsIgnoreCase(displayName) || displayName.contains(mobName) || mobName.contains(displayName)) {
                return mob;
            }
        }
        return null;
    }
}
