package com.mooclient.module;

import com.mooclient.module.modules.ArmorModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ArmorModuleTest {

    private ArmorModule armorModule;

    @BeforeEach
    public void setUp() {
        armorModule = new ArmorModule();
        ArmorModule.setArmorEnabled(false);
        ArmorModule.setStyle(ArmorModule.ArmorStyle.MOO_CLIENT);
        ArmorModule.setOrientation(ArmorModule.ArmorOrientation.HORIZONTAL);
        ArmorModule.setDurabilityMode(ArmorModule.DurabilityMode.BAR);
        ArmorModule.setShowBackground(true);
        ArmorModule.setShowEmptySlots(true);
        ArmorModule.setShowOffhand(false);
        ArmorModule.setShowMainHand(false);
    }

    @Test
    public void testModuleMetadata() {
        assertEquals("Armor HUD", armorModule.getName());
        assertEquals(Module.Category.HUD, armorModule.getCategory());
        assertFalse(ArmorModule.isArmorEnabled());
    }

    @Test
    public void testEnableDisable() {
        armorModule.onEnable();
        assertTrue(ArmorModule.isArmorEnabled());

        armorModule.onDisable();
        assertFalse(ArmorModule.isArmorEnabled());
    }

    @Test
    public void testDimensionsHorizontal() {
        ArmorModule.setOrientation(ArmorModule.ArmorOrientation.HORIZONTAL);
        ArmorModule.setShowOffhand(false);
        ArmorModule.setShowMainHand(false);
        ArmorModule.setDurabilityTextMode(ArmorModule.DurabilityTextMode.NONE);

        // 4 slots of 20px each + 3 gaps of 2px = 86px width, 20px height
        assertEquals(86, ArmorModule.calculateUnscaledWidth());
        assertEquals(20, ArmorModule.calculateUnscaledHeight());

        // With text mode enabled (adds 10px on top)
        ArmorModule.setDurabilityTextMode(ArmorModule.DurabilityTextMode.VALUE);
        assertEquals(86, ArmorModule.calculateUnscaledWidth());
        assertEquals(30, ArmorModule.calculateUnscaledHeight());

        // With scale 1.5
        assertEquals(Math.round(86 * 1.5f), ArmorModule.calculateBoxWidth(1.5f));
        assertEquals(Math.round(30 * 1.5f), ArmorModule.calculateBoxHeight(1.5f));
    }

    @Test
    public void testDimensionsVertical() {
        ArmorModule.setOrientation(ArmorModule.ArmorOrientation.VERTICAL);
        ArmorModule.setShowOffhand(false);
        ArmorModule.setShowMainHand(false);
        ArmorModule.setDurabilityTextMode(ArmorModule.DurabilityTextMode.NONE);

        // 4 slots of 20px each + 3 gaps of 2px = 20px width, 86px height
        assertEquals(20, ArmorModule.calculateUnscaledWidth());
        assertEquals(86, ArmorModule.calculateUnscaledHeight());
    }

    @Test
    public void testDimensionsWithOffhandAndMainHand() {
        ArmorModule.setOrientation(ArmorModule.ArmorOrientation.HORIZONTAL);
        ArmorModule.setShowOffhand(true);
        ArmorModule.setShowMainHand(true);

        // 6 slots of 20px each + 5 gaps of 2px = 120 + 10 = 130px width
        assertEquals(130, ArmorModule.calculateUnscaledWidth());
        assertEquals(20, ArmorModule.calculateUnscaledHeight());
        assertEquals(6, ArmorModule.getSlotCount());
    }

    @Test
    public void testCycleStyleAndOrientation() {
        ArmorModule.setStyle(ArmorModule.ArmorStyle.MOO_CLIENT);
        ArmorModule.cycleStyle();
        assertEquals(ArmorModule.ArmorStyle.SIMPLE, ArmorModule.getStyle());
        ArmorModule.cycleStyle();
        assertEquals(ArmorModule.ArmorStyle.COMPACT, ArmorModule.getStyle());

        ArmorModule.setOrientation(ArmorModule.ArmorOrientation.HORIZONTAL);
        ArmorModule.cycleOrientation();
        assertEquals(ArmorModule.ArmorOrientation.VERTICAL, ArmorModule.getOrientation());
    }

    @Test
    public void testCycleDurabilityMode() {
        ArmorModule.setDurabilityTextMode(ArmorModule.DurabilityTextMode.NONE);
        ArmorModule.cycleDurabilityTextMode();
        assertEquals(ArmorModule.DurabilityTextMode.PERCENT, ArmorModule.getDurabilityTextMode());
        ArmorModule.cycleDurabilityTextMode();
        assertEquals(ArmorModule.DurabilityTextMode.VALUE, ArmorModule.getDurabilityTextMode());
        ArmorModule.cycleDurabilityTextMode();
        assertEquals(ArmorModule.DurabilityTextMode.NONE, ArmorModule.getDurabilityTextMode());

        ArmorModule.setShowDurabilityBar(true);
        assertTrue(ArmorModule.isShowDurabilityBar());
        ArmorModule.toggleShowDurabilityBar();
        assertFalse(ArmorModule.isShowDurabilityBar());

        ArmorModule.setLowDurabilityWarning(true);
        assertTrue(ArmorModule.isLowDurabilityWarning());
        ArmorModule.toggleLowDurabilityWarning();
        assertFalse(ArmorModule.isLowDurabilityWarning());
    }

    @Test
    public void testSingleWarningTrigger() {
        ArmorModule.setLowDurabilityWarning(true);
        ArmorModule.resetWarningState();

        assertFalse(ArmorModule.checkAndTriggerWarning(null));
        assertFalse(ArmorModule.checkAndTriggerWarning(java.util.Collections.emptyList()));
    }

    @Test
    public void testModuleManagerRegistration() {
        ModuleManager manager = ModuleManager.getInstance();
        manager.init();
        assertTrue(manager.getModule("Armor HUD").isPresent());
        assertNotNull(manager.getModule(ArmorModule.class));
    }
}
