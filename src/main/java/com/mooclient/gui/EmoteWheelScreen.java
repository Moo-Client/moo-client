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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Warframe-inspired Radial Emote Wheel with curved wedge sectors,
 * glowing accent highlight, central Moo Client logo, and pure ASCII/Unicode-safe typography.
 */
public class EmoteWheelScreen extends Screen {

    private static final Identifier MOO_LOGO = Identifier.of("mooclient", "textures/gui/icon.png");

    private int selectedSlot = -1; // 0 = Frontflip, 1 = Backflip, 2 = Stop
    private int triggerKeyCode;
    private boolean isMouseTrigger;

    private static final int SEGMENTS_COUNT = 3;
    // Segment centers: Top-Right (-45°), Top-Left (-135°), Bottom (90°)
    private static final double[] SEGMENT_ANGLES = new double[] { -45.0, -135.0, 90.0 };

    public EmoteWheelScreen(int triggerKeyCode) {
        this(triggerKeyCode, false);
    }

    public EmoteWheelScreen(int triggerKeyCode, boolean isMouseTrigger) {
        super(Text.literal("Emote Wheel"));
        this.triggerKeyCode = triggerKeyCode;
        this.isMouseTrigger = isMouseTrigger;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // Subtle dark cinematic vignette
        context.fill(0, 0, this.width, this.height, 0x66000000);
    }

    @Override
    public void renderInGameBackground(DrawContext context) {
        // Disabled to prevent screen blur
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int cx = this.width / 2;
        int cy = this.height / 2;

        // Calculate mouse angle & distance
        double dx = mouseX - cx;
        double dy = mouseY - cy;
        double dist = Math.sqrt(dx * dx + dy * dy);

        if (dist > 28) {
            double rawAngle = Math.toDegrees(Math.atan2(dy, dx)); // -180 to 180
            // Map angle to 3 Warframe sectors:
            if (rawAngle >= -90 && rawAngle < 20) {
                selectedSlot = 0; // Top-Right: Frontflip
            } else if (rawAngle >= 20 && rawAngle < 160) {
                selectedSlot = 2; // Bottom: Stop
            } else {
                selectedSlot = 1; // Top-Left: Backflip
            }
        } else {
            selectedSlot = -1;
        }

        // --- 1. Render Warframe Wedge Petals ---
        for (int i = 0; i < SEGMENTS_COUNT; i++) {
            boolean isSelected = (i == selectedSlot);
            boolean isUnlocked = com.mooclient.util.EmoteAccessManager.hasAccess(i);
            double centerDeg = SEGMENT_ANGLES[i];
            double spanDeg = 110.0;
            double gapDeg = 8.0;

            double startDeg = centerDeg - (spanDeg / 2.0) + (gapDeg / 2.0);
            double endDeg = centerDeg + (spanDeg / 2.0) - (gapDeg / 2.0);

            float rIn = isSelected ? 48.0f : 52.0f;
            float rOut = isSelected ? 122.0f : 112.0f;

            int accent = MooClientSettings.getAccentColor();
            // Warframe glowing colors (or red/locked tint if not unlocked)
            int fillColor;
            int borderColor;
            if (isUnlocked) {
                fillColor = isSelected ? (accent & 0x00FFFFFF | 0xDD000000) : 0xD012121A;
                borderColor = isSelected ? 0xFFFFFFFF : 0x44777799;
            } else {
                fillColor = isSelected ? 0xDD2A1418 : 0xB0180E10;
                borderColor = isSelected ? 0xFFFF5555 : 0x44663333;
            }

            drawCurvedWedge(context, cx, cy, rIn, rOut, startDeg, endDeg, fillColor, borderColor, isSelected);

            // Text & Labels on each petal (Safe ASCII / Clean Polish / English)
            String label = getSlotLabel(i);
            String iconSymbol = isUnlocked ? getSlotSymbol(i) : "[L]";

            double midAngleRad = Math.toRadians(centerDeg);
            float rMid = (rIn + rOut) / 2.0f;
            int textX = (int) (cx + Math.cos(midAngleRad) * rMid);
            int textY = (int) (cy + Math.sin(midAngleRad) * rMid);

            int textColor = isUnlocked ? (isSelected ? 0xFFFFFFFF : 0xFFD0D0E0) : 0xFFAA7777;

            // Draw Symbol & Label centered
            int symbolW = this.textRenderer.getWidth(iconSymbol);
            int labelW = this.textRenderer.getWidth(label);

            int symbolColor = isUnlocked ? (isSelected ? 0xFF55FFFF : 0xFFAAAAAA) : (isSelected ? 0xFFFF5555 : 0xFF885555);
            context.drawTextWithShadow(this.textRenderer, iconSymbol, textX - (symbolW / 2), textY - 8, symbolColor);
            context.drawTextWithShadow(this.textRenderer, label, textX - (labelW / 2), textY + 3, textColor);
        }

        // --- 2. Central Disc with LARGE Moo Client Logo ---
        int centerRadius = 40;
        int centerBg = 0xFA0E0E14;
        int centerBorder = 0x44FFFFFF; // Subtle dark base border

        drawCircleFill(context, cx, cy, centerRadius, centerBg);
        drawCircleOutline(context, cx, cy, centerRadius, centerBorder);

        // Directional Glow: Highlight ONLY the arc in the direction of the selected item
        if (selectedSlot >= 0) {
            double centerDeg = SEGMENT_ANGLES[selectedSlot];
            double startDeg = centerDeg - 45.0;
            double endDeg = centerDeg + 45.0;
            boolean isUnlocked = com.mooclient.util.EmoteAccessManager.hasAccess(selectedSlot);
            int glowColor = isUnlocked ? MooClientSettings.getAccentColor() : 0xFFFF5555;
            drawArcOutline(context, cx, cy, centerRadius, startDeg, endDeg, glowColor);
            drawArcOutline(context, cx, cy, centerRadius + 1, startDeg, endDeg, glowColor);
        }

        // Large Cow Logo (52x52)
        int logoSize = 52;
        context.drawTexture(RenderLayer::getGuiTextured, MOO_LOGO, cx - logoSize / 2, cy - logoSize / 2, 0.0f, 0.0f, logoSize, logoSize, logoSize, logoSize);

        // Developer Mode Badge in top center
        if (com.mooclient.util.EmoteAccessManager.isLocalPlayerDeveloper()) {
            String devBadge = "[ " + MooLanguage.get("emotes_dev_badge") + " ]";
            int dbW = this.textRenderer.getWidth(devBadge);
            context.drawTextWithShadow(this.textRenderer, devBadge, cx - (dbW / 2), cy - 140, 0xFF55FFFF);
        }

        // --- 3. Warframe Center Title Callout ---
        String selectedTitle = getSelectedTitle();
        if (selectedTitle != null && !selectedTitle.isEmpty()) {
            int titleY = cy + 130;
            int textW = this.textRenderer.getWidth(selectedTitle);
            int pillPadding = 12;
            int pillX = cx - (textW / 2) - pillPadding;
            int pillW = textW + (pillPadding * 2);
            int pillH = 20;

            boolean isUnlocked = selectedSlot < 0 || com.mooclient.util.EmoteAccessManager.hasAccess(selectedSlot);
            int boxBorder = isUnlocked ? MooClientSettings.getAccentColor() : 0xFFFF5555;

            context.fill(pillX, titleY, pillX + pillW, titleY + pillH, 0xEE14141E);
            drawRectOutline(context, pillX, titleY, pillW, pillH, boxBorder);
            context.drawTextWithShadow(this.textRenderer, selectedTitle, cx - (textW / 2), titleY + 6, isUnlocked ? 0xFFFFFFFF : 0xFFFF7777);
        }

        // Bottom usage hint
        String hint = MooLanguage.get("emotes_wheel_hint");
        int hintW = this.textRenderer.getWidth(hint);
        context.drawTextWithShadow(this.textRenderer, hint, cx - (hintW / 2), this.height - 30, 0xFFA0A0AB);

        super.render(context, mouseX, mouseY, delta);
    }

    private String getSlotLabel(int slot) {
        return switch (slot) {
            case 0 -> MooLanguage.get("emotes_wheel_frontflip");
            case 1 -> MooLanguage.get("emotes_wheel_backflip");
            case 2 -> MooLanguage.get("emotes_wheel_stop");
            default -> "";
        };
    }

    private String getSlotSymbol(int slot) {
        return switch (slot) {
            case 0 -> ">>";  // Frontflip
            case 1 -> "<<";  // Backflip
            case 2 -> "[X]"; // Stop
            default -> "";
        };
    }

    private String getSelectedTitle() {
        if (selectedSlot < 0) return null;
        if (!com.mooclient.util.EmoteAccessManager.hasAccess(selectedSlot)) {
            return "> " + MooLanguage.get("emotes_store_required").toUpperCase() + " <";
        }
        return switch (selectedSlot) {
            case 0 -> "> " + MooLanguage.get("emotes_wheel_frontflip").toUpperCase() + " <";
            case 1 -> "> " + MooLanguage.get("emotes_wheel_backflip").toUpperCase() + " <";
            case 2 -> "> " + MooLanguage.get("emotes_wheel_stop").toUpperCase() + " <";
            default -> null;
        };
    }

    private static final int MAX_STEPS = 16;
    private final int[] outerX = new int[MAX_STEPS + 1];
    private final int[] outerY = new int[MAX_STEPS + 1];
    private final int[] innerX = new int[MAX_STEPS + 1];
    private final int[] innerY = new int[MAX_STEPS + 1];

    /**
     * Renders a curved Warframe sector with zero-allocation math for peak FPS.
     */
    private void drawCurvedWedge(DrawContext context, int cx, int cy, float rIn, float rOut, double startDeg, double endDeg, int fillColor, int borderColor, boolean isSelected) {
        int steps = 12;
        double stepSize = (endDeg - startDeg) / steps;

        for (int i = 0; i <= steps; i++) {
            double rad = Math.toRadians(startDeg + i * stepSize);
            double cos = Math.cos(rad);
            double sin = Math.sin(rad);
            outerX[i] = (int) (cx + cos * rOut);
            outerY[i] = (int) (cy + sin * rOut);
            innerX[i] = (int) (cx + cos * rIn);
            innerY[i] = (int) (cy + sin * rIn);
        }

        // Fill sub-quads without creating garbage collection objects
        for (int i = 0; i < steps; i++) {
            fillQuad(context, outerX[i], outerY[i], outerX[i + 1], outerY[i + 1], innerX[i + 1], innerY[i + 1], innerX[i], innerY[i], fillColor);
        }

        // Draw boundary outlines
        for (int i = 0; i < steps; i++) {
            drawLine(context, outerX[i], outerY[i], outerX[i + 1], outerY[i + 1], borderColor);
            drawLine(context, innerX[i], innerY[i], innerX[i + 1], innerY[i + 1], borderColor);
        }
        // Side straight lines
        drawLine(context, innerX[0], innerY[0], outerX[0], outerY[0], borderColor);
        drawLine(context, innerX[steps], innerY[steps], outerX[steps], outerY[steps], borderColor);

        // Warframe active pointer notch in center
        if (isSelected) {
            double midRad = Math.toRadians((startDeg + endDeg) / 2.0);
            int tipX = (int) (cx + Math.cos(midRad) * (rIn - 6));
            int tipY = (int) (cy + Math.sin(midRad) * (rIn - 6));
            int b1X = (int) (cx + Math.cos(midRad - 0.08) * rIn);
            int b1Y = (int) (cy + Math.sin(midRad - 0.08) * rIn);
            int b2X = (int) (cx + Math.cos(midRad + 0.08) * rIn);
            int b2Y = (int) (cy + Math.sin(midRad + 0.08) * rIn);

            fillQuad(context, tipX, tipY, b1X, b1Y, b2X, b2Y, tipX, tipY, borderColor);
            drawLine(context, b1X, b1Y, tipX, tipY, 0xFFFFFFFF);
            drawLine(context, b2X, b2Y, tipX, tipY, 0xFFFFFFFF);
        }
    }

    private void fillQuad(DrawContext context, int x1, int y1, int x2, int y2, int x3, int y3, int x4, int y4, int color) {
        int minY = Math.min(Math.min(y1, y2), Math.min(y3, y4));
        int maxY = Math.max(Math.max(y1, y2), Math.max(y3, y4));

        int e0x1 = x1, e0y1 = y1, e0x2 = x2, e0y2 = y2;
        int e1x1 = x2, e1y1 = y2, e1x2 = x3, e1y2 = y3;
        int e2x1 = x3, e2y1 = y3, e2x2 = x4, e2y2 = y4;
        int e3x1 = x4, e3y1 = y4, e3x2 = x1, e3y2 = y1;

        for (int y = minY; y <= maxY; y++) {
            int minX = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int count = 0;

            if ((e0y1 <= y && e0y2 > y) || (e0y2 <= y && e0y1 > y)) {
                int x = e0x1 + (y - e0y1) * (e0x2 - e0x1) / (e0y2 - e0y1);
                if (x < minX) minX = x;
                if (x > maxX) maxX = x;
                count++;
            }
            if ((e1y1 <= y && e1y2 > y) || (e1y2 <= y && e1y1 > y)) {
                int x = e1x1 + (y - e1y1) * (e1x2 - e1x1) / (e1y2 - e1y1);
                if (x < minX) minX = x;
                if (x > maxX) maxX = x;
                count++;
            }
            if ((e2y1 <= y && e2y2 > y) || (e2y2 <= y && e2y1 > y)) {
                int x = e2x1 + (y - e2y1) * (e2x2 - e2x1) / (e2y2 - e2y1);
                if (x < minX) minX = x;
                if (x > maxX) maxX = x;
                count++;
            }
            if ((e3y1 <= y && e3y2 > y) || (e3y2 <= y && e3y1 > y)) {
                int x = e3x1 + (y - e3y1) * (e3x2 - e3x1) / (e3y2 - e3y1);
                if (x < minX) minX = x;
                if (x > maxX) maxX = x;
                count++;
            }

            if (count >= 2 && minX <= maxX) {
                context.fill(minX, y, maxX + 1, y + 1, color);
            }
        }
    }

    private void drawLine(DrawContext context, int x1, int y1, int x2, int y2, int color) {
        int dx = Math.abs(x2 - x1);
        int dy = Math.abs(y2 - y1);
        int sx = x1 < x2 ? 1 : -1;
        int sy = y1 < y2 ? 1 : -1;
        int err = dx - dy;

        int x = x1;
        int y = y1;
        while (true) {
            context.fill(x, y, x + 1, y + 1, color);
            if (x == x2 && y == y2) break;
            int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                x += sx;
            }
            if (e2 < dx) {
                err += dx;
                y += sy;
            }
        }
    }

    private void drawCircleFill(DrawContext context, int cx, int cy, int radius, int color) {
        for (int y = -radius; y <= radius; y++) {
            int dx = (int) Math.sqrt(radius * radius - y * y);
            context.fill(cx - dx, cy + y, cx + dx, cy + y + 1, color);
        }
    }

    private void drawCircleOutline(DrawContext context, int cx, int cy, int radius, int color) {
        int segments = 36;
        double step = (2 * Math.PI) / segments;
        for (int i = 0; i < segments; i++) {
            double a1 = i * step;
            double a2 = (i + 1) * step;
            int x1 = (int) (cx + Math.cos(a1) * radius);
            int y1 = (int) (cy + Math.sin(a1) * radius);
            int x2 = (int) (cx + Math.cos(a2) * radius);
            int y2 = (int) (cy + Math.sin(a2) * radius);
            drawLine(context, x1, y1, x2, y2, color);
        }
    }

    private void drawArcOutline(DrawContext context, int cx, int cy, int radius, double startDeg, double endDeg, int color) {
        int segments = 16;
        double step = (endDeg - startDeg) / segments;
        for (int i = 0; i < segments; i++) {
            double a1 = Math.toRadians(startDeg + i * step);
            double a2 = Math.toRadians(startDeg + (i + 1) * step);
            int x1 = (int) (cx + Math.cos(a1) * radius);
            int y1 = (int) (cy + Math.sin(a1) * radius);
            int x2 = (int) (cx + Math.cos(a2) * radius);
            int y2 = (int) (cy + Math.sin(a2) * radius);
            drawLine(context, x1, y1, x2, y2, color);
        }
    }

    private void drawRectOutline(DrawContext context, int x, int y, int w, int h, int color) {
        context.fill(x, y, x + w, y + 1, color);
        context.fill(x, y + h - 1, x + w, y + h, color);
        context.fill(x, y + 1, x + 1, y + h - 1, color);
        context.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    private void executeAction() {
        if (!com.mooclient.util.EmoteAccessManager.hasAccess(selectedSlot)) {
            if (this.client != null && this.client.player != null) {
                this.client.player.sendMessage(Text.literal("§c[Moo Client] " + MooLanguage.get("emotes_store_required")), true);
            }
            return;
        }

        switch (selectedSlot) {
            case 0 -> EmotesModule.triggerFrontflipFromWheel();
            case 1 -> EmotesModule.triggerBackflipFromWheel();
            case 2 -> EmotesModule.stopEmotesFromWheel();
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (selectedSlot >= 0) {
                executeAction();
            }
            this.close();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (this.isMouseTrigger && button == this.triggerKeyCode) {
            if (selectedSlot >= 0) {
                executeAction();
            }
            this.close();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (!this.isMouseTrigger && (keyCode == this.triggerKeyCode || keyCode == EmotesModule.getWheelKeyCode() || keyCode == GLFW.GLFW_KEY_B)) {
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
