package com.mooclient.mixin;

import com.mooclient.module.modules.EmotesModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || state.id != client.player.getId()) {
            return;
        }

        float tickDelta = client.getRenderTickCounter().getTickDelta(true);

        // --- 1. Hands Up Animation ---
        float handsUpProgress = EmotesModule.getInterpolatedProgress(tickDelta);
        if (handsUpProgress > 0.001F) {
            float targetPitch = -(float) Math.PI;
            float targetYaw = 0.0F;
            float targetRoll = 0.0F;

            this.rightArm.pitch = MathHelper.lerp(handsUpProgress, this.rightArm.pitch, targetPitch);
            this.rightArm.yaw = MathHelper.lerp(handsUpProgress, this.rightArm.yaw, targetYaw);
            this.rightArm.roll = MathHelper.lerp(handsUpProgress, this.rightArm.roll, targetRoll);

            this.leftArm.pitch = MathHelper.lerp(handsUpProgress, this.leftArm.pitch, targetPitch);
            this.leftArm.yaw = MathHelper.lerp(handsUpProgress, this.leftArm.yaw, targetYaw);
            this.leftArm.roll = MathHelper.lerp(handsUpProgress, this.leftArm.roll, targetRoll);

            this.rightSleeve.resetTransform();
            this.leftSleeve.resetTransform();
        }

        // --- 2. Flip (Salto) Tuck Body Pose ---
        float tuckFactor = EmotesModule.getFlipTuckFactor(tickDelta);
        if (tuckFactor > 0.001F) {
            // Pozycja zwarta akrobaty: kolana podciągnięte, ręce przy kolanach, głowa przygięta
            float targetLegPitch = -1.25F;
            float targetArmPitch = 1.05F;

            this.rightLeg.pitch = MathHelper.lerp(tuckFactor, this.rightLeg.pitch, targetLegPitch);
            this.rightLeg.yaw = MathHelper.lerp(tuckFactor, this.rightLeg.yaw, 0.0F);
            this.rightLeg.roll = MathHelper.lerp(tuckFactor, this.rightLeg.roll, 0.0F);

            this.leftLeg.pitch = MathHelper.lerp(tuckFactor, this.leftLeg.pitch, targetLegPitch);
            this.leftLeg.yaw = MathHelper.lerp(tuckFactor, this.leftLeg.yaw, 0.0F);
            this.leftLeg.roll = MathHelper.lerp(tuckFactor, this.leftLeg.roll, 0.0F);

            this.rightArm.pitch = MathHelper.lerp(tuckFactor, this.rightArm.pitch, targetArmPitch);
            this.rightArm.yaw = MathHelper.lerp(tuckFactor, this.rightArm.yaw, 0.0F);
            this.rightArm.roll = MathHelper.lerp(tuckFactor, this.rightArm.roll, 0.0F);

            this.leftArm.pitch = MathHelper.lerp(tuckFactor, this.leftArm.pitch, targetArmPitch);
            this.leftArm.yaw = MathHelper.lerp(tuckFactor, this.leftArm.yaw, 0.0F);
            this.leftArm.roll = MathHelper.lerp(tuckFactor, this.leftArm.roll, 0.0F);

            this.body.pitch = MathHelper.lerp(tuckFactor, this.body.pitch, 0.20F);
            this.head.pitch = MathHelper.lerp(tuckFactor, this.head.pitch, 0.35F);

            // Zresetowanie lokalnych transformacji wszystkich warstw potomnych (dzieci kości)
            this.rightSleeve.resetTransform();
            this.leftSleeve.resetTransform();
            this.leftPants.resetTransform();
            this.rightPants.resetTransform();
            this.jacket.resetTransform();
        }
    }
}

