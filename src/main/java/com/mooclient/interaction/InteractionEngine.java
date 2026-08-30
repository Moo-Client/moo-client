package com.mooclient.interaction;

import com.mooclient.MooClient;
import com.mooclient.emote.Emote;
import com.mooclient.emote.EmoteEngine;
import com.mooclient.emote.EmotePlayerState;
import com.mooclient.emote.EmoteRegistry;
import com.mooclient.gui.InvitationUIManager;
import com.mooclient.network.MooNetworkHandler;
import com.mooclient.permissions.PermissionManager;
import com.mooclient.security.MooSessionValidator;
import com.mooclient.security.RateLimiter;
import com.mooclient.util.MooLanguage;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Główny koordynator systemu interakcji wieloosobowych (Interaction Engine).
 */
public class InteractionEngine {

    private static final InteractionEngine INSTANCE = new InteractionEngine();

    public static final long INVITATION_TIMEOUT_MS = 10000L; // 10 sekund na akceptację

    private final Map<UUID, Interaction> pendingInvitations = new ConcurrentHashMap<>();
    private Interaction activeInteraction = null;

    public static InteractionEngine getInstance() {
        return INSTANCE;
    }

    private InteractionEngine() {
    }

    public boolean hasActiveInteraction() {
        return activeInteraction != null && activeInteraction.isActive();
    }

    public Interaction getActiveInteraction() {
        return activeInteraction;
    }

    public Map<UUID, Interaction> getPendingInvitations() {
        return Collections.unmodifiableMap(pendingInvitations);
    }

    /**
     * Inicjuje wysłanie zaproszenia do gracza pod celownikiem dla danej emotki multiplayer.
     */
    public void initiateInteraction(String emoteId) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        Emote emote = EmoteRegistry.get(emoteId);
        if (emote == null || !emote.isMultiplayer()) {
            return;
        }

        // 1. Sprawdzenie czy gracz już uczestniczy w aktywnej interakcji
        if (hasActiveInteraction()) {
            sendClientMessage("§c" + MooLanguage.get("interaction_already_active"));
            return;
        }

        // 2. Wyszukanie celu w stożku wzroku (<3.0 m, ~18° FOV)
        InteractionTargeting.TargetResult targetRes = InteractionTargeting.findLookTarget(client);
        if (targetRes == null) {
            sendClientMessage("§e" + MooLanguage.get("interaction_no_target"));
            return;
        }

        PlayerEntity targetPlayer = targetRes.player;
        String targetName = targetPlayer.getName().getString();
        UUID targetUuid = targetPlayer.getUuid();

        // 3. Weryfikacja czy cel jest graczem Moo Client
        if (!targetRes.isMooUser) {
            sendClientMessage("§c" + targetName + " " + MooLanguage.get("interaction_not_moo_user"));
            return;
        }

        // 4. Rate Limiting — ochrona przed spamem zaproszeń do tego samego gracza
        if (!RateLimiter.canSendInvitation(targetUuid)) {
            sendClientMessage("§c" + MooLanguage.get("interaction_rate_limited"));
            return;
        }

        // 5. Sprawdzenie uprawnień lokalnego gracza
        UUID localUuid = MooSessionValidator.getLocalPlayerUuid();
        String localName = MooSessionValidator.getLocalPlayerName();

        PermissionManager.authorizeInteractionAsync(localUuid, targetUuid, emoteId).thenAccept(authorized -> {
            if (!authorized) {
                sendClientMessage("§c" + MooLanguage.get("interaction_no_permission"));
                return;
            }

            // 6. Utworzenie unikalnej sesji interakcji
            UUID interactionId = UUID.randomUUID();
            Interaction interaction = new Interaction(
                    interactionId, emoteId,
                    localUuid, localName,
                    targetUuid, targetName,
                    emote.getSceneConfig()
            );

            pendingInvitations.put(interactionId, interaction);

            // 7. Wysłanie komunikatu przez sieć (MQTT / Realtime transport)
            if (MooNetworkHandler.getInstance() != null) {
                MooNetworkHandler.getInstance().sendInteractionRequest(
                        interactionId, emoteId, targetUuid, targetName
                );
            }

            // 8. Komunikat zwrotny dla gracza
            String feedback = MooLanguage.get("interaction_invited")
                    .replace("{target}", targetName)
                    .replace("{emote}", emote.getDisplayName());
            sendClientMessage("§a" + feedback);
        });
    }

    /**
     * Odbiór przychodzącego zaproszenia od innego gracza.
     */
    public void onIncomingRequest(UUID interactionId, String emoteId, UUID fromUuid, String fromName) {
        if (interactionId == null || fromUuid == null) return;

        // Jeśli gracz jest już zajęty aktywną interakcją -> automatyczne odrzucenie z kodem BUSY
        if (hasActiveInteraction()) {
            if (MooNetworkHandler.getInstance() != null) {
                MooNetworkHandler.getInstance().sendInteractionResponse(interactionId, false, 0L);
            }
            return;
        }

        Emote emote = EmoteRegistry.get(emoteId);
        if (emote == null) return;

        UUID localUuid = MooSessionValidator.getLocalPlayerUuid();
        String localName = MooSessionValidator.getLocalPlayerName();

        Interaction interaction = new Interaction(
                interactionId, emoteId,
                fromUuid, fromName,
                localUuid, localName,
                emote.getSceneConfig()
        );

        pendingInvitations.put(interactionId, interaction);
        InvitationUIManager.getInstance().showInvitation(interaction);
    }

    /**
     * Akceptacja oczekującego zaproszenia przez lokalnego gracza.
     */
    public synchronized void acceptInvitation(UUID interactionId) {
        if (interactionId == null) return;
        Interaction interaction = pendingInvitations.remove(interactionId);
        if (interaction == null) return;

        // Blokada przed race condition — jeśli w międzyczasie rozpoczęto inną interakcję
        if (hasActiveInteraction()) {
            interaction.setState(InteractionState.DECLINED);
            if (MooNetworkHandler.getInstance() != null) {
                MooNetworkHandler.getInstance().sendInteractionResponse(interactionId, false, 0L);
            }
            return;
        }

        // Wszystkie pozostałe oczekujące zaproszenia zostają unieważnione
        for (UUID otherId : new ArrayList<>(pendingInvitations.keySet())) {
            Interaction other = pendingInvitations.remove(otherId);
            if (other != null) {
                other.setState(InteractionState.DECLINED);
                if (MooNetworkHandler.getInstance() != null) {
                    MooNetworkHandler.getInstance().sendInteractionResponse(otherId, false, 0L);
                }
            }
        }

        interaction.setState(InteractionState.ACCEPTED);
        this.activeInteraction = interaction;

        // Dynamiczna synchronizacja czasu (START_AT)
        long startEpochMs = DynamicLatencySynchronizer.computeSynchronizedStartTime(interaction.getInitiatorUuid());
        interaction.setStartEpochMs(startEpochMs);

        // Wysłanie odpowiedzi ACCEPT przez sieć
        if (MooNetworkHandler.getInstance() != null) {
            MooNetworkHandler.getInstance().sendInteractionResponse(interactionId, true, startEpochMs);
        }

        // Przygotowanie i uruchomienie sceny (Slot 1 dla akceptującego)
        startScene(interaction, 1, startEpochMs);
        InvitationUIManager.getInstance().clear();
    }

    /**
     * Odrzucenie oczekującego zaproszenia.
     */
    public synchronized void declineInvitation(UUID interactionId) {
        if (interactionId == null) return;
        Interaction interaction = pendingInvitations.remove(interactionId);
        if (interaction != null) {
            interaction.setState(InteractionState.DECLINED);
            if (MooNetworkHandler.getInstance() != null) {
                MooNetworkHandler.getInstance().sendInteractionResponse(interactionId, false, 0L);
            }
        }
        InvitationUIManager.getInstance().hideInvitation(interactionId);
    }

    /**
     * Odbiór odpowiedzi na nasze wysłane zaproszenie.
     */
    public synchronized void onIncomingResponse(UUID interactionId, boolean accepted, long startEpochMs) {
        Interaction interaction = pendingInvitations.remove(interactionId);
        if (interaction == null) return;

        if (accepted) {
            if (hasActiveInteraction()) {
                cancelCurrentInteraction("CONFLICT_BUSY");
                return;
            }

            interaction.setState(InteractionState.STARTED);
            interaction.setStartEpochMs(startEpochMs);
            this.activeInteraction = interaction;

            // Uruchomienie sceny dla Inicjatora (Slot 0)
            startScene(interaction, 0, startEpochMs);
        } else {
            interaction.setState(InteractionState.DECLINED);
            String msg = MooLanguage.get("interaction_declined").replace("{target}", interaction.getTargetName());
            sendClientMessage("§e" + msg);
        }
    }

    private void startScene(Interaction interaction, int localSlot, long startEpochMs) {
        Emote emote = EmoteRegistry.get(interaction.getEmoteId());
        if (emote == null) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.options != null && client.options.getPerspective().isFirstPerson()) {
            client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
        }

        // Ustawienie transformacji sceny w EmoteEngine dla gracza lokalnego
        EmotePlayerState localState = EmoteEngine.getInstance().getLocalPlayerState();
        if (interaction.getSceneConfig() != null) {
            localState.setCustomSceneTransform(interaction.getSceneConfig().getSlotTransform(localSlot));
        }
        localState.startEmote(emote, localSlot, startEpochMs);

        // Ustawienie transformacji sceny dla drugiego gracza
        UUID partnerUuid = (localSlot == 0) ? interaction.getTargetUuid() : interaction.getInitiatorUuid();
        int partnerSlot = (localSlot == 0) ? 1 : 0;

        EmotePlayerState partnerState = EmoteEngine.getInstance().getPlayerState(partnerUuid);
        if (partnerState != null) {
            if (interaction.getSceneConfig() != null) {
                partnerState.setCustomSceneTransform(interaction.getSceneConfig().getSlotTransform(partnerSlot));
            }
            partnerState.startEmote(emote, partnerSlot, startEpochMs);
        }
    }

    /**
     * Natychmiastowe przerwanie aktywnej interakcji u obu graczy.
     */
    public synchronized void cancelCurrentInteraction(String reason) {
        if (activeInteraction != null) {
            UUID id = activeInteraction.getInteractionId();
            activeInteraction.setState(InteractionState.INTERRUPTED);
            activeInteraction = null;

            EmoteEngine.getInstance().stopLocalEmote();

            // Wysłanie pakietu CANCEL
            if (MooNetworkHandler.getInstance() != null) {
                MooNetworkHandler.getInstance().sendInteractionCancel(id, reason);
            }
        }
    }

    /**
     * Odbiór pakietu CANCEL z sieci od drugiego gracza.
     */
    public synchronized void onIncomingCancel(UUID interactionId, String reason) {
        if (activeInteraction != null && activeInteraction.getInteractionId().equals(interactionId)) {
            activeInteraction.setState(InteractionState.CANCELLED);
            activeInteraction = null;
            EmoteEngine.getInstance().stopLocalEmote();
        }
        pendingInvitations.remove(interactionId);
        InvitationUIManager.getInstance().hideInvitation(interactionId);
    }

    /**
     * Aktualizacja w każdym ticku klienta.
     */
    public void onTick(MinecraftClient client) {
        // 1. Sprawdzanie wygaśnięcia oczekujących zaproszeń (Timeout lub odejście celu >3.0m)
        for (Map.Entry<UUID, Interaction> entry : new ArrayList<>(pendingInvitations.entrySet())) {
            Interaction req = entry.getValue();
            if (req.isExpired(INVITATION_TIMEOUT_MS)) {
                req.setState(InteractionState.EXPIRED);
                pendingInvitations.remove(entry.getKey());
                InvitationUIManager.getInstance().hideInvitation(entry.getKey());
                continue;
            }

            // Sprawdzenie dystansu do partnera
            if (client.world != null) {
                UUID otherUuid = req.getInitiatorUuid().equals(MooSessionValidator.getLocalPlayerUuid())
                        ? req.getTargetUuid() : req.getInitiatorUuid();
                PlayerEntity otherPlayer = client.world.getPlayerByUuid(otherUuid);
                if (otherPlayer != null && client.player != null) {
                    if (client.player.distanceTo(otherPlayer) > 3.5f) {
                        req.setState(InteractionState.EXPIRED);
                        pendingInvitations.remove(entry.getKey());
                        InvitationUIManager.getInstance().hideInvitation(entry.getKey());
                    }
                }
            }
        }

        // 2. Sprawdzanie stanu aktywnej interakcji
        if (activeInteraction != null) {
            // Weryfikacja dystansu w trakcie sceny
            if (client.world != null && client.player != null) {
                UUID partnerUuid = activeInteraction.getInitiatorUuid().equals(client.player.getUuid())
                        ? activeInteraction.getTargetUuid() : activeInteraction.getInitiatorUuid();
                PlayerEntity partner = client.world.getPlayerByUuid(partnerUuid);
                if (partner != null && client.player.distanceTo(partner) > 4.0f) {
                    cancelCurrentInteraction("DISTANCE_EXCEEDED");
                    return;
                }
            }

            // Weryfikacja zakończenia czasu trwania animacji
            Emote emote = EmoteRegistry.get(activeInteraction.getEmoteId());
            if (emote != null && !emote.isLooping() && emote.getDurationTicks() > 0) {
                long durationMs = (long) (emote.getDurationTicks() * 50);
                if (System.currentTimeMillis() >= activeInteraction.getStartEpochMs() + durationMs) {
                    activeInteraction.setState(InteractionState.COMPLETED);
                    activeInteraction = null;
                    EmoteEngine.getInstance().stopLocalEmote();
                }
            }
        }
    }

    private void sendClientMessage(String msg) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(Text.literal(msg), false);
        }
    }

    public void clearAll() {
        pendingInvitations.clear();
        if (activeInteraction != null) {
            cancelCurrentInteraction("CLEARED");
        }
        InvitationUIManager.getInstance().clear();
    }
}
