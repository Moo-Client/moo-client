package com.mooclient.emote;

import com.mooclient.MooClient;
import com.mooclient.module.modules.EmotesModule;
import com.mooclient.network.MooNetworkHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Główny kontroler silnika animacji Emote Engine.
 * Zarządza stanami animacji gracza lokalnego i wszystkich graczy zdalnych.
 */
public class EmoteEngine {

    private static final EmoteEngine INSTANCE = new EmoteEngine();

    private final EmotePlayerState localState = new EmotePlayerState();
    private final Map<UUID, EmotePlayerState> remoteStates = new ConcurrentHashMap<>();
    private boolean startedInFirstPerson = false;

    public static EmoteEngine getInstance() {
        return INSTANCE;
    }

    private EmoteEngine() {
    }

    public static void init() {
        EmoteRegistry.init();
    }

    public EmotePlayerState getLocalPlayerState() {
        return localState;
    }

    public boolean isLocalEmotePlaying() {
        return localState != null && localState.isPlaying();
    }

    public String getLocalPlayingEmoteId() {
        return (localState != null && localState.getActiveEmote() != null) ? localState.getActiveEmote().getId() : null;
    }

    public EmotePlayerState getPlayerState(UUID uuid) {
        if (uuid == null) return null;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null && uuid.equals(client.player.getUuid())) {
            return localState;
        }
        return remoteStates.computeIfAbsent(uuid, k -> new EmotePlayerState());
    }

    public EmotePlayerState getPlayerStateIfExists(UUID uuid) {
        if (uuid == null) return null;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null && uuid.equals(client.player.getUuid())) {
            return localState;
        }
        return remoteStates.get(uuid);
    }

    public void playLocalEmote(String emoteId) {
        if (emoteId == null) return;
        if (localState.isPlaying() && emoteId.equalsIgnoreCase(getLocalPlayingEmoteId())) {
            return;
        }
        Emote emote = EmoteRegistry.get(emoteId);
        if (emote == null) {
            MooClient.LOGGER.warn("Próba uruchomienia nieznanej emotki: {}", emoteId);
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        // Włączenie perspektywy F5 (trzecia osoba z tyłu), jeśli emotka tego wymaga
        if (client.options != null && emote.isForcesThirdPerson()) {
            this.startedInFirstPerson = client.options.getPerspective().isFirstPerson();
            if (this.startedInFirstPerson) {
                client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
            }
        }

        // Jeśli to emotka solo, rozpocznij animację i powiadom sieć
        if (!emote.isMultiplayer()) {
            localState.startEmote(emote, 0, System.currentTimeMillis());

            // Wysłanie komunikatu do sieci (MQTT / Fabric payload)
            if (MooNetworkHandler.getInstance() != null) {
                MooNetworkHandler.getInstance().sendSoloEmote(emote.getId(), true);
            }
        }
    }

    public void stopLocalEmote() {
        if (localState.isRendering()) {
            String lastEmoteId = localState.getActiveEmote() != null ? localState.getActiveEmote().getId() : "";
            localState.stopEmote();

            // Przywrócenie pierwszej osoby, jeśli włączono w opcjach
            restoreCameraIfNeeded();

            if (MooNetworkHandler.getInstance() != null && !lastEmoteId.isEmpty()) {
                MooNetworkHandler.getInstance().sendSoloEmote(lastEmoteId, false);
            }
        }
    }

    private void restoreCameraIfNeeded() {
        if (EmotesModule.isRestorePerspective() && this.startedInFirstPerson) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.options != null && !client.options.getPerspective().isFirstPerson()) {
                client.options.setPerspective(Perspective.FIRST_PERSON);
            }
        }
        this.startedInFirstPerson = false;
    }

    public void playRemoteEmote(UUID playerUuid, String emoteId, int slotIndex, long startEpochMs) {
        if (playerUuid == null) return;
        Emote emote = EmoteRegistry.get(emoteId);
        if (emote == null) {
            EmoteRemoteLoader.fetchOnDemandIfMissing(emoteId);
            return;
        }

        EmotePlayerState state = remoteStates.computeIfAbsent(playerUuid, k -> new EmotePlayerState());
        state.startEmote(emote, slotIndex, startEpochMs);
    }

    public void stopRemoteEmote(UUID playerUuid) {
        if (playerUuid == null) return;
        EmotePlayerState state = remoteStates.get(playerUuid);
        if (state != null) {
            state.stopEmote();
        }
    }

    public void onTick(MinecraftClient client) {
        // Ticking stanu lokalnego
        if (localState.isRendering()) {
            boolean wasPlaying = localState.isPlaying();
            localState.onTick();
            // Jeśli emotka dobiegła naturalnego końca czasu
            if (wasPlaying && !localState.isPlaying() && !localState.isRendering()) {
                restoreCameraIfNeeded();
                if (MooNetworkHandler.getInstance() != null) {
                    MooNetworkHandler.getInstance().sendSoloEmote("", false);
                }
            }
        }

        // Ticking stanów zdalnych
        for (Map.Entry<UUID, EmotePlayerState> entry : remoteStates.entrySet()) {
            EmotePlayerState state = entry.getValue();
            if (state.isRendering()) {
                state.onTick();
            }
        }
    }

    public void clearAll() {
        stopLocalEmote();
        remoteStates.clear();
    }
}
