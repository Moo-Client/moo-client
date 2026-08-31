package com.mooclient.gui;

import com.mooclient.MooClient;
import com.mooclient.emote.Emote;
import com.mooclient.emote.EmoteRegistry;
import com.mooclient.interaction.Interaction;
import com.mooclient.interaction.InteractionEngine;
import com.mooclient.util.MooClientSettings;
import com.mooclient.util.MooLanguage;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Menedżer i renderer interfejsu zaproszeń (Invitation UI).
 * Obsługuje 4 przełączalne warianty wizualne bez widocznego paska/odliczania czasu.
 */
public class InvitationUIManager {

    private static final InvitationUIManager INSTANCE = new InvitationUIManager();

    public enum UiVariant {
        FLOATING_CENTER(0, "Floating Center Window"),
        OVERHEAD_BILLBOARD(1, "Over-Head 3D Billboard"),
        COMPACT_TOAST(2, "Compact Corner Toast"),
        MOO_SLEEK_CARD(3, "Moo Sleek Action Card");

        public final int id;
        public final String displayName;

        UiVariant(int id, String displayName) {
            this.id = id;
            this.displayName = displayName;
        }

        public static UiVariant fromId(int id) {
            for (UiVariant v : values()) {
                if (v.id == id) return v;
            }
            return FLOATING_CENTER;
        }
    }

    private final Map<UUID, Interaction> activeInvitations = new ConcurrentHashMap<>();
    private UUID currentDisplayedId = null;

    public static InvitationUIManager getInstance() {
        return INSTANCE;
    }

    private InvitationUIManager() {
    }

    public void showInvitation(Interaction interaction) {
        if (interaction == null) return;
        activeInvitations.put(interaction.getInteractionId(), interaction);
        currentDisplayedId = interaction.getInteractionId();
    }

    public void hideInvitation(UUID interactionId) {
        if (interactionId == null) return;
        activeInvitations.remove(interactionId);
        if (interactionId.equals(currentDisplayedId)) {
            currentDisplayedId = activeInvitations.isEmpty() ? null : activeInvitations.keySet().iterator().next();
        }
    }

    public void clear() {
        activeInvitations.clear();
        currentDisplayedId = null;
    }

    public boolean hasInvitation() {
        return !activeInvitations.isEmpty();
    }

    /**
     * Tick czyszczący wygasłe zaproszenia z mapy UI (zabezpieczenie przed wisącymi elementami).
     */
    public void onTick() {
        if (activeInvitations.isEmpty()) return;

        for (Map.Entry<UUID, Interaction> entry : new ArrayList<>(activeInvitations.entrySet())) {
            Interaction inv = entry.getValue();
            if (inv.isExpired(InteractionEngine.INVITATION_TIMEOUT_MS)) {
                activeInvitations.remove(entry.getKey());
                if (entry.getKey().equals(currentDisplayedId)) {
                    currentDisplayedId = activeInvitations.isEmpty() ? null : activeInvitations.keySet().iterator().next();
                }
            }
        }
    }

    public Interaction getCurrentInvitation() {
        if (currentDisplayedId != null) {
            Interaction inv = activeInvitations.get(currentDisplayedId);
            if (inv != null) return inv;
        }
        if (!activeInvitations.isEmpty()) {
            currentDisplayedId = activeInvitations.keySet().iterator().next();
            return activeInvitations.get(currentDisplayedId);
        }
        return null;
    }

    public static String getAcceptKeyName() {
        return com.mooclient.module.modules.EmotesModule.getAcceptKeyName();
    }

    public static String getDeclineKeyName() {
        return com.mooclient.module.modules.EmotesModule.getDeclineKeyName();
    }

    /**
     * Główna metoda renderująca na ekranie HUD w 2D (InGameHud).
     */
    public void renderHud(DrawContext context, int screenWidth, int screenHeight, float tickDelta) {
        Interaction invitation = getCurrentInvitation();
        if (invitation == null) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.options.hudHidden) return;

        UiVariant variant = MooClientSettings.getInvitationUiVariant();

        switch (variant) {
            case FLOATING_CENTER:
                renderFloatingCenter(context, invitation, screenWidth, screenHeight);
                break;
            case COMPACT_TOAST:
                renderCompactToast(context, invitation, screenWidth, screenHeight);
                break;
            case MOO_SLEEK_CARD:
                renderMooSleekCard(context, invitation, screenWidth, screenHeight);
                break;
            case OVERHEAD_BILLBOARD:
                // Billboard 3D renderowany jest w przestrzeni świata (WorldRenderer/EntityRenderer)
                // Dodatkowo wyświetlamy mini prompt na dole ekranu
                renderMiniPrompt(context, invitation, screenWidth, screenHeight);
                break;
        }
    }

    // =========================================================================
    // WARIANT A: FLOATING CENTER WINDOW
    // =========================================================================
    private void renderFloatingCenter(DrawContext context, Interaction inv, int sw, int sh) {
        MinecraftClient client = MinecraftClient.getInstance();
        Emote emote = EmoteRegistry.get(inv.getEmoteId());
        String emoteName = emote != null ? emote.getDisplayName() : inv.getEmoteId();
        String inviter = inv.getInitiatorName();

        String acceptLabel = MooLanguage.get("invitation_accept_prompt");
        String declineLabel = MooLanguage.get("invitation_decline_prompt");
        String acceptText = "§a[" + getAcceptKeyName() + "] " + acceptLabel;
        String declineText = "§c[" + getDeclineKeyName() + "] " + declineLabel;

        int acceptW = client.textRenderer.getWidth(acceptText);

        int w = 240;
        int h = 64;
        int x = (sw - w) / 2;
        int y = sh / 4;

        int accent = MooClientSettings.getAccentColor();

        // Tło szklane z akcentem
        context.fill(x, y, x + w, y + h, 0xDD12121A);
        drawBorder(context, x, y, w, h, accent);

        // Ikona multiplayer / emote
        int iconX = x + 12;
        int iconY = y + 14;
        if (emote != null && emote.getIcon() != null) {
            context.drawTexture(net.minecraft.client.render.RenderLayer::getGuiTextured,
                    emote.getIcon(), iconX, iconY, 0, 0, 32, 32, 32, 32);
        } else {
            context.fill(iconX, iconY, iconX + 32, iconY + 32, 0x44FFFFFF);
        }

        // Tekst zaproszenia
        String title = MooLanguage.get("invitation_title");
        String desc = "§b" + inviter + " §f" + MooLanguage.get("invitation_desc") + " §e" + emoteName;
        context.drawTextWithShadow(client.textRenderer, title, iconX + 40, y + 10, 0xFFFFFFFF);
        context.drawTextWithShadow(client.textRenderer, desc, iconX + 40, y + 24, 0xFFAAAAAA);

        // Przyciski akcji — dynamiczne pozycje
        int btnStartX = iconX + 40;
        context.drawTextWithShadow(client.textRenderer, acceptText, btnStartX, y + 44, 0xFF55FF55);
        context.drawTextWithShadow(client.textRenderer, declineText, btnStartX + acceptW + 12, y + 44, 0xFFFF5555);
    }

    // =========================================================================
    // WARIANT C: COMPACT CORNER TOAST
    // =========================================================================
    private void renderCompactToast(DrawContext context, Interaction inv, int sw, int sh) {
        MinecraftClient client = MinecraftClient.getInstance();
        Emote emote = EmoteRegistry.get(inv.getEmoteId());
        String emoteName = emote != null ? emote.getDisplayName() : inv.getEmoteId();

        String acceptLabel = MooLanguage.get("invitation_accept_prompt");
        String declineLabel = MooLanguage.get("invitation_decline_prompt");

        String line1 = "§b👥 " + inv.getInitiatorName() + " §7» §e" + emoteName;
        String line2 = "§a[" + getAcceptKeyName() + "] " + acceptLabel + "  §7|  §c[" + getDeclineKeyName() + "] " + declineLabel;

        int w = Math.max(210, client.textRenderer.getWidth(line2) + 24);
        int h = 42;
        int x = sw - w - 12;
        int y = 12;

        int accent = MooClientSettings.getAccentColor();

        context.fill(x, y, x + w, y + h, 0xEE101018);
        drawBorder(context, x, y, w, h, accent);

        context.drawTextWithShadow(client.textRenderer, line1, x + 10, y + 8, 0xFFFFFFFF);
        context.drawTextWithShadow(client.textRenderer, line2, x + 10, y + 24, 0xFFFFFFFF);
    }

    // =========================================================================
    // WARIANT D: MOO SLEEK ACTION CARD
    // =========================================================================
    private void renderMooSleekCard(DrawContext context, Interaction inv, int sw, int sh) {
        MinecraftClient client = MinecraftClient.getInstance();
        Emote emote = EmoteRegistry.get(inv.getEmoteId());
        String emoteName = emote != null ? emote.getDisplayName() : inv.getEmoteId();

        String acceptLabel = MooLanguage.get("invitation_accept_prompt").toUpperCase();
        String declineLabel = MooLanguage.get("invitation_decline_prompt").toUpperCase();

        int w = 260;
        int h = 74;
        int x = (sw - w) / 2;
        int y = 24;

        int accent = MooClientSettings.getAccentColor();

        // Warstwa tła z gradientem
        context.fill(x, y, x + w, y + h, 0xF00A0A10);
        context.fill(x + 2, y + 2, x + w - 2, y + 20, 0x33000000 | (accent & 0x00FFFFFF));
        drawBorder(context, x, y, w, h, accent);

        // Header
        String header = "§6★ §f" + MooLanguage.get("invitation_header");
        context.drawTextWithShadow(client.textRenderer, header, x + 10, y + 6, 0xFFFFFFFF);

        // Treść
        String msg = "§b" + inv.getInitiatorName() + " §f" + MooLanguage.get("invitation_offers") + " §e" + emoteName;
        context.drawTextWithShadow(client.textRenderer, msg, x + 10, y + 26, 0xFFE0E0E0);

        // Kafelki klawiszy — dynamiczne
        int btnY = y + 44;
        int btnH = 20;
        int gap = 10;
        String acceptBtnText = "§a[" + getAcceptKeyName() + "] " + acceptLabel;
        String declineBtnText = "§c[" + getDeclineKeyName() + "] " + declineLabel;
        int acceptBtnW = client.textRenderer.getWidth(acceptBtnText) + 16;
        int declineBtnW = client.textRenderer.getWidth(declineBtnText) + 16;

        // Przycisk Akceptuj
        context.fill(x + 10, btnY, x + 10 + acceptBtnW, btnY + btnH, 0xCC1A3824);
        drawBorder(context, x + 10, btnY, acceptBtnW, btnH, 0xFF55FF55);
        context.drawCenteredTextWithShadow(client.textRenderer, acceptBtnText, x + 10 + acceptBtnW / 2, btnY + 6, 0xFFFFFFFF);

        // Przycisk Odrzuć
        int declineX = x + 10 + acceptBtnW + gap;
        context.fill(declineX, btnY, declineX + declineBtnW, btnY + btnH, 0xCC381A1A);
        drawBorder(context, declineX, btnY, declineBtnW, btnH, 0xFFFF5555);
        context.drawCenteredTextWithShadow(client.textRenderer, declineBtnText, declineX + declineBtnW / 2, btnY + 6, 0xFFFFFFFF);
    }

    private void renderMiniPrompt(DrawContext context, Interaction inv, int sw, int sh) {
        MinecraftClient client = MinecraftClient.getInstance();
        String acceptLabel = MooLanguage.get("invitation_accept_prompt");
        String declineLabel = MooLanguage.get("invitation_decline_prompt");
        String text = "§b" + inv.getInitiatorName() + " §f» §a[" + getAcceptKeyName() + "] " + acceptLabel + "  §c[" + getDeclineKeyName() + "] " + declineLabel;
        int w = client.textRenderer.getWidth(text) + 20;
        int x = (sw - w) / 2;
        int y = sh - 55;

        context.fill(x, y, x + w, y + 18, 0xCC101018);
        drawBorder(context, x, y, w, 18, MooClientSettings.getAccentColor());
        context.drawCenteredTextWithShadow(client.textRenderer, text, sw / 2, y + 5, 0xFFFFFFFF);
    }

    // =========================================================================
    // WARIANT B: IN-WORLD 3D BILLBOARD (Renderowany nad głową gracza)
    // =========================================================================
    public void renderInWorldBillboard(PlayerEntity entity, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
        if (entity == null || MooClientSettings.getInvitationUiVariant() != UiVariant.OVERHEAD_BILLBOARD) {
            return;
        }

        Interaction inv = getCurrentInvitation();
        if (inv == null || !entity.getUuid().equals(inv.getInitiatorUuid())) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        Emote emote = EmoteRegistry.get(inv.getEmoteId());
        String emoteName = emote != null ? emote.getDisplayName() : inv.getEmoteId();

        String acceptLabel = MooLanguage.get("invitation_accept_prompt");
        String declineLabel = MooLanguage.get("invitation_decline_prompt");

        matrices.push();
        matrices.translate(0.0, entity.getHeight() + 0.65, 0.0);
        matrices.multiply(client.getEntityRenderDispatcher().getRotation());
        matrices.scale(-0.025f, -0.025f, 0.025f);

        String text1 = "§6★ §e" + emoteName + " §6★";
        String text2 = "§a[" + getAcceptKeyName() + "] " + acceptLabel + " §f/ §c[" + getDeclineKeyName() + "] " + declineLabel;

        int w1 = client.textRenderer.getWidth(text1);
        int w2 = client.textRenderer.getWidth(text2);

        // Renderowanie tła i tekstu
        client.textRenderer.draw(text1, -w1 / 2.0f, -12, 0xFFFFFFFF, false, matrices.peek().getPositionMatrix(), vertexConsumers, net.minecraft.client.font.TextRenderer.TextLayerType.SEE_THROUGH, 0x88000000, light);
        client.textRenderer.draw(text2, -w2 / 2.0f, 0, 0xFFFFFFFF, false, matrices.peek().getPositionMatrix(), vertexConsumers, net.minecraft.client.font.TextRenderer.TextLayerType.SEE_THROUGH, 0x88000000, light);

        matrices.pop();
    }

    private void drawBorder(DrawContext context, int x, int y, int w, int h, int color) {
        context.fill(x, y, x + w, y + 1, color);
        context.fill(x, y + h - 1, x + w, y + h, color);
        context.fill(x, y + 1, x + 1, y + h - 1, color);
        context.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }
}
