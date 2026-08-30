package com.mooclient.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dynamic Access Manager for Moo Client Emotes and Cosmetics.
 * Connects directly to Supabase REST API to fetch real-time user roles and emote permissions
 * by matching either UUID or Player Nickname.
 * Safe non-blocking async execution, memory caching, zero hardcoded UUIDs.
 */
public class EmoteAccessManager {

    private static final String DEFAULT_SUPABASE_URL = "https://godjpceymapadkmqjrpj.supabase.co/rest/v1/users";
    private static final String DEFAULT_SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImdvZGpwY2V5bWFwYWRrbXFqcnBqIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODgwMjYwMTIsImV4cCI6MjEwMzYwMjAxMn0.VY52MMlGLdJsCMzh981JLzQkUFkbX7-YGZ0E2TY-weo";

    private static String apiEndpoint;
    private static String apiKey;

    static {
        String envEndpoint = System.getenv("MOOCLIENT_SUPABASE_URL");
        apiEndpoint = (envEndpoint != null && !envEndpoint.trim().isEmpty())
                ? envEndpoint.trim()
                : System.getProperty("mooclient.supabase.url", DEFAULT_SUPABASE_URL);

        String envKey = System.getenv("MOOCLIENT_SUPABASE_KEY");
        apiKey = (envKey != null && !envKey.trim().isEmpty())
                ? envKey.trim()
                : System.getProperty("mooclient.supabase.key", DEFAULT_SUPABASE_KEY);
    }

    public static class UserPermission {
        private final String role;
        private final boolean allEmotes;
        private final long fetchedTimeMs;

        public UserPermission(String role, boolean allEmotes) {
            this.role = role != null ? role.trim().toLowerCase() : "user";
            this.allEmotes = allEmotes;
            this.fetchedTimeMs = System.currentTimeMillis();
        }

        public String getRole() {
            return role;
        }

        public boolean hasAllEmotes() {
            return allEmotes;
        }

        public boolean isExpired(long ttlMs) {
            return (System.currentTimeMillis() - fetchedTimeMs) > ttlMs;
        }

        public static final UserPermission DEFAULT_USER = new UserPermission("user", false);
    }

    private static final Map<UUID, UserPermission> PERMISSION_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, UserPermission> NAME_PERMISSION_CACHE = new ConcurrentHashMap<>();
    private static final Set<String> PENDING_REQUESTS = ConcurrentHashMap.newKeySet();
    private static final long CACHE_TTL_MS = 5 * 60 * 1000L; // 5 minutes cache TTL

    public enum EmoteId {
        NONE(true),       // Free for all players
        FREE(true),       // Free for all players
        HANDS_UP(true),   // Free for all players
        STOP(true),       // Free for all players
        FRONTFLIP(false), // Store / Developer / Tester / all_emotes
        BACKFLIP(false),  // Store / Developer / Tester / all_emotes
        MEDITATION(false),// Store / Developer / Tester / all_emotes
        FACEPALM(false);  // Store / Developer / Tester / all_emotes

        private final boolean isFree;

        EmoteId(boolean isFree) {
            this.isFree = isFree;
        }

        public boolean isFree() {
            return isFree;
        }
    }

    public static void setApiEndpoint(String endpoint, String key) {
        if (endpoint != null && !endpoint.trim().isEmpty()) {
            apiEndpoint = endpoint.trim();
        }
        if (key != null && !key.trim().isEmpty()) {
            apiKey = key.trim();
        }
    }

    /**
     * Triggers asynchronous fetching of permissions for a player UUID and/or Name.
     * Completely non-blocking and safe against timeouts / network failures.
     */
    public static void fetchPermissionsAsync(UUID uuid, String playerName) {
        String cleanName = playerName != null ? playerName.trim() : null;
        String pendingKey = (uuid != null ? uuid.toString() : "") + ":" + (cleanName != null ? cleanName.toLowerCase() : "");
        if (pendingKey.equals(":")) return;

        if (uuid != null) {
            UserPermission cached = PERMISSION_CACHE.get(uuid);
            if (cached != null && !cached.isExpired(CACHE_TTL_MS)) return;
        }
        if (cleanName != null) {
            UserPermission cached = NAME_PERMISSION_CACHE.get(cleanName.toLowerCase());
            if (cached != null && !cached.isExpired(CACHE_TTL_MS)) return;
        }

        if (!PENDING_REQUESTS.add(pendingKey)) {
            return; // Already in progress
        }

        CompletableFuture.runAsync(() -> {
            try {
                String targetUrl;
                if (uuid != null && cleanName != null) {
                    String encName = URLEncoder.encode(cleanName, StandardCharsets.UTF_8);
                    targetUrl = apiEndpoint + "?or=(uuid.eq." + uuid.toString() + ",name.ilike." + encName + ")&select=role,all_emotes,name,uuid";
                } else if (uuid != null) {
                    targetUrl = apiEndpoint + "?uuid=eq." + uuid.toString() + "&select=role,all_emotes,name,uuid";
                } else {
                    String encName = URLEncoder.encode(cleanName, StandardCharsets.UTF_8);
                    targetUrl = apiEndpoint + "?name.ilike=" + encName + "&select=role,all_emotes,name,uuid";
                }

                URL url = URI.create(targetUrl).toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "MooClient/1.8.0");
                conn.setRequestProperty("Accept", "application/json");
                if (apiKey != null && !apiKey.isEmpty()) {
                    conn.setRequestProperty("apikey", apiKey);
                    conn.setRequestProperty("Authorization", "Bearer " + apiKey);
                }
                conn.setConnectTimeout(4000);
                conn.setReadTimeout(4000);

                int responseCode = conn.getResponseCode();
                if (responseCode >= 200 && responseCode < 300) {
                    try (InputStream in = conn.getInputStream()) {
                        String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                        JsonElement element = JsonParser.parseString(json);
                        JsonObject obj = null;

                        if (element.isJsonObject()) {
                            obj = element.getAsJsonObject();
                        } else if (element.isJsonArray() && element.getAsJsonArray().size() > 0) {
                            JsonElement first = element.getAsJsonArray().get(0);
                            if (first.isJsonObject()) {
                                obj = first.getAsJsonObject();
                            }
                        }

                        if (obj != null) {
                            String role = obj.has("role") && !obj.get("role").isJsonNull()
                                    ? obj.get("role").getAsString()
                                    : "user";

                            boolean allEmotes = false;
                            if (obj.has("all_emotes") && !obj.get("all_emotes").isJsonNull()) {
                                allEmotes = obj.get("all_emotes").getAsBoolean();
                            } else if (role.equalsIgnoreCase("developer") || role.equalsIgnoreCase("tester") || role.equalsIgnoreCase("admin") || role.equalsIgnoreCase("vip") || role.equalsIgnoreCase("dev")) {
                                allEmotes = true;
                            }

                            UserPermission permission = new UserPermission(role, allEmotes);
                            if (uuid != null) PERMISSION_CACHE.put(uuid, permission);
                            if (cleanName != null) NAME_PERMISSION_CACHE.put(cleanName.toLowerCase(), permission);
                        } else {
                            // Player not found in database -> standard user
                            if (uuid != null) PERMISSION_CACHE.put(uuid, UserPermission.DEFAULT_USER);
                            if (cleanName != null) NAME_PERMISSION_CACHE.put(cleanName.toLowerCase(), UserPermission.DEFAULT_USER);
                        }
                    }
                } else {
                    if (uuid != null) PERMISSION_CACHE.putIfAbsent(uuid, UserPermission.DEFAULT_USER);
                    if (cleanName != null) NAME_PERMISSION_CACHE.putIfAbsent(cleanName.toLowerCase(), UserPermission.DEFAULT_USER);
                }
            } catch (Exception ignored) {
                if (uuid != null) PERMISSION_CACHE.putIfAbsent(uuid, UserPermission.DEFAULT_USER);
                if (cleanName != null) NAME_PERMISSION_CACHE.putIfAbsent(cleanName.toLowerCase(), UserPermission.DEFAULT_USER);
            } finally {
                PENDING_REQUESTS.remove(pendingKey);
            }
        });
    }

    public static void fetchPermissionsAsync(UUID uuid) {
        fetchPermissionsAsync(uuid, null);
    }

    /**
     * Gets user permissions for a UUID and/or Name from cache, scheduling background fetch if missing.
     */
    public static UserPermission getUserPermission(UUID uuid, String playerName) {
        if (uuid != null) {
            UserPermission perm = PERMISSION_CACHE.get(uuid);
            if (perm != null && !perm.isExpired(CACHE_TTL_MS)) {
                return perm;
            }
        }
        if (playerName != null) {
            UserPermission perm = NAME_PERMISSION_CACHE.get(playerName.toLowerCase().trim());
            if (perm != null && !perm.isExpired(CACHE_TTL_MS)) {
                return perm;
            }
        }

        fetchPermissionsAsync(uuid, playerName);
        return UserPermission.DEFAULT_USER;
    }

    public static UserPermission getUserPermission(UUID uuid) {
        return getUserPermission(uuid, null);
    }

    /**
     * Gets current local player permissions.
     */
    public static UserPermission getLocalPlayerPermission() {
        MinecraftClient client = MinecraftClient.getInstance();
        UUID localUuid = null;
        String localName = null;

        if (client.player != null) {
            localUuid = client.player.getUuid();
            localName = client.player.getName().getString();
        } else if (client.getSession() != null) {
            localUuid = client.getSession().getUuidOrNull();
            localName = client.getSession().getUsername();
        }

        if (localUuid != null || localName != null) {
            return getUserPermission(localUuid, localName);
        }

        return UserPermission.DEFAULT_USER;
    }

    /**
     * Returns the active role string of the local player (e.g. "developer", "tester", "admin", "vip", "user").
     */
    public static String getLocalPlayerRole() {
        return getLocalPlayerPermission().getRole();
    }

    public static boolean isLocalPlayerDeveloper() {
        String r = getLocalPlayerRole();
        return "developer".equalsIgnoreCase(r) || "dev".equalsIgnoreCase(r);
    }

    public static boolean isLocalPlayerTester() {
        return "tester".equalsIgnoreCase(getLocalPlayerRole());
    }

    public static boolean isPlayerDeveloper(PlayerEntity player) {
        if (player == null) return false;
        String name = player.getName() != null ? player.getName().getString() : null;
        return "developer".equalsIgnoreCase(getUserPermission(player.getUuid(), name).getRole())
                || "dev".equalsIgnoreCase(getUserPermission(player.getUuid(), name).getRole());
    }

    public static boolean isPlayerTester(PlayerEntity player) {
        if (player == null) return false;
        String name = player.getName() != null ? player.getName().getString() : null;
        return "tester".equalsIgnoreCase(getUserPermission(player.getUuid(), name).getRole());
    }

    /**
     * Proactively triggers permission load for local player (called on game startup / world connect).
     */
    public static void fetchLocalPlayerPermissions() {
        MinecraftClient client = MinecraftClient.getInstance();
        UUID localUuid = null;
        String localName = null;

        if (client.player != null) {
            localUuid = client.player.getUuid();
            localName = client.player.getName().getString();
        } else if (client.getSession() != null) {
            localUuid = client.getSession().getUuidOrNull();
            localName = client.getSession().getUsername();
        }

        if (localUuid != null || localName != null) {
            fetchPermissionsAsync(localUuid, localName);
        }
    }

    /**
     * Determines whether the local player has access to execute a given emote.
     */
    public static boolean hasAccess(EmoteId emote) {
        if (emote == null) return false;

        // Free emotes are accessible to all players
        if (emote.isFree()) {
            return true;
        }

        // Unlocked via all_emotes permission from Supabase or developer/tester/admin role
        UserPermission perm = getLocalPlayerPermission();
        if (perm.hasAllEmotes()) {
            return true;
        }

        String role = perm.getRole();
        if ("developer".equalsIgnoreCase(role) || "tester".equalsIgnoreCase(role) || "admin".equalsIgnoreCase(role) || "dev".equalsIgnoreCase(role)) {
            return true;
        }

        return false;
    }

    public static boolean hasAccess(int slot) {
        return switch (slot) {
            case 0 -> hasAccess(EmoteId.FRONTFLIP);
            case 1 -> hasAccess(EmoteId.BACKFLIP);
            default -> false;
        };
    }
}
