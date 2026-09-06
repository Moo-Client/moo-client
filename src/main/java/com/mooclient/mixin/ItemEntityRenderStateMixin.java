package com.mooclient.mixin;

import com.mooclient.util.ItemEntityRenderStateAccess;
import net.minecraft.client.render.entity.state.ItemEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ItemEntityRenderState.class)
public class ItemEntityRenderStateMixin implements ItemEntityRenderStateAccess {

    @Unique
    private float moo$scale = 1.0f;

    @Override
    public float moo$getScale() {
        return moo$scale;
    }

    @Override
    public void moo$setScale(float scale) {
        this.moo$scale = scale;
    }
}
