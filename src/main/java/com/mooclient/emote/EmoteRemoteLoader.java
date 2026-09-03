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
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * System dynamicznego ładowania i synchronizacji emotek oraz ikon w 100% z chmury Supabase.
 * Baza Supabase jest jedynym nadrzędnym źródłem prawdy (Single Source of Truth) dla wszystkich graczy.
 * Żadne emotki nie są ładowane na ślepo z lokalnego dysku dewelopera.
 * Dysk służy wyłącznie jako pamięć podręczna (cache) dla pobranych z chmury zasobów.
 */
public class EmoteRemoteLoader {

    private static final String DEFAULT_SUPABASE_URL = "https://godjpceymapadkmqjrpj.supabase.co/rest/v1/emotes";
    private static final String DEFAULT_SUPABASE_STORAGE_URL = "https://godjpceymapadkmqjrpj.supabase.co/storage/v1/object/public/emotes";
    private static final String DEFAULT_SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImdvZGpwY2V5bWFwYWRrbXFqcnBqIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODgwMjYwMTIsImV4cCI6MjEwMzYwMjAxMn0.VY52MMlGLdJsCMzh981JLzQkUFkbX7-YGZ0E2TY-weo";

    private static final Identifier FALLBACK_ICON = Identifier.of("mooclient", "textures/gui/emotes/hands_up.png");

    private static final File BASE_DIR;
    private static final File CACHE_DIR;
    private static final File ICONS_CACHE_DIR;
    private static final File MANIFEST_FILE;

    private static final ConcurrentHashMap<String, Boolean> PENDING_DOWNLOADS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Boolean> PENDING_ICON_DOWNLOADS = new ConcurrentHashMap<>();
    private static final Map<String, Identifier> DYNAMIC_ICONS = new ConcurrentHashMap<>();

    static {
        File home = new File(System.getProperty("user.home"), ".mooclient");
        BASE_DIR = new File(home, "emotes");
        CACHE_DIR = new File(BASE_DIR, "cache");
        ICONS_CACHE_DIR = new File(CACHE_DIR, "icons");
        MANIFEST_FILE = new File(BASE_DIR, "manifest.json");

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
     * Pobiera aktualną listę emotek bezpośrednio z Supabase.
     */
    public static void init() {
        fetchRemoteEmotesAsync();
    }

    /**
     * Odświeża listę emotek z Supabase w tle (np. przy wejściu do edytora koła).
     */
    public static CompletableFuture<Void> refreshRemoteEmotesAsync() {
        return fetchRemoteEmotesAsync();
    }

    /**
     * Asynchronicznie pobiera listę wszystkich emotek z bazy danych Supabase (tabela 'emotes').
     * W razie braku połączenia internetowego, ładuje wyłącznie ostatnio zapisany manifest z Supabase.
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
                            Set<String> validRemoteIds = new HashSet<>();

                            // Zapisz manifest offline
                            saveManifest(json);

                            for (JsonElement item : array) {
                                if (item.isJsonObject()) {
                                    JsonObject obj = item.getAsJsonObject();
                                    if (obj.has("id") && !obj.get("id").isJsonNull()) {
                                        String id = obj.get("id").getAsString().toLowerCase().trim();
                                        validRemoteIds.add(id);
                                    }
                                    processRemoteEmoteEntry(obj);
                                }
                            }

                            // Usuń z gry i cache wszystkie emotki, których nie ma w bazie Supabase
                            pruneStaleEmotes(validRemoteIds);
                        }
                    }
                } else {
                    MooClient.LOGGER.warn("Supabase zwróciło kod odpowiedzi {}. Próba wczytania manifestu offline...", conn.getResponseCode());
                    loadOfflineManifest();
                }
            } catch (Exception e) {
                MooClient.LOGGER.info("Supabase niedostępne (tryb offline): {}. Wczytywanie manifestu...", e.getMessage());
                loadOfflineManifest();
            }
        });
    }

    /**
     * Wczytuje emotki z zapisanego wcześniej manifestu JSON z Supabase w trybie offline.
     */
    private static void loadOfflineManifest() {
        try {
            if (MANIFEST_FILE.exists() && MANIFEST_FILE.length() > 0) {
                String json = Files.readString(MANIFEST_FILE.toPath(), StandardCharsets.UTF_8);
                JsonElement element = JsonParser.parseString(json);
                if (element.isJsonArray()) {
                    JsonArray array = element.getAsJsonArray();
                    Set<String> validIds = new HashSet<>();
                    for (JsonElement item : array) {
                        if (item.isJsonObject()) {
                            JsonObject obj = item.getAsJsonObject();
                            if (obj.has("id") && !obj.get("id").isJsonNull()) {
                                validIds.add(obj.get("id").getAsString().toLowerCase().trim());
                            }
                            processRemoteEmoteEntry(obj);
                        }
                    }
                    pruneStaleEmotes(validIds);
                }
            }
        } catch (Exception e) {
            MooClient.LOGGER.error("Błąd podczas odczytu manifestu offline", e);
        }
    }

    private static void saveManifest(String json) {
        try {
            if (!BASE_DIR.exists()) BASE_DIR.mkdirs();
            Files.writeString(MANIFEST_FILE.toPath(), json, StandardCharsets.UTF_8);
        } catch (Exception ignored) {}
    }

    /**
     * Usuwa z rejestru gry i pamięci podręcznej wszystkie emotki, które nie istnieją w Supabase.
     */
    private static void pruneStaleEmotes(Set<String> validRemoteIds) {
        if (validRemoteIds == null || validRemoteIds.isEmpty()) return;

        // 1. Usunięcie z EmoteRegistry
        List<Emote> allCurrent = new ArrayList<>(EmoteRegistry.getAll());
        for (Emote emote : allCurrent) {
            String id = emote.getId().toLowerCase().trim();
            if (!validRemoteIds.contains(id)) {
                EmoteRegistry.unregister(id);
                MooClient.LOGGER.info("Wyrejestrowano nieistniejącą emotkę: {}", id);
            }
        }

        // 2. Usunięcie starych plików modeli z cache
        if (CACHE_DIR.exists() && CACHE_DIR.isDirectory()) {
            File[] files = CACHE_DIR.listFiles((dir, name) -> name.endsWith(".json") || name.endsWith(".bbmodel"));
            if (files != null) {
                for (File f : files) {
                    String fileName = f.getName();
                    String id = fileName.substring(0, fileName.lastIndexOf('.')).toLowerCase().trim();
                    if (!validRemoteIds.contains(id)) {
                        try {
                            f.delete();
                            MooClient.LOGGER.info("Usunięto plik cache usuniętej emotki: {}", f.getName());
                        } catch (Exception ignored) {}
                    }
                }
            }
        }

        // 3. Usunięcie starych ikon z cache
        if (ICONS_CACHE_DIR.exists() && ICONS_CACHE_DIR.isDirectory()) {
            File[] iconFiles = ICONS_CACHE_DIR.listFiles((dir, name) -> name.endsWith(".png"));
            if (iconFiles != null) {
                for (File f : iconFiles) {
                    String fileName = f.getName();
                    String id = fileName.substring(0, fileName.lastIndexOf('.')).toLowerCase().trim();
                    if (!validRemoteIds.contains(id)) {
                        try {
                            f.delete();
                        } catch (Exception ignored) {}
                    }
                }
            }
        }
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
                registerParsedAnimation(id, name, type, isFree, forcesThirdPerson, animJson, iconUrl);
                return;
            }

            // 2. Jeśli podano URL do pobrania z Supabase Storage (np. animation_url)
            String animationUrl = null;
            if (obj.has("animation_url") && !obj.get("animation_url").isJsonNull()) {
                String raw = obj.get("animation_url").getAsString();
                if (raw != null && !raw.trim().isEmpty()) {
                    animationUrl = raw.trim().split("\\s+")[0];
                }
            }

            // 3. Sprawdzenie czy animacja istnieje w lokalnym cache
            File cachedFile = new File(CACHE_DIR, id + ".bbmodel");
            if (!cachedFile.exists()) {
                cachedFile = new File(CACHE_DIR, id + ".json");
            }

            if (cachedFile.exists() && cachedFile.length() > 0) {
                String content = Files.readString(cachedFile.toPath(), StandardCharsets.UTF_8);
                registerParsedAnimation(id, name, type, isFree, forcesThirdPerson, content, iconUrl);
                return;
            }

            // 4. Pobranie z chmury Supabase
            if (animationUrl == null || animationUrl.isEmpty()) {
                animationUrl = DEFAULT_SUPABASE_STORAGE_URL + "/" + id + ".bbmodel";
            }

            downloadAndRegisterAnimation(id, name, type, isFree, forcesThirdPerson, animationUrl, iconUrl);
        } catch (Exception e) {
            MooClient.LOGGER.error("Błąd przetwarzania rekordu emotki z Supabase: " + obj, e);
        }
    }

    private static void downloadAndRegisterAnimation(String id, String name, EmoteType type,
                                                     boolean isFree, boolean forcesThirdPerson,
                                                     String downloadUrl, String iconUrl) {
        if (PENDING_DOWNLOADS.putIfAbsent(id, Boolean.TRUE) != null) {
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                URI uri = URI.create(downloadUrl);
                HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(4000);
                conn.setReadTimeout(4000);

                if (conn.getResponseCode() == 200) {
                    try (InputStream is = conn.getInputStream()) {
                        String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                        File targetFile = new File(CACHE_DIR, id + (downloadUrl.endsWith(".json") ? ".json" : ".bbmodel"));
                        try (FileWriter writer = new FileWriter(targetFile, StandardCharsets.UTF_8)) {
                            writer.write(content);
                        }

                        registerParsedAnimation(id, name, type, isFree, forcesThirdPerson, content, iconUrl);
                    }
                }
            } catch (Exception e) {
                MooClient.LOGGER.warn("Nie udało się pobrać animacji dla {}: {}", id, e.getMessage());
            } finally {
                PENDING_DOWNLOADS.remove(id);
            }
        });
    }

    private static void registerParsedAnimation(String id, String name, EmoteType type,
                                                boolean isFree, boolean forcesThirdPerson,
                                                String content, String iconUrl) {
        try {
            BlockbenchAnimation anim = BlockbenchAnimationParser.parse(content);
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
                existing.setType(type);
                existing.setParticipantCount(type == EmoteType.MULTIPLAYER ? 2 : 1);
                existing.setSceneConfig(sceneConfig);
                existing.setDurationTicks(durationTicks);
                existing.setLooping(looping);
                existing.setForcesThirdPerson(forcesThirdPerson);
                resolveIcon(id, iconUrl);
                EmoteRegistry.register(existing);
                MooClient.LOGGER.info("Zaktualizowano emotkę z Supabase: {} (type={})", id, type);
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
                MooClient.LOGGER.info("Pomyślnie załadowano emotkę z Supabase: {} (type={})", id, type);
            }
        } catch (Exception e) {
            MooClient.LOGGER.error("Błąd rejestracji pobranej animacji z Supabase: " + id, e);
        }
    }

    /**
     * Zwraca identyfikator ikony:
     * 1. Jeśli już zarejestrowano teksturę dynamiczną -> zwraca Identifier.
     * 2. Jeśli istnieje plik w lokalnym cache dyskowym -> ładuje i rejestruje w locie.
     * 3. W przeciwnym razie -> rozpoczyna pobieranie w tle z Supabase i zwraca bezpieczny FALLBACK_ICON.
     */
    public static Identifier resolveIcon(String id, String iconUrl) {
        if (id == null || id.isEmpty()) return FALLBACK_ICON;
        String cleanId = id.toLowerCase().trim();

        // 1. Zarejestrowana już tekstura dynamiczna w pamięci
        Identifier dynamicCached = DYNAMIC_ICONS.get(cleanId);
        if (dynamicCached != null) {
            return dynamicCached;
        }

        // 2. Sprawdzenie ikony w lokalnej pamięci podręcznej (~/.mooclient/emotes/cache/icons/<id>.png)
        File cachedIconFile = new File(ICONS_CACHE_DIR, cleanId + ".png");
        if (cachedIconFile.exists() && cachedIconFile.length() > 0) {
            loadDynamicTextureFromFile(cleanId, cachedIconFile);
            Identifier registered = DYNAMIC_ICONS.get(cleanId);
            if (registered != null) return registered;
        }

        // 3. Pobranie ikony z Supabase CDN
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
