package com.mooclient.tooltip;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;

/**
 * Manages the locked state and slot inspection for Shulker Box tooltips.
 * When holding SHIFT, locks tooltip in place and unlocks mouse to inspect individual items.
 * Releasing SHIFT immediately cancels/unlocks cleanly.
 */
public class ShulkerLockManager {

    private static boolean locked = false;
    private static ItemStack lockedStack = null;
    private static int lockedX = 0;
    private static int lockedY = 0;

    private static ItemStack activeHoverStack = null;
    private static int activeHoverX = 0;
    private static int activeHoverY = 0;
    private static boolean hasActiveHover = false;

    private static int lastGridX = 0;
    private static int lastGridY = 0;
    private static int lastGridW = 168;
    private static int lastGridH = 60;

    private static ItemStack hoveredInnerItem = null;
    private static int hoveredSlotIndex = -1;
    private static boolean renderingInnerTooltip = false;
    private static boolean renderedThisFrame = false;

    public static void updateActiveHover(ItemStack stack, int mouseX, int mouseY) {
        if (stack != null && !stack.isEmpty()) {
            activeHoverStack = stack.copy();
            activeHoverX = mouseX;
            activeHoverY = mouseY;
            hasActiveHover = true;
        } else {
            clearActiveHover();
        }
    }

    public static void clearActiveHover() {
        hasActiveHover = false;
        activeHoverStack = null;
    }

    public static boolean hasActiveHover() {
        return hasActiveHover && activeHoverStack != null && !activeHoverStack.isEmpty();
    }

    public static void lock(ItemStack stack, int mouseX, int mouseY) {
        if (stack != null && !stack.isEmpty()) {
            locked = true;
            lockedStack = stack.copy();
            lockedX = mouseX;
            lockedY = mouseY;
        }
    }

    public static void lockCurrent() {
        if (hasActiveHover && activeHoverStack != null && !activeHoverStack.isEmpty()) {
            lock(activeHoverStack, activeHoverX, activeHoverY);
        }
    }

    public static void unlock() {
        locked = false;
        lockedStack = null;
        hoveredInnerItem = null;
        hoveredSlotIndex = -1;
        renderedThisFrame = false;
    }

    public static boolean isLocked() {
        return locked && lockedStack != null && !lockedStack.isEmpty();
    }

    public static ItemStack getLockedStack() {
        return lockedStack;
    }

    public static int getLockedX() {
        return lockedX;
    }

    public static int getLockedY() {
        return lockedY;
    }

    public static void updateGridPosition(int x, int y, int w, int h) {
        lastGridX = x;
        lastGridY = y;
        lastGridW = w;
        lastGridH = h;
    }

    public static boolean isMouseInsideTooltip(int mouseX, int mouseY) {
        int pad = 20;
        return mouseX >= lastGridX - pad && mouseX <= lastGridX + lastGridW + pad
                && mouseY >= lastGridY - pad && mouseY <= lastGridY + lastGridH + pad;
    }

    public static boolean shouldStayLocked() {
        if (!locked) {
            return false;
        }
        try {
            return Screen.hasShiftDown();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static void setHoveredInnerItem(ItemStack item, int slotIndex) {
        if (item != null && !item.isEmpty()) {
            hoveredInnerItem = item;
            hoveredSlotIndex = slotIndex;
        } else {
            hoveredInnerItem = null;
            hoveredSlotIndex = -1;
        }
    }

    public static ItemStack getHoveredInnerItem() {
        return hoveredInnerItem;
    }

    public static int getHoveredSlotIndex() {
        return hoveredSlotIndex;
    }

    public static boolean isRenderingInnerTooltip() {
        return renderingInnerTooltip;
    }

    public static void setRenderingInnerTooltip(boolean rendering) {
        renderingInnerTooltip = rendering;
    }

    public static boolean isRenderedThisFrame() {
        return renderedThisFrame;
    }

    public static void markRenderedThisFrame() {
        renderedThisFrame = true;
    }

    public static void resetFrameFlag() {
        renderedThisFrame = false;
    }

    public static int getGuiMouseX() {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.getWindow() == null) {
                return 0;
            }
            return (int) (client.mouse.getX() * (double) client.getWindow().getScaledWidth() / (double) client.getWindow().getWidth());
        } catch (Throwable ignored) {
            return 0;
        }
    }

    public static int getGuiMouseY() {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.getWindow() == null) {
                return 0;
            }
            return (int) (client.mouse.getY() * (double) client.getWindow().getScaledHeight() / (double) client.getWindow().getHeight());
        } catch (Throwable ignored) {
            return 0;
        }
    }

    public static void renderHoveredInnerTooltip(DrawContext context, TextRenderer textRenderer) {
        if (hoveredInnerItem != null && !hoveredInnerItem.isEmpty()) {
            int mX = getGuiMouseX();
            int mY = getGuiMouseY();
            renderingInnerTooltip = true;
            try {
                context.getMatrices().push();
                // Elevate Z translation by +500 so the nested tooltip renders on top of all shulker item sprites
                context.getMatrices().translate(0.0f, 0.0f, 500.0f);
                context.drawItemTooltip(textRenderer, hoveredInnerItem, mX, mY);
                context.getMatrices().pop();
            } finally {
                renderingInnerTooltip = false;
            }
        }
    }
}
