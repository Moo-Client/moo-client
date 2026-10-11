package com.mooclient.module;

import com.mooclient.module.modules.HitboxModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class HitboxModuleTest {

    @BeforeEach
    public void setUp() {
        HitboxModule.setHitboxEnabled(false);
        HitboxModule.setColor(HitboxModule.HitboxColor.WHITE);
        HitboxModule.setShowEyeHeight(false);
        HitboxModule.setShowLookVector(false);
        HitboxModule.setOnlyGlidingOrSwimming(false);
    }

    @Test
    public void testModuleProperties() {
        HitboxModule module = new HitboxModule();
        assertEquals("Hitboxes", module.getName());
        assertEquals(Module.Category.RENDER, module.getCategory());
        assertFalse(module.isEnabled());
        assertFalse(HitboxModule.isHitboxEnabled());

        module.setEnabled(true, false);
        assertTrue(module.isEnabled());
        assertTrue(HitboxModule.isHitboxEnabled());

        module.setEnabled(false, false);
        assertFalse(module.isEnabled());
        assertFalse(HitboxModule.isHitboxEnabled());

        module.onEnable();
        assertTrue(module.isEnabled());
        assertTrue(HitboxModule.isHitboxEnabled());

        module.onDisable();
        assertFalse(module.isEnabled());
        assertFalse(HitboxModule.isHitboxEnabled());

        HitboxModule.toggleHitboxEnabled();
        assertTrue(HitboxModule.isHitboxEnabled());
        HitboxModule.toggleHitboxEnabled();
        assertFalse(HitboxModule.isHitboxEnabled());
    }

    @Test
    public void testColorCyclingAndValues() {
        assertEquals(HitboxModule.HitboxColor.WHITE, HitboxModule.getColor());
        assertEquals(1.0f, HitboxModule.getColorRed(), 0.001f);
        assertEquals(1.0f, HitboxModule.getColorGreen(), 0.001f);
        assertEquals(1.0f, HitboxModule.getColorBlue(), 0.001f);

        HitboxModule.cycleColor();
        assertEquals(HitboxModule.HitboxColor.RED, HitboxModule.getColor());
        assertEquals(1.0f, HitboxModule.getColorRed(), 0.001f);
        assertTrue(HitboxModule.getColorGreen() < 0.5f);

        HitboxModule.setColor(HitboxModule.HitboxColor.BLUE);
        assertEquals(HitboxModule.HitboxColor.BLUE, HitboxModule.getColor());
        assertEquals(1.0f, HitboxModule.getColorBlue(), 0.001f);
        assertEquals(0.0f, HitboxModule.getColorRed(), 0.001f);
    }

    @Test
    public void testFeatureToggles() {
        // Eye Height Line
        assertFalse(HitboxModule.isShowEyeHeight());
        HitboxModule.toggleShowEyeHeight();
        assertTrue(HitboxModule.isShowEyeHeight());
        HitboxModule.setShowEyeHeight(false);
        assertFalse(HitboxModule.isShowEyeHeight());

        // Look Vector Arrow
        assertFalse(HitboxModule.isShowLookVector());
        HitboxModule.toggleShowLookVector();
        assertTrue(HitboxModule.isShowLookVector());
        HitboxModule.setShowLookVector(false);
        assertFalse(HitboxModule.isShowLookVector());

        // Only Gliding Or Swimming
        assertFalse(HitboxModule.isOnlyGlidingOrSwimming());
        HitboxModule.toggleOnlyGlidingOrSwimming();
        assertTrue(HitboxModule.isOnlyGlidingOrSwimming());
        HitboxModule.setOnlyGlidingOrSwimming(false);
        assertFalse(HitboxModule.isOnlyGlidingOrSwimming());
    }
}
