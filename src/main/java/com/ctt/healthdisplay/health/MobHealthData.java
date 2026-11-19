package com.ctt.healthdisplay.health;

import net.minecraft.network.chat.Component;

/**
 * 头顶血条数据载体。v8.3.6 起 {@code name} 字段从 {@link String} 升级为 {@link Component}
 * （字段名 {@code nameText}），让服务端权威路径 / 客户端 bossbar 兜底路径都能保留
 * translate 键 + Style，客户端按本地 lang 自动渲染中文/英文。
 *
 * <p>派生 getter {@link #name()} 返回 {@code nameText.getString()}，向后兼容
 * {@link com.ctt.healthdisplay.mixin.TeammateHealthMixin} 的字符串匹配
 * （{@code findMobWithHealth} 用渲染后的串去匹配 vanilla 名牌）。
 *
 * <p>{@code nameColor} 字段保留为兼容字段，渲染端不再读取——颜色已写入
 * {@code nameText} 的 Style 中。
 */
public class MobHealthData {

    public Component nameText;
    public Component suffixText;
    public int hp;
    public int maxHP;
    /**
     * v8.5.3 · 从锁定 bossbar 颜色+进度推导的魂心数（CakeTeamPack HeartValue=2）。
     * 0 = 无魂心数据。v8.5.5 起血条恒为红心色、不再分层，此值改为与护甲同排的
     * 💛 数量段显示（{@link com.ctt.healthdisplay.client.MobArmorCollector#currentWithHearts}）。
     */
    public int soulHearts;
    /**
     * v8.5.4 · 蓝条（HeartValue=1，魂心必为 0）时 AllHearts 高出红心推导值的余额。
     * 数据包对怪物没有黑/蓝心专属通道、两者无法互辨，统一按黑心计。
     * v8.5.5 起同样只用于头顶 🖤 数量段显示。
     */
    public int blackHearts;
    public int blueHearts;
    public long lastUpdateTick;
    public boolean targetted;
    public int nameColor = 0xFFFFFFFF;
    /**
     * v8.6.0 · 纯客户端兜底条目：数值列只有百分比（数据包把 {@code HealthPercent}
     * 放上 below_name 展示槽，服务端会广播全部 holder 的分数，客户端 scoreboard
     * 里每只怪都有自己的一条），没有 AllHearts/MaxHP 绝对值。
     * {@code true} 时渲染端把血量数字画成 {@code "73%"} 而不是 {@code "73/100"}，
     * 且该条目不参与"受伤掉血检测"（每只怪的数据天然按 UUID 独立）。
     */
    public boolean percentOnly;

    public MobHealthData(Component nameText, Component suffixText, int hp, int maxHP, long tick) {
        this.nameText = nameText != null ? nameText : Component.empty();
        this.suffixText = suffixText != null ? suffixText : Component.empty();
        this.hp = hp;
        this.maxHP = maxHP;
        this.lastUpdateTick = tick;
        this.targetted = false;
    }

    /** 派生 getter：渲染后的名字串，用于跟 vanilla 名牌做字符串匹配。 */
    public String name() {
        return nameText != null ? nameText.getString() : "";
    }

    public int getPercent() {
        if (maxHP <= 0) return 100;
        return Math.max(0, Math.min(100, Math.round((float) hp * 100 / maxHP)));
    }
}
