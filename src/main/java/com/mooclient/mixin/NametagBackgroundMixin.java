package com.mooclient.mixin;

import com.mooclient.module.modules.NametagsModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin into EntityRenderer to render authentic Lunar/Badlion style Moo Client logo badge
 * EXCLUSIVELY before player nicknames for Moo Client users, render Ping ABOVE nicknames when selected,
 * remove nametag background, and apply text shadow.
 */
@Mixin(EntityRenderer.class)
public abstract class NametagBackgroundMixin {

    @Shadow
    public abstract TextRenderer getTextRenderer();

    private static final Identifier MOO_LOGO = Identifier.of("mooclient", "textures/gui/icon.png");
    private final ThreadLocal<EntityRenderState> mooClient$currentState = new ThreadLocal<>();
    private final ThreadLocal<Text> mooClient$currentText = new ThreadLocal<>();

    private static boolean isPlayerNicknameLabel(PlayerEntityRenderState playerState, Text text) {
        if (playerState == null || text == null) return false;
        // In 1.21.4 PlayerEntityRenderState: playerState.playerName is the SCOREBOARD sub-label (e.g. "20 ❤") when present.
        return playerState.playerName == null || text != playerState.playerName;
    }

    @Inject(method = "renderLabelIfPresent", at = @At("HEAD"))
    private void mooClient$captureState(EntityRenderState state, Text text, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        this.mooClient$currentState.set(state);
        this.mooClient$currentText.set(text);
    }

    @Inject(method = "renderLabelIfPresent", at = @At("RETURN"))
    private void mooClient$clearState(EntityRenderState state, Text text, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        this.mooClient$currentState.remove();
        this.mooClient$currentText.remove();
    }

    @Redirect(
        method = "renderLabelIfPresent",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/option/GameOptions;getTextBackgroundOpacity(F)F")
    )
    private float mooClient$removeNametagBackground(GameOptions options, float fallback) {
        if (NametagsModule.isNametagsEnabled() && NametagsModule.isRemoveBackground()) {
            return 0.0F;
        }
        return options.getTextBackgroundOpacity(fallback);
    }

    @ModifyArg(
        method = "renderLabelIfPresent",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/font/TextRenderer;draw(Lnet/minecraft/text/Text;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/client/font/TextRenderer$TextLayerType;II)I"),
        index = 1
    )
    private float mooClient$centerNametagWithBadge(float originalX) {
        EntityRenderState state = this.mooClient$currentState.get();
        Text text = this.mooClient$currentText.get();
        if (state instanceof PlayerEntityRenderState playerState
                && isPlayerNicknameLabel(playerState, text)
                && com.mooclient.util.MooUserManager.isMooUser(playerState.name, playerState.id)) {
            float badgeTotalWidth = 11.0f; // iconSize (8.5f) + gap (2.5f)
            return originalX + (badgeTotalWidth / 2.0f);
        }
        return originalX;
    }

    @ModifyArg(
        method = "renderLabelIfPresent",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/font/TextRenderer;draw(Lnet/minecraft/text/Text;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/client/font/TextRenderer$TextLayerType;II)I"),
        index = 4
    )
    private boolean mooClient$modifyNametagShadow(boolean originalShadow) {
        if (NametagsModule.isNametagsEnabled() && NametagsModule.isTextShadow()) {
            return true;
        }
        return originalShadow;
    }

    @Inject(
        method = "renderLabelIfPresent",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;pop()V")
    )
    private void mooClient$renderNametagAddons(EntityRenderState state, Text text, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        if (!(state instanceof PlayerEntityRenderState playerState)) {
            return;
        }

        // Exclusively render cow logo and ping on the player's nickname line (never on health, score, or sub-labels)
        if (!isPlayerNicknameLabel(playerState, text)) {
            return;
        }

        Matrix4f matrix = matrices.peek().getPositionMatrix();

        // 1. Authentic Moo Client Cow Logo Badge rendering (EXCLUSIVELY in front of nickname)
        if (com.mooclient.util.MooUserManager.isMooUser(playerState.name, playerState.id)) {
            float textWidth = this.getTextRenderer().getWidth(text);
            float badgeTotalWidth = 11.0f;
            float iconSize = 8.5f;

            float totalWidth = textWidth + badgeTotalWidth;
            float iconX = -totalWidth / 2.0f;
            float iconY = 0.0f;

            boolean seeThrough = !playerState.sneaking;

            if (seeThrough) {
                // Render see-through layer (visible through blocks and walls)
                VertexConsumer seeThroughBuffer = vertexConsumers.getBuffer(RenderLayer.getTextSeeThrough(MOO_LOGO));
                seeThroughBuffer.vertex(matrix, iconX, iconY, 0.0f).color(255, 255, 255, 128).texture(0.0f, 0.0f).light(light);
                seeThroughBuffer.vertex(matrix, iconX, iconY + iconSize, 0.0f).color(255, 255, 255, 128).texture(0.0f, 1.0f).light(light);
                seeThroughBuffer.vertex(matrix, iconX + iconSize, iconY + iconSize, 0.0f).color(255, 255, 255, 128).texture(1.0f, 1.0f).light(light);
                seeThroughBuffer.vertex(matrix, iconX + iconSize, iconY, 0.0f).color(255, 255, 255, 128).texture(1.0f, 0.0f).light(light);

                // Render normal layer (full brightness with depth testing when in line of sight)
                int emissiveLight = net.minecraft.client.render.LightmapTextureManager.applyEmission(light, 2);
                VertexConsumer normalBuffer = vertexConsumers.getBuffer(RenderLayer.getText(MOO_LOGO));
                normalBuffer.vertex(matrix, iconX, iconY, 0.0f).color(255, 255, 255, 255).texture(0.0f, 0.0f).light(emissiveLight);
                normalBuffer.vertex(matrix, iconX, iconY + iconSize, 0.0f).color(255, 255, 255, 255).texture(0.0f, 1.0f).light(emissiveLight);
                normalBuffer.vertex(matrix, iconX + iconSize, iconY + iconSize, 0.0f).color(255, 255, 255, 255).texture(1.0f, 1.0f).light(emissiveLight);
                normalBuffer.vertex(matrix, iconX + iconSize, iconY, 0.0f).color(255, 255, 255, 255).texture(1.0f, 0.0f).light(emissiveLight);
            } else {
                // Sneaking player: only normal depth layer
                VertexConsumer normalBuffer = vertexConsumers.getBuffer(RenderLayer.getText(MOO_LOGO));
                normalBuffer.vertex(matrix, iconX, iconY, 0.0f).color(255, 255, 255, 128).texture(0.0f, 0.0f).light(light);
                normalBuffer.vertex(matrix, iconX, iconY + iconSize, 0.0f).color(255, 255, 255, 128).texture(0.0f, 1.0f).light(light);
                normalBuffer.vertex(matrix, iconX + iconSize, iconY + iconSize, 0.0f).color(255, 255, 255, 128).texture(1.0f, 1.0f).light(light);
                normalBuffer.vertex(matrix, iconX + iconSize, iconY, 0.0f).color(255, 255, 255, 128).texture(1.0f, 0.0f).light(light);
            }
        }

        // 2. Render Ping ABOVE nickname for all players when ABOVE mode is active
        if (NametagsModule.isNametagsEnabled() && NametagsModule.isShowPing() && NametagsModule.getPingPosition() == NametagsModule.PingPosition.ABOVE) {
            Text pingText = NametagsModule.getPingText(playerState.id, playerState.name);
            if (pingText != null) {
                float pingWidth = this.getTextRenderer().getWidth(pingText);
                float pingX = -pingWidth / 2.0f;
                float pingY = -10.0f;
                int bgAlpha = NametagsModule.isRemoveBackground() ? 0 : (int)(net.minecraft.client.MinecraftClient.getInstance().options.getTextBackgroundOpacity(0.25F) * 255.0F);
                int bgColor = bgAlpha << 24;

                boolean seeThrough = !playerState.sneaking;

                this.getTextRenderer().draw(
                    pingText,
                    pingX,
                    pingY,
                    -2130706433,
                    NametagsModule.isTextShadow(),
                    matrix,
                    vertexConsumers,
                    seeThrough ? TextRenderer.TextLayerType.SEE_THROUGH : TextRenderer.TextLayerType.NORMAL,
                    bgColor,
                    light
                );

                if (seeThrough) {
                    this.getTextRenderer().draw(
                        pingText,
                        pingX,
                        pingY,
                        -1,
                        NametagsModule.isTextShadow(),
                        matrix,
                        vertexConsumers,
                        TextRenderer.TextLayerType.NORMAL,
                        0,
                        net.minecraft.client.render.LightmapTextureManager.applyEmission(light, 2)
                    );
                }
            }
        }

        // 3. Render Armor Set Durability UNDER nickname in real time when Armor HUD option is active
        if (com.mooclient.module.modules.ArmorModule.isArmorEnabled()
                && com.mooclient.module.modules.ArmorModule.isShowNametagDurability()) {
            MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
            if (client != null && client.player != null && (playerState.id == client.player.getId()
                    || (playerState.name != null && playerState.name.equalsIgnoreCase(client.player.getGameProfile().getName())))) {
                return;
            }
            Text armorText = com.mooclient.module.modules.ArmorModule.getNametagArmorText(playerState);
            if (armorText != null) {
                float armorWidth = com.mooclient.module.modules.ArmorModule.getNametagArmorWidth(playerState.id, armorText, this.getTextRenderer());
                float armorX = -armorWidth / 2.0f;
                // If a scoreboard below-name objective is present (e.g. "1600 pk" on TabMC or health hearts),
                // shift armor durability down so it renders cleanly below the points/health instead of overlapping them!
                float armorY = (playerState.playerName != null) ? 21.5f : 11.0f;
                boolean textShadow = NametagsModule.isNametagsEnabled() ? NametagsModule.isTextShadow() : true;
                boolean removeBg = NametagsModule.isNametagsEnabled() && NametagsModule.isRemoveBackground();
                int bgAlpha = removeBg ? 0 : (int)(net.minecraft.client.MinecraftClient.getInstance().options.getTextBackgroundOpacity(0.25F) * 255.0F);
                int bgColor = bgAlpha << 24;

                boolean seeThrough = !playerState.sneaking;

                this.getTextRenderer().draw(
                    armorText,
                    armorX,
                    armorY,
                    -2130706433,
                    textShadow,
                    matrix,
                    vertexConsumers,
                    seeThrough ? TextRenderer.TextLayerType.SEE_THROUGH : TextRenderer.TextLayerType.NORMAL,
                    bgColor,
                    light
                );

                if (seeThrough) {
                    this.getTextRenderer().draw(
                        armorText,
                        armorX,
                        armorY,
                        -1,
                        textShadow,
                        matrix,
                        vertexConsumers,
                        TextRenderer.TextLayerType.NORMAL,
                        0,
                        net.minecraft.client.render.LightmapTextureManager.applyEmission(light, 2)
                    );
                }
            }
        }
    }
}
