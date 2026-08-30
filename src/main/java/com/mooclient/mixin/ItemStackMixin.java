package com.mooclient.mixin;

import com.mooclient.module.modules.ShulkerTooltipModule;
import com.mooclient.tooltip.ShulkerTooltipData;
import com.mooclient.tooltip.ShulkerTooltipUtil;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipData;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Mixin(ItemStack.class)
public class ItemStackMixin {

    @Inject(method = "getTooltipData", at = @At("HEAD"), cancellable = true)
    private void mooClient$getShulkerTooltipData(CallbackInfoReturnable<Optional<TooltipData>> cir) {
        if (!ShulkerTooltipModule.isShulkerEnabled()) {
            return;
        }

        if (ShulkerTooltipModule.isRequireShift() && !Screen.hasShiftDown()) {
            return;
        }

        ItemStack self = (ItemStack) (Object) this;
        if (ShulkerTooltipUtil.isShulkerBox(self) && ShulkerTooltipUtil.hasItems(self)) {
            cir.setReturnValue(Optional.of(new ShulkerTooltipData(self)));
        }
    }

    @Inject(method = "getTooltip", at = @At("RETURN"))
    private void mooClient$cleanShulkerTooltipText(Item.TooltipContext context, PlayerEntity player, TooltipType type, CallbackInfoReturnable<List<Text>> cir) {
        if (!ShulkerTooltipModule.isShulkerEnabled()) {
            return;
        }

        if (ShulkerTooltipModule.isRequireShift() && !Screen.hasShiftDown()) {
            return;
        }

        ItemStack self = (ItemStack) (Object) this;
        if (ShulkerTooltipUtil.isShulkerBox(self) && ShulkerTooltipUtil.hasItems(self)) {
            List<Text> list = cir.getReturnValue();
            if (list != null && list.size() > 1) {
                DefaultedList<ItemStack> items = ShulkerTooltipUtil.getItems(self);
                Set<String> itemNames = new HashSet<>();
                for (ItemStack st : items) {
                    if (st != null && !st.isEmpty()) {
                        itemNames.add(st.getName().getString());
                    }
                }

                list.removeIf(text -> {
                    String str = text.getString().trim();
                    if (str.startsWith("and ") && str.endsWith("more...")) return true;
                    if (str.startsWith("i ") && str.endsWith("więcej...")) return true;
                    for (String name : itemNames) {
                        if (str.startsWith(name) && (str.contains(" x") || str.equals(name))) {
                            return true;
                        }
                    }
                    return false;
                });
            }
        }
    }
}
