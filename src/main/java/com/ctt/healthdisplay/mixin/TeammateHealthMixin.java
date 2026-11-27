package com.ctt.healthdisplay.mixin;

import com.ctt.healthdisplay.CttHealthDisplay;
import com.ctt.healthdisplay.client.ClientMobHealthCache;
import com.ctt.healthdisplay.client.HudRenderGate;
import com.ctt.healthdisplay.config.ModConfig;
import com.ctt.healthdisplay.health.HealthData;
import com.ctt.healthdisplay.hud.MobBarScreenRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public abstract class TeammateHealthMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Entity;"
            + "Lnet/minecraft/client/renderer/entity/state/EntityRenderState;F)V", at = @At("TAIL"))
    private void ctt$adjustHealthLabel(Entity entity, EntityRenderState state, float partialTick, CallbackInfo ci) {
        var client = Minecraft.getInstance();
        if (client.player == null || client.gui.hud.isHidden()) return;
        var config = ModConfig.INSTANCE;
        if (config.suppressNpcHealth && ClientMobHealthCache.isNpc(entity)) {
            state.scoreText = null;
            return;
        }
        boolean effect = HudRenderGate.shouldSuppressModHud(CttHealthDisplay.statsData);
        if (entity instanceof Player player) {
            if (!effect && config.showTeammateHeadHP && entity != client.player && !player.isSpectator()
                    && state.distanceToCameraSq <= (double) config.playerHeadDistance * config.playerHeadDistance
                    && HealthData.getTeammateMap().containsKey(player.getName().getString())) {
                state.scoreText = null;
            }
            return;
        }
        if ((!effect || config.showEnemyHealthDuringEffects) && config.isMobHeadHPEnabled()
                && state.distanceToCameraSq <= (double) config.enemyHeadDistance * config.enemyHeadDistance) {
            if ((state.nameTag != null || state.scoreText != null)
                    && MobBarScreenRenderer.replacesNameplate(client, entity, config)) {
                state.nameTag = null;
                state.scoreText = null;
            }
        }
    }
}
