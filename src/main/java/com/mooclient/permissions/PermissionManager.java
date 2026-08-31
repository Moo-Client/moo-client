package com.mooclient.permissions;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mooclient.emote.Emote;
import com.mooclient.emote.EmoteRegistry;
import com.mooclient.security.MooSessionValidator;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Zaufany menedżer uprawnień do emotek (PermissionManager).
 * Integruje się z Supabase REST API dla tabel:
 * 1. public.users (uuid, name, role, all_emotes, is_active)
 * 2. public.user_emotes (id, user_uuid, emote_id, purchased_at)
 */
public class PermissionManager {

    private static final String DEFAULT_SUPABASE_BASE = "https://godjpceymapadkmqjrpj.supabase.co/rest/v1";
    private static final String DEFAULT_SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImdvZGpwY2V5bWFwYWRrbXFqcnBqIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODgwMjYwMTIsImV4cCI6MjEwMzYwMjAxMn0.VY52MMlGLdJsCMzh981JLzQkUFkbX7-YGZ0E2TY-weo";

    private static String apiBaseUrl;
    private static String apiKey;

    static {
        String envEndpoint = System.getenv("MOOCLIENT_SUPABASE_URL");
        apiBaseUrl = (envEndpoint != null && !envEndpoint.trim().isEmpty())
                ? envEndpoint.trim()
                : System.getProperty("mooclient.supabase.url", DEFAULT_SUPABASE_BASE);

        String envKey = System.getenv("MOOCLIENT_SUPABASE_KEY");
        apiKey = (envKey != null && !envKey.trim().isEmpty())
                ? envKey.trim()
                : System.getProperty("mooclient.supabase.key", DEFAULT_SUPABASE_KEY);
    }

    public static class UserPermission {
        private final String role;
        private final boolean allEmotes;
        private final Set<String> unlockedEmoteIds = ConcurrentHashMap.newKeySet();
        private final long fetchedTimeMs;

        public UserPermission(String role, boolean allEmotes, Collection<String> unlockedEmotes) {
            this.role = role != null ? role.trim().toLowerCase() : "user";
            this.allEmotes = allEmotes;
            if (unlockedEmotes != null) {
                for (String e : unlockedEmotes) {
                    if (e != null && !e.trim().isEmpty()) {
                        this.unlockedEmoteIds.add(e.trim().toLowerCase());
                    }
                }
            }
            this.fetchedTimeMs = System.currentTimeMillis();
        }

        public String getRole() {
            return role;
        }

        public boolean isStaffRole() {
            return "developer".equalsIgnoreCase(role)
                    || "dev".equalsIgnoreCase(role)
                    || "tester".equalsIgnoreCase(role)
                    || "admin".equalsIgnoreCase(role)
                    || "headadmin".equalsIgnoreCase(role)
                    || "owner".equalsIgnoreCase(role)
                    || "creator".equalsIgnoreCase(role)
                    || "wspoltworca".equalsIgnoreCase(role)
                    || "współtwórca".equalsIgnoreCase(role)
                    || "vip".equalsIgnoreCase(role)
                    || "mod".equalsIgnoreCase(role)
                    || "moderator".equalsIgnoreCase(role);
        }

        public boolean hasAllEmotes() {
            return allEmotes;
        }

        public boolean hasEmote(String emoteId) {
            if (allEmotes) return true;
            if (emoteId == null) return false;
            return unlockedEmoteIds.contains(emoteId.toLowerCase().trim());
        }

        public Set<String> getUnlockedEmoteIds() {
            return Collections.unmodifiableSet(unlockedEmoteIds);
        }

        public boolean isExpired(long ttlMs) {
            return (System.currentTimeMillis() - fetchedTimeMs) > ttlMs;
        }

        public static final UserPermission DEFAULT_USER = new UserPermission("user", false, Collections.emptyList());
    }

    private static final Map<UUID, UserPermission> PERMISSION_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, UserPermission> NAME_PERMISSION_CACHE = new ConcurrentHashMap<>();
    private static final Set<String> PENDING_REQUESTS = ConcurrentHashMap.newKeySet();
    private static final long CACHE_TTL_MS = 60 * 1000L; // 60 sekund TTL

    public static boolean hasAccess(UUID playerUuid, String emoteId) {
        if (emoteId == null) return false;
        Emote emote = EmoteRegistry.get(emoteId);
        if (emote != null && emote.isFree()) {
            return true;
        }

        String nickname = MooSessionValidator.getLocalPlayerName();

        UserPermission perm = null;
        if (playerUuid != null) perm = PERMISSION_CACHE.get(playerUuid);
        if (perm == null && nickname != null && !nickname.trim().isEmpty()) {
            perm = NAME_PERMISSION_CACHE.get(nickname.toLowerCase().trim());
        }

        if (perm != null && perm.hasEmote(emoteId)) {
            if (perm.isExpired(CACHE_TTL_MS)) {
                fetchPermissionsAsync(playerUuid, nickname, false);
            }
            return true;
        }

        if (perm == null || perm.isExpired(CACHE_TTL_MS)) {
            fetchPermissionsAsync(playerUuid, nickname, false);
        }

        return perm != null && perm.hasEmote(emoteId);
    }

    public static boolean hasAccessLocal(String emoteId) {
        UUID localUuid = MooSessionValidator.getLocalPlayerUuid();
        return hasAccess(localUuid, emoteId);
    }

    public static void fetchLocalPlayerPermissions() {
        fetchLocalPlayerPermissions(false);
    }

    public static CompletableFuture<UserPermission> fetchLocalPlayerPermissions(boolean force) {
        UUID localUuid = MooSessionValidator.getLocalPlayerUuid();
        String nickname = MooSessionValidator.getLocalPlayerName();
        return fetchPermissionsAsync(localUuid, nickname, force);
    }

    public static CompletableFuture<UserPermission> fetchPermissionsAsync(UUID playerUuid, String nickname) {
        return fetchPermissionsAsync(playerUuid, nickname, false);
    }

    public static CompletableFuture<UserPermission> fetchPermissionsAsync(UUID playerUuid, String nickname, boolean force) {
        String cleanNick = (nickname != null && !nickname.trim().isEmpty()) ? nickname.trim().toLowerCase() : null;

        // 1. Sprawdź najpierw pamięć podręczną (Cache Hit) jeśli nie wymuszono odświeżenia
        UserPermission cached = playerUuid != null ? PERMISSION_CACHE.get(playerUuid) : null;
        if (cached == null && cleanNick != null) {
            cached = NAME_PERMISSION_CACHE.get(cleanNick);
        }
        if (!force && cached != null && !cached.isExpired(CACHE_TTL_MS)) {
            return CompletableFuture.completedFuture(cached);
        }

        String reqKey = (playerUuid != null ? playerUuid.toString() : "") + ":" + (cleanNick != null ? cleanNick : "");
        if (reqKey.equals(":")) {
            return CompletableFuture.completedFuture(UserPermission.DEFAULT_USER);
        }

        if (!PENDING_REQUESTS.add(reqKey)) {
            return CompletableFuture.completedFuture(cached != null ? cached : UserPermission.DEFAULT_USER);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                UserPermission fetched = querySupabasePermissions(playerUuid, cleanNick);
                if (playerUuid != null) {
                    PERMISSION_CACHE.put(playerUuid, fetched);
                }
                if (cleanNick != null && !cleanNick.isEmpty()) {
                    NAME_PERMISSION_CACHE.put(cleanNick, fetched);
                }
                return fetched;
            } catch (Exception e) {
                return UserPermission.DEFAULT_USER;
            } finally {
                PENDING_REQUESTS.remove(reqKey);
            }
        });
    }

    private static UserPermission querySupabasePermissions(UUID playerUuid, String nickname) {
        try {
            StringBuilder urlBuilder = new StringBuilder(apiBaseUrl).append("/users?select=*");
            List<String> orFilters = new ArrayList<>();

            if (playerUuid != null) {
                String dashed = playerUuid.toString().toLowerCase();
                String undashed = dashed.replace("-", "");
                orFilters.add("uuid.eq." + dashed);
                orFilters.add("uuid.eq." + undashed);
            }
            if (nickname != null && !nickname.trim().isEmpty()) {
                String encodedNick = URLEncoder.encode(nickname.trim(), StandardCharsets.UTF_8);
                orFilters.add("name.ilike." + encodedNick);
            }

            if (!orFilters.isEmpty()) {
                urlBuilder.append("&or=(").append(String.join(",", orFilters)).append(")");
            }
            urlBuilder.append("&limit=1");

            String jsonUsers = executeGet(urlBuilder.toString());
            if (jsonUsers == null || jsonUsers.isEmpty()) {
                return UserPermission.DEFAULT_USER;
            }

            JsonElement parsed = JsonParser.parseString(jsonUsers);
            if (!parsed.isJsonArray()) {
                return UserPermission.DEFAULT_USER;
            }

            JsonArray arr = parsed.getAsJsonArray();
            if (arr.isEmpty()) {
                return UserPermission.DEFAULT_USER;
            }

            JsonObject userObj = arr.get(0).getAsJsonObject();

            // Weryfikacja czy konto gracza jest aktywne w Supabase (is_active)
            boolean isActive = true;
            if (userObj.has("is_active") && !userObj.get("is_active").isJsonNull()) {
                isActive = userObj.get("is_active").getAsBoolean();
            }
            if (!isActive) {
                return UserPermission.DEFAULT_USER;
            }

            String role = userObj.has("role") && !userObj.get("role").isJsonNull() ? userObj.get("role").getAsString() : "user";
            boolean allEmotes = userObj.has("all_emotes") && !userObj.get("all_emotes").isJsonNull() && userObj.get("all_emotes").getAsBoolean();

            // Jeśli w bazie all_emotes jest TRUE, gracz ma odblokowane wszystkie emotki
            if (allEmotes) {
                return new UserPermission(role, true, Collections.emptyList());
            }

            // Jeśli all_emotes jest FALSE, pobieramy tylko przypisane emotki z tabeli user_emotes
            Set<String> unlockedEmotes = new HashSet<>();
            StringBuilder emotesUrlBuilder = new StringBuilder(apiBaseUrl).append("/user_emotes?select=*");
            List<String> emoteOr = new ArrayList<>();

            if (playerUuid != null) {
                String dashed = playerUuid.toString().toLowerCase();
                String undashed = dashed.replace("-", "");
                emoteOr.add("user_uuid.eq." + dashed);
                emoteOr.add("user_uuid.eq." + undashed);
            }

            String dbUuid = userObj.has("uuid") && !userObj.get("uuid").isJsonNull() ? userObj.get("uuid").getAsString().trim() : null;
            if (dbUuid != null && !dbUuid.isEmpty()) {
                String dashed = dbUuid.contains("-") ? dbUuid : dbUuid.replaceFirst("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5");
                String undashed = dbUuid.replace("-", "");
                emoteOr.add("user_uuid.eq." + dashed.toLowerCase());
                emoteOr.add("user_uuid.eq." + undashed.toLowerCase());
            }

            if (!emoteOr.isEmpty()) {
                emotesUrlBuilder.append("&or=(").append(String.join(",", emoteOr)).append(")");
            }

            String jsonEmotes = executeGet(emotesUrlBuilder.toString());
            if (jsonEmotes != null && !jsonEmotes.isEmpty()) {
                JsonElement emotesParsed = JsonParser.parseString(jsonEmotes);
                if (emotesParsed.isJsonArray()) {
                    for (JsonElement item : emotesParsed.getAsJsonArray()) {
                        if (item.isJsonObject()) {
                            JsonObject obj = item.getAsJsonObject();
                            if (obj.has("emote_id") && !obj.get("emote_id").isJsonNull()) {
                                unlockedEmotes.add(obj.get("emote_id").getAsString().toLowerCase().trim());
                            }
                        }
                    }
                }
            }

            return new UserPermission(role, false, unlockedEmotes);
        } catch (Exception e) {
            return UserPermission.DEFAULT_USER;
        }
    }

    public static CompletableFuture<Boolean> authorizeInteractionAsync(UUID initiatorUuid, UUID targetUuid, String emoteId) {
        if (emoteId == null) return CompletableFuture.completedFuture(false);
        Emote emote = EmoteRegistry.get(emoteId);
        if (emote != null && (emote.isFree() || emote.isLocal())) {
            return CompletableFuture.completedFuture(true);
        }

        String localName = MooSessionValidator.getLocalPlayerName();

        // 1. Sprawdź najpierw pamięć podręczną lokalnego gracza
        UserPermission cached = initiatorUuid != null ? PERMISSION_CACHE.get(initiatorUuid) : null;
        if (cached == null && localName != null) {
            cached = NAME_PERMISSION_CACHE.get(localName.toLowerCase().trim());
        }
        if (cached != null && !cached.isExpired(CACHE_TTL_MS) && cached.hasEmote(emoteId)) {
            return CompletableFuture.completedFuture(true);
        }

        // 2. Jeśli brak w cache — pobierz asynchronicznie sprawdzając zarówno UUID jak i Nick gracza
        return fetchPermissionsAsync(initiatorUuid, localName).thenApply(perm ->
                perm != null && perm.hasEmote(emoteId)
        );
    }

    public static void clearCache() {
        PERMISSION_CACHE.clear();
        NAME_PERMISSION_CACHE.clear();
        PENDING_REQUESTS.clear();
    }

    private static String executeGet(String urlString) {
        try {
            URI uri = URI.create(urlString);
            HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("apikey", apiKey);
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setRequestProperty("Accept", "application/json");
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(4000);

            int code = conn.getResponseCode();
            if (code == 200) {
                try (InputStream is = conn.getInputStream()) {
                    return new String(is.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        } catch (Exception ignored) {}
        return null;
    }
}
