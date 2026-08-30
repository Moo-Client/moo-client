package com.mooclient.security;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Zabezpieczenie przed spamem, floodem i nadmiernymi zapytaniami sieciowymi.
 */
public class RateLimiter {

    private static final Map<String, Long> LAST_ACTION_TIMES = new ConcurrentHashMap<>();
    private static final Map<String, Integer> ACTION_COUNTS = new ConcurrentHashMap<>();

    /**
     * Sprawdza, czy dana akcja może zostać wykonana dla danego klucza w zadanym oknie czasowym (cooldown).
     */
    public static boolean tryAcquire(String actionKey, long cooldownMs) {
        long now = System.currentTimeMillis();
        Long lastTime = LAST_ACTION_TIMES.get(actionKey);

        if (lastTime == null || (now - lastTime) >= cooldownMs) {
            LAST_ACTION_TIMES.put(actionKey, now);
            return true;
        }
        return false;
    }

    /**
     * Ochrona przed spamem zaproszeń do tego samego gracza (maks. 1 zaproszenie na 3 sekundy).
     */
    public static boolean canSendInvitation(UUID targetUuid) {
        if (targetUuid == null) return false;
        return tryAcquire("invite_to_" + targetUuid, 3000L);
    }

    public static boolean canSendRequest(UUID targetUuid) {
        return canSendInvitation(targetUuid);
    }

    public static void recordRequestSent(UUID targetUuid) {
        if (targetUuid != null) {
            LAST_ACTION_TIMES.put("invite_to_" + targetUuid, System.currentTimeMillis());
        }
    }

    /**
     * Ochrona przed floodem przychodzących zaproszeń z jednego UUID (maks. 3 zaproszenia na 5 sekund).
     */
    public static boolean canAcceptIncomingRequest(UUID senderUuid) {
        if (senderUuid == null) return false;
        String key = "incoming_" + senderUuid;
        long now = System.currentTimeMillis();

        Long lastTime = LAST_ACTION_TIMES.get(key);
        if (lastTime == null || (now - lastTime) > 5000L) {
            LAST_ACTION_TIMES.put(key, now);
            ACTION_COUNTS.put(key, 1);
            return true;
        }

        int count = ACTION_COUNTS.getOrDefault(key, 0);
        if (count < 3) {
            ACTION_COUNTS.put(key, count + 1);
            return true;
        }

        return false;
    }

    /**
     * Ochrona przed spamem pakietów ogólnych.
     */
    public static boolean canSendNetworkPacket() {
        return tryAcquire("network_out_global", 100L);
    }

    public static void clear() {
        LAST_ACTION_TIMES.clear();
        ACTION_COUNTS.clear();
    }
}
