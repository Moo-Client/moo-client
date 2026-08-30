package com.mooclient.tooltip;

import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DyeColor;
import net.minecraft.util.collection.DefaultedList;

public class ShulkerTooltipUtil {

    public static boolean isShulkerBox(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        if (stack.getItem() instanceof BlockItem blockItem) {
            return blockItem.getBlock() instanceof ShulkerBoxBlock;
        }
        return false;
    }

    public static DefaultedList<ItemStack> getItems(ItemStack stack) {
        DefaultedList<ItemStack> items = DefaultedList.ofSize(27, ItemStack.EMPTY);
        if (stack == null || stack.isEmpty()) {
            return items;
        }

        ContainerComponent container = stack.get(DataComponentTypes.CONTAINER);
        if (container != null) {
            container.copyTo(items);
        }
        return items;
    }

    public static boolean hasItems(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        ContainerComponent container = stack.get(DataComponentTypes.CONTAINER);
        if (container != null) {
            return container.stream().findAny().isPresent();
        }
        return false;
    }

    public static int getBorderColor(ItemStack stack) {
        if (stack != null && stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof ShulkerBoxBlock shulkerBox) {
            DyeColor color = shulkerBox.getColor();
            if (color != null) {
                return 0xFF000000 | color.getEntityColor();
            }
        }
        // Default classic Shulker Purple
        return 0xFF975A9E;
    }
}
