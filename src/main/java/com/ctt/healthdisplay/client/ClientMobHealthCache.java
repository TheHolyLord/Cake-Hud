package com.ctt.healthdisplay.client;

import com.ctt.healthdisplay.health.MobHealthData;
import com.ctt.healthdisplay.network.MobHealthPayload;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * v8.3.0 · M7 · 客户端怪物血量缓存（服务端权威）。
 *
 * <h2>职责</h2>
 * <ul>
 *   <li>接 {@link MobHealthPayload}，把 {@link MobHealthPayload.Entry} 列表转成
 *       {@link MobHealthData} 的 {@code UUID → data} 只读 snapshot；</li>
 *   <li>提供 {@link #isFresh()}：最近一次推送不超过 {@value #FRESH_WINDOW_MS} ms
 *       视作新鲜，驱动 {@link com.ctt.healthdisplay.health.HealthData#getMobHealthMap()}
 *       的顶部分叉；</li>
 *   <li>提供 {@link #viewSnapshot()}：渲染侧直接读的只读 map；</li>
 *   <li>{@link #reset()} 给 DISCONNECT 钩子清缓存，避免切服后看到上一局残留条。</li>
 * </ul>
 *
 * <h2>v8.3.6 · v2 协议适配</h2>
 * <ul>
 *   <li>{@code nameText} 直接拿来用——translate 键留给客户端 lang 渲染；</li>
 *   <li>{@code defence / armorType / armorValue} 按 datapack
 *       {@code misc/bossbars/targetting_enemy.mcfunction} 的 8 档模板（armorType 0..7）
 *       重建带粗体 + 颜色的 suffix Component，UI 渲染端继续读 {@code data.suffixText}。</li>
 * </ul>
 *
 * <h2>服务端未装 mod 的兼容路径</h2>
 * <p>从未收到 payload → {@code lastPushMs = 0} → {@code isFresh() = false} →
 * {@link com.ctt.healthdisplay.health.HealthData#getMobHealthMap()} 继续返回本地
 * {@code mobHealthMap}；{@link com.ctt.healthdisplay.CttHealthDisplay#updateMobTracking}
 * 正常扫 bossbar 写入本地 map。行为与 v8.2 一致。
 *
 * <h2>服务端中途下线的失鲜回落</h2>
 * <p>5 s 没收到包 → {@code isFresh()} 翻 false → HUD 同一时刻切回本地路径，
 * 下一 tick {@link com.ctt.healthdisplay.CttHealthDisplay#updateMobTracking} 重新
 * 填本地 map。最坏情况是 1-2 tick (50-100 ms) 头顶条闪一下 —— 在用户视角是
 * "服务端刚下线这一下"的自然过渡，可接受。
 */
public final class ClientMobHealthCache {

    /** 新鲜窗口：5 秒没收到 payload → 视作服务端失联，回落本地路径。 */
    private static final long FRESH_WINDOW_MS = 5_000L;

    private static volatile long lastPushMs = 0L;
    private static volatile Map<UUID, MobHealthData> snapshot = Collections.emptyMap();
    /** 服务端 4 Hz 推送的 CrackedHearts 原始分数：正数碎心，负数粉心。 */
    private static volatile int crackedHeartsScore = 0;

    /**
     * v8.6.1 · 服务端 4 Hz 推送的玩家自身法力 / 体力（datapack objectives
     * Mana / MaxMana / Stamina / MaxStamina，都没上展示槽，纯客户端读不到）。
     * HUD 的 SwanJelly 法力条、近战手持时法力↔Stamina 互换、Joey 条换法力都吃这四个数。
     * maxMana / maxStamina = 0 视作"本通道无数据"，渲染端回落 bossbar 文本解析。
     */
    private static volatile int playerMana = 0;
    private static volatile int playerMaxMana = 0;
    private static volatile int playerStamina = 0;
    private static volatile int playerMaxStamina = 0;
    /** 服务端骑士资源模式：0=未提供/非骑士，1=体力，2=法力。 */
    private static volatile byte knightResourceMode = 0;

    private ClientMobHealthCache() {}

    public static void onPayload(MobHealthPayload payload) {
        if (payload == null) return;
        Map<UUID, MobHealthData> map = new HashMap<>(Math.max(16, payload.entries().size() * 2));
        for (MobHealthPayload.Entry e : payload.entries()) {
            Component nameText = e.nameText() != null ? e.nameText() : Component.empty();
            Component suffixText = buildArmorSuffix(e.defence(), e.armorType(), e.armorValue());
            // lastUpdateTick 留 0：fresh 路径的 map 由 CttHealthDisplay.updateMobTracking
            // 的 stale-cleanup 完全绕过，这里填 0 也不会被当成陈旧条项错删。
            MobHealthData d = new MobHealthData(nameText, suffixText, e.hp(), e.maxHp(), 0L);
            d.soulHearts = e.soulHearts();
            d.blackHearts = e.blackHearts();
            d.blueHearts = e.blueHearts();
            d.targetted = e.targetted();
            map.put(e.uuid(), d);
        }
        snapshot = Collections.unmodifiableMap(map);
        crackedHeartsScore = payload.crackedHearts();
        playerMana = Math.max(0, payload.mana());
        playerMaxMana = Math.max(0, payload.maxMana());
        playerStamina = Math.max(0, payload.stamina());
        playerMaxStamina = Math.max(0, payload.maxStamina());
        knightResourceMode = payload.knightResourceMode();
        lastPushMs = System.currentTimeMillis();
    }

    /** 最近一次推送里的玩家 CrackedHearts 原始分数。 */
    public static int crackedHeartsScore() { return crackedHeartsScore; }

    /** v8.6.1 · 最近一次推送里的玩家法力 / 体力（DISCONNECT 后归 0）。 */
    public static int playerMana() { return playerMana; }
    public static int playerMaxMana() { return playerMaxMana; }
    public static int playerStamina() { return playerStamina; }
    public static int playerMaxStamina() { return playerMaxStamina; }
    /** 最近服务端推送的骑士资源模式；仅服务端 payload 新字段会改变它。 */
    public static byte knightResourceMode() { return knightResourceMode; }

    /**
     * 按 datapack {@code targetting_enemy.mcfunction} 第 6-13 行的 8 档模板还原 suffix Component。
     *
     * <p>固定结构：{@code 🛡<defence>}（蓝色）+ 可选 {@code  <icon><value>}（按 armorType 选色）。
     * defence 与 armorType 同时为 0 → 返回空 Component，渲染层不会画前导护甲段（避免在
     * 没有数据的怪头顶画一个 "🛡 0" 误导）。
     *
     * <h3>颜色映射（与 datapack 一致）</h3>
     * <ul>
     *   <li>1 = 🔥 FireArmor → {@link ChatFormatting#GOLD}（gold）</li>
     *   <li>2 = ☃ IceArmor  → {@link ChatFormatting#AQUA}（aqua）</li>
     *   <li>3 = ☠ DarkArmor → {@link ChatFormatting#DARK_PURPLE}（dark_purple）</li>
     *   <li>4 = ☀ LightArmor → {@link ChatFormatting#WHITE}（white）</li>
     *   <li>5 = 🛡 TrueArmor → {@link ChatFormatting#WHITE}（white；与首段蓝 🛡 同图标但白色）</li>
     *   <li>6 = ⚓ WaterArmor → {@link ChatFormatting#AQUA}（aqua）</li>
     *   <li>7 = ⚡ ElectricArmor → {@link ChatFormatting#YELLOW}（yellow）</li>
     * </ul>
     */
    public static Component buildArmorSuffix(int defence, int armorType, int armorValue) {
        if (defence == 0 && armorType == 0) return Component.empty();
        MutableComponent t = Component.empty();
        // 首段：🛡 Defence （蓝），datapack 在 ArmorDisplay 任意档（含 0）都恒输出
        appendIconValue(t, "\uD83D\uDEE1", defence, ChatFormatting.BLUE);
        switch (armorType) {
            case 1 -> appendIconValue(t, " \uD83D\uDD25", armorValue, ChatFormatting.GOLD);          // 🔥 FireArmor
            case 2 -> appendIconValue(t, " \u2603",       armorValue, ChatFormatting.AQUA);          // ☃ IceArmor
            case 3 -> appendIconValue(t, " \u2620",       armorValue, ChatFormatting.DARK_PURPLE);   // ☠ DarkArmor
            case 4 -> appendIconValue(t, " \u2600",       armorValue, ChatFormatting.WHITE);         // ☀ LightArmor
            case 5 -> appendIconValue(t, " \uD83D\uDEE1", armorValue, ChatFormatting.WHITE);         // 🛡(白) TrueArmor
            case 6 -> appendIconValue(t, " \u2693",       armorValue, ChatFormatting.AQUA);          // ⚓ WaterArmor
            case 7 -> appendIconValue(t, " \u26A1",       armorValue, ChatFormatting.YELLOW);        // ⚡ ElectricArmor
            default -> {}
        }
        return t;
    }

    private static void appendIconValue(MutableComponent root, String icon, int value, ChatFormatting color) {
        root.append(Component.literal(icon).withStyle(ChatFormatting.BOLD, color))
            .append(Component.literal(String.valueOf(value)).withStyle(ChatFormatting.BOLD, color));
    }

    public static boolean isFresh() {
        long last = lastPushMs;
        if (last == 0L) return false;
        return (System.currentTimeMillis() - last) < FRESH_WINDOW_MS;
    }

    /** 只读 snapshot — 渲染侧直接用，不要修改。 */
    public static Map<UUID, MobHealthData> viewSnapshot() {
        return snapshot;
    }

    /** DISCONNECT / reset 时清缓存，避免切服后看到上一局残留。 */
    public static void reset() {
        snapshot = Collections.emptyMap();
        lastPushMs = 0L;
        crackedHeartsScore = 0;
        playerMana = 0;
        playerMaxMana = 0;
        playerStamina = 0;
        playerMaxStamina = 0;
        knightResourceMode = 0;
    }
}
