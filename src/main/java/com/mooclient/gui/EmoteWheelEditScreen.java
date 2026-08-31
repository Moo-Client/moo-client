package com.mooclient.gui;

import com.mooclient.emote.Emote;
import com.mooclient.emote.EmoteRegistry;
import com.mooclient.module.modules.EmotesModule;
import com.mooclient.permissions.PermissionManager;
import com.mooclient.util.EmoteWheelConfig;
import com.mooclient.util.MooClientSettings;
import com.mooclient.util.MooConfig;
import com.mooclient.util.MooLanguage;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Interaktywny edytor 12-slotowego koła emotek drag & drop wraz z pełnym panelem ustawień emotek.
 * Zawiera konfigurację slotów koła, skrótu klawiszowego, trybu HOLD/TOGGLE oraz opcji powrotu kamery.
 */
public class EmoteWheelEditScreen extends Screen {

    private static final Identifier MOO_LOGO = Identifier.of("mooclient", "textures/gui/icon.png");
    private static final Identifier LOCK_ICON = Identifier.of("mooclient", "textures/gui/emotes/lock.png");

    private final Screen parentScreen;
    private final String[] workingSlots = new String[EmoteWheelConfig.TOTAL_SLOTS];

    // Drag & Drop State
    private String draggingEmoteId = null;
    private int dragSourceSlotIndex = -1;
    private int hoveredWheelSlot = -1;
    private boolean hoveredTrashZone = false;
    private String hoveredDockEmoteId = null;
    private int hoveredDeleteSlot = -1;

    // Dock Horizontal Scrolling State
    private float dockScrollOffset = 0.0f;
    private float targetDockScrollOffset = 0.0f;

    public EmoteWheelEditScreen(Screen parentScreen) {
        super(Text.literal("Emote Wheel Editor"));
        this.parentScreen = parentScreen;
    }

    @Override
    protected void init() {
        super.init();
        EmoteWheelConfig.load();
        PermissionManager.fetchLocalPlayerPermissions(true);
        for (int i = 0; i < EmoteWheelConfig.TOTAL_SLOTS; i++) {
            String slot = EmoteWheelConfig.getSlot(i);
            workingSlots[i] = "hands_up".equalsIgnoreCase(slot) ? null : slot;
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0xDD0C0C12);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int cx = this.width / 2;
        int cy = this.height / 2 - 25;

        // --- 1. Header ---
        String title = MooLanguage.get("emotes_editor_title");
        String subtitle = MooLanguage.get("emotes_editor_subtitle");

        int titleW = this.textRenderer.getWidth(title);
        int subW = this.textRenderer.getWidth(subtitle);

        context.drawTextWithShadow(this.textRenderer, title, cx - (titleW / 2), 16, 0xFFFFFFFF);
        context.drawTextWithShadow(this.textRenderer, subtitle, cx - (subW / 2), 28, 0xFFA0A0B0);

        // Top-Right Action Buttons: [ ↺ Domyślne ] and [ ✔ Zapisz i zamknij ]
        int saveBtnX = this.width - 150;
        int saveBtnY = 16;
        int saveBtnW = 135;
        int saveBtnH = 24;
        boolean saveHover = mouseX >= saveBtnX && mouseX <= saveBtnX + saveBtnW && mouseY >= saveBtnY && mouseY <= saveBtnY + saveBtnH;
        int saveBg = saveHover ? 0xFF33AA55 : 0xDD227740;
        context.fill(saveBtnX, saveBtnY, saveBtnX + saveBtnW, saveBtnY + saveBtnH, saveBg);
        drawRectOutline(context, saveBtnX, saveBtnY, saveBtnW, saveBtnH, 0xFFFFFFFF);
        String saveText = MooLanguage.get("emotes_save_btn");
        int saveTextW = this.textRenderer.getWidth(saveText);
        context.drawTextWithShadow(this.textRenderer, saveText, saveBtnX + (saveBtnW - saveTextW) / 2, saveBtnY + 8, 0xFFFFFFFF);

        int resetBtnX = this.width - 270;
        int resetBtnY = 16;
        int resetBtnW = 110;
        int resetBtnH = 24;
        boolean resetHover = mouseX >= resetBtnX && mouseX <= resetBtnX + resetBtnW && mouseY >= resetBtnY && mouseY <= resetBtnY + resetBtnH;
        int resetBg = resetHover ? 0xFF555566 : 0xAA333344;
        context.fill(resetBtnX, resetBtnY, resetBtnX + resetBtnW, resetBtnY + resetBtnH, resetBg);
        drawRectOutline(context, resetBtnX, resetBtnY, resetBtnW, resetBtnH, 0x88AAAAAA);
        String resetText = MooLanguage.get("emotes_reset_btn");
        int resetTextW = this.textRenderer.getWidth(resetText);
        context.drawTextWithShadow(this.textRenderer, resetText, resetBtnX + (resetBtnW - resetTextW) / 2, resetBtnY + 8, 0xFFD0D0D0);

        // --- 2a. Trash / Remove Zone (Left Side) ---
        int trashX = 25;
        int trashY = cy - 45;
        int trashW = 150;
        int trashH = 90;
        hoveredTrashZone = mouseX >= trashX && mouseX <= trashX + trashW && mouseY >= trashY && mouseY <= trashY + trashH;

        int trashBg = hoveredTrashZone ? 0xDD661A22 : (draggingEmoteId != null ? 0x99441418 : 0x772A1418);
        int trashBorder = hoveredTrashZone ? 0xFFFF4444 : (draggingEmoteId != null ? 0xFFAA4444 : 0x66AA4444);
        context.fill(trashX, trashY, trashX + trashW, trashY + trashH, trashBg);
        drawRectOutline(context, trashX, trashY, trashW, trashH, trashBorder);

        String trashText1 = MooLanguage.get("emotes_trash_title");
        String trashText2 = MooLanguage.get("emotes_trash_subtitle");
        int t1W = this.textRenderer.getWidth(trashText1);
        int t2W = this.textRenderer.getWidth(trashText2);
        context.drawTextWithShadow(this.textRenderer, trashText1, trashX + (trashW - t1W) / 2, trashY + 28, hoveredTrashZone ? 0xFFFF6666 : 0xFFEE8888);
        context.drawTextWithShadow(this.textRenderer, trashText2, trashX + (trashW - t2W) / 2, trashY + 48, 0xFFAA7777);

        // --- 3. 12 Fixed Radial Positions Rendering ---
        double dx = mouseX - cx;
        double dy = mouseY - cy;
        double dist = Math.sqrt(dx * dx + dy * dy);

        hoveredWheelSlot = -1;
        hoveredDeleteSlot = -1;

        if (dist > 30 && dist < 145) {
            double rawAngle = Math.toDegrees(Math.atan2(dy, dx));
            hoveredWheelSlot = Math.floorMod((int) Math.round((rawAngle + 90.0) / 30.0), 12);
        }

        for (int i = 0; i < 12; i++) {
            boolean isSlotHovered = (i == hoveredWheelSlot);
            String emoteId = workingSlots[i];
            boolean hasEmote = (emoteId != null && EmoteRegistry.has(emoteId));

            Emote emote = hasEmote ? EmoteRegistry.get(emoteId) : null;
            boolean isUnlocked = emote != null && (emote.isFree() || PermissionManager.hasAccessLocal(emote.getId()));

            double centerDeg = -90.0 + i * 30.0;
            double spanDeg = 26.0;
            double gapDeg = 3.5;

            double startDeg = centerDeg - (spanDeg / 2.0) + (gapDeg / 2.0);
            double endDeg = centerDeg + (spanDeg / 2.0) - (gapDeg / 2.0);

            float rIn = isSlotHovered ? 46.0f : 50.0f;
            float rOut = isSlotHovered ? 130.0f : 120.0f;

            double midAngleRad = Math.toRadians(centerDeg);
            float rMid = (rIn + rOut) / 2.0f;
            int iconCenterX = (int) (cx + Math.cos(midAngleRad) * rMid);
            int iconCenterY = (int) (cy + Math.sin(midAngleRad) * rMid);

            int accent = MooClientSettings.getAccentColor();
            int fillColor;
            int borderColor;

            if (hasEmote) {
                if (isSlotHovered) {
                    fillColor = accent & 0x00FFFFFF | 0xEE000000;
                    borderColor = 0xFFFFFFFF;
                } else if (isUnlocked) {
                    fillColor = 0xD012121A;
                    borderColor = 0x55777799;
                } else {
                    fillColor = 0xB0180E10;
                    borderColor = 0x66663333;
                }
            } else {
                if (isSlotHovered) {
                    fillColor = 0x88225533;
                    borderColor = 0xFF55FF88;
                } else {
                    fillColor = 0x220A0A10;
                    borderColor = 0x33444466;
                }
            }

            drawCurvedWedge(context, cx, cy, rIn, rOut, startDeg, endDeg, fillColor, borderColor, isSlotHovered);

            if (hasEmote && emote != null) {
                int iconSize = isSlotHovered ? 34 : 30;
                if (emote.getIcon() != null) {
                    int ix = iconCenterX - (iconSize / 2);
                    int iy = iconCenterY - (iconSize / 2);
                    context.drawTexture(RenderLayer::getGuiTextured, emote.getIcon(), ix, iy, 0.0f, 0.0f, iconSize, iconSize, iconSize, iconSize);
                }

                if (!isUnlocked) {
                    int lockSize = 12;
                    int lockX = iconCenterX + (iconSize / 2) - 6;
                    int lockY = iconCenterY - (iconSize / 2) - 2;
                    context.drawTexture(RenderLayer::getGuiTextured, LOCK_ICON, lockX, lockY, 0.0f, 0.0f, lockSize, lockSize, lockSize, lockSize);
                }

                String displayName = emote.getDisplayName().toUpperCase();
                if (emote.isMultiplayer()) {
                    displayName = (emote.getParticipantCount() > 2 ? "👥👤 " : "👥 ") + displayName;
                }
                int dnW = this.textRenderer.getWidth(displayName);
                context.drawTextWithShadow(this.textRenderer, displayName, iconCenterX - (dnW / 2), (int) (cy + Math.sin(midAngleRad) * (rOut - 10)), 0xFFFFFFFF);

                String slotNum = "#" + (i + 1);
                int snW = this.textRenderer.getWidth(slotNum);
                context.drawTextWithShadow(this.textRenderer, slotNum, (int) (cx + Math.cos(midAngleRad) * (rIn + 12)) - (snW / 2), (int) (cy + Math.sin(midAngleRad) * (rIn + 12)) - 4, 0xFFA0B4C8);

                if (isSlotHovered && draggingEmoteId == null) {
                    int delX = (int) (cx + Math.cos(midAngleRad + 0.18) * (rOut - 10));
                    int delY = (int) (cy + Math.sin(midAngleRad + 0.18) * (rOut - 10));
                    boolean delHover = (mouseX >= delX - 7 && mouseX <= delX + 7 && mouseY >= delY - 7 && mouseY <= delY + 7);
                    if (delHover) {
                        hoveredDeleteSlot = i;
                    }
                    context.fill(delX - 7, delY - 7, delX + 7, delY + 7, delHover ? 0xFFFF3333 : 0xCCAA2222);
                    drawRectOutline(context, delX - 7, delY - 7, 14, 14, 0xFFFFFFFF);
                    context.drawTextWithShadow(this.textRenderer, "✕", delX - 3, delY - 4, 0xFFFFFFFF);
                }
            } else {
                String slotNum = "#" + (i + 1);
                int snW = this.textRenderer.getWidth(slotNum);
                context.drawTextWithShadow(this.textRenderer, slotNum, iconCenterX - (snW / 2), iconCenterY - 8, isSlotHovered ? 0xFF55FF88 : 0xFF667788);

                String plusSymbol = "+";
                context.drawTextWithShadow(this.textRenderer, plusSymbol, iconCenterX - 3, iconCenterY + 4, isSlotHovered ? 0xFFFFFFFF : 0xFF445566);
            }
        }

        // Center Cow Disc
        int centerRadius = 46;
        drawCircleFill(context, cx, cy, centerRadius, 0xFA0E0E14);
        drawCircleOutline(context, cx, cy, centerRadius, 0xFF6A8EAE);
        int logoSize = 48;
        context.drawTexture(RenderLayer::getGuiTextured, MOO_LOGO, cx - logoSize / 2, cy - logoSize / 2, 0.0f, 0.0f, logoSize, logoSize, logoSize, logoSize);

        // Center Info Callout
        if (hoveredWheelSlot >= 0) {
            String calloutText;
            String emoteId = workingSlots[hoveredWheelSlot];
            if (emoteId != null) {
                Emote emote = EmoteRegistry.get(emoteId);
                calloutText = (emote != null) ? MooLanguage.get("emotes_slot_prefix") + (hoveredWheelSlot + 1) + ": " + emote.getDisplayName().toUpperCase() : "";
            } else {
                calloutText = MooLanguage.get("emotes_drop_in_slot") + (hoveredWheelSlot + 1);
            }

            if (!calloutText.isEmpty()) {
                int nW = this.textRenderer.getWidth(calloutText);
                int pillPadding = 12;
                int pillX = cx - (nW / 2) - pillPadding;
                int pillY = cy + 125;
                int pillW = nW + (pillPadding * 2);
                int pillH = 20;

                context.fill(pillX, pillY, pillX + pillW, pillY + pillH, 0xEE14141E);
                drawRectOutline(context, pillX, pillY, pillW, pillH, MooClientSettings.getAccentColor());
                context.drawTextWithShadow(this.textRenderer, calloutText, cx - (nW / 2), pillY + 6, 0xFFFFFFFF);
            }
        }

        // --- 4. Available Emotes Dock (Bottom Panel) ---
        int dockH = 86;
        int dockY = this.height - dockH - 12;
        int dockW = Math.min(560, this.width - 40);
        int dockX = cx - (dockW / 2);

        context.fill(dockX, dockY, dockX + dockW, dockY + dockH, 0xEE111118);
        drawRectOutline(context, dockX, dockY, dockW, dockH, 0x55555577);

        String dockTitle = MooLanguage.get("emotes_dock_title");
        context.drawTextWithShadow(this.textRenderer, dockTitle, dockX + 16, dockY + 8, 0xFFE0E0EE);

        List<Emote> availableEmotes = new ArrayList<>();
        List<String> equippedList = Arrays.asList(workingSlots);
        for (Emote emote : EmoteRegistry.getAll()) {
            if ("hands_up".equalsIgnoreCase(emote.getId())) continue;
            if (!equippedList.contains(emote.getId())) {
                availableEmotes.add(emote);
            }
        }

        hoveredDockEmoteId = null;

        if (availableEmotes.isEmpty()) {
            String allEquipped = MooLanguage.get("emotes_all_assigned");
            int eqW = this.textRenderer.getWidth(allEquipped);
            context.drawTextWithShadow(this.textRenderer, allEquipped, cx - (eqW / 2), dockY + 40, 0xFF88CC88);
        } else {
            int cardW = 130;
            int cardH = 50;
            int totalCardsW = availableEmotes.size() * cardW + (availableEmotes.size() > 0 ? (availableEmotes.size() - 1) * 12 : 0);
            int visibleW = dockW - 32;

            float maxScroll = Math.max(0, totalCardsW - visibleW);
            dockScrollOffset = MathHelper.clamp(dockScrollOffset + (targetDockScrollOffset - dockScrollOffset) * 0.35f, 0.0f, maxScroll);

            context.enableScissor(dockX + 16, dockY + 24, dockX + dockW - 16, dockY + dockH - 6);

            int startCardX = (int) (dockX + 16 - dockScrollOffset);
            int startCardY = dockY + 26;

            for (int idx = 0; idx < availableEmotes.size(); idx++) {
                Emote emote = availableEmotes.get(idx);
                int cardX = startCardX + idx * (cardW + 12);
                int cardY = startCardY;

                if (cardX + cardW < dockX + 16 || cardX > dockX + dockW - 16) {
                    continue;
                }

                boolean isCardHover = (mouseX >= cardX && mouseX <= cardX + cardW && mouseY >= cardY && mouseY <= cardY + cardH);
                if (isCardHover && draggingEmoteId == null) {
                    hoveredDockEmoteId = emote.getId();
                }

                boolean isUnlocked = emote.isFree() || PermissionManager.hasAccessLocal(emote.getId());
                int cardBg = isCardHover ? 0xDD2A2A38 : (isUnlocked ? 0xCC1A1A24 : 0xAA1E1418);
                int cardBorder = isCardHover ? 0xFF55FFFF : (isUnlocked ? 0x55555577 : 0x55553333);

                context.fill(cardX, cardY, cardX + cardW, cardY + cardH, cardBg);
                drawRectOutline(context, cardX, cardY, cardW, cardH, cardBorder);

                int iconSize = 32;
                if (emote.getIcon() != null) {
                    context.drawTexture(RenderLayer::getGuiTextured, emote.getIcon(), cardX + 8, cardY + 9, 0.0f, 0.0f, iconSize, iconSize, iconSize, iconSize);
                }

                if (!isUnlocked) {
                    int lockSize = 12;
                    context.drawTexture(RenderLayer::getGuiTextured, LOCK_ICON, cardX + 28, cardY + 6, 0.0f, 0.0f, lockSize, lockSize, lockSize, lockSize);
                }

                String cardName = emote.getDisplayName();
                if (emote.isMultiplayer()) {
                    cardName = (emote.getParticipantCount() > 2 ? "👥👤 " : "👥 ") + cardName;
                }
                if (cardName.length() > 14) {
                    cardName = cardName.substring(0, 12) + "...";
                }
                context.drawTextWithShadow(this.textRenderer, cardName, cardX + 46, cardY + 12, isUnlocked ? 0xFFFFFFFF : 0xFFFFAAAA);

                String cardSub = emote.isMultiplayer() ? "Multiplayer" : (emote.isLooping() ? "Loop" : "Action");
                context.drawTextWithShadow(this.textRenderer, cardSub, cardX + 46, cardY + 26, 0xFF888899);
            }

            context.disableScissor();

            if (maxScroll > 0) {
                int trackX = dockX + 16;
                int trackW = dockW - 32;
                int trackY = dockY + dockH - 4;
                context.fill(trackX, trackY, trackX + trackW, trackY + 2, 0x55333344);

                float scrollRatio = (maxScroll > 0) ? (dockScrollOffset / maxScroll) : 0.0f;
                int thumbW = Math.max(28, (int) (trackW * ((float) visibleW / totalCardsW)));
                int thumbX = (int) (trackX + scrollRatio * (trackW - thumbW));
                context.fill(thumbX, trackY, thumbX + thumbW, trackY + 2, 0xCC8888BB);
            }
        }

        // --- 5. Render Floating Dragged Item Thumbnail ---
        if (draggingEmoteId != null) {
            int dragW = 50;
            int dragH = 50;
            int dragX = mouseX - (dragW / 2);
            int dragY = mouseY - (dragH / 2);

            context.fill(dragX, dragY, dragX + dragW, dragY + dragH, 0xEE1E1E2C);
            drawRectOutline(context, dragX, dragY, dragW, dragH, 0xFF55FFFF);

            Emote dragEmote = EmoteRegistry.get(draggingEmoteId);
            if (dragEmote != null && dragEmote.getIcon() != null) {
                context.drawTexture(RenderLayer::getGuiTextured, dragEmote.getIcon(), dragX + 9, dragY + 9, 0.0f, 0.0f, 32, 32, 32, 32);
            }
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int cx = this.width / 2;
            int cy = this.height / 2 - 25;

            // 1. Save button
            int saveBtnX = this.width - 150;
            int saveBtnY = 16;
            if (mouseX >= saveBtnX && mouseX <= saveBtnX + 135 && mouseY >= saveBtnY && mouseY <= saveBtnY + 24) {
                applyAndClose();
                return true;
            }

            // 2. Reset button
            int resetBtnX = this.width - 270;
            int resetBtnY = 16;
            if (mouseX >= resetBtnX && mouseX <= resetBtnX + 110 && mouseY >= resetBtnY && mouseY <= resetBtnY + 24) {
                EmoteWheelConfig.resetDefaults();
                for (int i = 0; i < EmoteWheelConfig.TOTAL_SLOTS; i++) {
                    workingSlots[i] = EmoteWheelConfig.getSlot(i);
                }
                return true;
            }

            // 3. Delete slot button
            if (hoveredDeleteSlot >= 0 && hoveredDeleteSlot < 12) {
                workingSlots[hoveredDeleteSlot] = null;
                return true;
            }

            // 5. Wheel slot drag start
            if (hoveredWheelSlot >= 0 && hoveredWheelSlot < 12 && workingSlots[hoveredWheelSlot] != null) {
                draggingEmoteId = workingSlots[hoveredWheelSlot];
                dragSourceSlotIndex = hoveredWheelSlot;
                return true;
            }

            // 6. Dock item drag start
            if (hoveredDockEmoteId != null) {
                Emote emote = EmoteRegistry.get(hoveredDockEmoteId);
                boolean isUnlocked = emote != null && (emote.isFree() || PermissionManager.hasAccessLocal(emote.getId()));
                if (!isUnlocked) {
                    if (this.client != null && this.client.player != null) {
                        this.client.player.sendMessage(Text.literal("§c" + MooLanguage.get("emotes_store_required")), true);
                    }
                    return true;
                }
                draggingEmoteId = hoveredDockEmoteId;
                dragSourceSlotIndex = -1;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && draggingEmoteId != null) {
            if (hoveredTrashZone) {
                if (dragSourceSlotIndex >= 0) {
                    workingSlots[dragSourceSlotIndex] = null;
                }
            } else if (hoveredWheelSlot >= 0 && hoveredWheelSlot < 12) {
                Emote emote = EmoteRegistry.get(draggingEmoteId);
                boolean isUnlocked = emote != null && (emote.isFree() || PermissionManager.hasAccessLocal(emote.getId()));
                if (!isUnlocked) {
                    if (this.client != null && this.client.player != null) {
                        this.client.player.sendMessage(Text.literal("§c" + MooLanguage.get("emotes_store_required")), true);
                    }
                    if (dragSourceSlotIndex >= 0) {
                        workingSlots[dragSourceSlotIndex] = null;
                    }
                } else {
                    if (dragSourceSlotIndex >= 0) {
                        String temp = workingSlots[dragSourceSlotIndex];
                        workingSlots[dragSourceSlotIndex] = workingSlots[hoveredWheelSlot];
                        workingSlots[hoveredWheelSlot] = temp;
                    } else {
                        workingSlots[hoveredWheelSlot] = draggingEmoteId;
                    }
                }
            } else if (mouseY >= this.height - 110) {
                if (dragSourceSlotIndex >= 0) {
                    workingSlots[dragSourceSlotIndex] = null;
                }
            }

            draggingEmoteId = null;
            dragSourceSlotIndex = -1;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int dockH = 86;
        int dockY = this.height - dockH - 12;
        int dockW = Math.min(560, this.width - 40);

        List<String> equippedList = Arrays.asList(workingSlots);
        int availableCount = 0;
        for (Emote emote : EmoteRegistry.getAll()) {
            if ("hands_up".equalsIgnoreCase(emote.getId())) continue;
            if (!equippedList.contains(emote.getId())) {
                availableCount++;
            }
        }

        int cardW = 130;
        int totalCardsW = availableCount * cardW + (availableCount > 0 ? (availableCount - 1) * 12 : 0);
        int visibleW = dockW - 32;
        float maxScroll = Math.max(0, totalCardsW - visibleW);

        targetDockScrollOffset = MathHelper.clamp(targetDockScrollOffset - (float) (verticalAmount * 45.0), 0.0f, maxScroll);
        return true;
    }

    private void applyAndClose() {
        for (int i = 0; i < EmoteWheelConfig.TOTAL_SLOTS; i++) {
            String slotId = workingSlots[i];
            if (slotId != null) {
                Emote emote = EmoteRegistry.get(slotId);
                if (emote != null && !emote.isFree() && !PermissionManager.hasAccessLocal(emote.getId())) {
                    workingSlots[i] = null;
                }
            }
        }
        EmoteWheelConfig.setActiveSlots(Arrays.asList(workingSlots));
        if (this.client != null) {
            this.client.setScreen(parentScreen);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            applyAndClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
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
                        if (spanStart == -1) spanStart = cx + dx;
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
}
