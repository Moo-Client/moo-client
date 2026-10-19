package com.mooclient.mixin;

import com.mooclient.gui.MooClientScreen;
import com.mooclient.module.modules.FullbrightModule;
import com.mooclient.waypoint.WaypointRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.entity.LivingEntity;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Disables vanilla screen blur when MooClientScreen is open,
 * handles Fullbright Night Vision, Zoom FOV, and captures exact world projection matrix for Waypoints.
 */
@Mixin(GameRenderer.class)
public class GameRendererMixin {

    @Inject(method = "renderBlur", at = @At("HEAD"), cancellable = true)
    private void mooClient$cancelBlur(CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.currentScreen instanceof MooClientScreen) {
            ci.cancel();
        }
    }

    @Inject(method = "getNightVisionStrength", at = @At("HEAD"), cancellable = true)
    private static void mooClient$fullbrightNightVision(LivingEntity entity, float tickDelta, CallbackInfoReturnable<Float> cir) {
        if (FullbrightModule.isFullbrightActive()) {
            cir.setReturnValue(1.0f);
        }
    }

//? if >=1.21.11 {
    @ModifyArg(
        method = "renderWorld",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/render/RawProjectionMatrix;set(Lorg/joml/Matrix4f;)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;"
        ),
        index = 0
    )
    private Matrix4f mooClient$captureWorldProjection(Matrix4f projMatrix) {
        WaypointRenderer.worldProjectionMatrix.set(projMatrix);
        return projMatrix;
    }
//?} else {
    /*@ModifyArg(
        method = "renderWorld",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/RenderSystem;setProjectionMatrix(Lorg/joml/Matrix4f;Lcom/mojang/blaze3d/systems/ProjectionType;)V"
        ),
        index = 0
    )
    private Matrix4f mooClient$captureWorldProjection(Matrix4f projMatrix) {
        WaypointRenderer.worldProjectionMatrix.set(projMatrix);
        return projMatrix;
    }
*///?}

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void mooClient$applyZoomFov(Camera camera, float tickDelta, boolean changingFov, CallbackInfoReturnable<Float> cir) {
        float fov = cir.getReturnValueF();
        if (com.mooclient.module.modules.ZoomModule.isZooming()) {
            fov = com.mooclient.module.modules.ZoomModule.calculateZoomFov(fov, tickDelta);
            cir.setReturnValue(fov);
        }
    }
}
