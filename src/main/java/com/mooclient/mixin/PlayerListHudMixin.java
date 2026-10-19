package com.mooclient.mixin;

import com.mooclient.module.modules.NametagsModule;
import com.mooclient.util.MooUserManager;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Renders the Moo Client cow logo badge right before player nicknames on the Tab list,
 * and expands the column width so the ping latency icon does not overlap the player nickname.
 * Output: [Head] [Cow Logo] Nickname [Ping Signal Bars]
 */
@Mixin(PlayerListHud.class)
public class PlayerListHudMixin {

    private static final Identifier MOO_LOGO = Identifier.of("mooclient", "textures/gui/icon.png");

    @Redirect(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/font/TextRenderer;getWidth(Lnet/minecraft/text/StringVisitable;)I"
        )
    )
    private int mooClient$expandColumnWidthForLogo(TextRenderer textRenderer, StringVisitable text) {
        int width = textRenderer.getWidth(text);
        if (text != null && NametagsModule.isNametagsEnabled() && NametagsModule.isShowLogo()) {
            String raw = text.getString();
            if (raw != null && !raw.isEmpty() && MooUserManager.isMooUser(raw, -1)) {
                // Reserve +10px in the tab list column for the cow logo
                return width + 10;
            }
        }
        return width;
    }

//? if >=1.21.11 {
    @Redirect(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/DrawContext;drawTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;III)V"
        )
    )
    private void mooClient$renderPlayerNameWithLogo(DrawContext context, TextRenderer textRenderer, Text text, int x, int y, int color) {
        if (text != null && NametagsModule.isNametagsEnabled() && NametagsModule.isShowLogo()) {
            String rawText = text.getString();
            if (rawText != null && !rawText.isEmpty() && MooUserManager.isMooUser(rawText, -1)) {
                // Draw 8x8 cow logo badge before player nickname
                context.drawTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED, MOO_LOGO, x, y, 0.0f, 0.0f, 8, 8, 8, 8);
                // Draw player nickname shifted right by 10px
                context.drawTextWithShadow(textRenderer, text, x + 10, y, color);
                return;
            }
        }
        context.drawTextWithShadow(textRenderer, text, x, y, color);
    }
//?} else {
    /*@Redirect(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/DrawContext;drawTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;III)I"
        )
    )
    private int mooClient$renderPlayerNameWithLogo(DrawContext context, TextRenderer textRenderer, Text text, int x, int y, int color) {
        if (text != null && NametagsModule.isNametagsEnabled() && NametagsModule.isShowLogo()) {
            String rawText = text.getString();
            if (rawText != null && !rawText.isEmpty() && MooUserManager.isMooUser(rawText, -1)) {
                // Draw 8x8 cow logo badge before player nickname
                context.drawTexture(RenderLayer::getGuiTextured, MOO_LOGO, x, y, 0.0f, 0.0f, 8, 8, 8, 8);
                // Draw player nickname shifted right by 10px
                return context.drawTextWithShadow(textRenderer, text, x + 10, y, color);
            }
        }
        return context.drawTextWithShadow(textRenderer, text, x, y, color);
    }
*///?}
}
