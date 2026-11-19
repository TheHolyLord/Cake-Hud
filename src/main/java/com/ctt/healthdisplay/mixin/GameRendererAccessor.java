package com.ctt.healthdisplay.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.GameRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * v8.5.7 · 让 HUD 血条用和实体渲染<b>完全相同</b>的"投影 × 晃动 × 视图旋转"矩阵。
 *
 * <p>26.3 里视角晃动（bobHurt 受伤倾斜 + bobView 行走摆动）不在 {@code Camera} 上，
 * 而是 {@code GameRenderer.renderLevel()} 每帧把两个私有方法施加到一个 PoseStack，
 * 再乘进投影矩阵。HUD 若只用 {@code camera.getViewRotationProjectionMatrix()}（无 bob），
 * 怪物模型晃、血条不晃 → 血条相对头顶漂移（用户看到的"跟着屏幕晃"）。
 *
 * <p>这里把同一对方法暴露出来直接调用，血条矩阵 = vanilla 矩阵，一分不差。
 */
@Mixin(GameRenderer.class)
public interface GameRendererAccessor {

    /** 当前帧渲染状态（含 {@code levelRenderState.cameraRenderState}）。 */
    @Accessor("gameRenderState")
    GameRenderState ctt$getGameRenderState();

    /** vanilla 受伤视角倾斜（对相机空间的旋转，直接写进传入的 PoseStack）。 */
    @Invoker("bobHurt")
    void ctt$bobHurt(CameraRenderState state, PoseStack poseStack);

    /** vanilla 行走视角晃动（需要 {@code optionsRenderState.bobView} 为 true 才调用）。 */
    @Invoker("bobView")
    void ctt$bobView(CameraRenderState state, PoseStack poseStack);
}
