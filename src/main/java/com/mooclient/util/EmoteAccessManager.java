package com.mooclient.util;

import com.mooclient.permissions.PermissionManager;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * @deprecated Zastąpione przez {@link com.mooclient.permissions.PermissionManager}.
 */
@Deprecated
public class EmoteAccessManager {

    public enum EmoteId {
        FREE,
        FRONTFLIP,
        BACKFLIP,
        MEDITATION,
        FACEPALM,
        HANDS_UP
    }

    public static void fetchLocalPlayerPermissions() {
        PermissionManager.fetchLocalPlayerPermissions();
    }

    public static CompletableFuture<PermissionManager.UserPermission> fetchPermissionsAsync(UUID uuid, String username) {
        return PermissionManager.fetchPermissionsAsync(uuid, username);
    }

    public static boolean hasAccess(EmoteId emoteId) {
        if (emoteId == null || emoteId == EmoteId.FREE) return true;
        return PermissionManager.hasAccessLocal(emoteId.name().toLowerCase());
    }

    public static boolean hasAccess(UUID uuid, String username, EmoteId emoteId) {
        if (emoteId == null || emoteId == EmoteId.FREE) return true;
        return PermissionManager.hasAccessLocal(emoteId.name().toLowerCase());
    }
}
