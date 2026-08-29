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
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dynamic Access Manager for Moo Client Emotes and Cosmetics.
 * Fetches roles and permissions directly from an external REST API / Database,
 * with non-blocking async requests, memory caching, and safe offline fallbacks.
 * Zero hardcoded UUIDs.
 */
public class EmoteAccessManager {

    /**
     * Base URL for the user permissions API.
     * Can be overridden via system property -Dmooclient.api.users="https://your-api.com/users/"
     * or via environment variable MOOCLIENT_USERS_API.
     */
    private static String apiEndpoint;

    static {
        String envEndpoint = System.getenv("MOOCLIENT_USERS_API");
        if (envEndpoint != null && !envEndpoint.trim().isEmpty()) {
            apiEndpoint = envEndpoint.trim();
        } else {
            apiEndpoint = System.getProperty("mooclient.api.users", "https://api.mooclient.com/users/");
        }
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
    private static final Set<UUID> PENDING_REQUESTS = ConcurrentHashMap.newKeySet();
    private static final long CACHE_TTL_MS = 5 * 60 * 1000L; // 5 minutes cache TTL

    public enum EmoteId {
        HANDS_UP(true),   // Free for all players
        STOP(true),       // Free for all players
        FRONTFLIP(false), // Store / Developer / Tester / all_emotes
        BACKFLIP(false);  // Store / Developer / Tester / all_emotes

        private final boolean isFree;

        EmoteId(boolean isFree) {
            this.isFree = isFree;
        }

        public boolean isFree() {
            return isFree;
        }
    }

    public static void setApiEndpoint(String endpoint) {
        if (endpoint != null && !endpoint.trim().isEmpty()) {
            apiEndpoint = endpoint.trim();
        }
    }

    public static String getApiEndpoint() {
        return apiEndpoint;
    }

    /**
     * Triggers asynchronous fetching of permissions for a player UUID.
     * Completely non-blocking and safe against timeouts / network failures.
     */
    public static void fetchPermissionsAsync(UUID uuid) {
        if (uuid == null) return;

        UserPermission cached = PERMISSION_CACHE.get(uuid);
        if (cached != null && !cached.isExpired(CACHE_TTL_MS)) {
            return;
        }

        if (!PENDING_REQUESTS.add(uuid)) {
            return; // Already in progress
        }

        CompletableFuture.runAsync(() -> {
            try {
                String uuidStr = uuid.toString();
                String targetUrl = apiEndpoint.endsWith("/") ? (apiEndpoint + uuidStr) : (apiEndpoint + "/" + uuidStr);

                URL url = URI.create(targetUrl).toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "MooClient/1.7.0");
                conn.setRequestProperty("Accept", "application/json");
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
                            // Support Supabase / REST array response e.g. [{ "role": "tester", "all_emotes": true }]
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
                            } else if (role.equalsIgnoreCase("developer") || role.equalsIgnoreCase("tester") || role.equalsIgnoreCase("admin")) {
                                allEmotes = true;
                            }

                            PERMISSION_CACHE.put(uuid, new UserPermission(role, allEmotes));
                        } else {
                            PERMISSION_CACHE.put(uuid, UserPermission.DEFAULT_USER);
                        }
                    }
                } else if (responseCode == 404) {
                    // Standard user not in special database
                    PERMISSION_CACHE.put(uuid, UserPermission.DEFAULT_USER);
                } else {
                    // Temporary network or server error, retain safe default
                    PERMISSION_CACHE.putIfAbsent(uuid, UserPermission.DEFAULT_USER);
                }
            } catch (Exception ignored) {
                // Safe default on connection drop / offline mode
                PERMISSION_CACHE.putIfAbsent(uuid, UserPermission.DEFAULT_USER);
            } finally {
                PENDING_REQUESTS.remove(uuid);
            }
        });
    }

    /**
     * Gets user permissions for a UUID from cache, scheduling a background fetch if missing.
     */
    public static UserPermission getUserPermission(UUID uuid) {
        if (uuid == null) return UserPermission.DEFAULT_USER;

        UserPermission perm = PERMISSION_CACHE.get(uuid);
        if (perm == null || perm.isExpired(CACHE_TTL_MS)) {
            fetchPermissionsAsync(uuid);
            return perm != null ? perm : UserPermission.DEFAULT_USER;
        }
        return perm;
    }

    /**
     * Gets current local player permissions.
     */
    public static UserPermission getLocalPlayerPermission() {
        MinecraftClient client = MinecraftClient.getInstance();
        UUID localUuid = null;

        if (client.player != null && client.player.getUuid() != null) {
            localUuid = client.player.getUuid();
        } else if (client.getSession() != null && client.getSession().getUuidOrNull() != null) {
            localUuid = client.getSession().getUuidOrNull();
        }

        if (localUuid != null) {
            return getUserPermission(localUuid);
        }

        // Local development environment fallback
        if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
            return new UserPermission("developer", true);
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
        return "developer".equalsIgnoreCase(getLocalPlayerRole()) || "dev".equalsIgnoreCase(getLocalPlayerRole());
    }

    public static boolean isLocalPlayerTester() {
        return "tester".equalsIgnoreCase(getLocalPlayerRole());
    }

    public static boolean isPlayerDeveloper(PlayerEntity player) {
        if (player == null || player.getUuid() == null) return false;
        return "developer".equalsIgnoreCase(getUserPermission(player.getUuid()).getRole());
    }

    public static boolean isPlayerTester(PlayerEntity player) {
        if (player == null || player.getUuid() == null) return false;
        return "tester".equalsIgnoreCase(getUserPermission(player.getUuid()).getRole());
    }

    /**
     * Proactively triggers permission load for local player (called on game startup / world connect).
     */
    public static void fetchLocalPlayerPermissions() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null && client.player.getUuid() != null) {
            fetchPermissionsAsync(client.player.getUuid());
        } else if (client.getSession() != null && client.getSession().getUuidOrNull() != null) {
            fetchPermissionsAsync(client.getSession().getUuidOrNull());
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

        // Unlocked via all_emotes permission from API or developer/tester role
        UserPermission perm = getLocalPlayerPermission();
        if (perm.hasAllEmotes()) {
            return true;
        }

        // Development environment override for convenience
        if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
            return true;
        }

        // Store-locked for regular non-upgraded accounts
        return false;
    }

    public static boolean hasAccess(int slot) {
        return switch (slot) {
            case 0 -> hasAccess(EmoteId.BACKFLIP);
            case 1 -> hasAccess(EmoteId.FRONTFLIP);
            case 2 -> hasAccess(EmoteId.STOP);
            default -> false;
        };
    }
}
