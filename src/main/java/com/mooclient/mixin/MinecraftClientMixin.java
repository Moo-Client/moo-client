package com.mooclient.mixin;

import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin to set the window title for Minecraft & clipping tools compatibility (Medal.tv, SteelSeries Moments, OBS, GeForce).
 */
@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {

    @Inject(method = "getWindowTitle", at = @At("HEAD"), cancellable = true)
    private void mooClient$getWindowTitle(CallbackInfoReturnable<String> cir) {
        cir.setReturnValue("Minecraft 1.21.4 - Moo Client");
    }
}
