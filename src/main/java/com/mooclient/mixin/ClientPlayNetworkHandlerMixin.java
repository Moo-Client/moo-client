package com.mooclient.mixin;

import com.mooclient.util.PlayerArmorDurabilityTracker;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.EntityDamageS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityEquipmentUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerMixin {

    @Shadow
    private net.minecraft.client.world.ClientWorld world;

    @Inject(method = "onEntityDamage", at = @At("TAIL"))
    private void mooClient$onEntityDamage(EntityDamageS2CPacket packet, CallbackInfo ci) {
        if (this.world == null) return;
        Entity entity = this.world.getEntityById(packet.entityId());
        if (entity instanceof PlayerEntity player) {
            try {
                PlayerArmorDurabilityTracker.recordDamage(player, 0, packet.createDamageSource(this.world));
            } catch (Throwable ignored) {
                PlayerArmorDurabilityTracker.recordDamage(player, 0, null);
            }
        }
    }

    @Inject(method = "onEntityStatus", at = @At("TAIL"))
    private void mooClient$onEntityStatus(EntityStatusS2CPacket packet, CallbackInfo ci) {
        if (this.world == null) return;
        Entity entity = packet.getEntity(this.world);
        if (entity != null) {
            if (packet.getStatus() == 2) { // Entity hurt / red damage flash
                if (entity instanceof PlayerEntity player) {
                    PlayerArmorDurabilityTracker.recordDamage(player, 0, null);
                }
            } else if (packet.getStatus() == 3) { // Entity death
                PlayerArmorDurabilityTracker.clearEntity(entity.getId());
            }
        }
    }

    @Inject(method = "onEntityEquipmentUpdate", at = @At("TAIL"))
    private void mooClient$onEntityEquipmentUpdate(EntityEquipmentUpdateS2CPacket packet, CallbackInfo ci) {
        if (this.world == null) return;
        Entity entity = this.world.getEntityById(packet.getEntityId());
        if (entity instanceof PlayerEntity player) {
            for (var pair : packet.getEquipmentList()) {
                EquipmentSlot slot = pair.getFirst();
                ItemStack stack = pair.getSecond();
                if (slot.isArmorSlot()) {
                    PlayerArmorDurabilityTracker.getRemainingDurability(player, slot, stack);
                }
            }
        }
    }
}
