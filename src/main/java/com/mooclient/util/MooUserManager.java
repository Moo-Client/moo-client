package com.mooclient.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * High-performance, zero-lag player tracking for Moo Client.
 * Local player is ALWAYS recognized instantly.
 * Includes O(1) concurrent caching for maximum render-loop FPS.
 */
public class MooUserManager {

    private static final Set<String> MOO_USERS_NAMES = Collections.synchronizedSet(new HashSet<>());
    private static final Set<UUID> MOO_USERS_UUIDS = Collections.synchronizedSet(new HashSet<>());
    private static final Map<String, Boolean> LOOKUP_CACHE = new ConcurrentHashMap<>();
    private static final Pattern USERNAME_PATTERN = Pattern.compile("[a-zA-Z0-9_]{3,16}");

    public static void registerUser(String username, UUID uuid) {
        if (username != null && !username.trim().isEmpty()) {
            String clean = cleanName(username);
            if (!clean.isEmpty()) {
                MOO_USERS_NAMES.add(clean);
                LOOKUP_CACHE.clear();
            }
        }
        if (uuid != null) {
            MOO_USERS_UUIDS.add(uuid);
            LOOKUP_CACHE.clear();
        }
    }

    public static void unregisterUser(String username, UUID uuid) {
        if (username != null) {
            String clean = cleanName(username);
            if (!clean.isEmpty()) {
                MOO_USERS_NAMES.remove(clean);
                LOOKUP_CACHE.clear();
            }
        }
        if (uuid != null) {
            MOO_USERS_UUIDS.remove(uuid);
            LOOKUP_CACHE.clear();
        }
    }

    public static void clear() {
        MOO_USERS_NAMES.clear();
        MOO_USERS_UUIDS.clear();
        LOOKUP_CACHE.clear();
    }

    public static String cleanName(String name) {
        if (name == null || name.isEmpty()) return "";

        if (name.indexOf('§') == -1 && !name.startsWith("literal{")) {
            return name.trim().toLowerCase();
        }

        return name.replaceAll("(?i)§[0-9a-fk-or]", "")
                   .replaceAll("(?i)literal\\{text='(.*?)'\\}", "$1")
                   .trim().toLowerCase();
    }

    public static boolean isMooUser(UUID uuid) {
        if (uuid == null) return false;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null && uuid.equals(client.player.getUuid())) return true;
        return MOO_USERS_UUIDS.contains(uuid);
    }

    public static boolean isMooUser(String name) {
        if (name == null || name.isEmpty()) return false;
        return isMooUser(name, -1);
    }

    /**
     * Checks if the player represented by the Tab list entry is a confirmed Moo Client user.
     */
    public static boolean isMooUser(PlayerListEntry entry) {
        if (entry == null || entry.getProfile() == null) return false;
        if (!com.mooclient.module.modules.NametagsModule.isNametagsEnabled() || !com.mooclient.module.modules.NametagsModule.isShowLogo()) {
            return false;
        }

        MinecraftClient client = MinecraftClient.getInstance();

        // 1. Always check local player (O(1))
        if (client.getSession() != null && client.getSession().getUsername() != null && client.getSession().getUsername().equalsIgnoreCase(entry.getProfile().getName())) {
            return true;
        }
        if (client.player != null && client.player.getUuid() != null && client.player.getUuid().equals(entry.getProfile().getId())) {
            return true;
        }

        // 2. UUID Match (O(1))
        if (entry.getProfile().getId() != null && MOO_USERS_UUIDS.contains(entry.getProfile().getId())) {
            return true;
        }

        // 3. Exact profile name match
        String nameClean = cleanName(entry.getProfile().getName());
        if (!nameClean.isEmpty() && MOO_USERS_NAMES.contains(nameClean)) {
            return true;
        }

        // 4. Match any words in display name
        if (entry.getDisplayName() != null) {
            String display = cleanName(entry.getDisplayName().getString());
            if (matchesAnyUser(display)) {
                return true;
            }
        }

        return matchesAnyUser(nameClean);
    }

    public static boolean isMooUser(Text text, int entityId) {
        if (text == null) return false;
        return isMooUser(text.getString(), entityId);
    }

    /**
     * Checks if the given player is a confirmed Moo Client user (with O(1) concurrent caching).
     */
    public static boolean isMooUser(String playerName, int entityId) {
        if (playerName == null || playerName.isEmpty()) return false;

        MinecraftClient client = MinecraftClient.getInstance();

        // 1. Local Player Fast Path (ALWAYS TRUE FOR LOCAL PLAYER)
        if (client.player != null && client.player.getId() == entityId) {
            return true;
        }

        String targetClean = cleanName(playerName);
        if (targetClean.isEmpty()) return false;

        if (client.getSession() != null && client.getSession().getUsername() != null) {
            String myClean = cleanName(client.getSession().getUsername());
            if (!myClean.isEmpty() && (targetClean.equals(myClean) || targetClean.contains(myClean) || myClean.contains(targetClean))) {
                return true;
            }
        }
        if (client.player != null && client.player.getName() != null) {
            String myNameClean = cleanName(client.player.getName().getString());
            if (!myNameClean.isEmpty() && (targetClean.equals(myNameClean) || targetClean.contains(myNameClean) || myNameClean.contains(targetClean))) {
                return true;
            }
        }

        // 2. Check LRU / Fast Cache
        Boolean cached = LOOKUP_CACHE.get(targetClean);
        if (cached != null) {
            return cached;
        }

        boolean result = evaluateUser(targetClean, entityId, client);
        if (LOOKUP_CACHE.size() > 500) {
            LOOKUP_CACHE.clear();
        }
        LOOKUP_CACHE.put(targetClean, result);
        return result;
    }

    private static boolean evaluateUser(String targetClean, int entityId, MinecraftClient client) {
        // Direct username match in registered Moo users
        if (MOO_USERS_NAMES.contains(targetClean)) {
            return true;
        }

        // Word token matcher (handles ranks like "[VIP] Player", "★ Nick ★", etc.)
        if (matchesAnyUser(targetClean)) {
            return true;
        }

        // Check entity in world
        if (client.world != null && entityId >= 0) {
            Entity entity = client.world.getEntityById(entityId);
            if (entity instanceof PlayerEntity player) {
                if (player.getUuid() != null && MOO_USERS_UUIDS.contains(player.getUuid())) {
                    return true;
                }
                String entityClean = cleanName(player.getNameForScoreboard());
                if (!entityClean.isEmpty() && (MOO_USERS_NAMES.contains(entityClean) || matchesAnyUser(entityClean))) {
                    return true;
                }
            }
        }

        // Tab list entry match
        if (client.getNetworkHandler() != null) {
            for (PlayerListEntry entry : client.getNetworkHandler().getPlayerList()) {
                if (entry.getProfile() != null) {
                    if (MOO_USERS_UUIDS.contains(entry.getProfile().getId())) {
                        String profileClean = cleanName(entry.getProfile().getName());
                        if (targetClean.contains(profileClean) || profileClean.contains(targetClean)) {
                            return true;
                        }
                    }
                    String profileClean = cleanName(entry.getProfile().getName());
                    if (MOO_USERS_NAMES.contains(profileClean)) {
                        if (targetClean.contains(profileClean) || profileClean.contains(targetClean)) {
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    private static boolean matchesAnyUser(String text) {
        if (text == null || text.isEmpty()) return false;
        Matcher m = USERNAME_PATTERN.matcher(text);
        while (m.find()) {
            String token = m.group().toLowerCase();
            if (MOO_USERS_NAMES.contains(token)) {
                return true;
            }
        }
        for (String registered : MOO_USERS_NAMES) {
            if (text.contains(registered) || registered.contains(text)) {
                return true;
            }
        }
        return false;
    }
}
