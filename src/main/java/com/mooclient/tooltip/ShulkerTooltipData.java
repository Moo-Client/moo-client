package com.mooclient.tooltip;

import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipData;
import net.minecraft.util.collection.DefaultedList;

public class ShulkerTooltipData implements TooltipData {
    private final ItemStack shulkerStack;
    private final DefaultedList<ItemStack> items;

    public ShulkerTooltipData(ItemStack shulkerStack) {
        this.shulkerStack = shulkerStack;
        this.items = ShulkerTooltipUtil.getItems(shulkerStack);
    }

    public ItemStack getShulkerStack() {
        return shulkerStack;
    }

    public DefaultedList<ItemStack> getItems() {
        return items;
    }
}
