package com.ctt.healthdisplay.client;

import com.ctt.healthdisplay.config.ModConfig;
import com.ctt.healthdisplay.health.StatsData;

/**
 * 癫狂（Berserk）/ "Curse of the Unknown" 诅咒期间是否抑制本模组 HUD 的统一判定。
 *
 * <p>调用链：各渲染入口 → {@link #shouldSuppressModHud(StatsData)}
 * → {@link ModConfig#hideHudOnBerserk} +（{@link BerserkDetector#isActive()} 或
 * {@link CurseOfUnknownDetector#isActive()}）。两种状态玩家都"看不到任何 UI"，
 * 本模组的血条 / 属性 / 面板一并隐藏，行为保持一致。
 */
public final class HudRenderGate {

    private HudRenderGate() {}

    /**
     * @param statsData 当前会话属性数据（通常为 {@link com.ctt.healthdisplay.CttHealthDisplay#statsData}）
     * @return {@code true} 时应跳过本模组全部 HUD 绘制（2D + 3D 头顶条）
     */
    public static boolean shouldSuppressModHud(StatsData statsData) {
        if (!ModConfig.INSTANCE.hideHudOnBerserk) return false;
        return BerserkDetector.isActive() || CurseOfUnknownDetector.isActive();
    }
}
