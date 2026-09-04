package com.mooclient.mixin;

import com.mooclient.emote.EmoteEngine;
import com.mooclient.emote.EmotePlayerState;
import com.mooclient.emote.animation.BoneTransform;
import com.mooclient.gui.InvitationUIManager;
import com.mooclient.interaction.SceneTransform;
import com.mooclient.module.modules.NametagsModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
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
    private void mooClient$renderInWorldBillboard(PlayerEntityRenderState state, Text text, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
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

        EmotePlayerState emoteState = EmoteEngine.getInstance().getPlayerStateIfExists(player);
        if (emoteState == null || !emoteState.isRendering()) return;

        float tickDelta = client.getRenderTickCounter().getTickDelta(true);

        // 1. Uniesienie w górę (skok/wysokość salta aplikowana w pionie w świecie gry)
        float visualY = emoteState.getVisualYOffset(tickDelta);
        if (visualY != 0.0f) {
            matrices.translate(0.0f, visualY, 0.0f);
        }

        // 2. Przesunięcie poziome kości root (blokowane dla backflip i frontflip, by wykonywały się ściśle w miejscu)
        String emoteId = (emoteState.getActiveEmote() != null) ? emoteState.getActiveEmote().getId() : "";
        boolean isInPlaceFlip = "backflip".equalsIgnoreCase(emoteId) || "frontflip".equalsIgnoreCase(emoteId);

        if (!isInPlaceFlip) {
            float visualX = emoteState.getVisualXOffset(tickDelta);
            float visualZ = emoteState.getVisualZOffset(tickDelta);
            if (visualX != 0.0f || visualZ != 0.0f) {
                matrices.translate(visualX, 0.0f, visualZ);
            }
        }

        // 3. Czysto wizualne rotacje całego ciała wokół środka sylwetki (~0.9m wysokości)
        float pitch = emoteState.getVisualPitch(tickDelta);
        float roll = emoteState.getVisualRoll(tickDelta);
        float yaw;

        SceneTransform sceneTransform = emoteState.getCustomSceneTransform();
        if (sceneTransform != null && sceneTransform.lockFacingTarget) {
            // Obliczamy dynamicznie obrót tak, aby w połączeniu z (180 - bodyYaw) ciało było
            // skierowane idealnie pod kątem targetYaw twarzą w twarz:
            // (180 - bodyYaw) + (bodyYaw - targetYaw) = 180 - targetYaw
            float targetOffset = MathHelper.wrapDegrees(bodyYaw - sceneTransform.targetYaw);
            BoneTransform root = emoteState.getBoneTransform("root");
            float rootYaw = (root != null) ? (float) Math.toDegrees(root.yaw) : 0.0f;
            yaw = targetOffset + rootYaw;
        } else {
            yaw = emoteState.getVisualYaw(tickDelta);
        }

        if (Math.abs(pitch) > 0.001f || Math.abs(yaw) > 0.001f || Math.abs(roll) > 0.001f) {
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

            matrices.translate(0.0f, -0.9f, 0.0f);
        }

        // 4. Czysto wizualne przesunięcie sceny (w układzie obróconym ku partnerowi)
        float sceneX = emoteState.getSceneOffsetX();
        float sceneY = emoteState.getSceneOffsetY();
        float sceneZ = emoteState.getSceneOffsetZ();
        if (sceneX != 0.0f || sceneY != 0.0f || sceneZ != 0.0f) {
            matrices.translate(sceneX, sceneY, sceneZ);
        }
    }
}
