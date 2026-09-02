package com.mooclient.util;

import com.mooclient.module.modules.InventoryViewModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;

/**
 * Dedicated HUD renderer for the Inventory View module.
 */
public class InventoryViewRenderer {

    public static void render(DrawContext context, MinecraftClient client, int scaledWidth,
            int scaledHeight, float hudScale, boolean customScale, boolean isMenu) {
        if (client == null || (client.player == null && !isMenu)) {
            return;
        }

        InventoryViewModule.InventoryStyle style = InventoryViewModule.getStyle();
        boolean showBg = InventoryViewModule.isShowBackground();
        boolean showEmpty = InventoryViewModule.isShowEmptySlots();
        int slotSize = InventoryViewModule.getSlotSize();
        int itemOffset = (slotSize - 16) / 2;

        int boxW = InventoryViewModule.calculateBoxWidth(hudScale);
        int boxH = InventoryViewModule.calculateBoxHeight(hudScale);
        InventoryViewModule.width = boxW;
        InventoryViewModule.height = boxH;

        int startX = InventoryViewModule.position.calculateX(boxW, scaledWidth);
        int startY = InventoryViewModule.position.calculateY(boxH, scaledHeight);

        if (customScale) {
            context.getMatrices().push();
            context.getMatrices().translate(startX, startY, 0);
            context.getMatrices().scale(hudScale, hudScale, 1.0f);
            context.getMatrices().translate(-startX, -startY, 0);
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int slotIndex = 9 + row * 9 + col;
                ItemStack stack = ItemStack.EMPTY;
                if (client.player != null && client.player.getInventory() != null
                        && slotIndex < client.player.getInventory().main.size()) {
                    stack = client.player.getInventory().main.get(slotIndex);
                } else if (isMenu) {
                    if (row == 0 && col == 0) stack = new ItemStack(net.minecraft.item.Items.GOLDEN_APPLE, 64);
                    else if (row == 0 && col == 1) stack = new ItemStack(net.minecraft.item.Items.ENDER_PEARL, 16);
                    else if (row == 1 && col == 0) stack = new ItemStack(net.minecraft.item.Items.TOTEM_OF_UNDYING);
                    else if (row == 2 && col == 8) stack = new ItemStack(net.minecraft.item.Items.EXPERIENCE_BOTTLE, 64);
                }

                int slotX = startX + col * slotSize;
                int slotY = startY + row * slotSize;

                if (showBg) {
                    if (style == InventoryViewModule.InventoryStyle.MOO_CLIENT) {
                        context.fill(slotX, slotY, slotX + slotSize, slotY + slotSize, 0x66000000);
                        drawSlotBorder(context, slotX, slotY, slotSize, slotSize, 0x55B0D8EA);
                    } else if (style == InventoryViewModule.InventoryStyle.VANILLA) {
                        context.fill(slotX, slotY, slotX + slotSize, slotY + slotSize, 0x88181824);
                        drawSlotBorder(context, slotX, slotY, slotSize, slotSize, 0x33FFFFFF);
                    } else {
                        context.fill(slotX, slotY, slotX + slotSize, slotY + slotSize, 0x55000000);
                    }
                } else if (showEmpty) {
                    context.fill(slotX + 4, slotY + 4, slotX + slotSize - 4, slotY + slotSize - 4, 0x15FFFFFF);
                }

                if (stack != null && !stack.isEmpty()) {
                    context.drawItem(stack, slotX + itemOffset, slotY + itemOffset);
                    context.drawStackOverlay(client.textRenderer, stack, slotX + itemOffset, slotY + itemOffset);
                }
            }
        }

        if (customScale) {
            context.getMatrices().pop();
        }
    }

    private static void drawSlotBorder(DrawContext context, int x, int y, int w, int h, int color) {
        context.fill(x, y, x + w, y + 1, color);
        context.fill(x, y + h - 1, x + w, y + h, color);
        context.fill(x, y + 1, x + 1, y + h - 1, color);
        context.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }
}
