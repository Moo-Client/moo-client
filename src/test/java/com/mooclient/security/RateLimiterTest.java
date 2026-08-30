package com.mooclient.security;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

public class RateLimiterTest {

    @BeforeEach
    public void setUp() {
        RateLimiter.clear();
    }

    @Test
    public void testCooldownEnforcement() {
        UUID target = UUID.randomUUID();

        // Pierwsza próba powinna być dozwolona
        Assertions.assertTrue(RateLimiter.canSendRequest(target), "Pierwsze zaproszenie powinno przejść");
        RateLimiter.recordRequestSent(target);

        // Natychmiastowa kolejna próba powinna zostać zablokowana przez cooldown
        Assertions.assertFalse(RateLimiter.canSendRequest(target), "Kolejne zaproszenie przed upływem cooldownu powinno być zablokowane");
    }

    @Test
    public void testIncomingRequestLimit() {
        UUID sender = UUID.randomUUID();

        // 3 zaproszenia w krótkim czasie powinny przejść (limit = 3)
        Assertions.assertTrue(RateLimiter.canAcceptIncomingRequest(sender));
        Assertions.assertTrue(RateLimiter.canAcceptIncomingRequest(sender));
        Assertions.assertTrue(RateLimiter.canAcceptIncomingRequest(sender));

        // 4 zaproszenie powinno zostać zablokowane (ochrona przed spamem)
        Assertions.assertFalse(RateLimiter.canAcceptIncomingRequest(sender), "Czwarta próba z tego samego UUID powinna zostać odrzucona przez anti-spam");
    }
}
