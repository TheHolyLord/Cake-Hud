package com.ctt.healthdisplay.client;

import com.ctt.healthdisplay.mixin.BossBarHudAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.BossEvent;

import java.util.Map;
import java.util.UUID;

/**
 * "Curse of the Unknown"（未知诅咒，诅咒房间事件 D_Curse02_Unknown）客户端探测。
 *
 * <p>数据包出处：{@code cake_team_tower/function/floors/curse_rooms/d_curse02_unknown.mcfunction}。
 * 诅咒生效期间，数据包把 {@code player_1001..1010} 全部个人状态 bossbar 的名称替换成
 * 乱码文本（obfuscated），这些文本段的翻译键都带同一个前缀
 * {@code Floors_Curse_Rooms_D_Curse02_Unknown_}——键名不随语言包变化，是客户端最稳的
 * 识别锚点。
 *
 * <p>命中后行为与 {@link BerserkDetector}（癫狂）完全一致：通过 {@link HudRenderGate}
 * 隐藏本模组全部 UI（血条 / 属性 / 队友头顶条 / 伤害面板等）。
 * <p>同癫狂一样带 2 s 滞后释放，避免 bossbar 逐 tick 翻页期间闪烁。
 */
public final class CurseOfUnknownDetector {

    /** 诅咒 bossbar 文本段的翻译键前缀（与数据包 JSON 一一对应）。 */
    private static final String CURSE_BAR_KEY_PREFIX = "Floors_Curse_Rooms_D_Curse02_Unknown_";

    private static final int CLEAR_DELAY_TICKS = 40;

    private static volatile boolean stickyActive = false;
    private static int clearCooldownTicks = 0;

    private CurseOfUnknownDetector() {}

    /** 每客户端 tick 调用，更新滞后状态。 */
    public static void tick(Minecraft client) {
        if (probeCurseBossbar(client)) {
            stickyActive = true;
            clearCooldownTicks = 0;
        } else if (stickyActive) {
            if (++clearCooldownTicks >= CLEAR_DELAY_TICKS) {
                stickyActive = false;
            }
        }
    }

    /** 当前是否应视为处于"Curse of the Unknown"（含 sticky 窗口）。 */
    public static boolean isActive() {
        return stickyActive;
    }

    /** 断线 / 切服时清状态，避免上一局 sticky 残留。 */
    public static void reset() {
        stickyActive = false;
        clearCooldownTicks = 0;
    }

    private static boolean probeCurseBossbar(Minecraft client) {
        if (client.player == null || client.gui.hud == null) return false;
        try {
            BossBarHudAccessor accessor = (BossBarHudAccessor) client.gui.hud.getBossOverlay();
            Map<UUID, BossEvent> bars = accessor.getBossBars();
            if (bars == null || bars.isEmpty()) return false;
            for (BossEvent bar : bars.values()) {
                if (textMarksCurseOfUnknown(bar.getName())) return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    /**
     * 组件树里任一文本段的翻译键命中 {@value #CURSE_BAR_KEY_PREFIX} 前缀即为诅咒条。
     * 数据包 bossbar 名称是 ["", {translate:_14}, {translate:_15}, ...] 的兄弟段列表，
     * 所以除自身 contents 外还要递归看 siblings（翻译键在本地化后仍可经
     * {@link TranslatableContents#getKey()} 原样取到）。
     */
    static boolean textMarksCurseOfUnknown(Component text) {
        if (text == null) return false;
        if (text.getContents() instanceof TranslatableContents ttc
                && ttc.getKey().startsWith(CURSE_BAR_KEY_PREFIX)) {
            return true;
        }
        for (Component sibling : text.getSiblings()) {
            if (textMarksCurseOfUnknown(sibling)) return true;
        }
        return false;
    }
}
