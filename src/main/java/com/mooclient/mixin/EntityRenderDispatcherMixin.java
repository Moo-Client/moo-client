package com.mooclient.mixin;

import com.mooclient.module.modules.HitboxModule;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {

    @Shadow
    private static void renderHitbox(MatrixStack matrices, VertexConsumer vertices, Entity entity, float tickDelta, float red, float green, float blue) {
        throw new AssertionError();
    }

    @Redirect(
        method = "render(Lnet/minecraft/entity/Entity;DDDFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/client/render/entity/EntityRenderer;)V",
        at = @At(value = "FIELD", target = "Lnet/minecraft/client/render/entity/EntityRenderDispatcher;renderHitboxes:Z", opcode = Opcodes.GETFIELD)
    )
    private boolean mooClient$redirectRenderHitboxes(EntityRenderDispatcher dispatcher) {
        if (HitboxModule.isHitboxEnabled()) {
            return true;
        }
        return dispatcher.shouldRenderHitboxes();
    }

    @Redirect(
        method = "render(Lnet/minecraft/entity/Entity;DDDFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/client/render/entity/EntityRenderer;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/EntityRenderDispatcher;renderHitbox(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;Lnet/minecraft/entity/Entity;FFFF)V")
    )
    private void mooClient$redirectRenderHitboxCall(MatrixStack matrices, VertexConsumer vertices, Entity entity, float tickDelta, float red, float green, float blue) {
        if (HitboxModule.isHitboxEnabled()) {
            if (HitboxModule.isOnlyGlidingOrSwimming()) {
                if (!(entity instanceof LivingEntity living) || (!living.isGliding() && !living.isSwimming())) {
                    return; // Skip rendering hitbox for non-gliding / non-swimming entities
                }
            }
        }
        renderHitbox(matrices, vertices, entity, tickDelta, red, green, blue);
    }

    @Redirect(
        method = "renderHitbox",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/VertexRendering;drawBox(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;Lnet/minecraft/util/math/Box;FFFF)V")
    )
    private static void mooClient$modifyHitboxColor(MatrixStack matrices, VertexConsumer vertices, Box box, float red, float green, float blue, float alpha) {
        if (HitboxModule.isHitboxEnabled()) {
            VertexRendering.drawBox(matrices, vertices, box,
                    HitboxModule.getColorRed(),
                    HitboxModule.getColorGreen(),
                    HitboxModule.getColorBlue(),
                    HitboxModule.getColorAlpha());
        } else {
            VertexRendering.drawBox(matrices, vertices, box, red, green, blue, alpha);
        }
    }

    @Redirect(
        method = "renderHitbox",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/VertexRendering;drawBox(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;DDDDDDFFFF)V")
    )
    private static void mooClient$filterEyeHeightBox(MatrixStack matrices, VertexConsumer vertices, double x1, double y1, double z1, double x2, double y2, double z2, float red, float green, float blue, float alpha) {
        // Red eye-height box has red = 1.0f, green = 0.0f, blue = 0.0f
        if (red == 1.0f && green == 0.0f && blue == 0.0f) {
            if (HitboxModule.isHitboxEnabled() && !HitboxModule.isShowEyeHeight()) {
                return;
            }
        }
        VertexRendering.drawBox(matrices, vertices, x1, y1, z1, x2, y2, z2, red, green, blue, alpha);
    }

    @Redirect(
        method = "renderHitbox",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/VertexRendering;drawVector(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;Lorg/joml/Vector3f;Lnet/minecraft/util/math/Vec3d;I)V")
    )
    private static void mooClient$filterLookVector(MatrixStack matrices, VertexConsumer vertices, Vector3f origin, Vec3d vector, int color) {
        if (HitboxModule.isHitboxEnabled() && !HitboxModule.isShowLookVector()) {
            return;
        }
        VertexRendering.drawVector(matrices, vertices, origin, vector, color);
    }
}
