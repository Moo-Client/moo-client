package com.mooclient.tooltip;

import com.mooclient.module.modules.ShulkerTooltipModule;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.TooltipComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.util.collection.DefaultedList;

public class ShulkerBoxTooltipComponent implements TooltipComponent {
    public static final int COLUMNS = 9;
    public static final int ROWS = 3;
    public static final int SLOT_SIZE = 18;
    public static final int PADDING = 3;

    private final ShulkerTooltipData data;

    public ShulkerBoxTooltipComponent(ShulkerTooltipData data) {
        this.data = data;
    }

    @Override
    public int getWidth(TextRenderer textRenderer) {
        return COLUMNS * SLOT_SIZE + PADDING * 2;
    }

    @Override
    public int getHeight(TextRenderer textRenderer) {
        return ROWS * SLOT_SIZE + PADDING * 2;
    }

    @Override
    public void drawItems(TextRenderer textRenderer, int x, int y, int width, int height, DrawContext context) {
        renderGrid(textRenderer, x, y, context);
    }

    public void drawItems(TextRenderer textRenderer, int x, int y, DrawContext context) {
        renderGrid(textRenderer, x, y, context);
    }

    private void renderGrid(TextRenderer textRenderer, int x, int y, DrawContext context) {
        DefaultedList<ItemStack> items = data.getItems();
        int totalW = getWidth(textRenderer);
        int totalH = getHeight(textRenderer);

        ShulkerLockManager.updateGridPosition(x, y, totalW, totalH);

        int borderColor = ShulkerTooltipModule.isColorMatchedBorder()
                ? ShulkerTooltipUtil.getBorderColor(data.getShulkerStack())
                : 0xFFB0D8EA;

        // 1. Draw Background & Outer Border (Moo Client standard)
        context.fill(x, y, x + totalW, y + totalH, 0xEE12121A);
        drawBorder(context, x, y, totalW, totalH, borderColor);

        // 2. Determine hovered slot
        int startX = x + PADDING;
        int startY = y + PADDING;
        int curMouseX = ShulkerLockManager.getGuiMouseX();
        int curMouseY = ShulkerLockManager.getGuiMouseY();

        int hoveredSlot = -1;
        if (ShulkerTooltipModule.isInspectEnabled() && ShulkerLockManager.isLocked()) {
            int relX = curMouseX - startX;
            int relY = curMouseY - startY;
            if (relX >= 0 && relX < COLUMNS * SLOT_SIZE && relY >= 0 && relY < ROWS * SLOT_SIZE) {
                int col = relX / SLOT_SIZE;
                int row = relY / SLOT_SIZE;
                hoveredSlot = row * COLUMNS + col;
            }
        }

        // 3. Draw Slots & Items
        for (int i = 0; i < 27; i++) {
            int col = i % COLUMNS;
            int row = i / COLUMNS;
            int slotX = startX + col * SLOT_SIZE;
            int slotY = startY + row * SLOT_SIZE;
            boolean isHovered = (i == hoveredSlot);

            // Slot Background
            if (isHovered) {
                context.fill(slotX, slotY, slotX + 17, slotY + 17, 0x5538BDF8);
                drawBorder(context, slotX, slotY, 17, 17, 0xFF38BDF8);
            } else if (ShulkerTooltipModule.isShowEmptySlots() || (i < items.size() && !items.get(i).isEmpty())) {
                context.fill(slotX, slotY, slotX + 17, slotY + 17, 0x33000000);
                drawBorder(context, slotX, slotY, 17, 17, 0x1FFFFFFF);
            }

            if (i < items.size()) {
                ItemStack stack = items.get(i);
                if (!stack.isEmpty()) {
                    context.drawItem(stack, slotX + 1, slotY + 1);
                    context.drawStackOverlay(textRenderer, stack, slotX + 1, slotY + 1);
                    if (isHovered && ShulkerTooltipModule.isInspectEnabled()) {
                        ShulkerLockManager.setHoveredInnerItem(stack, i);
                    }
                }
            }
        }
    }

    private void drawBorder(DrawContext context, int x, int y, int w, int h, int color) {
        context.fill(x, y, x + w, y + 1, color);
        context.fill(x, y + h - 1, x + w, y + h, color);
        context.fill(x, y + 1, x + 1, y + h - 1, color);
        context.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }
}
