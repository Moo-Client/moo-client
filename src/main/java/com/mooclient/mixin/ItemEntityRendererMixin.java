package com.mooclient.mixin;

import com.mooclient.module.modules.ItemScaleModule;
import com.mooclient.util.ItemEntityRenderStateAccess;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.ItemEntityRenderer;
import net.minecraft.client.render.entity.state.ItemEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntityRenderer.class)
public class ItemEntityRendererMixin {

    @Inject(method = "updateRenderState(Lnet/minecraft/entity/ItemEntity;Lnet/minecraft/client/render/entity/state/ItemEntityRenderState;F)V", at = @At("TAIL"))
    private void onUpdateRenderState(ItemEntity entity, ItemEntityRenderState state, float tickProgress, CallbackInfo ci) {
        float scale = ItemScaleModule.getScaleForItem(entity.getStack());
        ((ItemEntityRenderStateAccess) state).moo$setScale(scale);
    }

//? if >=1.21.11 {
    @Inject(
        method = "render(Lnet/minecraft/client/render/entity/state/ItemEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/ItemEntityRenderer;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;ILnet/minecraft/client/render/entity/state/ItemStackEntityRenderState;Lnet/minecraft/util/math/random/Random;Lnet/minecraft/util/math/Box;)V")
    )
    private void onBeforeRenderStack(ItemEntityRenderState state, MatrixStack matrices, net.minecraft.client.render.command.OrderedRenderCommandQueue queue, net.minecraft.client.render.state.CameraRenderState cameraRenderState, CallbackInfo ci) {
        float scale = ((ItemEntityRenderStateAccess) state).moo$getScale();
        if (scale != 1.0f) {
            matrices.scale(scale, scale, scale);
        }
    }
//?} else {
    /*@Inject(method = "render(Lnet/minecraft/client/render/entity/state/ItemEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/ItemEntityRenderer;renderStack(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/client/render/entity/state/ItemStackEntityRenderState;Lnet/minecraft/util/math/random/Random;)V"))
    private void onBeforeRenderStack(ItemEntityRenderState state, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        float scale = ((ItemEntityRenderStateAccess) state).moo$getScale();
        if (scale != 1.0f) {
            matrices.scale(scale, scale, scale);
        }
    }
*///?}
}
