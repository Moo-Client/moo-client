package com.mooclient.mixin;

import com.mooclient.module.modules.EmotesModule;
import com.mooclient.module.modules.PlayerEmoteState;
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
        if (state == null || !EmotesModule.isEmotesEnabled()) {
            return;
        }

        PlayerEmoteState emoteState = EmotesModule.getPlayerState(state.id);
        if (emoteState == null) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        float tickDelta = client.getRenderTickCounter().getTickDelta(true);

        // --- 1. Hands Up Animation ---
        float handsUpProgress = emoteState.getInterpolatedHandsUpProgress(tickDelta);
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
        float tuckFactor = emoteState.getFlipTuckFactor(tickDelta);
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

        // --- 3. 12 Pixel-Art Emotes Animation Poses ---
        if (emoteState.currentEmote != EmotesModule.EmoteType.NONE) {
            float p = emoteState.getInterpolatedEmoteProgress(tickDelta);
            float blend = (float) Math.sin(p * Math.PI);

            switch (emoteState.currentEmote) {
                case WAVE -> {
                    float waveAngle = (float) (Math.sin(p * 22.0) * 0.45F);
                    this.rightArm.pitch = MathHelper.lerp(blend, this.rightArm.pitch, -2.85F);
                    this.rightArm.yaw = MathHelper.lerp(blend, this.rightArm.yaw, 0.0F);
                    this.rightArm.roll = MathHelper.lerp(blend, this.rightArm.roll, waveAngle);
                }
                case DANCE -> {
                    float danceSway = (float) (Math.sin(p * 18.0) * 0.35F);
                    float armSwing = (float) (Math.sin(p * 18.0) * 0.65F);
                    this.body.yaw = MathHelper.lerp(blend, this.body.yaw, danceSway);
                    this.rightArm.pitch = MathHelper.lerp(blend, this.rightArm.pitch, armSwing);
                    this.leftArm.pitch = MathHelper.lerp(blend, this.leftArm.pitch, -armSwing);
                    this.head.yaw = MathHelper.lerp(blend, this.head.yaw, -danceSway * 0.5F);
                }
                case POINT -> {
                    this.rightArm.pitch = MathHelper.lerp(blend, this.rightArm.pitch, -1.55F);
                    this.rightArm.yaw = MathHelper.lerp(blend, this.rightArm.yaw, -0.35F);
                    this.rightArm.roll = MathHelper.lerp(blend, this.rightArm.roll, 0.0F);
                }
                case BRAVO -> {
                    this.rightArm.pitch = MathHelper.lerp(blend, this.rightArm.pitch, -1.25F);
                    this.rightArm.yaw = MathHelper.lerp(blend, this.rightArm.yaw, -0.30F);
                    this.rightArm.roll = MathHelper.lerp(blend, this.rightArm.roll, -0.20F);
                }
                case CRAWL -> {
                    this.body.pitch = MathHelper.lerp(blend, this.body.pitch, 1.45F);
                    this.head.pitch = MathHelper.lerp(blend, this.head.pitch, -0.85F);
                    float crawlLeg = (float) (Math.sin(p * 15.0) * 0.6F);
                    this.rightLeg.pitch = MathHelper.lerp(blend, this.rightLeg.pitch, crawlLeg);
                    this.leftLeg.pitch = MathHelper.lerp(blend, this.leftLeg.pitch, -crawlLeg);
                    this.rightArm.pitch = MathHelper.lerp(blend, this.rightArm.pitch, -1.4F + crawlLeg * 0.5F);
                    this.leftArm.pitch = MathHelper.lerp(blend, this.leftArm.pitch, -1.4F - crawlLeg * 0.5F);
                }
                case VICTORY -> {
                    this.rightArm.pitch = MathHelper.lerp(blend, this.rightArm.pitch, -2.95F);
                    this.rightArm.yaw = MathHelper.lerp(blend, this.rightArm.yaw, 0.0F);
                    this.head.pitch = MathHelper.lerp(blend, this.head.pitch, -0.35F);
                }
                case THINK -> {
                    this.rightArm.pitch = MathHelper.lerp(blend, this.rightArm.pitch, -1.40F);
                    this.rightArm.yaw = MathHelper.lerp(blend, this.rightArm.yaw, -0.65F);
                    this.head.pitch = MathHelper.lerp(blend, this.head.pitch, 0.25F);
                    this.head.roll = MathHelper.lerp(blend, this.head.roll, 0.15F);
                }
                case CLAP -> {
                    float clapCycle = (float) Math.abs(Math.sin(p * 26.0));
                    this.rightArm.pitch = MathHelper.lerp(blend, this.rightArm.pitch, -1.30F);
                    this.rightArm.yaw = MathHelper.lerp(blend, this.rightArm.yaw, -0.40F - clapCycle * 0.25F);
                    this.leftArm.pitch = MathHelper.lerp(blend, this.leftArm.pitch, -1.30F);
                    this.leftArm.yaw = MathHelper.lerp(blend, this.leftArm.yaw, 0.40F + clapCycle * 0.25F);
                }
                case SALUTE -> {
                    this.rightArm.pitch = MathHelper.lerp(blend, this.rightArm.pitch, -1.50F);
                    this.rightArm.yaw = MathHelper.lerp(blend, this.rightArm.yaw, -0.70F);
                    this.rightArm.roll = MathHelper.lerp(blend, this.rightArm.roll, 0.50F);
                }
                case LAUGH -> {
                    float laughShake = (float) (Math.sin(p * 32.0) * 0.15F);
                    this.head.pitch = MathHelper.lerp(blend, this.head.pitch, -0.30F + laughShake);
                    this.body.pitch = MathHelper.lerp(blend, this.body.pitch, laughShake * 0.5F);
                }
                case SAD -> {
                    this.head.pitch = MathHelper.lerp(blend, this.head.pitch, 0.55F);
                    this.rightArm.pitch = MathHelper.lerp(blend, this.rightArm.pitch, 0.25F);
                    this.leftArm.pitch = MathHelper.lerp(blend, this.leftArm.pitch, 0.25F);
                }
                case ANGRY -> {
                    float angryShake = (float) (Math.sin(p * 35.0) * 0.12F);
                    this.head.yaw = MathHelper.lerp(blend, this.head.yaw, angryShake);
                    this.rightArm.pitch = MathHelper.lerp(blend, this.rightArm.pitch, -0.75F);
                    this.leftArm.pitch = MathHelper.lerp(blend, this.leftArm.pitch, -0.75F);
                }
                case MEDITATION -> {
                    float medBlend = emoteState.getMeditationBlend(tickDelta);
                    if (medBlend > 0.001F) {
                        float t = (emoteState.emoteDurationTicks + tickDelta) * 0.08F;
                        float breathe = (float) (Math.sin(t) * 0.05F);

                        this.rightLeg.pitch = MathHelper.lerp(medBlend, this.rightLeg.pitch, -1.40F);
                        this.rightLeg.yaw = MathHelper.lerp(medBlend, this.rightLeg.yaw, -0.65F);
                        this.rightLeg.roll = MathHelper.lerp(medBlend, this.rightLeg.roll, 0.25F);

                        this.leftLeg.pitch = MathHelper.lerp(medBlend, this.leftLeg.pitch, -1.40F);
                        this.leftLeg.yaw = MathHelper.lerp(medBlend, this.leftLeg.yaw, 0.65F);
                        this.leftLeg.roll = MathHelper.lerp(medBlend, this.leftLeg.roll, -0.25F);

                        this.rightArm.pitch = MathHelper.lerp(medBlend, this.rightArm.pitch, -0.75F);
                        this.rightArm.yaw = MathHelper.lerp(medBlend, this.rightArm.yaw, -0.40F);
                        this.rightArm.roll = MathHelper.lerp(medBlend, this.rightArm.roll, 0.45F);

                        this.leftArm.pitch = MathHelper.lerp(medBlend, this.leftArm.pitch, -0.75F);
                        this.leftArm.yaw = MathHelper.lerp(medBlend, this.leftArm.yaw, 0.40F);
                        this.leftArm.roll = MathHelper.lerp(medBlend, this.leftArm.roll, -0.45F);

                        this.body.pitch = MathHelper.lerp(medBlend, this.body.pitch, -0.05F);
                        this.head.pitch = MathHelper.lerp(medBlend, this.head.pitch, -0.15F + breathe);
                    }
                }
                case FRIENDLY_WAVE -> {
                    float waveAngle = (float) (Math.sin(p * 28.0) * 0.55F);
                    this.rightArm.pitch = MathHelper.lerp(blend, this.rightArm.pitch, -2.80F);
                    this.rightArm.yaw = MathHelper.lerp(blend, this.rightArm.yaw, 0.0F);
                    this.rightArm.roll = MathHelper.lerp(blend, this.rightArm.roll, waveAngle);
                    this.head.roll = MathHelper.lerp(blend, this.head.roll, 0.18F);
                }
                case ARM_WAVE -> {
                    // Continuous horizontal arm wave flowing from left arm through torso to right arm
                    float t = (emoteState.emoteDurationTicks + tickDelta) * 0.18F;

                    // Left Arm: Horizontal spread + flowing sine
                    float waveL = (float) Math.sin(t);
                    this.leftArm.roll = -1.55F + (float) Math.sin(t + 0.5) * 0.25F;
                    this.leftArm.pitch = waveL * 0.35F;
                    this.leftArm.yaw = (float) Math.cos(t) * 0.15F;

                    // Torso & Belly: delayed phase wave passing across
                    this.body.roll = (float) Math.sin(t - 1.0) * 0.12F;
                    this.body.yaw = (float) Math.sin(t - 1.0) * 0.14F;
                    this.head.roll = (float) Math.sin(t - 1.0) * 0.10F;

                    // Right Arm: Horizontal spread + delayed flowing sine
                    float waveR = (float) Math.sin(t - 2.0);
                    this.rightArm.roll = 1.55F + (float) Math.sin(t - 1.5) * 0.25F;
                    this.rightArm.pitch = waveR * 0.35F;
                    this.rightArm.yaw = (float) Math.cos(t - 2.0) * 0.15F;
                }
                case FACEPALM -> {
                    this.rightArm.pitch = MathHelper.lerp(blend, this.rightArm.pitch, -1.85F);
                    this.rightArm.yaw = MathHelper.lerp(blend, this.rightArm.yaw, -0.65F);
                    this.rightArm.roll = MathHelper.lerp(blend, this.rightArm.roll, 0.45F);
                    this.head.pitch = MathHelper.lerp(blend, this.head.pitch, 0.45F);
                    this.head.yaw = MathHelper.lerp(blend, this.head.yaw, (float) Math.sin(p * 20.0) * 0.08F);
                }
                default -> {}
            }

            this.rightSleeve.resetTransform();
            this.leftSleeve.resetTransform();
            this.leftPants.resetTransform();
            this.rightPants.resetTransform();
            this.jacket.resetTransform();
        }
    }
}
