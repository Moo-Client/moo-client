package com.mooclient.mixin;

import com.mooclient.interaction.InteractionInputBlocker;
import com.mooclient.module.modules.FreelookModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public class MouseMixin {

    @Shadow
    @Final
    private MinecraftClient client;

    @Shadow
    private double cursorDeltaX;

    @Shadow
    private double cursorDeltaY;

    @Inject(method = "updateMouse", at = @At("HEAD"), cancellable = true)
    private void mooClient$onUpdateMouse(double timeDelta, CallbackInfo ci) {
        if (this.client.currentScreen == null && FreelookModule.isActive()) {
            double dx = this.cursorDeltaX;
            double dy = this.cursorDeltaY;
            this.cursorDeltaX = 0.0;
            this.cursorDeltaY = 0.0;

            if (dx != 0.0 || dy != 0.0) {
                double sensitivity = this.client.options.getMouseSensitivity().getValue() * 0.6D + 0.2D;
                double multiplier = sensitivity * sensitivity * sensitivity * 8.0D;
                int invertY = this.client.options.getInvertYMouse().getValue() ? -1 : 1;

                FreelookModule.onMouseLook(dx * multiplier, dy * (double) invertY * multiplier);
            }

            ci.cancel();
        } else if (this.client.currentScreen == null && com.mooclient.module.modules.ZoomModule.isZooming()) {
            double divisor = com.mooclient.module.modules.ZoomModule.getZoomDivisor();
            if (divisor > 1.0) {
                this.cursorDeltaX /= divisor;
                this.cursorDeltaY /= divisor;
            }
        }
    }

    @Inject(method = "onMouseButton", at = @At("HEAD"))
    private void mooClient$onMouseButton(long window, int button, int action, int mods, CallbackInfo ci) {
        if (action == GLFW.GLFW_PRESS && this.client.currentScreen == null && this.client.player != null) {
            InteractionInputBlocker.onPlayerAction();

            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                com.mooclient.module.modules.CpsModule.registerLeftClick();
            } else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                com.mooclient.module.modules.CpsModule.registerRightClick();
            }
        }
    }
}
