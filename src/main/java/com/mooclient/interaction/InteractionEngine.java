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
import com.mooclient.util.MooUserManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Główny koordynator systemu interakcji wieloosobowych (Interaction Engine).
 * Pozycjonuje obu graczy twarzą w twarz i synchronizuje animacje u obu uczestników.
 */
public class InteractionEngine {

    private static final InteractionEngine INSTANCE = new InteractionEngine();

    public static final long INVITATION_TIMEOUT_MS = 10000L; // 10 sekund na akceptację

    private final Map<UUID, Interaction> pendingInvitations = new ConcurrentHashMap<>();
    private Interaction activeInteraction = null;
    private boolean interactionSwitchedPerspective = false;

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

        // 3. Rejestracja celu w menedżerze graczy
        MooUserManager.registerUser(targetName, targetUuid);

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
            this.interactionSwitchedPerspective = true;
        }

        UUID partnerUuid = (localSlot == 0) ? interaction.getTargetUuid() : interaction.getInitiatorUuid();
        String partnerName = (localSlot == 0) ? interaction.getTargetName() : interaction.getInitiatorName();
        int partnerSlot = (localSlot == 0) ? 1 : 0;

        float yaw0 = 0.0f;
        float yaw1 = 180.0f;
        float visualShiftZ = 0.0f;

        PlayerEntity partnerPlayer = null;
        if (client != null && client.world != null && client.player != null) {
            if (partnerUuid != null) {
                partnerPlayer = client.world.getPlayerByUuid(partnerUuid);
            }
            if (partnerPlayer == null && partnerName != null) {
                for (PlayerEntity p : client.world.getPlayers()) {
                    if (partnerName.equalsIgnoreCase(p.getName().getString())
                            || partnerName.equalsIgnoreCase(MooUserManager.cleanName(p.getNameForScoreboard()))) {
                        partnerPlayer = p;
                        break;
                    }
                }
            }

            if (partnerPlayer != null) {
                PlayerEntity p0 = (localSlot == 0) ? client.player : partnerPlayer;
                PlayerEntity p1 = (localSlot == 0) ? partnerPlayer : client.player;

                double dx = p1.getX() - p0.getX();
                double dz = p1.getZ() - p0.getZ();
                if (Math.abs(dx) > 0.001 || Math.abs(dz) > 0.001) {
                    yaw0 = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
                    yaw1 = yaw0 + 180.0f;

                    double dist = Math.sqrt(dx * dx + dz * dz);
                    float targetDist = (emote.getSceneConfig() != null && emote.getSceneConfig().getDefaultDistance() > 0.5f)
                            ? emote.getSceneConfig().getDefaultDistance() : 1.20f;
                    float shift = Math.max(0.0f, (float) (dist - targetDist) / 2.0f);
                    visualShiftZ = -shift;
                }
            }
        }

        // Slot 0 jest skierowany pod kątem yaw0, a Slot 1 pod kątem yaw1.
        // Obydwaj gracze są wizualnie przyciągani do siebie (visualShiftZ = -shift)
        // tak, aby ich dłonie stykały się dokładnie w uścisku (odległość 1.20 m).
        SceneTransform st0 = new SceneTransform(0.0f, 0.0f, visualShiftZ, yaw0, true);
        SceneTransform st1 = new SceneTransform(0.0f, 0.0f, visualShiftZ, yaw1, true);

        // 1. Ustawienie transformacji i uruchomienie animacji dla gracza lokalnego
        EmotePlayerState localState = EmoteEngine.getInstance().getLocalPlayerState();
        localState.setCustomSceneTransform((localSlot == 0) ? st0 : st1);
        localState.startEmote(emote, localSlot, startEpochMs);

        // 2. Ustawienie transformacji i uruchomienie animacji dla partnera w scenie
        UUID actualPartnerUuid = (partnerPlayer != null && partnerPlayer.getUuid() != null)
                ? partnerPlayer.getUuid() : partnerUuid;
        String actualPartnerName = (partnerPlayer != null)
                ? partnerPlayer.getName().getString() : partnerName;

        if (actualPartnerUuid != null || actualPartnerName != null) {
            EmoteEngine.getInstance().playRemoteEmote(actualPartnerUuid, actualPartnerName, emote.getId(), partnerSlot, startEpochMs);
            EmotePlayerState partnerState = EmoteEngine.getInstance().getPlayerState(actualPartnerUuid, actualPartnerName);
            if (partnerState != null) {
                partnerState.setCustomSceneTransform((localSlot == 0) ? st1 : st0);
            }
        }
    }

    /**
     * Natychmiastowe przerwanie aktywnej interakcji u obu graczy.
     */
    public synchronized void cancelCurrentInteraction(String reason) {
        if (activeInteraction != null) {
            UUID id = activeInteraction.getInteractionId();
            UUID partnerUuid = activeInteraction.getInitiatorUuid().equals(MooSessionValidator.getLocalPlayerUuid())
                    ? activeInteraction.getTargetUuid() : activeInteraction.getInitiatorUuid();
            String partnerName = activeInteraction.getInitiatorUuid().equals(MooSessionValidator.getLocalPlayerUuid())
                    ? activeInteraction.getTargetName() : activeInteraction.getInitiatorName();

            activeInteraction.setState(InteractionState.INTERRUPTED);
            activeInteraction = null;

            EmoteEngine.getInstance().stopLocalEmote();
            if (partnerUuid != null || partnerName != null) {
                EmoteEngine.getInstance().stopRemoteEmote(partnerUuid, partnerName);
            }

            restorePerspectiveIfNeeded();

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
            UUID partnerUuid = activeInteraction.getInitiatorUuid().equals(MooSessionValidator.getLocalPlayerUuid())
                    ? activeInteraction.getTargetUuid() : activeInteraction.getInitiatorUuid();
            String partnerName = activeInteraction.getInitiatorUuid().equals(MooSessionValidator.getLocalPlayerUuid())
                    ? activeInteraction.getTargetName() : activeInteraction.getInitiatorName();

            activeInteraction.setState(InteractionState.CANCELLED);
            activeInteraction = null;

            EmoteEngine.getInstance().stopLocalEmote();
            if (partnerUuid != null || partnerName != null) {
                EmoteEngine.getInstance().stopRemoteEmote(partnerUuid, partnerName);
            }

            restorePerspectiveIfNeeded();
        }
        pendingInvitations.remove(interactionId);
        InvitationUIManager.getInstance().hideInvitation(interactionId);
    }

    private void restorePerspectiveIfNeeded() {
        if (this.interactionSwitchedPerspective) {
            this.interactionSwitchedPerspective = false;
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.options != null && !client.options.getPerspective().isFirstPerson()) {
                client.options.setPerspective(Perspective.FIRST_PERSON);
            }
        }
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

            // Weryfikacja zakończenia czasu trwania animacji w oparciu o stan lokalnego odtwarzacza
            Emote emote = EmoteRegistry.get(activeInteraction.getEmoteId());
            EmotePlayerState localState = EmoteEngine.getInstance().getLocalPlayerState();

            boolean finished = false;
            if (emote != null && !emote.isLooping() && emote.getDurationTicks() > 0) {
                if (localState == null || !localState.isRendering()) {
                    finished = true;
                }
            }

            if (finished) {
                UUID partnerUuid = activeInteraction.getInitiatorUuid().equals(client.player.getUuid())
                        ? activeInteraction.getTargetUuid() : activeInteraction.getInitiatorUuid();
                String partnerName = activeInteraction.getInitiatorUuid().equals(client.player.getUuid())
                        ? activeInteraction.getTargetName() : activeInteraction.getInitiatorName();

                activeInteraction.setState(InteractionState.COMPLETED);
                activeInteraction = null;
                EmoteEngine.getInstance().stopLocalEmote();
                if (partnerUuid != null || partnerName != null) {
                    EmoteEngine.getInstance().stopRemoteEmote(partnerUuid, partnerName);
                }
                restorePerspectiveIfNeeded();
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
        restorePerspectiveIfNeeded();
        InvitationUIManager.getInstance().clear();
    }
}
