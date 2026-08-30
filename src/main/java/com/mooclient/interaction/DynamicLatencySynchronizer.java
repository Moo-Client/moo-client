package com.mooclient.interaction;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dynamiczny synchronizator czasu i opóźnień sieciowych dla interakcji multiplayer.
 * Zamiast sztywnego 300 ms, wyznacza adaptacyjny lead time oparty o zmierzone RTT i bufor bezpieczeństwa.
 */
public class DynamicLatencySynchronizer {

    private static final Map<UUID, Long> LAST_PING_RTT = new ConcurrentHashMap<>();
    private static final long DEFAULT_ESTIMATED_RTT_MS = 60L;
    private static final long MIN_LEAD_TIME_MS = 120L;
    private static final long MAX_LEAD_TIME_MS = 600L;

    public static void recordPingRtt(UUID playerUuid, long rttMs) {
        if (playerUuid != null && rttMs > 0) {
            LAST_PING_RTT.put(playerUuid, Math.min(rttMs, 1000L));
        }
    }

    public static long getEstimatedRtt(UUID playerUuid) {
        if (playerUuid != null && LAST_PING_RTT.containsKey(playerUuid)) {
            return LAST_PING_RTT.get(playerUuid);
        }
        return DEFAULT_ESTIMATED_RTT_MS;
    }

    /**
     * Oblicza dynamiczny czas przygotowania sceny (lead time) przed rozpoczęciem odtwarzania.
     */
    public static long calculateLeadTimeMs(UUID targetUuid) {
        long rtt = getEstimatedRtt(targetUuid);
        // Lead time = RTT + bufor 80ms na przygotowanie sceny w rendererze
        long lead = rtt + 80L;
        return Math.max(MIN_LEAD_TIME_MS, Math.min(MAX_LEAD_TIME_MS, lead));
    }

    /**
     * Wyznacza wspólny timestamp startu animacji (START_AT).
     */
    public static long computeSynchronizedStartTime(UUID targetUuid) {
        long leadTime = calculateLeadTimeMs(targetUuid);
        return System.currentTimeMillis() + leadTime;
    }
}
