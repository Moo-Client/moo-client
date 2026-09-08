package com.mooclient.util;

import com.mooclient.module.modules.KeystrokesModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * Dedicated renderer for the Keystrokes HUD widget (WSAD + Space).
 */
public class KeystrokesRenderer {

    public static void render(DrawContext context, MinecraftClient client, int scaledWidth,
            int scaledHeight, float hudScale, boolean customScale, boolean isEditor) {
        if (client == null) {
            return;
        }

        int baseW = KeystrokesModule.getBaseWidth();
        int baseH = KeystrokesModule.getBaseHeight();
        int boxW = Math.round(baseW * hudScale);
        int boxH = Math.round(baseH * hudScale);
        KeystrokesModule.width = boxW;
        KeystrokesModule.height = boxH;

        int startX = KeystrokesModule.position.calculateX(boxW, scaledWidth);
        int startY = KeystrokesModule.position.calculateY(boxH, scaledHeight);

        if (customScale) {
            context.getMatrices().push();
            context.getMatrices().translate(startX, startY, 0);
            context.getMatrices().scale(hudScale, hudScale, 1.0f);
            context.getMatrices().translate(-startX, -startY, 0);
        }

        boolean wPressed = !isEditor && KeystrokesModule.isForwardPressed();
        boolean aPressed = !isEditor && KeystrokesModule.isLeftPressed();
        boolean sPressed = !isEditor && KeystrokesModule.isBackPressed();
        boolean dPressed = !isEditor && KeystrokesModule.isRightPressed();
        boolean spacePressed = !isEditor && KeystrokesModule.isJumpPressed();

        String wText = KeystrokesModule.getForwardLabel();
        String aText = KeystrokesModule.getLeftLabel();
        String sText = KeystrokesModule.getBackLabel();
        String dText = KeystrokesModule.getRightLabel();
        String spaceText = KeystrokesModule.getJumpLabel();

        KeystrokesModule.KeystrokesStyle style = KeystrokesModule.getStyle();
        boolean showBg = KeystrokesModule.isShowBackground();
        boolean textShadow = KeystrokesModule.isTextShadow();
        boolean isBar = KeystrokesModule.getSpaceMode() == KeystrokesModule.SpaceMode.BAR;

        // Row 1: W (centered at 24, size 22x22)
        renderKey(context, client, startX + 24, startY, 22, 22, wText, wPressed, style, showBg, textShadow, false);
        // Row 2: A, S, D (size 22x22 each, gap 2)
        renderKey(context, client, startX, startY + 24, 22, 22, aText, aPressed, style, showBg, textShadow, false);
        renderKey(context, client, startX + 24, startY + 24, 22, 22, sText, sPressed, style, showBg, textShadow, false);
        renderKey(context, client, startX + 48, startY + 24, 22, 22, dText, dPressed, style, showBg, textShadow, false);
        // Row 3: Spacebar (width 70, height 14)
        if (KeystrokesModule.isShowSpace()) {
            renderKey(context, client, startX, startY + 48, 70, 14, spaceText, spacePressed, style, showBg, textShadow, isBar);
        }

        if (customScale) {
            context.getMatrices().pop();
        }
    }

    private static void renderKey(DrawContext context, MinecraftClient client, int x, int y, int w, int h,
            String text, boolean pressed, KeystrokesModule.KeystrokesStyle style, boolean showBg,
            boolean textShadow, boolean isBar) {
        int bgColor;
        int borderColor;
        int textColor;
        int accent = MooClientSettings.getAccentColor();

        if (style == KeystrokesModule.KeystrokesStyle.MOO_CLIENT) {
            if (pressed) {
                bgColor = 0xDDFFFFFF;
                borderColor = 0xFFFFFFFF;
                textColor = 0xFF0A0E17;
            } else {
                bgColor = showBg ? 0x880A0E17 : 0x00000000;
                borderColor = showBg ? 0x33FFFFFF : 0x00000000;
                textColor = 0xFFFFFFFF;
            }
        } else if (style == KeystrokesModule.KeystrokesStyle.ACCENT) {
            if (pressed) {
                bgColor = (accent & 0x00FFFFFF) | 0xDD000000;
                borderColor = 0xFFFFFFFF;
                textColor = 0xFFFFFFFF;
            } else {
                bgColor = showBg ? 0x880A0E17 : 0x00000000;
                borderColor = showBg ? ((accent & 0x00FFFFFF) | 0x44000000) : 0x00000000;
                textColor = 0xFFFFFFFF;
            }
        } else { // SIMPLE
            if (pressed) {
                bgColor = 0xAAFFFFFF;
                borderColor = 0x00000000;
                textColor = 0xFF000000;
            } else {
                bgColor = showBg ? 0x66000000 : 0x00000000;
                borderColor = 0x00000000;
                textColor = 0xFFDDDDDD;
            }
        }

        if (bgColor != 0) {
            context.fill(x, y, x + w, y + h, bgColor);
        }
        if (borderColor != 0) {
            context.fill(x, y, x + w, y + 1, borderColor);
            context.fill(x, y + h - 1, x + w, y + h, borderColor);
            context.fill(x, y + 1, x + 1, y + h - 1, borderColor);
            context.fill(x + w - 1, y + 1, x + w, y + h - 1, borderColor);
        }

        if (isBar) {
            int barW = 32;
            int barH = 2;
            int barX = x + (w - barW) / 2;
            int barY = y + (h - barH) / 2;
            context.fill(barX, barY, barX + barW, barY + barH, textColor);
        } else {
            int tw = client.textRenderer.getWidth(text);
            int tx = x + (w - tw) / 2;
            int ty = y + (h - 9) / 2 + 1;
            boolean shadow = textShadow && (textColor == 0xFFFFFFFF);
            context.drawText(client.textRenderer, text, tx, ty, textColor, shadow);
        }
    }
}
