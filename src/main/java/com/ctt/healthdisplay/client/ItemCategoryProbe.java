package com.ctt.healthdisplay.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * v8.6.1 · CTT 物品分类探测（用户拍板的新机制的数据源）。
 *
 * <p>CakeTeamPack 把物品分类写进 {@code components → "minecraft:custom_data"} 的
 * 键名里，按"稀有度 + 分类"命名：
 * <ul>
 *   <li>耗体力武器：{@code commonMelee / beyondMelee} 与 {@code commonDagger / godlikeDagger}
 *       等分类键；龙之重剑还额外有 {@code dragonspire} 特判键；</li>
 *   <li>盾牌：裸分类键 {@code shield}（_randomitem 盾牌池通用写法）+ 各盾牌专属键
 *       （{@code nanoShield / crackedShield / hylianShield / commonShield / ...}）
 *       —— 判定规则：<b>键名等于 "shield"（忽略大小写）或以 "Shield" 结尾</b>。
 *       {@code shieldAbility} 这类"以 Ability 结尾"的功能键不会误判。</li>
 * </ul>
 *
 * <p>物品本体是数据包换皮的 {@code carrot_on_a_stick / warped_fungus_on_a_stick} 等，
 * 盾牌冷却进度用 {@code Damage / 最大耐久} 比值表达（用户拍板："检测盾牌的耐久即可"）。
 */
public final class ItemCategoryProbe {

    private ItemCategoryProbe() {}

    /** 主手物品是否属于近战分类（custom_data 键名以 Melee 结尾）。 */
    public static boolean holdingMelee(Minecraft mc) {
        if (mc.player == null) return false;
        return hasCategory(mc.player.getItemInHand(InteractionHand.MAIN_HAND), Category.MELEE);
    }

    /**
     * 当前主手是否是 Magic 类物品。CakeTeamPack 的六档魔法物品会在
     * custom_data 中写入 commonMagic / uncommonMagic / rareMagic / mythicalMagic /
     * legendaryMagic / godlikeMagic；恶魔魔法和天使魔法也分别带 godlikeMagic。
     */
    public static boolean holdingMagic(Minecraft mc) {
        if (mc.player == null) return false;
        return hasCategory(mc.player.getItemInHand(InteractionHand.MAIN_HAND), Category.MAGIC);
    }

    /**
     * 当前主手是否是会消耗体力的武器：Melee、Dagger，或龙之重剑 Dragonspire。
     * Dragonspire 单独判定是为了兼容某些只写入 {@code dragonspire} 而没有分类键的版本。
     */
    public static boolean holdingStaminaWeapon(Minecraft mc) {
        if (mc.player == null) return false;
        return isStaminaWeapon(mc.player.getItemInHand(InteractionHand.MAIN_HAND));
    }

    public static boolean isStaminaWeapon(ItemStack stack) {
        return hasCategory(stack, Category.MELEE)
                || hasCategory(stack, Category.DAGGER)
                || hasCategory(stack, Category.DRAGONSPIRE);
    }

    /**
     * 找手上（主手优先，其次副手）的盾牌分类物品；没有返回 null。
     * 主手就是盾牌时也用主手那份的耐久算冷却。
     */
    public static ItemStack heldShield(Minecraft mc) {
        if (mc.player == null) return null;
        ItemStack main = mc.player.getItemInHand(InteractionHand.MAIN_HAND);
        if (hasCategory(main, Category.SHIELD)) return main;
        ItemStack off = mc.player.getItemInHand(InteractionHand.OFF_HAND);
        if (hasCategory(off, Category.SHIELD)) return off;
        return null;
    }

    /**
     * 盾牌冷却进度 0..1：耐久剩余比 = (maxDamage - damage) / maxDamage。
     * 无耐久概念（unbreakable / maxDamage=0）视作 1（冷却完毕、条画满）。
     */
    public static float shieldProgress(ItemStack shield) {
        if (shield == null || shield.isEmpty()) return 1f;
        int max = shield.getMaxDamage();
        if (max <= 0) return 1f;
        int dmg = Math.max(0, Math.min(max, shield.getDamageValue()));
        float p = (float) (max - dmg) / max;
        return Math.max(0f, Math.min(1f, p));
    }

    private enum Category { MELEE, DAGGER, DRAGONSPIRE, SHIELD, MAGIC }

    /** 读 custom_data 组件键名做分类判定；空物品 / 无组件直接 false。 */
    public static boolean hasCategory(ItemStack stack, Category category) {
        if (stack == null || stack.isEmpty()) return false;
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        if (customData == null || customData.isEmpty()) return false;
        CompoundTag tag = customData.copyTag();
        for (String key : tag.keySet()) {
            if (key == null || key.isEmpty()) continue;
            if (category == Category.MELEE) {
                if (key.endsWith("Melee") || key.equalsIgnoreCase("melee")) return true;
            } else if (category == Category.DAGGER) {
                if (key.endsWith("Dagger") || key.equalsIgnoreCase("dagger")) return true;
            } else if (category == Category.DRAGONSPIRE) {
                if (key.equalsIgnoreCase("dragonspire")) return true;
            } else if (category == Category.MAGIC) {
                if (key.toLowerCase(java.util.Locale.ROOT).endsWith("magic")) return true;
            } else {
                if (key.equalsIgnoreCase("shield") || key.endsWith("Shield")) return true;
            }
        }
        return false;
    }

    /** 便捷入口：当前玩家手上是否有盾牌分类物品。 */
    public static boolean holdingShield(Player player) {
        if (player == null) return false;
        return hasCategory(player.getItemInHand(InteractionHand.MAIN_HAND), Category.SHIELD)
                || hasCategory(player.getItemInHand(InteractionHand.OFF_HAND), Category.SHIELD);
    }
}
