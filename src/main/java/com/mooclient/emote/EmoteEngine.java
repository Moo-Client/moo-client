package com.mooclient.emote;

import com.mooclient.MooClient;
import com.mooclient.module.modules.EmotesModule;
import com.mooclient.network.MooNetworkHandler;
import com.mooclient.permissions.PermissionManager;
import com.mooclient.util.MooLanguage;
import com.mooclient.util.MooUserManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Główny kontroler silnika animacji Emote Engine.
 * Zarządza stanami animacji gracza lokalnego i wszystkich graczy zdalnych (po UUID oraz nicku).
 */
public class EmoteEngine {

    private static final EmoteEngine INSTANCE = new EmoteEngine();

    private final EmotePlayerState localState = new EmotePlayerState();
    private final Map<UUID, EmotePlayerState> remoteStatesByUuid = new ConcurrentHashMap<>();
    private final Map<String, EmotePlayerState> remoteStatesByName = new ConcurrentHashMap<>();
    private final Map<String, List<PendingEmotePlay>> pendingEmotes = new ConcurrentHashMap<>();

    private record PendingEmotePlay(UUID uuid, String username, String emoteId, int slotIndex, long startEpochMs) {}

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
        return remoteStatesByUuid.computeIfAbsent(uuid, k -> new EmotePlayerState());
    }

    public EmotePlayerState getPlayerStateIfExists(UUID uuid) {
        if (uuid == null) return null;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null && uuid.equals(client.player.getUuid())) {
            return localState;
        }
        return remoteStatesByUuid.get(uuid);
    }

    public EmotePlayerState getPlayerStateIfExists(PlayerEntity player) {
        if (player == null) return null;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null && player.getId() == client.player.getId()) {
            return localState;
        }
        if (player.getUuid() != null) {
            EmotePlayerState state = remoteStatesByUuid.get(player.getUuid());
            if (state != null) return state;
        }
        String cleanName = MooUserManager.cleanName(player.getNameForScoreboard());
        if (!cleanName.isEmpty()) {
            EmotePlayerState state = remoteStatesByName.get(cleanName);
            if (state != null) return state;
        }
        return null;
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

        // Weryfikacja uprawnień gracza do emotki
        if (!emote.isFree() && !PermissionManager.hasAccessLocal(emote.getId())) {
            client.player.sendMessage(Text.literal("§c" + MooLanguage.get("emotes_store_required")), true);
            return;
        }

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

    public void handleNetworkEmote(UUID playerUuid, String username, String emoteId, boolean play) {
        if (play && emoteId != null) {
            playRemoteEmote(playerUuid, username, emoteId, 0, System.currentTimeMillis());
        } else {
            stopRemoteEmote(playerUuid, username);
        }
    }

    public void playRemoteEmote(UUID playerUuid, String emoteId, int slotIndex, long startEpochMs) {
        playRemoteEmote(playerUuid, null, emoteId, slotIndex, startEpochMs);
    }

    public void playRemoteEmote(UUID playerUuid, String username, String emoteId, int slotIndex, long startEpochMs) {
        if (playerUuid == null && username == null) return;
        if (emoteId == null) return;

        Emote emote = EmoteRegistry.get(emoteId);
        if (emote == null) {
            // Queue pending play once downloaded
            pendingEmotes.computeIfAbsent(emoteId.toLowerCase().trim(), k -> new CopyOnWriteArrayList<>())
                    .add(new PendingEmotePlay(playerUuid, username, emoteId, slotIndex, startEpochMs));
            EmoteRemoteLoader.fetchOnDemandIfMissing(emoteId);
            return;
        }

        EmotePlayerState state = null;
        if (playerUuid != null) {
            state = remoteStatesByUuid.computeIfAbsent(playerUuid, k -> new EmotePlayerState());
        }
        if (username != null && !username.trim().isEmpty()) {
            String clean = MooUserManager.cleanName(username);
            if (state == null) {
                state = remoteStatesByName.computeIfAbsent(clean, k -> new EmotePlayerState());
            } else {
                remoteStatesByName.put(clean, state);
            }
        }

        // Link with matching player in client world
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world != null && username != null) {
            for (PlayerEntity p : client.world.getPlayers()) {
                String pName = MooUserManager.cleanName(p.getNameForScoreboard());
                if (pName.equals(MooUserManager.cleanName(username)) && p.getUuid() != null && state != null) {
                    remoteStatesByUuid.put(p.getUuid(), state);
                }
            }
        }

        if (state != null) {
            state.startEmote(emote, slotIndex, startEpochMs);
        }
    }

    public void stopRemoteEmote(UUID playerUuid) {
        stopRemoteEmote(playerUuid, null);
    }

    public void stopRemoteEmote(UUID playerUuid, String username) {
        if (playerUuid != null) {
            EmotePlayerState state = remoteStatesByUuid.get(playerUuid);
            if (state != null) state.stopEmote();
        }
        if (username != null && !username.trim().isEmpty()) {
            String clean = MooUserManager.cleanName(username);
            EmotePlayerState state = remoteStatesByName.get(clean);
            if (state != null) state.stopEmote();
        }
    }

    public void onEmoteRegistered(Emote emote) {
        if (emote == null || emote.getId() == null) return;
        List<PendingEmotePlay> pending = pendingEmotes.remove(emote.getId().toLowerCase().trim());
        if (pending != null) {
            for (PendingEmotePlay p : pending) {
                playRemoteEmote(p.uuid, p.username, p.emoteId, p.slotIndex, p.startEpochMs);
            }
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

        // Ticking unikalnych stanów zdalnych
        for (EmotePlayerState state : remoteStatesByUuid.values()) {
            if (state.isRendering()) {
                state.onTick();
            }
        }
        for (EmotePlayerState state : remoteStatesByName.values()) {
            if (state.isRendering() && !remoteStatesByUuid.containsValue(state)) {
                state.onTick();
            }
        }
    }

    public void clearAll() {
        stopLocalEmote();
        remoteStatesByUuid.clear();
        remoteStatesByName.clear();
        pendingEmotes.clear();
    }
}
