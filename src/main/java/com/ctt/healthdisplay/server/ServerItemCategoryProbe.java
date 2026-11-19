package com.ctt.healthdisplay.server;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.Locale;

/**
 * 服务端物品分类探测。
 *
 * <p>骑士的资源条切换必须由服务端决定，不能依赖客户端自己猜测职业或物品。
 * CakeTeamPack 将物品分类写在 minecraft:custom_data 的键名中，因此这里只读取
 * 主手物品的分类键，不把客户端 HUD 类代码带进 dedicated server。</p>
 */
public final class ServerItemCategoryProbe {

    private ServerItemCategoryProbe() {}

    /** 主手是否为 Magic 分类物品。 */
    public static boolean holdingMagic(Player player) {
        return player != null && hasMagicCategory(player.getItemInHand(InteractionHand.MAIN_HAND));
    }

    private static boolean hasMagicCategory(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        if (customData == null || customData.isEmpty()) return false;

        CompoundTag tag = customData.copyTag();
        for (String key : tag.keySet()) {
            if (key != null && key.toLowerCase(Locale.ROOT).endsWith("magic")) {
                return true;
            }
        }
        return false;
    }
}
