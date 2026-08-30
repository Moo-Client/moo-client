package com.mooclient.mixin;

import com.mooclient.emote.EmoteEngine;
import com.mooclient.emote.EmotePlayerState;
import com.mooclient.gui.InvitationUIManager;
import com.mooclient.module.modules.NametagsModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin renderera gracza aplikujący czysto wizualne transformacje sceny (MatrixStack)
 * dla salt, uniesień, medytacji i wzajemnego pozycjonowania interakcji multiplayer.
 * WAŻNE: Hitbox i pozycja entity gracza w świecie pozostają w 100% nienaruszone.
 */
@Mixin(PlayerEntityRenderer.class)
public class PlayerEntityRendererMixin {

    @ModifyVariable(
        method = "renderLabelIfPresent(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;Lnet/minecraft/text/Text;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
        at = @At("HEAD"),
        argsOnly = true
    )
    private Text mooClient$modifyNametagText(Text text, PlayerEntityRenderState state) {
        if (NametagsModule.isNametagsEnabled() && state != null && text != null) {
            if (state.playerName != null && text == state.playerName) {
                return text;
            }
            if (state.name != null && !text.getString().toLowerCase().contains(state.name.toLowerCase())) {
                return text;
            }
            return NametagsModule.formatNametag(text, state.id, state.name);
        }
        return text;
    }

    @Inject(
        method = "renderLabelIfPresent(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;Lnet/minecraft/text/Text;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
        at = @At("TAIL")
    )
    private void mooClient$renderInWorldInvitationBillboard(PlayerEntityRenderState state, Text text, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        if (state == null) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return;

        Entity entity = client.world.getEntityById(state.id);
        if (entity instanceof PlayerEntity player) {
            InvitationUIManager.getInstance().renderInWorldBillboard(player, matrices, vertexConsumers, light);
        }
    }

    @Inject(
        method = "setupTransforms(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;FF)V",
        at = @At("TAIL")
    )
    private void mooClient$applyEmoteVisualTransforms(PlayerEntityRenderState state, MatrixStack matrices, float bodyYaw, float baseTickDelta, CallbackInfo ci) {
        if (state == null) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return;

        Entity entity = client.world.getEntityById(state.id);
        if (!(entity instanceof PlayerEntity player)) return;

        EmotePlayerState emoteState = EmoteEngine.getInstance().getPlayerStateIfExists(player.getUuid());
        if (emoteState == null || !emoteState.isRendering()) return;

        float tickDelta = client.getRenderTickCounter().getTickDelta(true);

        // 1. Czysto wizualne przesunięcie sceny multiplayerowej oraz kości root z pliku animacji
        float sceneX = emoteState.getSceneOffsetX() + emoteState.getVisualXOffset(tickDelta);
        float sceneY = emoteState.getSceneOffsetY() + emoteState.getVisualYOffset(tickDelta);
        float sceneZ = emoteState.getSceneOffsetZ() + emoteState.getVisualZOffset(tickDelta);

        if (sceneX != 0.0f || sceneY != 0.0f || sceneZ != 0.0f) {
            matrices.translate(sceneX, sceneY, sceneZ);
        }

        // 2. Czysto wizualne rotacje całego ciała (pitch dla salta, yaw dla sceny, roll)
        float pitch = emoteState.getVisualPitch(tickDelta);
        float yaw = emoteState.getVisualYaw(tickDelta);
        float roll = emoteState.getVisualRoll(tickDelta);

        if (Math.abs(pitch) > 0.001f || Math.abs(yaw) > 0.001f || Math.abs(roll) > 0.001f) {
            // Przesunięcie punktu obrotu na środek sylwetki gracza (~0.9m wysokości)
            matrices.translate(0.0f, 0.9f, 0.0f);

            if (Math.abs(yaw) > 0.001f) {
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yaw));
            }
            if (Math.abs(pitch) > 0.001f) {
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitch));
            }
            if (Math.abs(roll) > 0.001f) {
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(roll));
            }

            // Powrót z punktu obrotu
            matrices.translate(0.0f, -0.9f, 0.0f);
        }
    }
}
