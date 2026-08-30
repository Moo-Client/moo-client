package com.mooclient.module;

import com.mooclient.module.modules.ShulkerTooltipModule;
import com.mooclient.tooltip.ShulkerLockManager;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ShulkerTooltipModuleTest {

    @BeforeEach
    public void setUp() {
        ShulkerTooltipModule.setShulkerEnabled(true);
        ShulkerTooltipModule.setInspectEnabled(true);
        ShulkerTooltipModule.setColorMatchedBorder(true);
        ShulkerTooltipModule.setShowEmptySlots(true);
        ShulkerTooltipModule.setRequireShift(false);
        ShulkerLockManager.unlock();
    }

    @Test
    public void testModuleProperties() {
        ShulkerTooltipModule module = new ShulkerTooltipModule();
        assertEquals("Shulker Tooltip", module.getName());
        assertEquals(Module.Category.UTILITY, module.getCategory());
        assertTrue(module.isEnabled());
        assertTrue(ShulkerTooltipModule.isShulkerEnabled());

        module.setEnabled(false, false);
        assertFalse(module.isEnabled());
        assertFalse(ShulkerTooltipModule.isShulkerEnabled());

        module.toggle();
        assertTrue(module.isEnabled());
        assertTrue(ShulkerTooltipModule.isShulkerEnabled());

        module.toggle();
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

        assertTrue(ShulkerTooltipModule.isInspectEnabled());
        ShulkerTooltipModule.toggleInspectEnabled();
        assertFalse(ShulkerTooltipModule.isInspectEnabled());
    }

    @Test
    public void testShulkerLockManager() {
        assertFalse(ShulkerLockManager.isLocked());
        assertFalse(ShulkerLockManager.hasActiveHover());

        ShulkerLockManager.updateGridPosition(100, 150, 168, 60);
        assertTrue(ShulkerLockManager.isMouseInsideTooltip(110, 160));
        assertFalse(ShulkerLockManager.isMouseInsideTooltip(500, 500));

        ShulkerLockManager.unlock();
        assertFalse(ShulkerLockManager.isLocked());
        assertNull(ShulkerLockManager.getHoveredInnerItem());
        assertEquals(-1, ShulkerLockManager.getHoveredSlotIndex());
    }

    @Test
    public void testModuleManagerRegistration() {
        ModuleManager manager = ModuleManager.getInstance();
        manager.init();
        assertTrue(manager.getModule("Shulker Tooltip").isPresent());
        assertNotNull(manager.getModule(ShulkerTooltipModule.class));
    }
}
