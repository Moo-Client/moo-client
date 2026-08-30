package com.mooclient.emote;

import com.mooclient.MooClient;
import com.mooclient.emote.animation.BlockbenchAnimation;
import com.mooclient.emote.animation.BlockbenchAnimationParser;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Centralny rejestr emotek (Solo i Multiplayer) w Moo Client.
 * Wszystkie emotki są ładowane wyłącznie dynamicznie z bazy Supabase,
 * z lokalnego katalogu ~/.mooclient/emotes/ lub z wbudowanych zasobów zewnętrznych.
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

        // 1. Wbudowana, stała, darmowa standardowa emotka (Ręce w górę / Hands Up pod klawisz R)
        loadBuiltinHandsUp();

        // 2. Ładowanie pozostałych dynamicznych emotek z bazy Supabase oraz lokalnego katalogu ~/.mooclient/emotes/
        EmoteRemoteLoader.init();

        MooClient.LOGGER.info("Zainicjalizowano dynamiczny EmoteRegistry.");
    }

    private static void loadBuiltinHandsUp() {
        try (InputStream is = EmoteRegistry.class.getResourceAsStream("/assets/mooclient/emotes/animations/hands_up.json")) {
            if (is != null) {
                String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                BlockbenchAnimation parsed = BlockbenchAnimationParser.parse(content);
                if (parsed != null) {
                    Emote emote = new Emote(
                            "hands_up", "emotes_wheel_hands_up",
                            Identifier.of("mooclient", "textures/gui/emotes/hands_up.png"),
                            EmoteType.SOLO, 1,
                            0, // Stałe trzymanie dopóki nie zostanie wywołane stop
                            false,
                            true, false, // free = true, forcesThirdPerson = false (nie zmienia kamery)
                            parsed, null
                    );
                    register(emote);
                    MooClient.LOGGER.info("Pomyślnie załadowano wbudowaną standardową emotkę: hands_up");
                }
            }
        } catch (Exception e) {
            MooClient.LOGGER.error("Błąd ładowania wbudowanej emotki hands_up", e);
        }
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
