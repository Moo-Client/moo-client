package com.mooclient.emote;

import com.mooclient.MooClient;

import java.util.*;

/**
 * Centralny rejestr emotek (Solo i Multiplayer) w Moo Client.
 * Wszystkie emotki są ładowane wyłącznie dynamicznie z bazy Supabase
 * za pośrednictwem EmoteRemoteLoader.
 */
public class EmoteRegistry {

    private static final Map<String, Emote> REGISTRY = new LinkedHashMap<>();
    private static final List<Emote> ALL_EMOTES = new ArrayList<>();
    private static final List<Emote> SOLO_EMOTES = new ArrayList<>();
    private static final List<Emote> MULTIPLAYER_EMOTES = new ArrayList<>();

    public static void init() {
        REGISTRY.clear();
        ALL_EMOTES.clear();
        SOLO_EMOTES.clear();
        MULTIPLAYER_EMOTES.clear();

        // Ładowanie dynamicznych emotek wyłącznie z chmury Supabase
        EmoteRemoteLoader.init();

        MooClient.LOGGER.info("Zainicjalizowano dynamiczny EmoteRegistry (zasilany w 100% przez Supabase).");
    }

    public static void register(Emote emote) {
        if (emote == null || emote.getId() == null) return;
        String id = emote.getId().toLowerCase().trim();
        REGISTRY.put(id, emote);

        if (!ALL_EMOTES.contains(emote)) {
            ALL_EMOTES.add(emote);
        }
        if (emote.isMultiplayer()) {
            if (!MULTIPLAYER_EMOTES.contains(emote)) MULTIPLAYER_EMOTES.add(emote);
        } else {
            if (!SOLO_EMOTES.contains(emote)) SOLO_EMOTES.add(emote);
        }

        EmoteEngine.getInstance().onEmoteRegistered(emote);
    }

    public static void unregister(String id) {
        if (id == null) return;
        Emote emote = REGISTRY.remove(id.toLowerCase().trim());
        if (emote != null) {
            ALL_EMOTES.remove(emote);
            SOLO_EMOTES.remove(emote);
            MULTIPLAYER_EMOTES.remove(emote);
        }
    }

    public static Emote get(String id) {
        if (id == null) return null;
        return REGISTRY.get(id.toLowerCase().trim());
    }

    public static List<Emote> getAll() {
        return Collections.unmodifiableList(ALL_EMOTES);
    }

    public static List<Emote> getSoloEmotes() {
        return Collections.unmodifiableList(SOLO_EMOTES);
    }

    public static List<Emote> getMultiplayerEmotes() {
        return Collections.unmodifiableList(MULTIPLAYER_EMOTES);
    }

    public static boolean has(String id) {
        if (id == null) return false;
        Emote e = REGISTRY.get(id.toLowerCase().trim());
        return e != null && e.getAnimation() != null;
    }
}
