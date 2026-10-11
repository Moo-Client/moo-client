package com.mooclient.mixin;

import com.mooclient.util.PlayerArmorDurabilityTracker;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerInteractionManager.class)
public class ClientPlayerInteractionManagerMixin {

    @Inject(method = "attackEntity", at = @At("HEAD"))
    private void mooClient$onAttackEntity(PlayerEntity player, Entity target, CallbackInfo ci) {
        if (target instanceof PlayerEntity targetPlayer) {
            PlayerArmorDurabilityTracker.recordLocalAttack(targetPlayer, player.getMainHandStack());
        }
    }
}
