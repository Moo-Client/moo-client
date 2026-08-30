package com.mooclient.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mooclient.emote.Emote;
import com.mooclient.emote.EmoteRegistry;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Path;
import java.util.*;

/**
 * Menedżer konfiguracji 12-slotowego koła emotek.
 * Gwarantuje, że każda emotka może znajdować się na kole co najwyżej RAZ (brak duplikatów).
 */
public class EmoteWheelConfig {

    public static final int TOTAL_SLOTS = 12;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("mooclient_emotes.json");

    private static final String[] slots = new String[TOTAL_SLOTS];

    static {
        load();
    }

    public static synchronized String getSlot(int index) {
        if (index < 0 || index >= TOTAL_SLOTS) return null;
        return slots[index];
    }

    public static synchronized Emote getSlotEmote(int index) {
        String id = getSlot(index);
        if (id == null) return null;
        Emote emote = EmoteRegistry.get(id);
        return (emote != null && emote.getAnimation() != null) ? emote : null;
    }

    public static synchronized boolean hasEmoteInAnySlot(String emoteId) {
        if (emoteId == null) return false;
        String clean = emoteId.trim().toLowerCase();
        for (int i = 0; i < TOTAL_SLOTS; i++) {
            if (clean.equalsIgnoreCase(slots[i])) {
                return true;
            }
        }
        return false;
    }

    public static synchronized void setSlot(int index, String emoteId) {
        if (index < 0 || index >= TOTAL_SLOTS) return;
        if (emoteId == null || emoteId.trim().isEmpty()) {
            slots[index] = null;
            save();
            return;
        }

        String clean = emoteId.trim().toLowerCase();

        // Usunięcie z każdego innego slotu, aby zapobiec duplikatom
        for (int i = 0; i < TOTAL_SLOTS; i++) {
            if (i != index && clean.equalsIgnoreCase(slots[i])) {
                slots[i] = null;
            }
        }

        slots[index] = clean;
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

    public static synchronized List<String> getActiveSlotsList() {
        List<String> list = new ArrayList<>(TOTAL_SLOTS);
        for (int i = 0; i < TOTAL_SLOTS; i++) {
            list.add(slots[i]);
        }
        return list;
    }

    public static synchronized void setActiveSlots(List<String> newSlots) {
        Arrays.fill(slots, null);
        Set<String> added = new HashSet<>();
        if (newSlots != null) {
            for (int i = 0; i < Math.min(newSlots.size(), TOTAL_SLOTS); i++) {
                String id = newSlots.get(i);
                if (id != null && !id.trim().isEmpty()) {
                    String clean = id.trim().toLowerCase();
                    if (added.add(clean)) {
                        slots[i] = clean;
                    }
                }
            }
        }
        save();
    }

    public static synchronized void resetDefaults() {
        Arrays.fill(slots, null);
        List<Emote> available = EmoteRegistry.getAll();
        int slot = 0;
        for (Emote e : available) {
            if (slot >= TOTAL_SLOTS) break;
            if (e != null && e.getId() != null && !"hands_up".equalsIgnoreCase(e.getId())) {
                slots[slot++] = e.getId().toLowerCase();
            }
        }
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
                    Set<String> added = new HashSet<>();
                    for (int i = 0; i < TOTAL_SLOTS; i++) {
                        String key = String.valueOf(i);
                        if (slotsObj.has(key) && !slotsObj.get(key).isJsonNull()) {
                            String emoteId = slotsObj.get(key).getAsString();
                            if (emoteId != null && !emoteId.trim().isEmpty()) {
                                String clean = emoteId.trim().toLowerCase();
                                if (added.add(clean)) {
                                    slots[i] = clean;
                                }
                            }
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }
    }

    public static synchronized void save() {
        try {
            File file = CONFIG_PATH.toFile();
            if (file.getParentFile() != null && !file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }

            JsonObject json = new JsonObject();
            JsonObject slotsObj = new JsonObject();
            Set<String> written = new HashSet<>();
            for (int i = 0; i < TOTAL_SLOTS; i++) {
                if (slots[i] != null && !slots[i].trim().isEmpty()) {
                    String clean = slots[i].trim().toLowerCase();
                    if (written.add(clean)) {
                        slotsObj.addProperty(String.valueOf(i), clean);
                    }
                }
            }
            json.add("slots", slotsObj);

            try (FileWriter writer = new FileWriter(file)) {
                GSON.toJson(json, writer);
            }
        } catch (Exception ignored) {
        }
    }
}
