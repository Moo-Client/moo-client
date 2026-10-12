package com.mooclient.util;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class PlayerArmorDurabilityTrackerTest {

    @Test
    public void testSetExactDurability() {
        String username = "TestPlayer";
        UUID uuid = UUID.randomUUID();

        // Create player record manually in tracker
        PlayerArmorDurabilityTracker.TrackedPlayerArmor tracked =
                new PlayerArmorDurabilityTracker.TrackedPlayerArmor(101, username, uuid);
        tracked.slots[0] = new PlayerArmorDurabilityTracker.TrackedSlot(null, 407.0f, 407, 3);
        tracked.slots[1] = new PlayerArmorDurabilityTracker.TrackedSlot(null, 592.0f, 592, 3);
        tracked.slots[2] = new PlayerArmorDurabilityTracker.TrackedSlot(null, 555.0f, 555, 3);
        tracked.slots[3] = new PlayerArmorDurabilityTracker.TrackedSlot(null, 481.0f, 481, 3);
        PlayerArmorDurabilityTracker.registerTrackedPlayer(tracked);

        PlayerArmorDurabilityTracker.setExactDurability(username, uuid, 380, 520, 500, 410);

        // Tracker updates slots if present
        assertEquals(380, Math.round(tracked.slots[0].currentDurability));
        assertEquals(520, Math.round(tracked.slots[1].currentDurability));
        assertEquals(500, Math.round(tracked.slots[2].currentDurability));
        assertEquals(410, Math.round(tracked.slots[3].currentDurability));
    }

    @Test
    public void testLocalAttackAttemptDoesNotDamageDurabilityImmediately() {
        String username = "SpawnPlayer";
        UUID uuid = UUID.randomUUID();
        int entityId = 202;

        PlayerArmorDurabilityTracker.TrackedPlayerArmor tracked =
                new PlayerArmorDurabilityTracker.TrackedPlayerArmor(entityId, username, uuid);
        tracked.slots[0] = new PlayerArmorDurabilityTracker.TrackedSlot(null, 407.0f, 407, 3);
        PlayerArmorDurabilityTracker.registerTrackedPlayer(tracked);

        // Simulate local attack attempt (e.g. hitting on spawn)
        PlayerArmorDurabilityTracker.recordLocalAttackAttempt(entityId, null, false);

        // Armor durability should remain 100% untouched because no server damage packet was received!
        assertEquals(407, Math.round(tracked.slots[0].currentDurability));
    }

    @Test
    public void testClearEntity() {
        int entityId = 999;
        PlayerArmorDurabilityTracker.clearEntity(entityId);
        // Should execute cleanly without throwing
        assertTrue(true);
    }
}
