package com.mooclient.util;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Access Manager for Moo Client Emotes and Cosmetics.
 * Authorizes Developer and Tester accounts for 100% full access to all current and future emotes,
 * while managing free vs store-locked emotes for regular players.
 */
public class EmoteAccessManager {

    private static final Set<UUID> DEVELOPER_UUIDS = new HashSet<>();
    private static final Set<UUID> TESTER_UUIDS = new HashSet<>();

    static {
        // Developer Master Keys (Full unlocked access)
        addDeveloper(UUID.fromString("2e485d7b-47a9-41a3-b574-c534a25c6165"));
        addDeveloper(UUID.fromString("64c37d2b-406a-4685-ad2f-b386bdb0c4c5"));

        // Tester Keys (Full unlocked access + Tester Badge)
        addTester(UUID.fromString("6d4b68b1-0afd-4b6f-9247-e859154936b4"));
    }

    public enum EmoteId {
        HANDS_UP(true),   // Free for all players
        STOP(true),       // Free for all players
        FRONTFLIP(false), // Store / Developer / Tester
        BACKFLIP(false);  // Store / Developer / Tester

        private final boolean isFree;

        EmoteId(boolean isFree) {
            this.isFree = isFree;
        }

        public boolean isFree() {
            return isFree;
        }
    }

    public static void addDeveloper(UUID uuid) {
        if (uuid != null) {
            DEVELOPER_UUIDS.add(uuid);
        }
    }

    public static void addTester(UUID uuid) {
        if (uuid != null) {
            TESTER_UUIDS.add(uuid);
        }
    }

    /**
     * Checks if the local player has developer privileges.
     */
    public static boolean isLocalPlayerDeveloper() {
        MinecraftClient client = MinecraftClient.getInstance();

        // 1. Client Player UUID
        if (client.player != null && client.player.getUuid() != null) {
            if (DEVELOPER_UUIDS.contains(client.player.getUuid())) {
                return true;
            }
        }

        // 2. Client Session UUID
        if (client.getSession() != null && client.getSession().getUuidOrNull() != null) {
            if (DEVELOPER_UUIDS.contains(client.getSession().getUuidOrNull())) {
                return true;
            }
        }

        // 3. Development environment override (unless player is a tester)
        if (FabricLoader.getInstance().isDevelopmentEnvironment() && !isLocalPlayerTester()) {
            return true;
        }

        return false;
    }

    /**
     * Checks if the local player has tester privileges.
     */
    public static boolean isLocalPlayerTester() {
        MinecraftClient client = MinecraftClient.getInstance();

        // 1. Client Player UUID
        if (client.player != null && client.player.getUuid() != null) {
            if (TESTER_UUIDS.contains(client.player.getUuid())) {
                return true;
            }
        }

        // 2. Client Session UUID
        if (client.getSession() != null && client.getSession().getUuidOrNull() != null) {
            if (TESTER_UUIDS.contains(client.getSession().getUuidOrNull())) {
                return true;
            }
        }

        return false;
    }

    /**
     * Checks if a specific player entity has developer privileges.
     */
    public static boolean isPlayerDeveloper(PlayerEntity player) {
        if (player == null || player.getUuid() == null) return false;
        return DEVELOPER_UUIDS.contains(player.getUuid());
    }

    /**
     * Checks if a specific player entity has tester privileges.
     */
    public static boolean isPlayerTester(PlayerEntity player) {
        if (player == null || player.getUuid() == null) return false;
        return TESTER_UUIDS.contains(player.getUuid());
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

        // Developers and Testers have full unlocked access to 100% of emotes
        if (isLocalPlayerDeveloper() || isLocalPlayerTester()) {
            return true;
        }

        // Store-locked for non-developer players (can be hooked to backend API purchases)
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
