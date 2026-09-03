package com.mooclient.mixin;

import com.mooclient.emote.EmoteEngine;
import com.mooclient.emote.EmotePlayerState;
import com.mooclient.emote.animation.BoneTransform;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin do PlayerEntityModel aplikujący kąty kości ze zintegrowanego silnika EmoteEngine
 * w oparciu o interpolowane klatki animacji Blockbench.
 */
@Mixin(PlayerEntityModel.class)
public abstract class PlayerEntityModelMixin extends BipedEntityModel<PlayerEntityRenderState> {

    @Shadow @Final
    public ModelPart leftSleeve;

    @Shadow @Final
    public ModelPart rightSleeve;

    @Shadow @Final
    public ModelPart leftPants;

    @Shadow @Final
    public ModelPart rightPants;

    @Shadow @Final
    public ModelPart jacket;

    public PlayerEntityModelMixin(ModelPart root) {
        super(root);
    }

    @Inject(method = "setAngles(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;)V", at = @At("TAIL"))
    private void mooClient$onSetAngles(PlayerEntityRenderState state, CallbackInfo ci) {
        if (state == null) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return;

        Entity entity = client.world.getEntityById(state.id);
        if (!(entity instanceof PlayerEntity player)) return;

        EmotePlayerState emoteState = EmoteEngine.getInstance().getPlayerStateIfExists(player);
        if (emoteState == null || !emoteState.isRendering()) return;

        float tickDelta = client.getRenderTickCounter().getTickDelta(true);
        emoteState.updateRenderTransforms(tickDelta);

        // Aplikacja transformacji kości głowy (Head) - nadpisanie kątów animacją, by spojrzenie gracza nie kolidowało z rękami
        BoneTransform headT = emoteState.getBoneTransform("head");
        if (headT != null) {
            this.head.pitch = headT.pitch;
            this.head.yaw = headT.yaw;
            this.head.roll = headT.roll;
        }

        // Aplikacja transformacji tułowia (Body)
        BoneTransform bodyT = emoteState.getBoneTransform("body");
        if (bodyT != null) {
            this.body.pitch = bodyT.pitch;
            this.body.yaw = bodyT.yaw;
            this.body.roll = bodyT.roll;
        }

        // Aplikacja transformacji prawego ramienia (Right Arm)
        BoneTransform rArmT = emoteState.getBoneTransform("right_arm");
        if (rArmT != null) {
            this.rightArm.pitch = rArmT.pitch;
            this.rightArm.yaw = rArmT.yaw;
            this.rightArm.roll = rArmT.roll;
        }

        // Aplikacja transformacji lewego ramienia (Left Arm)
        BoneTransform lArmT = emoteState.getBoneTransform("left_arm");
        if (lArmT != null) {
            this.leftArm.pitch = lArmT.pitch;
            this.leftArm.yaw = lArmT.yaw;
            this.leftArm.roll = lArmT.roll;
        }

        // Aplikacja transformacji prawej nogi (Right Leg)
        BoneTransform rLegT = emoteState.getBoneTransform("right_leg");
        if (rLegT != null) {
            this.rightLeg.pitch = rLegT.pitch;
            this.rightLeg.yaw = rLegT.yaw;
            this.rightLeg.roll = rLegT.roll;
        }

        // Aplikacja transformacji lewej nogi (Left Leg)
        BoneTransform lLegT = emoteState.getBoneTransform("left_leg");
        if (lLegT != null) {
            this.leftLeg.pitch = lLegT.pitch;
            this.leftLeg.yaw = lLegT.yaw;
            this.leftLeg.roll = lLegT.roll;
        }

        // Synchronizacja warstw nakładek (sleeve, pants, jacket) z odpowiednimi kończynami
        this.rightSleeve.copyTransform(this.rightArm);
        this.leftSleeve.copyTransform(this.leftArm);
        this.rightPants.copyTransform(this.rightLeg);
        this.leftPants.copyTransform(this.leftLeg);
        this.jacket.copyTransform(this.body);
    }
}
