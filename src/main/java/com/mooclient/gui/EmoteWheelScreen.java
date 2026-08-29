package com.mooclient.gui;

import com.mooclient.module.modules.EmotesModule;
import com.mooclient.util.MooClientSettings;
import com.mooclient.util.MooLanguage;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * Modern Radial Emote Wheel with prominent central Moo Client Logo,
 * glassmorphism cards, and instant directional selection.
 */
public class EmoteWheelScreen extends Screen {

    private static final Identifier MOO_LOGO = Identifier.of("mooclient", "textures/gui/icon.png");

    private int selectedSlot = -1; // 0 = Hands Up, 1 = Frontflip, 2 = Stop, 3 = Backflip
    private int triggerKeyCode;

    public EmoteWheelScreen(int triggerKeyCode) {
        super(Text.literal("Emote Wheel"));
        this.triggerKeyCode = triggerKeyCode;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // Transparent overlay to keep the game visible
        context.fill(0, 0, this.width, this.height, 0x66000000);
    }

    @Override
    public void renderInGameBackground(DrawContext context) {
        // Disabled to prevent vanilla screen blur
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int cx = this.width / 2;
        int cy = this.height / 2;

        // Determine mouse distance and angle from center
        double dx = mouseX - cx;
        double dy = mouseY - cy;
        double dist = Math.sqrt(dx * dx + dy * dy);

        if (dist > 30) {
            double angle = Math.toDegrees(Math.atan2(dy, dx)); // -180 to 180
            if (angle >= -135 && angle < -45) {
                selectedSlot = 0; // Top: Hands Up
            } else if (angle >= -45 && angle < 45) {
                selectedSlot = 1; // Right: Frontflip
            } else if (angle >= 45 && angle < 135) {
                selectedSlot = 2; // Bottom: Stop
            } else {
                selectedSlot = 3; // Left: Backflip
            }
        } else {
            selectedSlot = -1;
        }

        // Draw connecting outer guideline ring
        drawOuterRing(context, cx, cy, 88);

        // Render 4 Radial Action Cards
        renderEmoteCard(context, cx, cy - 88, 0, "🙋‍♂️", MooLanguage.get("emotes_wheel_hands_up"), selectedSlot == 0);
        renderEmoteCard(context, cx + 96, cy, 1, "🤸‍♂️", MooLanguage.get("emotes_wheel_frontflip"), selectedSlot == 1);
        renderEmoteCard(context, cx, cy + 88, 2, "🛑", MooLanguage.get("emotes_wheel_stop"), selectedSlot == 2);
        renderEmoteCard(context, cx - 96, cy, 3, "🤸‍♀️", MooLanguage.get("emotes_wheel_backflip"), selectedSlot == 3);

        // --- Central Disc with Large Moo Client Logo ---
        int centerRadius = 38;
        int centerBg = 0xEE12121A;
        int centerBorder = (selectedSlot >= 0) ? MooClientSettings.getAccentColor() : 0x66FFFFFF;

        // Center background fill & border
        context.fill(cx - centerRadius, cy - centerRadius, cx + centerRadius, cy + centerRadius, centerBg);
        drawRoundedBorder(context, cx - centerRadius, cy - centerRadius, centerRadius * 2, centerRadius * 2, centerBorder);

        // Large Cow Logo in center (48x48)
        int logoSize = 48;
        context.drawTexture(RenderLayer::getGuiTextured, MOO_LOGO, cx - logoSize / 2, cy - logoSize / 2, 0.0f, 0.0f, logoSize, logoSize, logoSize, logoSize);

        // --- Active Selection Header Text ---
        String selectedTitle = getSelectedTitle();
        if (selectedTitle != null && !selectedTitle.isEmpty()) {
            int titleY = cy - 130;
            int textW = this.textRenderer.getWidth(selectedTitle);
            int pillPadding = 12;
            int pillX = cx - (textW / 2) - pillPadding;
            int pillW = textW + (pillPadding * 2);
            int pillH = 20;

            context.fill(pillX, titleY - 4, pillX + pillW, titleY - 4 + pillH, 0xEE161622);
            drawRoundedBorder(context, pillX, titleY - 4, pillW, pillH, MooClientSettings.getAccentColor());
            context.drawTextWithShadow(this.textRenderer, selectedTitle, cx - (textW / 2), titleY + 2, 0xFFFFFFFF);
        }

        // Bottom usage hint
        String hint = MooLanguage.get("emotes_wheel_hint");
        int hintW = this.textRenderer.getWidth(hint);
        context.drawTextWithShadow(this.textRenderer, hint, cx - (hintW / 2), this.height - 35, 0xFFA0A0AB);

        super.render(context, mouseX, mouseY, delta);
    }

    private void renderEmoteCard(DrawContext context, int x, int y, int slot, String icon, String title, boolean isSelected) {
        int cardW = 120;
        int cardH = 34;
        int cardX = x - (cardW / 2);
        int cardY = y - (cardH / 2);

        int accentColor = MooClientSettings.getAccentColor();
        int bg = isSelected ? (accentColor & 0x00FFFFFF | 0xDD000000) : 0xDD161622;
        int border = isSelected ? 0xFFFFFFFF : 0x44FFFFFF;
        int textColor = isSelected ? 0xFF0A2514 : 0xFFFFFFFF;

        // Card body & border
        context.fill(cardX, cardY, cardX + cardW, cardY + cardH, bg);
        drawRoundedBorder(context, cardX, cardY, cardW, cardH, border);

        // Icon + Label
        String fullText = icon + " " + title;
        int tw = this.textRenderer.getWidth(fullText);
        context.drawTextWithShadow(this.textRenderer, fullText, cardX + (cardW - tw) / 2, cardY + 13, isSelected ? 0xFFFFFFFF : textColor);
    }

    private void drawOuterRing(DrawContext context, int cx, int cy, int radius) {
        int segments = 32;
        double step = (2 * Math.PI) / segments;
        for (int i = 0; i < segments; i++) {
            double a1 = i * step;
            double a2 = (i + 1) * step;
            int x1 = (int) (cx + Math.cos(a1) * radius);
            int y1 = (int) (cy + Math.sin(a1) * radius);
            int x2 = (int) (cx + Math.cos(a2) * radius);
            int y2 = (int) (cy + Math.sin(a2) * radius);
            context.fill(x1, y1, x1 + 1, y1 + 1, 0x33FFFFFF);
        }
    }

    private void drawRoundedBorder(DrawContext context, int x, int y, int w, int h, int color) {
        context.fill(x, y, x + w, y + 1, color);
        context.fill(x, y + h - 1, x + w, y + h, color);
        context.fill(x, y + 1, x + 1, y + h - 1, color);
        context.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    private String getSelectedTitle() {
        return switch (selectedSlot) {
            case 0 -> "» " + MooLanguage.get("emotes_wheel_hands_up").toUpperCase() + " «";
            case 1 -> "» " + MooLanguage.get("emotes_wheel_frontflip").toUpperCase() + " «";
            case 2 -> "» " + MooLanguage.get("emotes_wheel_stop").toUpperCase() + " «";
            case 3 -> "» " + MooLanguage.get("emotes_wheel_backflip").toUpperCase() + " «";
            default -> null;
        };
    }

    private void executeAction() {
        switch (selectedSlot) {
            case 0 -> EmotesModule.toggleHandsUp();
            case 1 -> EmotesModule.triggerFrontflip();
            case 2 -> {
                EmotesModule.setHandsUp(false);
            }
            case 3 -> EmotesModule.triggerBackflip();
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) { // Left click
            if (selectedSlot >= 0) {
                executeAction();
            }
            this.close();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (keyCode == this.triggerKeyCode || keyCode == EmotesModule.getWheelKeyCode() || keyCode == GLFW.GLFW_KEY_B) {
            if (selectedSlot >= 0) {
                executeAction();
            }
            this.close();
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
