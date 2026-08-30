package com.mooclient.interaction;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.UUID;

public class DynamicLatencySynchronizerTest {

    @Test
    public void testDynamicLeadTimeCalculation() {
        UUID testPlayer = UUID.randomUUID();

        // 1. Domyślny lead time przy braku pomiaru RTT (domyślny RTT = 60ms -> 60 + 80 = 140ms >= 120ms)
        long defaultLeadTime = DynamicLatencySynchronizer.calculateLeadTimeMs(testPlayer);
        Assertions.assertEquals(140L, defaultLeadTime);

        // 2. Niskie opóźnienie (RTT = 20ms) -> 20 + 80 = 100ms < 120ms -> powinno wynosić min 120ms
        DynamicLatencySynchronizer.recordPingRtt(testPlayer, 20L);
        long lowRttLeadTime = DynamicLatencySynchronizer.calculateLeadTimeMs(testPlayer);
        Assertions.assertEquals(120L, lowRttLeadTime, "LeadTime dla małego RTT powinien wynosić MIN 120ms");

        // 3. Wyższe opóźnienie (RTT = 100ms) -> 100 + 80 = 180ms > 120ms
        DynamicLatencySynchronizer.recordPingRtt(testPlayer, 100L);
        long highRttLeadTime = DynamicLatencySynchronizer.calculateLeadTimeMs(testPlayer);
        Assertions.assertEquals(180L, highRttLeadTime, "LeadTime powinien dynamicznie rosnąć z RTT");

        // 4. Bardzo wysokie opóźnienie (RTT = 350ms) -> 350 + 80 = 430ms
        DynamicLatencySynchronizer.recordPingRtt(testPlayer, 350L);
        long extremeLeadTime = DynamicLatencySynchronizer.calculateLeadTimeMs(testPlayer);
        Assertions.assertEquals(430L, extremeLeadTime);
    }

    @Test
    public void testSynchronizedStartTimeCalculation() {
        UUID target = UUID.randomUUID();
        DynamicLatencySynchronizer.recordPingRtt(target, 50L); // 50 + 80 = 130ms

        long now = System.currentTimeMillis();
        long startAt = DynamicLatencySynchronizer.computeSynchronizedStartTime(target);

        Assertions.assertTrue(startAt >= now + 120L, "Czas START_AT powinien być w przyszłości o co najmniej leadTime");
        Assertions.assertTrue(startAt <= now + 200L, "Czas START_AT nie powinien być nadmiernie opóźniony");
    }
}
