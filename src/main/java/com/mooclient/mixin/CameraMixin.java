package com.mooclient.mixin;

import com.mooclient.module.modules.FreelookModule;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {

    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Shadow
    protected abstract void setPos(double x, double y, double z);

    @Shadow
    protected abstract void moveBy(float f, float g, float h);

    @Shadow
    private float lastCameraY;

    @Shadow
    private float cameraY;

    @Shadow
    protected abstract float clipToSpace(float f);

//? if >=1.21.11 {
    @Inject(method = "update", at = @At("TAIL"))
    private void mooClient$onCameraUpdateTail(net.minecraft.world.World area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickDelta, CallbackInfo ci) {
//?} else {
    /*@Inject(method = "update", at = @At("TAIL"))
    private void mooClient$onCameraUpdateTail(BlockView area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickDelta, CallbackInfo ci) {
*///?}
        if (FreelookModule.isActive()) {
            float yaw = FreelookModule.getCameraYaw();
            float pitch = FreelookModule.getCameraPitch();

            // Set camera rotation to Freelook yaw and pitch
            this.setRotation(yaw, pitch);

            // Reset camera position to player's eye height
//? if >=1.21.11 {
            double posX = MathHelper.lerp((double) tickDelta, focusedEntity.lastRenderX, focusedEntity.getX());
            double posY = MathHelper.lerp((double) tickDelta, focusedEntity.lastRenderY, focusedEntity.getY())
                    + (double) MathHelper.lerp(tickDelta, this.lastCameraY, this.cameraY);
            double posZ = MathHelper.lerp((double) tickDelta, focusedEntity.lastRenderZ, focusedEntity.getZ());
//?} else {
            /*double posX = MathHelper.lerp((double) tickDelta, focusedEntity.prevX, focusedEntity.getX());
            double posY = MathHelper.lerp((double) tickDelta, focusedEntity.prevY, focusedEntity.getY())
                    + (double) MathHelper.lerp(tickDelta, this.lastCameraY, this.cameraY);
            double posZ = MathHelper.lerp((double) tickDelta, focusedEntity.prevZ, focusedEntity.getZ());
*///?}
            this.setPos(posX, posY, posZ);

            // Move camera back 4 blocks along the freelook line of sight (respecting block collision)
            float scale = (focusedEntity instanceof LivingEntity living) ? living.getScale() : 1.0F;
            float distance = this.clipToSpace(4.0F * scale);
            this.moveBy(-distance, 0.0F, 0.0F);
        }
    }
}
