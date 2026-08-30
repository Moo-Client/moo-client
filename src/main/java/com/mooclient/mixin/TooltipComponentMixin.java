package com.mooclient.mixin;

import com.mooclient.tooltip.ShulkerBoxTooltipComponent;
import com.mooclient.tooltip.ShulkerTooltipData;
import net.minecraft.client.gui.tooltip.TooltipComponent;
import net.minecraft.item.tooltip.TooltipData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TooltipComponent.class)
public interface TooltipComponentMixin {

    @Inject(method = "of(Lnet/minecraft/item/tooltip/TooltipData;)Lnet/minecraft/client/gui/tooltip/TooltipComponent;", at = @At("HEAD"), cancellable = true)
    private static void mooClient$createShulkerTooltipComponent(TooltipData data, CallbackInfoReturnable<TooltipComponent> cir) {
        if (data instanceof ShulkerTooltipData shulkerData) {
            cir.setReturnValue(new ShulkerBoxTooltipComponent(shulkerData));
        }
    }
}
