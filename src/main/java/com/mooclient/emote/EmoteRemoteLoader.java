package com.mooclient.emote;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mooclient.MooClient;
import com.mooclient.emote.animation.BlockbenchAnimation;
import com.mooclient.emote.animation.BlockbenchAnimationParser;
import com.mooclient.interaction.InteractionSceneConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * System dynamicznego ładowania i synchronizacji emotek oraz ikon w 100% z chmury Supabase.
 * Baza Supabase jest jedynym nadrzędnym źródłem prawdy (Single Source of Truth).
 * Pobiera zarówno modele .bbmodel, jak i ikony .png w locie bez konieczności aktualizacji klienta.
 */
public class EmoteRemoteLoader {

    private static final String DEFAULT_SUPABASE_URL = "https://godjpceymapadkmqjrpj.supabase.co/rest/v1/emotes";
    private static final String DEFAULT_SUPABASE_STORAGE_URL = "https://godjpceymapadkmqjrpj.supabase.co/storage/v1/object/public/emotes";
    private static final String DEFAULT_SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImdvZGpwY2V5bWFwYWRrbXFqcnBqIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODgwMjYwMTIsImV4cCI6MjEwMzYwMjAxMn0.VY52MMlGLdJsCMzh981JLzQkUFkbX7-YGZ0E2TY-weo";

    private static final Identifier FALLBACK_ICON = Identifier.of("mooclient", "textures/gui/emotes/hands_up.png");

    private static final File CACHE_DIR;
    private static final File ICONS_CACHE_DIR;
    private static final ConcurrentHashMap<String, Boolean> PENDING_DOWNLOADS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Boolean> PENDING_ICON_DOWNLOADS = new ConcurrentHashMap<>();
    private static final Map<String, Identifier> DYNAMIC_ICONS = new ConcurrentHashMap<>();

    static {
        File home = new File(System.getProperty("user.home"), ".mooclient");
        File localEmotes = new File(home, "emotes");
        CACHE_DIR = new File(localEmotes, "cache");
        ICONS_CACHE_DIR = new File(CACHE_DIR, "icons");

        if (!CACHE_DIR.exists()) CACHE_DIR.mkdirs();
        if (!ICONS_CACHE_DIR.exists()) ICONS_CACHE_DIR.mkdirs();
    }

    public static File getCacheDir() {
        return CACHE_DIR;
    }

    public static File getIconsCacheDir() {
        return ICONS_CACHE_DIR;
    }

    /**
     * Główna metoda inicjalizacyjna wywoływana przy starcie gry.
     */
    public static void init() {
        // 1. Wczytanie z lokalnego cache na wypadek braku połączenia z siecią (tryb offline)
        loadCachedEmotes();

        // 2. Asynchroniczne pobranie z Supabase jako jedynego nadrzędnego źródła
        fetchRemoteEmotesAsync();
    }

    /**
     * Odświeża listę emotek z Supabase w tle (np. przy wejściu do edytora koła).
     */
    public static CompletableFuture<Void> refreshRemoteEmotesAsync() {
        return fetchRemoteEmotesAsync();
    }

    /**
     * Wczytuje pobrane wcześniej animacje z lokalnego cache na wypadek braku internetu.
     */
    public static void loadCachedEmotes() {
        try {
            if (CACHE_DIR.exists() && CACHE_DIR.isDirectory()) {
                File[] cacheFiles = CACHE_DIR.listFiles((dir, name) -> name.endsWith(".json") || name.endsWith(".bbmodel"));
                if (cacheFiles != null) {
                    for (File file : cacheFiles) {
                        try {
                            String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
                            BlockbenchAnimation anim = BlockbenchAnimationParser.parse(content);
                            if (anim == null) continue;

                            String fileName = file.getName();
                            String id = fileName.substring(0, fileName.lastIndexOf('.')).toLowerCase();

                            int durationTicks = Math.round(anim.getLengthSeconds() * 20.0f);
                            boolean looping = anim.isLooping();

                            Emote existing = EmoteRegistry.get(id);
                            if (existing != null) {
                                existing.setAnimation(anim);
                            } else {
                                Emote newEmote = new Emote(
                                        id, "emotes_wheel_" + id,
                                        resolveIcon(id, null),
                                        EmoteType.SOLO, 1,
                                        durationTicks, looping, false, true,
                                        anim, null
                                );
                                EmoteRegistry.register(newEmote);
                            }
                        } catch (Exception ignored) {}
                    }
                }
            }
        } catch (Exception e) {
            MooClient.LOGGER.error("Błąd podczas odczytu cache emotek", e);
        }
    }

    /**
     * Asynchronicznie pobiera listę wszystkich emotek z bazy danych Supabase (tabela 'emotes').
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
                } else {
                    MooClient.LOGGER.warn("Supabase zwróciło kod odpowiedzi: {}", conn.getResponseCode());
                }
            } catch (Exception e) {
                MooClient.LOGGER.info("Supabase w trybie offline: {}", e.getMessage());
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
            boolean forcesThirdPerson = !obj.has("forces_third_person") || obj.get("forces_third_person").isJsonNull() || obj.get("forces_third_person").getAsBoolean();

            // Odczyt linku do ikony (z kolumny icon_url lub domyślnego magazynu CDN)
            String iconUrl = null;
            if (obj.has("icon_url") && !obj.get("icon_url").isJsonNull()) {
                String raw = obj.get("icon_url").getAsString();
                if (raw != null && !raw.trim().isEmpty()) {
                    iconUrl = raw.trim().split("\\s+")[0];
                }
            }
            if (iconUrl == null) {
                iconUrl = DEFAULT_SUPABASE_STORAGE_URL + "/" + id + ".png";
            }

            // 1. Jeśli animacja jest przesłana bezpośrednio jako JSON w bazie
            if (obj.has("animation_data") && !obj.get("animation_data").isJsonNull()) {
                String animJson = obj.get("animation_data").isJsonObject()
                        ? obj.get("animation_data").toString()
                        : obj.get("animation_data").getAsString();

                saveAndRegisterAnimation(id, name, type, isFree, forcesThirdPerson, iconUrl, animJson);
                return;
            }

            // 2. Jeśli animacja znajduje się pod zdalnym adresem URL (CDN / Supabase Storage)
            if (obj.has("animation_url") && !obj.get("animation_url").isJsonNull()) {
                String rawUrl = obj.get("animation_url").getAsString();
                if (rawUrl != null && !rawUrl.trim().isEmpty()) {
                    // Sanityzacja URL (oczyszczenie z enterów i spacji)
                    String animUrl = rawUrl.trim().split("\\s+")[0];
                    downloadAnimationFromUrl(id, name, type, isFree, forcesThirdPerson, iconUrl, animUrl);
                }
            }
        } catch (Exception e) {
            MooClient.LOGGER.warn("Błąd parsowania rekordu emotki z Supabase", e);
        }
    }

    private static void downloadAnimationFromUrl(String id, String name, EmoteType type, boolean isFree, boolean forcesThirdPerson, String iconUrl, String urlStr) {
        if (urlStr == null || urlStr.trim().isEmpty()) return;
        String cleanUrl = urlStr.trim().split("\\s+")[0];

        if (PENDING_DOWNLOADS.putIfAbsent(id, Boolean.TRUE) != null) {
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                URI uri = URI.create(cleanUrl);
                HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                if (conn.getResponseCode() == 200) {
                    try (InputStream is = conn.getInputStream()) {
                        String animJson = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                        saveAndRegisterAnimation(id, name, type, isFree, forcesThirdPerson, iconUrl, animJson);
                    }
                }
            } catch (Exception ignored) {
            } finally {
                PENDING_DOWNLOADS.remove(id);
            }
        });
    }

    private static void saveAndRegisterAnimation(String id, String name, EmoteType type, boolean isFree, boolean forcesThirdPerson, String iconUrl, String animJson) {
        try {
            // Zapis do lokalnego cache
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
                // Upewnij się, że ikona jest dynamicznie odpytana
                resolveIcon(id, iconUrl);
            } else {
                Identifier iconId = resolveIcon(id, iconUrl);
                Emote emote = new Emote(
                        id, "emotes_wheel_" + id,
                        iconId,
                        type, type == EmoteType.MULTIPLAYER ? 2 : 1,
                        durationTicks, looping, isFree, forcesThirdPerson,
                        anim, sceneConfig
                );
                EmoteRegistry.register(emote);
                MooClient.LOGGER.info("Pomyślnie załadowano emotkę z Supabase: {}", id);
            }
        } catch (Exception e) {
            MooClient.LOGGER.error("Błąd rejestracji pobranej animacji z Supabase: " + id, e);
        }
    }

    /**
     * Zwraca identyfikator ikony:
     * 1. Jeśli już pobrano dynamiczną ikonę z Supabase -> zwraca zarejestrowany Identifier.
     * 2. Jeśli istnieje wbudowany plik w JAR -> zwraca wbudowany Identifier.
     * 3. Jeśli istnieje plik w cache dyskowym -> ładuje i rejestruje w locie.
     * 4. W przeciwnym razie -> rozpoczyna pobieranie w tle i zwraca bezpieczny FALLBACK_ICON.
     */
    public static Identifier resolveIcon(String id, String iconUrl) {
        if (id == null || id.isEmpty()) return FALLBACK_ICON;
        String cleanId = id.toLowerCase().trim();

        // 1. Zarejestrowana już tekstura dynamiczna
        Identifier dynamicCached = DYNAMIC_ICONS.get(cleanId);
        if (dynamicCached != null) {
            return dynamicCached;
        }

        // 2. Sprawdzenie wbudowanego zasobu wewnątrz JAR
        try (InputStream is = EmoteRemoteLoader.class.getResourceAsStream("/assets/mooclient/textures/gui/emotes/" + cleanId + ".png")) {
            if (is != null) {
                Identifier builtin = Identifier.of("mooclient", "textures/gui/emotes/" + cleanId + ".png");
                DYNAMIC_ICONS.put(cleanId, builtin);
                return builtin;
            }
        } catch (Exception ignored) {}

        // 3. Sprawdzenie ikony w lokalnej pamięci podręcznej (~/.mooclient/emotes/cache/icons/<id>.png)
        File cachedIconFile = new File(ICONS_CACHE_DIR, cleanId + ".png");
        if (cachedIconFile.exists() && cachedIconFile.length() > 0) {
            loadDynamicTextureFromFile(cleanId, cachedIconFile);
            Identifier registered = DYNAMIC_ICONS.get(cleanId);
            if (registered != null) return registered;
        }

        // 4. Uruchomienie asynchronicznego pobierania ikony ze zdalnego URL
        downloadIconFromUrl(cleanId, iconUrl);

        return FALLBACK_ICON;
    }

    private static void loadDynamicTextureFromFile(String id, File file) {
        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            registerDynamicTextureBytes(id, bytes);
        } catch (Exception ignored) {}
    }

    private static void downloadIconFromUrl(String id, String iconUrl) {
        if (iconUrl == null || iconUrl.trim().isEmpty()) return;
        String cleanUrl = iconUrl.trim().split("\\s+")[0];

        if (PENDING_ICON_DOWNLOADS.putIfAbsent(id, Boolean.TRUE) != null) {
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                URI uri = URI.create(cleanUrl);
                HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(4000);
                conn.setReadTimeout(4000);

                if (conn.getResponseCode() == 200) {
                    try (InputStream is = conn.getInputStream()) {
                        byte[] bytes = is.readAllBytes();
                        if (bytes.length > 0) {
                            File targetFile = new File(ICONS_CACHE_DIR, id + ".png");
                            Files.write(targetFile.toPath(), bytes);
                            registerDynamicTextureBytes(id, bytes);
                        }
                    }
                }
            } catch (Exception ignored) {
            } finally {
                PENDING_ICON_DOWNLOADS.remove(id);
            }
        });
    }

    private static void registerDynamicTextureBytes(String id, byte[] bytes) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;

        client.execute(() -> {
            try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes)) {
                NativeImage image = NativeImage.read(bais);
                NativeImageBackedTexture texture = new NativeImageBackedTexture(image);
                Identifier dynamicIdentifier = Identifier.of("mooclient_dynamic", "emotes/" + id);

                client.getTextureManager().registerTexture(dynamicIdentifier, texture);
                DYNAMIC_ICONS.put(id, dynamicIdentifier);

                Emote emote = EmoteRegistry.get(id);
                if (emote != null) {
                    emote.setIcon(dynamicIdentifier);
                }
            } catch (Exception e) {
                MooClient.LOGGER.warn("Błąd rejestracji dynamicznej tekstury dla emotki: {}", id);
            }
        });
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
