package com.mooclient.emote;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mooclient.MooClient;
import com.mooclient.emote.animation.BlockbenchAnimation;
import com.mooclient.emote.animation.BlockbenchAnimationParser;
import com.mooclient.interaction.InteractionSceneConfig;
import com.mooclient.util.EmoteWheelConfig;
import net.minecraft.util.Identifier;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * System dynamicznego ładowania i synchronizacji emotek bez konieczności aktualizacji launchera ani klienta.
 *
 * 1. Skanuje lokalny katalog dyskowy użytkownika: ~/.mooclient/emotes/ (można bezpośrednio wrzucać pliki .json / .bbmodel).
 * 2. Pobiera nowe emotki z chmury Supabase REST API / CDN w tle.
 * 3. Obsługuje pobieranie na żądanie (On-Demand), gdy inny gracz użyje nowo dodanej emotki.
 */
public class EmoteRemoteLoader {

    private static final String DEFAULT_SUPABASE_URL = "https://godjpceymapadkmqjrpj.supabase.co/rest/v1/emotes";
    private static final String DEFAULT_SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImdvZGpwY2V5bWFwYWRrbXFqcnBqIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODgwMjYwMTIsImV4cCI6MjEwMzYwMjAxMn0.VY52MMlGLdJsCMzh981JLzQkUFkbX7-YGZ0E2TY-weo";

    private static final File LOCAL_EMOTES_DIR;
    private static final File CACHE_DIR;
    private static final ConcurrentHashMap<String, Boolean> PENDING_DOWNLOADS = new ConcurrentHashMap<>();

    static {
        File home = new File(System.getProperty("user.home"), ".mooclient");
        LOCAL_EMOTES_DIR = new File(home, "emotes");
        CACHE_DIR = new File(LOCAL_EMOTES_DIR, "cache");

        if (!LOCAL_EMOTES_DIR.exists()) LOCAL_EMOTES_DIR.mkdirs();
        if (!CACHE_DIR.exists()) CACHE_DIR.mkdirs();
    }

    public static File getLocalEmotesDir() {
        return LOCAL_EMOTES_DIR;
    }

    /**
     * Główna metoda inicjalizacyjna wywoływana przy starcie gry.
     */
    public static void init() {
        // 1. Natychmiastowe załadowanie plików z dysku użytkownika ~/.mooclient/emotes/
        loadLocalDiskEmotes();

        // 2. Asynchroniczne odpytanie Supabase o nowe definicje i animacje w chmurze
        fetchRemoteEmotesAsync();
    }

    /**
     * Ładuje wszystkie pliki .json / .bbmodel znajdujące się bezpośrednio w ~/.mooclient/emotes/ oraz w podkatalogu cache.
     */
    public static void loadLocalDiskEmotes() {
        try {
            if (LOCAL_EMOTES_DIR.exists() && LOCAL_EMOTES_DIR.isDirectory()) {
                File[] files = LOCAL_EMOTES_DIR.listFiles((dir, name) -> name.endsWith(".json") || name.endsWith(".bbmodel"));
                if (files != null) {
                    for (File file : files) {
                        loadEmoteFromFile(file);
                    }
                }
            }

            if (CACHE_DIR.exists() && CACHE_DIR.isDirectory()) {
                File[] cacheFiles = CACHE_DIR.listFiles((dir, name) -> name.endsWith(".json") || name.endsWith(".bbmodel"));
                if (cacheFiles != null) {
                    for (File file : cacheFiles) {
                        loadEmoteFromFile(file);
                    }
                }
            }
        } catch (Exception e) {
            MooClient.LOGGER.error("Błąd podczas odczytu lokalnych emotek z ~/.mooclient/emotes/", e);
        }
    }

    private static void loadEmoteFromFile(File file) {
        try {
            String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            BlockbenchAnimation anim = BlockbenchAnimationParser.parse(content);
            if (anim == null) return;

            String fileName = file.getName();
            String id = fileName.substring(0, fileName.lastIndexOf('.')).toLowerCase();

            boolean multi = "handshake".equals(id);
            int durationTicks = Math.round(anim.getLengthSeconds() * 20.0f);
            boolean looping = anim.isLooping();
            boolean free = "hands_up".equals(id);
            InteractionSceneConfig sceneConfig = multi ? InteractionSceneConfig.createFacingDuo(1.0f) : null;

            Emote existing = EmoteRegistry.get(id);
            if (existing != null) {
                existing.setAnimation(anim);
                existing.setFree(free);
                MooClient.LOGGER.info("Zaktualizowano animację z pliku dyskowego dla: {}", id);
            } else {
                Emote newEmote = new Emote(
                        id, "emotes_wheel_" + id,
                        resolveIcon(id),
                        multi ? EmoteType.MULTIPLAYER : EmoteType.SOLO, multi ? 2 : 1,
                        durationTicks, looping, free, true,
                        anim, sceneConfig
                );
                EmoteRegistry.register(newEmote);
                assignToFirstEmptySlot(id);
                MooClient.LOGGER.info("Zarejestrowano nową dynamiczną emotkę z pliku {}: {}", fileName, id);
            }
        } catch (Exception ignored) {}
    }

    /**
     * Asynchronicznie pobiera listę nowych emotek z bazy danych Supabase (tabela 'emotes').
     */
    public static CompletableFuture<Void> fetchRemoteEmotesAsync() {
        return CompletableFuture.runAsync(() -> {
            try {
                String endpoint = System.getProperty("mooclient.supabase.emotes.url", DEFAULT_SUPABASE_URL);
                String key = System.getProperty("mooclient.supabase.key", DEFAULT_SUPABASE_KEY);

                URI uri = URI.create(endpoint + "?select=*");
                HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("apikey", key);
                conn.setRequestProperty("Authorization", "Bearer " + key);
                conn.setRequestProperty("Accept", "application/json");
                conn.setConnectTimeout(4000);
                conn.setReadTimeout(4000);

                if (conn.getResponseCode() == 200) {
                    try (InputStream is = conn.getInputStream()) {
                        String json = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                        JsonElement element = JsonParser.parseString(json);
                        if (element.isJsonArray()) {
                            JsonArray array = element.getAsJsonArray();
                            for (JsonElement item : array) {
                                if (item.isJsonObject()) {
                                    processRemoteEmoteEntry(item.getAsJsonObject());
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                // Bezpieczne ciche pominięcie w trybie offline
            }
        });
    }

    private static void processRemoteEmoteEntry(JsonObject obj) {
        try {
            if (!obj.has("id")) return;
            String id = obj.get("id").getAsString().toLowerCase().trim();
            String name = obj.has("name") ? obj.get("name").getAsString() : id;
            String typeStr = obj.has("type") ? obj.get("type").getAsString() : "solo";
            EmoteType type = typeStr.equalsIgnoreCase("multiplayer") ? EmoteType.MULTIPLAYER : EmoteType.SOLO;
            boolean isFree = obj.has("is_free") && !obj.get("is_free").isJsonNull() && obj.get("is_free").getAsBoolean();

            // 1. Jeśli animacja jest przesłana bezpośrednio jako JSON w bazie
            if (obj.has("animation_data") && !obj.get("animation_data").isJsonNull()) {
                String animJson = obj.get("animation_data").isJsonObject()
                        ? obj.get("animation_data").toString()
                        : obj.get("animation_data").getAsString();

                saveAndRegisterAnimation(id, name, type, isFree, animJson);
                return;
            }

            // 2. Jeśli animacja znajduje się pod zdalnym adresem URL (CDN / Supabase Storage)
            if (obj.has("animation_url") && !obj.get("animation_url").isJsonNull()) {
                String animUrl = obj.get("animation_url").getAsString();
                downloadAnimationFromUrl(id, name, type, isFree, animUrl);
            }
        } catch (Exception e) {
            MooClient.LOGGER.warn("Błąd parsowania zdalnego rekordu emotki", e);
        }
    }

    private static void downloadAnimationFromUrl(String id, String name, EmoteType type, boolean isFree, String urlStr) {
        if (PENDING_DOWNLOADS.putIfAbsent(id, Boolean.TRUE) != null) {
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                URI uri = URI.create(urlStr);
                HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                if (conn.getResponseCode() == 200) {
                    try (InputStream is = conn.getInputStream()) {
                        String animJson = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                        saveAndRegisterAnimation(id, name, type, isFree, animJson);
                    }
                }
            } catch (Exception ignored) {
            } finally {
                PENDING_DOWNLOADS.remove(id);
            }
        });
    }

    private static void saveAndRegisterAnimation(String id, String name, EmoteType type, boolean isFree, String animJson) {
        try {
            // Zapis do cache na dysku
            File cacheFile = new File(CACHE_DIR, id + ".json");
            try (FileWriter writer = new FileWriter(cacheFile, StandardCharsets.UTF_8)) {
                writer.write(animJson);
            }

            BlockbenchAnimation anim = BlockbenchAnimationParser.parse(animJson);
            if (anim == null) return;

            int durationTicks = Math.round(anim.getLengthSeconds() * 20.0f);
            boolean looping = anim.isLooping();

            InteractionSceneConfig sceneConfig = (type == EmoteType.MULTIPLAYER)
                    ? InteractionSceneConfig.createFacingDuo(1.0f)
                    : null;

            Emote existing = EmoteRegistry.get(id);
            if (existing != null) {
                existing.setAnimation(anim);
                existing.setFree(isFree);
            } else {
                Emote emote = new Emote(
                        id, "emotes_wheel_" + id,
                        resolveIcon(id),
                        type, type == EmoteType.MULTIPLAYER ? 2 : 1,
                        durationTicks, looping, isFree, true,
                        anim, sceneConfig
                );
                EmoteRegistry.register(emote);
                assignToFirstEmptySlot(id);
                MooClient.LOGGER.info("Pomyślnie zsynchronizowano nową emotkę z chmury: {}", id);
            }
        } catch (Exception e) {
            MooClient.LOGGER.error("Błąd podczas rejestracji pobranej animacji: " + id, e);
        }
    }

    private static Identifier resolveIcon(String id) {
        return Identifier.of("mooclient", "textures/gui/emotes/" + id + ".png");
    }

    private static void assignToFirstEmptySlot(String emoteId) {
        if (emoteId == null || emoteId.trim().isEmpty()) return;
        if (EmoteWheelConfig.hasEmoteInAnySlot(emoteId)) return;
        for (int i = 0; i < EmoteWheelConfig.TOTAL_SLOTS; i++) {
            if (EmoteWheelConfig.getSlot(i) == null) {
                EmoteWheelConfig.setSlot(i, emoteId);
                break;
            }
        }
    }

    /**
     * Pobieranie animacji na żądanie (On-Demand), gdy lokalny klient napotka nieznane ID emotki od innego gracza.
     */
    public static void fetchOnDemandIfMissing(String emoteId) {
        if (emoteId == null || EmoteRegistry.has(emoteId)) return;
        if (PENDING_DOWNLOADS.putIfAbsent(emoteId, Boolean.TRUE) != null) return;

        CompletableFuture.runAsync(() -> {
            try {
                String endpoint = System.getProperty("mooclient.supabase.emotes.url", DEFAULT_SUPABASE_URL);
                String key = System.getProperty("mooclient.supabase.key", DEFAULT_SUPABASE_KEY);

                URI uri = URI.create(endpoint + "?id=eq." + emoteId + "&select=*");
                HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("apikey", key);
                conn.setRequestProperty("Authorization", "Bearer " + key);
                conn.setRequestProperty("Accept", "application/json");
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(3000);

                if (conn.getResponseCode() == 200) {
                    try (InputStream is = conn.getInputStream()) {
                        String json = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                        JsonElement element = JsonParser.parseString(json);
                        if (element.isJsonArray()) {
                            JsonArray array = element.getAsJsonArray();
                            if (array.size() > 0) {
                                processRemoteEmoteEntry(array.get(0).getAsJsonObject());
                            }
                        }
                    }
                }
            } catch (Exception ignored) {
            } finally {
                PENDING_DOWNLOADS.remove(emoteId);
            }
        });
    }
}
