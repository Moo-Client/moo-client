package com.mooclient.module;

import com.mooclient.module.modules.ShulkerTooltipModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ShulkerTooltipModuleTest {

    @BeforeEach
    public void setUp() {
        ShulkerTooltipModule.setShulkerEnabled(true);
        ShulkerTooltipModule.setColorMatchedBorder(true);
        ShulkerTooltipModule.setShowEmptySlots(true);
        ShulkerTooltipModule.setRequireShift(false);
    }

    @Test
    public void testModuleProperties() {
        ShulkerTooltipModule module = new ShulkerTooltipModule();
        assertEquals("Shulker Tooltip", module.getName());
        assertEquals(Module.Category.UTILITY, module.getCategory());
        assertTrue(module.isEnabled());

        module.setEnabled(false);
        assertFalse(module.isEnabled());
        assertFalse(ShulkerTooltipModule.isShulkerEnabled());
    }

    @Test
    public void testToggles() {
        assertTrue(ShulkerTooltipModule.isColorMatchedBorder());
        ShulkerTooltipModule.toggleColorMatchedBorder();
        assertFalse(ShulkerTooltipModule.isColorMatchedBorder());

        assertTrue(ShulkerTooltipModule.isShowEmptySlots());
        ShulkerTooltipModule.toggleShowEmptySlots();
        assertFalse(ShulkerTooltipModule.isShowEmptySlots());

        assertFalse(ShulkerTooltipModule.isRequireShift());
        ShulkerTooltipModule.toggleRequireShift();
        assertTrue(ShulkerTooltipModule.isRequireShift());
    }

    @Test
    public void testModuleManagerRegistration() {
        ModuleManager manager = ModuleManager.getInstance();
        manager.init();
        assertTrue(manager.getModule("Shulker Tooltip").isPresent());
        assertNotNull(manager.getModule(ShulkerTooltipModule.class));
    }
}
