package com.mooclient.mixin;

import com.mooclient.module.modules.ShulkerTooltipModule;
import com.mooclient.tooltip.ShulkerLockManager;
import com.mooclient.tooltip.ShulkerTooltipUtil;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin extends Screen {

    @Shadow
    @Nullable
    protected Slot focusedSlot;

    protected HandledScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "drawMouseoverTooltip", at = @At("HEAD"), cancellable = true)
    private void mooClient$drawShulkerMouseoverTooltip(DrawContext context, int x, int y, CallbackInfo ci) {
        if (!ShulkerTooltipModule.isShulkerEnabled()) {
            if (ShulkerLockManager.isLocked()) {
                ShulkerLockManager.unlock();
            }
            return;
        }

        // If Shift is not held down, immediately unlock
        if (!Screen.hasShiftDown() && ShulkerLockManager.isLocked()) {
            ShulkerLockManager.unlock();
        }

        // Track if currently hovering over a slot with a Shulker Box
        if (this.focusedSlot != null && this.focusedSlot.hasStack()) {
            ItemStack stack = this.focusedSlot.getStack();
            if (ShulkerTooltipUtil.isShulkerBox(stack) && ShulkerTooltipUtil.hasItems(stack)) {
                ShulkerLockManager.updateActiveHover(stack, x, y);
                if (ShulkerTooltipModule.isInspectEnabled() && Screen.hasShiftDown() && !ShulkerLockManager.isLocked()) {
                    ShulkerLockManager.lock(stack, x, y);
                }
            } else {
                ShulkerLockManager.clearActiveHover();
            }
        } else {
            ShulkerLockManager.clearActiveHover();
        }

        // If locked in inspection mode
        if (ShulkerLockManager.isLocked()) {
            if (!Screen.hasShiftDown()) {
                ShulkerLockManager.unlock();
                return;
            }

            ci.cancel();

            ItemStack lockedStack = ShulkerLockManager.getLockedStack();
            if (lockedStack != null && !lockedStack.isEmpty()) {
                context.drawItemTooltip(this.textRenderer, lockedStack, ShulkerLockManager.getLockedX(), ShulkerLockManager.getLockedY());
                ShulkerLockManager.renderHoveredInnerTooltip(context, this.textRenderer);
                ShulkerLockManager.markRenderedThisFrame();
            }
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void mooClient$renderLockedShulkerTail(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!ShulkerTooltipModule.isShulkerEnabled()) {
            return;
        }

        if (!Screen.hasShiftDown() && ShulkerLockManager.isLocked()) {
            ShulkerLockManager.unlock();
            return;
        }

        if (ShulkerLockManager.isLocked() && !ShulkerLockManager.isRenderedThisFrame()) {
            ItemStack lockedStack = ShulkerLockManager.getLockedStack();
            if (lockedStack != null && !lockedStack.isEmpty()) {
                context.drawItemTooltip(this.textRenderer, lockedStack, ShulkerLockManager.getLockedX(), ShulkerLockManager.getLockedY());
                ShulkerLockManager.renderHoveredInnerTooltip(context, this.textRenderer);
            }
        }

        ShulkerLockManager.resetFrameFlag();
    }

    @Inject(method = "keyPressed", at = @At("HEAD"))
    private void mooClient$onKeyPressed(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (!ShulkerTooltipModule.isShulkerEnabled() || !ShulkerTooltipModule.isInspectEnabled()) {
            return;
        }

        if (keyCode == GLFW.GLFW_KEY_LEFT_SHIFT || keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT) {
            if (ShulkerLockManager.hasActiveHover() && !ShulkerLockManager.isLocked()) {
                ShulkerLockManager.lockCurrent();
            }
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"))
    private void mooClient$onMouseClicked(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (ShulkerLockManager.isLocked()) {
            ShulkerLockManager.unlock();
        }
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void mooClient$onClose(CallbackInfo ci) {
        ShulkerLockManager.unlock();
    }
}
