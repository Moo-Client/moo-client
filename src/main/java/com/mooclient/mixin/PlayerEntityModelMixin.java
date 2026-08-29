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

    public PlayerEntityModelMixin(ModelPart root) {
        super(root);
    }

    @Inject(method = "setAngles(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;)V", at = @At("TAIL"))
    private void mooClient$onSetAngles(PlayerEntityRenderState state, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null && state.id == client.player.getId()) {
            float tickDelta = client.getRenderTickCounter().getTickDelta(true);
            float progress = EmotesModule.getInterpolatedProgress(tickDelta);

            if (progress > 0.001F) {
                // Docelowe kąty: idealnie pionowo w górę (dokładnie 180 stopni w pionie, bez skrzywień)
                float targetPitch = -(float) Math.PI;
                float targetYaw = 0.0F;
                float targetRoll = 0.0F;

                // Płynna interpolacja od naturalnej pozycji rąk (chód, trzymany przedmiot) do rąk w górze
                this.rightArm.pitch = MathHelper.lerp(progress, this.rightArm.pitch, targetPitch);
                this.rightArm.yaw = MathHelper.lerp(progress, this.rightArm.yaw, targetYaw);
                this.rightArm.roll = MathHelper.lerp(progress, this.rightArm.roll, targetRoll);

                this.leftArm.pitch = MathHelper.lerp(progress, this.leftArm.pitch, targetPitch);
                this.leftArm.yaw = MathHelper.lerp(progress, this.leftArm.yaw, targetYaw);
                this.leftArm.roll = MathHelper.lerp(progress, this.leftArm.roll, targetRoll);

                // W Minecraft 1.21.4 rękawy są dziećmi rąk (child ModelPart),
                // więc ich lokalna transformacja musi być zresetowana (0,0,0),
                // aby idealnie i płynnie podążały za ręką bez podwójnego obrotu i przesunięcia
                this.rightSleeve.resetTransform();
                this.leftSleeve.resetTransform();
            }
        }
    }
}

