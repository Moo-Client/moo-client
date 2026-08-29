package com.mooclient.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mooclient.module.modules.EmotesModule;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Identifier;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Path;
import java.util.*;

/**
 * Manages 12-slot Emote Wheel configuration, persistent JSON saving,
 * and custom slot assignment for Moo Client emotes.
 */
public class EmoteWheelConfig {

    public static final int TOTAL_SLOTS = 12;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("mooclient_emotes.json");

    public record EmoteDefinition(
            String id,
            String nameKey,
            Identifier icon,
            EmoteAccessManager.EmoteId accessId,
            Runnable triggerAction
    ) {
        public String getDisplayName() {
            return MooLanguage.get(nameKey);
        }

        public boolean isUnlocked() {
            return EmoteAccessManager.hasAccess(accessId);
        }
    }

    private static final Map<String, EmoteDefinition> REGISTRY = new LinkedHashMap<>();
    private static final String[] slots = new String[TOTAL_SLOTS];

    static {
        register(new EmoteDefinition(
                "frontflip",
                "emotes_wheel_frontflip",
                Identifier.of("mooclient", "textures/gui/emotes/frontflip.png"),
                EmoteAccessManager.EmoteId.FRONTFLIP,
                EmotesModule::triggerFrontflipFromWheel
        ));

        register(new EmoteDefinition(
                "backflip",
                "emotes_wheel_backflip",
                Identifier.of("mooclient", "textures/gui/emotes/backflip.png"),
                EmoteAccessManager.EmoteId.BACKFLIP,
                EmotesModule::triggerBackflipFromWheel
        ));

        register(new EmoteDefinition(
                "meditation",
                "emotes_wheel_meditation",
                Identifier.of("mooclient", "textures/gui/emotes/meditation.png"),
                EmoteAccessManager.EmoteId.MEDITATION,
                () -> EmotesModule.triggerGenericEmote(EmotesModule.EmoteType.MEDITATION)
        ));

        register(new EmoteDefinition(
                "friendly_wave",
                "emotes_wheel_friendly_wave",
                Identifier.of("mooclient", "textures/gui/emotes/friendly_wave.png"),
                EmoteAccessManager.EmoteId.FREE,
                () -> EmotesModule.triggerGenericEmote(EmotesModule.EmoteType.FRIENDLY_WAVE)
        ));

        register(new EmoteDefinition(
                "facepalm",
                "emotes_wheel_facepalm",
                Identifier.of("mooclient", "textures/gui/emotes/facepalm.png"),
                EmoteAccessManager.EmoteId.FACEPALM,
                () -> EmotesModule.triggerGenericEmote(EmotesModule.EmoteType.FACEPALM)
        ));

        // Load configuration on class initialization
        load();
    }

    public static void register(EmoteDefinition definition) {
        REGISTRY.put(definition.id(), definition);
    }

    public static Collection<EmoteDefinition> getAllEmotes() {
        return Collections.unmodifiableCollection(REGISTRY.values());
    }

    public static EmoteDefinition getDefinition(String id) {
        if (id == null) return null;
        return REGISTRY.get(id);
    }

    public static synchronized String getSlot(int index) {
        if (index < 0 || index >= TOTAL_SLOTS) return null;
        return slots[index];
    }

    public static synchronized EmoteDefinition getSlotDefinition(int index) {
        String id = getSlot(index);
        return id != null ? getDefinition(id) : null;
    }

    public static synchronized void setSlot(int index, String emoteId) {
        if (index < 0 || index >= TOTAL_SLOTS) return;
        slots[index] = (emoteId != null && REGISTRY.containsKey(emoteId)) ? emoteId : null;
        save();
    }

    public static synchronized void clearSlot(int index) {
        if (index < 0 || index >= TOTAL_SLOTS) return;
        slots[index] = null;
        save();
    }

    public static synchronized void clearAllSlots() {
        Arrays.fill(slots, null);
        save();
    }

    public static synchronized void swapSlots(int fromIndex, int toIndex) {
        if (fromIndex < 0 || fromIndex >= TOTAL_SLOTS) return;
        if (toIndex < 0 || toIndex >= TOTAL_SLOTS) return;
        if (fromIndex == toIndex) return;

        String temp = slots[fromIndex];
        slots[fromIndex] = slots[toIndex];
        slots[toIndex] = temp;
        save();
    }

    public static synchronized boolean hasAnyEquippedEmote() {
        for (String slot : slots) {
            if (slot != null && REGISTRY.containsKey(slot)) {
                return true;
            }
        }
        return false;
    }

    public static synchronized Set<String> getEquippedEmoteIds() {
        Set<String> set = new HashSet<>();
        for (String slot : slots) {
            if (slot != null && REGISTRY.containsKey(slot)) {
                set.add(slot);
            }
        }
        return set;
    }

    public static synchronized List<String> getActiveSlotsList() {
        List<String> list = new ArrayList<>(TOTAL_SLOTS);
        for (int i = 0; i < TOTAL_SLOTS; i++) {
            list.add(slots[i]);
        }
        return list;
    }

    public static synchronized void setActiveSlots(List<String> newSlots) {
        Arrays.fill(slots, null);
        if (newSlots != null) {
            for (int i = 0; i < Math.min(newSlots.size(), TOTAL_SLOTS); i++) {
                String id = newSlots.get(i);
                if (id != null && REGISTRY.containsKey(id)) {
                    slots[i] = id;
                }
            }
        }
        save();
    }

    public static synchronized void resetDefaults() {
        Arrays.fill(slots, null);
        slots[0] = "frontflip";     // Slot #1 (12:00)
        slots[1] = "backflip";      // Slot #2 (1:00)
        slots[2] = "meditation";    // Slot #3 (2:00)
        slots[3] = "friendly_wave"; // Slot #4 (3:00)
        slots[4] = "facepalm";      // Slot #5 (4:00)
        save();
    }

    public static synchronized void load() {
        Arrays.fill(slots, null);
        File file = CONFIG_PATH.toFile();
        if (file.exists()) {
            try (FileReader reader = new FileReader(file)) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                if (json.has("slots") && json.get("slots").isJsonObject()) {
                    JsonObject slotsObj = json.getAsJsonObject("slots");
                    for (int i = 0; i < TOTAL_SLOTS; i++) {
                        String key = String.valueOf(i);
                        if (slotsObj.has(key) && !slotsObj.get(key).isJsonNull()) {
                            String emoteId = slotsObj.get(key).getAsString();
                            if (REGISTRY.containsKey(emoteId)) {
                                slots[i] = emoteId;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            resetDefaults();
        }
    }

    public static synchronized void save() {
        try {
            File file = CONFIG_PATH.toFile();
            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }

            JsonObject json = new JsonObject();
            JsonObject slotsObj = new JsonObject();

            for (int i = 0; i < TOTAL_SLOTS; i++) {
                if (slots[i] != null && REGISTRY.containsKey(slots[i])) {
                    slotsObj.addProperty(String.valueOf(i), slots[i]);
                }
            }
            json.add("slots", slotsObj);

            try (FileWriter writer = new FileWriter(file)) {
                GSON.toJson(json, writer);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
