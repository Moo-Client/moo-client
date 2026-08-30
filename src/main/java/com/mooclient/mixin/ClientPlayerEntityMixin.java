package com.mooclient.mixin;

import com.mooclient.interaction.InteractionInputBlocker;
import com.mooclient.module.modules.ToggleSprintModule;
import com.mooclient.module.modules.WaypointsModule;
import com.mooclient.waypoint.WaypointManager;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.effect.StatusEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin into ClientPlayerEntity to implement Toggle Sprint, Auto Death Waypoint,
 * and movement input blocking during active multiplayer interactions.
 */
@Mixin(ClientPlayerEntity.class)
public class ClientPlayerEntityMixin {

    @Unique
    private boolean mooClient$wasDead = false;

    @Inject(method = "tickMovement", at = @At("HEAD"))
    private void mooClient$autoSprint(CallbackInfo ci) {
        ClientPlayerEntity player = (ClientPlayerEntity) (Object) this;

        if (player.input != null) {
            InteractionInputBlocker.processPlayerInput(player.input);
        }

        if (ToggleSprintModule.shouldSprint()
                && !player.isSneaking()
                && !player.hasStatusEffect(StatusEffects.BLINDNESS)
                && !player.horizontalCollision
                && player.getHungerManager().getFoodLevel() > 6
                && player.input != null
                && player.input.movementForward > 0.1f) {
            player.setSprinting(true);
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void mooClient$trackDeath(CallbackInfo ci) {
        ClientPlayerEntity player = (ClientPlayerEntity) (Object) this;
        if (player.isDead() || player.getHealth() <= 0.0f) {
            if (!this.mooClient$wasDead) {
                this.mooClient$wasDead = true;
                if (WaypointsModule.isWaypointsEnabled() && WaypointsModule.isDeathWaypoint() && player.clientWorld != null) {
                    WaypointManager.getInstance().createDeathWaypoint(
                            player.getX(),
                            player.getY(),
                            player.getZ(),
                            player.clientWorld
                    );
                }
            }
        } else {
            this.mooClient$wasDead = false;
        }
    }
}
