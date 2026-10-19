package com.mooclient.mixin;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.SplashOverlay;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Function;
import java.util.function.IntSupplier;

/**
 * Custom Moo Client Splash & Loading Screen.
 * - Replaces Mojang red background with sleek dark #0C0C10 theme.
 * - Displays large composite Moo Client logo & typography (White "MOO" + Silver "CLIENT").
 * - Features a modern, sleek grey/silver progress bar.
 */
@Mixin(SplashOverlay.class)
public abstract class SplashOverlayMixin {

    private static final Identifier SPLASH_LOGO = Identifier.of("mooclient", "splash_logo.png");

    @Shadow private float progress;

    /**
     * Replaces Mojang red (0xEF323D) with dark Moo Client theme (0xFF0C0C10) everywhere in SplashOverlay.
     */
    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Ljava/util/function/IntSupplier;getAsInt()I"))
    private int mooClient$redirectBrandColor(IntSupplier supplier) {
        return 0xFF0C0C10;
    }

    @Inject(method = "withAlpha", at = @At("HEAD"), cancellable = true)
    private static void mooClient$setDarkBackgroundAlpha(int color, int alpha, CallbackInfoReturnable<Integer> cir) {
        int a = MathHelper.clamp(alpha, 0, 255);
        cir.setReturnValue((a << 24) | 0x0C0C10);
    }

    /**
     * Draws the enlarged 512x256 composite Moo Client logo (Cow icon + White "MOO" + Silver "CLIENT").
     */
//? if >=1.21.11 {
    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawTexture(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/util/Identifier;IIFFIIIIIII)V", ordinal = 0))
    private void mooClient$drawCustomLogo(DrawContext context, com.mojang.blaze3d.pipeline.RenderPipeline pipeline, Identifier sprite, int x, int y, float u, float v, int width, int height, int regionWidth, int regionHeight, int textureWidth, int textureHeight, int color) {
        int screenW = context.getScaledWindowWidth();
        int screenH = context.getScaledWindowHeight();

        // Enlarged prominent logo display (260x130)
        int logoW = 260;
        int logoH = 130;
        int logoX = (screenW - logoW) / 2;
        int logoY = (screenH / 2) - 75;

        // Fully sampled 512x256 texture with alpha blending
        context.drawTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED, SPLASH_LOGO, logoX, logoY, 0.0f, 0.0f, logoW, logoH, 512, 256, 512, 256, color);
    }

    /**
     * Cancels the second half of Mojang logo draw so it doesn't draw Mojang text.
     */
    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawTexture(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/util/Identifier;IIFFIIIIIII)V", ordinal = 1))
    private void mooClient$cancelSecondLogoHalf(DrawContext context, com.mojang.blaze3d.pipeline.RenderPipeline pipeline, Identifier sprite, int x, int y, float u, float v, int width, int height, int regionWidth, int regionHeight, int textureWidth, int textureHeight, int color) {
        // No-op (suppress second half of Mojang logo)
    }
//?} else {
    /*@Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawTexture(Ljava/util/function/Function;Lnet/minecraft/util/Identifier;IIFFIIIIIII)V", ordinal = 0))
    private void mooClient$drawCustomLogo(DrawContext context, Function<Identifier, RenderLayer> renderLayers, Identifier sprite, int x, int y, float u, float v, int width, int height, int regionWidth, int regionHeight, int textureWidth, int textureHeight, int color) {
        int screenW = context.getScaledWindowWidth();
        int screenH = context.getScaledWindowHeight();

        // Enlarged prominent logo display (260x130)
        int logoW = 260;
        int logoH = 130;
        int logoX = (screenW - logoW) / 2;
        int logoY = (screenH / 2) - 75;

        // Fully sampled 512x256 texture with alpha blending
        context.drawTexture(RenderLayer::getGuiTextured, SPLASH_LOGO, logoX, logoY, 0.0f, 0.0f, logoW, logoH, 512, 256, 512, 256, color);
    }

    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawTexture(Ljava/util/function/Function;Lnet/minecraft/util/Identifier;IIFFIIIIIII)V", ordinal = 1))
    private void mooClient$cancelSecondLogoHalf(DrawContext context, Function<Identifier, RenderLayer> renderLayers, Identifier sprite, int x, int y, float u, float v, int width, int height, int regionWidth, int regionHeight, int textureWidth, int textureHeight, int color) {
        // No-op (suppress second half of Mojang logo)
    }
*///?}

    /**
     * Custom sleek grey / silver progress bar matching the Moo Client monochrome aesthetic.
     */
    @Inject(method = "renderProgressBar", at = @At("HEAD"), cancellable = true)
    private void mooClient$customProgressBar(DrawContext context, int minX, int minY, int maxX, int maxY, float opacity, CallbackInfo ci) {
        ci.cancel();

        int alpha = MathHelper.clamp(Math.round(opacity * 255.0f), 0, 255);
        if (alpha <= 0) return;

        int screenW = context.getScaledWindowWidth();
        int screenH = context.getScaledWindowHeight();

        // Sleek compact progress bar dimensions
        int barW = Math.min(280, screenW - 60);
        int barH = 6;
        int barX = (screenW - barW) / 2;
        int barY = screenH - 46;

        int trackBg = (Math.min(alpha, 140) << 24) | 0x14141C;
        int trackBorder = (Math.min(alpha, 50) << 24) | 0xFFFFFF;
        int fillStart = (alpha << 24) | 0xE4E4E7; // Silver-white (#E4E4E7)
        int fillEnd = (alpha << 24) | 0x9CA3AF;   // Sleek grey (#9CA3AF)

        // Outer glass border & inner track
        context.fill(barX - 1, barY - 1, barX + barW + 1, barY + barH + 1, trackBorder);
        context.fill(barX, barY, barX + barW, barY + barH, trackBg);

        // Sleek Grey / Silver Fill
        int fillWidth = MathHelper.ceil((float)barW * MathHelper.clamp(this.progress, 0.0f, 1.0f));
        if (fillWidth > 0) {
            context.fillGradient(barX, barY, barX + fillWidth, barY + barH, fillStart, fillEnd);
        }
    }
}
