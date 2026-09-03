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
 * Gwarantuje perfekcyjne dopasowanie i synchronizację zewnętrznych warstw skina (3D layers)
 * dla wszystkich emotek na raz, bez odrywania i podnoszenia warstw.
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

        // 1. Głowa (Head)
        BoneTransform headT = emoteState.getBoneTransform("head");
        if (headT != null) {
            this.head.pitch = headT.pitch;
            this.head.yaw = headT.yaw;
            this.head.roll = headT.roll;
            if (headT.posX != 0.0f || headT.posY != 0.0f || headT.posZ != 0.0f) {
                this.head.pivotX = headT.posX;
                this.head.pivotY = -headT.posY;
                this.head.pivotZ = -headT.posZ;
            }
        }

        // 2. Tułów (Body)
        BoneTransform bodyT = emoteState.getBoneTransform("body");
        if (bodyT != null) {
            this.body.pitch = bodyT.pitch;
            this.body.yaw = bodyT.yaw;
            this.body.roll = bodyT.roll;
            if (bodyT.posX != 0.0f || bodyT.posY != 0.0f || bodyT.posZ != 0.0f) {
                this.body.pivotX = bodyT.posX;
                this.body.pivotY = -bodyT.posY;
                this.body.pivotZ = -bodyT.posZ;
            }
        }

        // 3. Prawe ramię (Right Arm)
        BoneTransform rArmT = emoteState.getBoneTransform("right_arm");
        if (rArmT != null) {
            this.rightArm.pitch = rArmT.pitch;
            this.rightArm.yaw = rArmT.yaw;
            this.rightArm.roll = rArmT.roll;
            if (rArmT.posX != 0.0f || rArmT.posY != 0.0f || rArmT.posZ != 0.0f) {
                this.rightArm.pivotX = -5.0f + rArmT.posX;
                this.rightArm.pivotY = 2.0f - rArmT.posY;
                this.rightArm.pivotZ = -rArmT.posZ;
            }
        }

        // 4. Lewe ramię (Left Arm)
        BoneTransform lArmT = emoteState.getBoneTransform("left_arm");
        if (lArmT != null) {
            this.leftArm.pitch = lArmT.pitch;
            this.leftArm.yaw = lArmT.yaw;
            this.leftArm.roll = lArmT.roll;
            if (lArmT.posX != 0.0f || lArmT.posY != 0.0f || lArmT.posZ != 0.0f) {
                this.leftArm.pivotX = 5.0f + lArmT.posX;
                this.leftArm.pivotY = 2.0f - lArmT.posY;
                this.leftArm.pivotZ = -lArmT.posZ;
            }
        }

        // 5. Prawa noga (Right Leg)
        BoneTransform rLegT = emoteState.getBoneTransform("right_leg");
        if (rLegT != null) {
            this.rightLeg.pitch = rLegT.pitch;
            this.rightLeg.yaw = rLegT.yaw;
            this.rightLeg.roll = rLegT.roll;
            if (rLegT.posX != 0.0f || rLegT.posY != 0.0f || rLegT.posZ != 0.0f) {
                this.rightLeg.pivotX = -1.9f + rLegT.posX;
                this.rightLeg.pivotY = 12.0f - rLegT.posY;
                this.rightLeg.pivotZ = -rLegT.posZ;
            }
        }

        // 6. Lewa noga (Left Leg)
        BoneTransform lLegT = emoteState.getBoneTransform("left_leg");
        if (lLegT != null) {
            this.leftLeg.pitch = lLegT.pitch;
            this.leftLeg.yaw = lLegT.yaw;
            this.leftLeg.roll = lLegT.roll;
            if (lLegT.posX != 0.0f || lLegT.posY != 0.0f || lLegT.posZ != 0.0f) {
                this.leftLeg.pivotX = 1.9f + lLegT.posX;
                this.leftLeg.pivotY = 12.0f - lLegT.posY;
                this.leftLeg.pivotZ = -lLegT.posZ;
            }
        }

        // 7. Pełna synchronizacja wszystkich zewnętrznych warstw skina (dla wszystkich emotek na raz)
        // Zastępuje błędne resetTransform(), które podnosiło warstwy do góry
        this.hat.copyTransform(this.head);
        this.jacket.copyTransform(this.body);
        this.rightSleeve.copyTransform(this.rightArm);
        this.leftSleeve.copyTransform(this.leftArm);
        this.rightPants.copyTransform(this.rightLeg);
        this.leftPants.copyTransform(this.leftLeg);
    }
}
