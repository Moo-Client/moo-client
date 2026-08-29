package com.mooclient.mixin;

import com.mooclient.module.modules.EmotesModule;
import com.mooclient.module.modules.NametagsModule;
import com.mooclient.module.modules.PlayerEmoteState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityRenderer.class)
public class PlayerEntityRendererMixin {

    @ModifyVariable(
        method = "renderLabelIfPresent(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;Lnet/minecraft/text/Text;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
        at = @At("HEAD"),
        argsOnly = true
    )
    private Text mooClient$modifyNametagText(Text text, PlayerEntityRenderState state) {
        if (NametagsModule.isNametagsEnabled() && state != null && text != null) {
            // Do NOT format if text is the scoreboard sub-label (e.g. "20 ❤")
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
        method = "setupTransforms(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;FF)V",
        at = @At("TAIL")
    )
    private void mooClient$applyFlipRotation(PlayerEntityRenderState state, MatrixStack matrices, float bodyYaw, float baseTickDelta, CallbackInfo ci) {
        if (state == null || !EmotesModule.isEmotesEnabled()) return;

        PlayerEmoteState emoteState = EmotesModule.getPlayerState(state.id);
        if (emoteState != null && emoteState.isFlipping()) {
            MinecraftClient client = MinecraftClient.getInstance();
            float delta = client.getRenderTickCounter().getTickDelta(true);
            float flipAngle = emoteState.getFlipRotationDegrees(delta);
            float jumpHeight = emoteState.getFlipJumpHeight(delta);

            if (Math.abs(flipAngle) > 0.001F || jumpHeight > 0.001F) {
                // Czysto wizualny wyskok postaci w górę (MatrixStack translate bez zmiany hitboxa)
                matrices.translate(0.0F, jumpHeight, 0.0F);
                // Przesuwamy punkt obrotu na środek sylwetki gracza (~0.9m wysokości)
                matrices.translate(0.0F, 0.9F, 0.0F);
                // Płynna rotacja wokół osi X (pitch)
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(flipAngle));
                // Przesunięcie punktu obrotu z powrotem
                matrices.translate(0.0F, -0.9F, 0.0F);
            }
        }
    }
}
