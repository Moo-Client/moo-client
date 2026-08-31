package com.mooclient.gui;

import com.mooclient.emote.Emote;
import com.mooclient.emote.EmoteEngine;
import com.mooclient.emote.EmoteRegistry;
import com.mooclient.interaction.InteractionEngine;
import com.mooclient.module.modules.EmotesModule;
import com.mooclient.permissions.PermissionManager;
import com.mooclient.util.EmoteWheelConfig;
import com.mooclient.util.MooClientSettings;
import com.mooclient.util.MooLanguage;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * 12-Slot Pixel-Art Emote Wheel matching user reference design 1:1,
 * with clean steel-blue outlines, #1..#12 clock slots, prominent emote titles,
 * multiplayer 👥 badges, and direct interaction trigger.
 */
public class EmoteWheelScreen extends Screen {

    private static final Identifier MOO_LOGO = Identifier.of("mooclient", "textures/gui/icon.png");
    private static final Identifier LOCK_ICON = Identifier.of("mooclient", "textures/gui/emotes/lock.png");

    private int selectedSlot = -1;
    private int triggerKeyCode;
    private boolean isMouseTrigger;

    public EmoteWheelScreen(int triggerKeyCode) {
        this(triggerKeyCode, false);
    }

    public EmoteWheelScreen(int triggerKeyCode, boolean isMouseTrigger) {
        super(Text.literal("Emote Wheel"));
        this.triggerKeyCode = triggerKeyCode;
        this.isMouseTrigger = isMouseTrigger;
    }

    @Override
    protected void init() {
        super.init();
        EmoteWheelConfig.load();
        PermissionManager.fetchLocalPlayerPermissions(true);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0x66000000);
    }

    @Override
    public void renderInGameBackground(DrawContext context) {
        // Transparent in-game
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int cx = this.width / 2;
        int cy = this.height / 2;

        // Top Title
        String mainTitle = MooLanguage.get("emotes_wheel_title");
        int titleW = this.textRenderer.getWidth(mainTitle);
        context.drawTextWithShadow(this.textRenderer, mainTitle, cx - (titleW / 2), cy - 160, 0xFFFFFFFF);

        // Calculate mouse angle & distance
        double dx = mouseX - cx;
        double dy = mouseY - cy;
        double dist = Math.sqrt(dx * dx + dy * dy);

        selectedSlot = -1;
        if (dist > 32 && dist < 155) {
            double rawAngle = Math.toDegrees(Math.atan2(dy, dx)); // -180 to 180
            int slot = Math.floorMod((int) Math.round((rawAngle + 90.0) / 30.0), 12);
            if (EmoteWheelConfig.getSlot(slot) != null) {
                selectedSlot = slot;
            }
        }

        float rIn = 52.0f;
        float rOut = 138.0f;
        int themeBlue = 0xFF6A8EAE;
        int themeBlueHighlight = 0xFF8EAFCE;

        // --- 1. Base Dark Ring Fill ---
        drawDonutFill(context, cx, cy, rIn, rOut, 0xD0111318);

        // --- 2. Render 12 Radial Petals ---
        for (int i = 0; i < 12; i++) {
            boolean isSelected = (i == selectedSlot);
            String emoteId = EmoteWheelConfig.getSlot(i);
            boolean hasEmote = (emoteId != null && !"hands_up".equalsIgnoreCase(emoteId));

            Emote emote = hasEmote ? EmoteRegistry.get(emoteId) : null;
            boolean isUnlocked = emote != null && (emote.isFree() || PermissionManager.hasAccessLocal(emote.getId()));

            double centerDeg = -90.0 + i * 30.0;
            double spanDeg = 28.5;
            double startDeg = centerDeg - (spanDeg / 2.0);
            double endDeg = centerDeg + (spanDeg / 2.0);

            // Highlight Active / Hovered Petal
            if (isSelected && hasEmote) {
                int fillColor = isUnlocked ? 0x992C4862 : 0x99482C32;
                int borderColor = isUnlocked ? themeBlueHighlight : 0xFFFF5555;
                drawCurvedWedge(context, cx, cy, rIn, rOut, startDeg, endDeg, fillColor, borderColor, true);
            }

            double midAngleRad = Math.toRadians(centerDeg);

            // Slot number tag (#1, #2...) near inner ring
            int snRadius = (int) (rIn + 12);
            int snX = (int) (cx + Math.cos(midAngleRad) * snRadius);
            int snY = (int) (cy + Math.sin(midAngleRad) * snRadius);
            String slotNum = "#" + (i + 1);
            int snW = this.textRenderer.getWidth(slotNum);
            context.drawTextWithShadow(this.textRenderer, slotNum, snX - (snW / 2), snY - 4, isSelected ? 0xFFFFFFFF : 0xFFA0B4C8);

            if (hasEmote && emote != null && emote.getAnimation() != null) {
                // Render 2D Pixel-Art Icon
                int iconRadius = (int) (rIn + 42);
                int iconCenterX = (int) (cx + Math.cos(midAngleRad) * iconRadius);
                int iconCenterY = (int) (cy + Math.sin(midAngleRad) * iconRadius);
                int iconSize = isSelected ? 34 : 30;

                if (emote.getIcon() != null) {
                    int ix = iconCenterX - (iconSize / 2);
                    int iy = iconCenterY - (iconSize / 2);
                    context.drawTexture(RenderLayer::getGuiTextured, emote.getIcon(), ix, iy, 0.0f, 0.0f, iconSize, iconSize, iconSize, iconSize);
                }

                // Render Emote Name (with multiplayer badge 👥)
                int nameRadius = (int) (rIn + 68);
                int nameCenterX = (int) (cx + Math.cos(midAngleRad) * nameRadius);
                int nameCenterY = (int) (cy + Math.sin(midAngleRad) * nameRadius);
                String displayName = emote.getDisplayName().toUpperCase();
                if (emote.isMultiplayer()) {
                    displayName = (emote.getParticipantCount() > 2 ? "👥👤 " : "👥 ") + displayName;
                }
                int dnW = this.textRenderer.getWidth(displayName);
                context.drawTextWithShadow(this.textRenderer, displayName, nameCenterX - (dnW / 2), nameCenterY - 4, isUnlocked ? 0xFFFFFFFF : 0xFFAAAAAA);

                // Lock badge overlay
                if (!isUnlocked) {
                    int lockSize = 12;
                    int lockX = iconCenterX + (iconSize / 2) - 6;
                    int lockY = iconCenterY - (iconSize / 2) - 2;
                    context.drawTexture(RenderLayer::getGuiTextured, LOCK_ICON, lockX, lockY, 0.0f, 0.0f, lockSize, lockSize, lockSize, lockSize);
                }
            } else {
                // Subtle '+' for empty slots matching reference layout
                int plusRadius = (int) (rIn + 26);
                int plusX = (int) (cx + Math.cos(midAngleRad) * plusRadius);
                int plusY = (int) (cy + Math.sin(midAngleRad) * plusRadius);
                context.drawTextWithShadow(this.textRenderer, "+", plusX - 3, plusY - 4, 0x667A9EBE);
            }
        }

        // --- 3. Crisp Concentric Boundary Circles ---
        drawCircleOutline(context, cx, cy, (int) rOut, themeBlue);
        drawCircleOutline(context, cx, cy, (int) rIn, themeBlue);

        // --- 4. Central Disc with Moo Cow Logo ---
        int centerRadius = 46;
        drawCircleFill(context, cx, cy, centerRadius, 0xFA0E0E14);
        drawCircleOutline(context, cx, cy, centerRadius, themeBlue);

        int logoSize = 48;
        context.drawTexture(RenderLayer::getGuiTextured, MOO_LOGO, cx - logoSize / 2, cy - logoSize / 2, 0.0f, 0.0f, logoSize, logoSize, logoSize, logoSize);

        // Top-Right "Edit Wheel" Button
        int editBtnX = this.width - 130;
        int editBtnY = 16;
        int editBtnW = 114;
        int editBtnH = 22;
        boolean editHover = mouseX >= editBtnX && mouseX <= editBtnX + editBtnW && mouseY >= editBtnY && mouseY <= editBtnY + editBtnH;

        int editBg = editHover ? 0xDD28283C : 0xAA181824;
        int editBorder = editHover ? MooClientSettings.getAccentColor() : 0x558888AA;
        context.fill(editBtnX, editBtnY, editBtnX + editBtnW, editBtnY + editBtnH, editBg);
        drawRectOutline(context, editBtnX, editBtnY, editBtnW, editBtnH, editBorder);

        String editBtnText = MooLanguage.get("emotes_edit_wheel_btn");
        int ebW = this.textRenderer.getWidth(editBtnText);
        context.drawTextWithShadow(this.textRenderer, editBtnText, editBtnX + (editBtnW - ebW) / 2, editBtnY + 7, editHover ? 0xFFFFFFFF : 0xFFD0D0E0);

        super.render(context, mouseX, mouseY, delta);
    }

    private void drawDonutFill(DrawContext context, int cx, int cy, float rIn, float rOut, int color) {
        int minY = (int) Math.floor(cy - rOut);
        int maxY = (int) Math.ceil(cy + rOut);
        float rInSq = rIn * rIn;
        float rOutSq = rOut * rOut;

        for (int y = minY; y <= maxY; y++) {
            int dy = y - cy;
            int dySq = dy * dy;
            if (dySq > rOutSq) continue;

            int maxDx = (int) Math.sqrt(rOutSq - dySq);
            int minDx = (dySq < rInSq) ? (int) Math.ceil(Math.sqrt(rInSq - dySq)) : 0;

            if (minDx > 0) {
                context.fill(cx - maxDx, y, cx - minDx + 1, y + 1, color);
                context.fill(cx + minDx, y, cx + maxDx + 1, y + 1, color);
            } else {
                context.fill(cx - maxDx, y, cx + maxDx + 1, y + 1, color);
            }
        }
    }

    private void drawCurvedWedge(DrawContext context, int cx, int cy, float rIn, float rOut, double startDeg, double endDeg, int fillColor, int borderColor, boolean isSelected) {
        int minY = (int) Math.floor(cy - rOut);
        int maxY = (int) Math.ceil(cy + rOut);
        float rInSq = rIn * rIn;
        float rOutSq = rOut * rOut;

        for (int y = minY; y <= maxY; y++) {
            int dy = y - cy;
            int dySq = dy * dy;
            if (dySq > rOutSq) continue;

            int maxDx = (int) Math.sqrt(rOutSq - dySq);
            int spanStart = -1;

            for (int dx = -maxDx; dx <= maxDx; dx++) {
                int dSq = dx * dx + dySq;
                if (dSq >= rInSq && dSq <= rOutSq) {
                    double angle = Math.toDegrees(Math.atan2(dy, dx));
                    if (isAngleBetween(angle, startDeg, endDeg)) {
                        if (spanStart == -1) {
                            spanStart = cx + dx;
                        }
                    } else {
                        if (spanStart != -1) {
                            context.fill(spanStart, y, cx + dx, y + 1, fillColor);
                            spanStart = -1;
                        }
                    }
                } else {
                    if (spanStart != -1) {
                        context.fill(spanStart, y, cx + dx, y + 1, fillColor);
                        spanStart = -1;
                    }
                }
            }
            if (spanStart != -1) {
                context.fill(spanStart, y, cx + maxDx + 1, y + 1, fillColor);
            }
        }

        drawArcOutline(context, cx, cy, (int) rOut, startDeg, endDeg, borderColor);
        drawArcOutline(context, cx, cy, (int) rIn, startDeg, endDeg, borderColor);

        double startRad = Math.toRadians(startDeg);
        int sx1 = (int) Math.round(cx + Math.cos(startRad) * rIn);
        int sy1 = (int) Math.round(cy + Math.sin(startRad) * rIn);
        int sx2 = (int) Math.round(cx + Math.cos(startRad) * rOut);
        int sy2 = (int) Math.round(cy + Math.sin(startRad) * rOut);
        drawLine(context, sx1, sy1, sx2, sy2, borderColor);

        double endRad = Math.toRadians(endDeg);
        int ex1 = (int) Math.round(cx + Math.cos(endRad) * rIn);
        int ey1 = (int) Math.round(cy + Math.sin(endRad) * rIn);
        int ex2 = (int) Math.round(cx + Math.cos(endRad) * rOut);
        int ey2 = (int) Math.round(cy + Math.sin(endRad) * rOut);
        drawLine(context, ex1, ey1, ex2, ey2, borderColor);
    }

    private static boolean isAngleBetween(double angle, double start, double end) {
        double nStart = normalizeDeg(start);
        double nEnd = normalizeDeg(end);
        double nAngle = normalizeDeg(angle);

        if (nStart <= nEnd) {
            return nAngle >= nStart && nAngle <= nEnd;
        } else {
            return nAngle >= nStart || nAngle <= nEnd;
        }
    }

    private static double normalizeDeg(double deg) {
        deg = deg % 360.0;
        if (deg < 0) deg += 360.0;
        return deg;
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
        int segments = 48;
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
        int segments = 24;
        double totalSpan = normalizeDeg(endDeg - startDeg);
        if (totalSpan <= 0) totalSpan += 360.0;
        double step = totalSpan / segments;
        for (int i = 0; i < segments; i++) {
            double a1 = Math.toRadians(startDeg + i * step);
            double a2 = Math.toRadians(startDeg + (i + 1) * step);
            int x1 = (int) Math.round(cx + Math.cos(a1) * radius);
            int y1 = (int) Math.round(cy + Math.sin(a1) * radius);
            int x2 = (int) Math.round(cx + Math.cos(a2) * radius);
            int y2 = (int) Math.round(cy + Math.sin(a2) * radius);
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
        if (selectedSlot < 0) return;
        String emoteId = EmoteWheelConfig.getSlot(selectedSlot);
        if (emoteId == null) return;

        Emote emote = EmoteRegistry.get(emoteId);
        if (emote == null || emote.getAnimation() == null) return;

        boolean isUnlocked = emote.isFree() || PermissionManager.hasAccessLocal(emote.getId());
        if (!isUnlocked) {
            if (this.client != null && this.client.player != null) {
                this.client.player.sendMessage(Text.literal("§c" + MooLanguage.get("emotes_store_required")), true);
            }
            return;
        }

        if (emote.isMultiplayer()) {
            if (this.client != null && this.client.isInSingleplayer()) {
                if (this.client.player != null) {
                    this.client.player.sendMessage(Text.literal("§c" + MooLanguage.get("interaction_requires_multiplayer")), true);
                }
                return;
            }
            InteractionEngine.getInstance().initiateInteraction(emote.getId());
        } else {
            EmoteEngine.getInstance().playLocalEmote(emote.getId());
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int editBtnX = this.width - 130;
            int editBtnY = 16;
            if (mouseX >= editBtnX && mouseX <= editBtnX + 114 && mouseY >= editBtnY && mouseY <= editBtnY + 22) {
                if (this.client != null) {
                    this.client.setScreen(new EmoteWheelEditScreen(this));
                }
                return true;
            }

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
