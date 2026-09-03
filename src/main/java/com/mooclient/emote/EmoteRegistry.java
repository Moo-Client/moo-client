package com.mooclient.emote;

import com.mooclient.MooClient;
import com.mooclient.emote.animation.BlockbenchAnimation;
import com.mooclient.emote.animation.Keyframe;
import net.minecraft.util.Identifier;

import java.util.*;

/**
 * Centralny rejestr emotek (Solo i Multiplayer) w Moo Client.
 * Hands Up jest stałą, wbudowaną emotką taktyczną,
 * a pozostałe emotki ładowane są dynamicznie z bazy Supabase.
 */
public class EmoteRegistry {

    private static final Map<String, Emote> REGISTRY = new LinkedHashMap<>();
    private static final List<Emote> ALL_EMOTES = new ArrayList<>();
    private static final List<Emote> SOLO_EMOTES = new ArrayList<>();
    private static final List<Emote> MULTIPLAYER_EMOTES = new ArrayList<>();

    public static synchronized void init() {
        REGISTRY.clear();
        ALL_EMOTES.clear();
        SOLO_EMOTES.clear();
        MULTIPLAYER_EMOTES.clear();

        // 1. Zawsze ładuj wbudowaną na stałe emotkę Hands Up
        loadBuiltinHandsUp();

        // 2. Ładowanie dynamicznych emotek z Supabase i cache
        EmoteRemoteLoader.init();

        MooClient.LOGGER.info("Zainicjalizowano dynamiczny EmoteRegistry z wbudowanym Hands Up.");
    }

    public static void loadBuiltinHandsUp() {
        BlockbenchAnimation anim = new BlockbenchAnimation("hands_up", 0.25f, false);
        BlockbenchAnimation.BoneTracks rArm = anim.getOrCreateBone("right_arm");
        rArm.rotation.addKeyframe(new Keyframe(0.0f, 0.0f, 0.0f, 0.0f));
        rArm.rotation.addKeyframe(new Keyframe(0.08f, -60.0f, 0.0f, 0.0f));
        rArm.rotation.addKeyframe(new Keyframe(0.16f, -135.0f, 0.0f, 0.0f));
        rArm.rotation.addKeyframe(new Keyframe(0.25f, -180.0f, 0.0f, 0.0f));

        BlockbenchAnimation.BoneTracks lArm = anim.getOrCreateBone("left_arm");
        lArm.rotation.addKeyframe(new Keyframe(0.0f, 0.0f, 0.0f, 0.0f));
        lArm.rotation.addKeyframe(new Keyframe(0.08f, -60.0f, 0.0f, 0.0f));
        lArm.rotation.addKeyframe(new Keyframe(0.16f, -135.0f, 0.0f, 0.0f));
        lArm.rotation.addKeyframe(new Keyframe(0.25f, -180.0f, 0.0f, 0.0f));

        Emote emote = new Emote(
                "hands_up", "emotes_wheel_hands_up",
                Identifier.of("mooclient", "textures/gui/emotes/hands_up.png"),
                EmoteType.SOLO, 1,
                0, // 0 = stałe trzymanie dopóki nie zostanie wywołane stop
                false,
                true, false, // free = true, forcesThirdPerson = false
                anim, null
        );
        register(emote);
        MooClient.LOGGER.info("Pomyślnie zarejestrowano wbudowaną stałą emotkę: hands_up");
    }

    public static synchronized void register(Emote emote) {
        if (emote == null || emote.getId() == null) return;
        String id = emote.getId().toLowerCase().trim();
        Emote old = REGISTRY.put(id, emote);
        if (old != null && old != emote) {
            ALL_EMOTES.remove(old);
            SOLO_EMOTES.remove(old);
            MULTIPLAYER_EMOTES.remove(old);
        }

        if (!ALL_EMOTES.contains(emote)) {
            ALL_EMOTES.add(emote);
        }
        if (emote.isMultiplayer()) {
            if (!MULTIPLAYER_EMOTES.contains(emote)) MULTIPLAYER_EMOTES.add(emote);
            SOLO_EMOTES.remove(emote);
        } else {
            if (!SOLO_EMOTES.contains(emote)) SOLO_EMOTES.add(emote);
            MULTIPLAYER_EMOTES.remove(emote);
        }

        EmoteEngine.getInstance().onEmoteRegistered(emote);
    }

    public static synchronized void unregister(String id) {
        if (id == null) return;
        Emote emote = REGISTRY.remove(id.toLowerCase().trim());
        if (emote != null) {
            ALL_EMOTES.remove(emote);
            SOLO_EMOTES.remove(emote);
            MULTIPLAYER_EMOTES.remove(emote);
        }
    }

    public static synchronized Emote get(String id) {
        if (id == null) return null;
        return REGISTRY.get(id.toLowerCase().trim());
    }

    public static synchronized List<Emote> getAll() {
        return Collections.unmodifiableList(new ArrayList<>(ALL_EMOTES));
    }

    public static synchronized List<Emote> getSoloEmotes() {
        return Collections.unmodifiableList(new ArrayList<>(SOLO_EMOTES));
    }

    public static synchronized List<Emote> getMultiplayerEmotes() {
        return Collections.unmodifiableList(new ArrayList<>(MULTIPLAYER_EMOTES));
    }

    public static synchronized boolean has(String id) {
        if (id == null) return false;
        Emote e = REGISTRY.get(id.toLowerCase().trim());
        return e != null && e.getAnimation() != null;
    }
}
