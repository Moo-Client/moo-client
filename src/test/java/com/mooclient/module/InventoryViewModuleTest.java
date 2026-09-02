package com.mooclient.module;

import com.mooclient.module.modules.InventoryViewModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import static org.junit.jupiter.api.Assertions.*;

public class InventoryViewModuleTest {

    @BeforeEach
    public void setup() {
        InventoryViewModule.setModuleEnabled(false);
        InventoryViewModule.setActive(true);
        InventoryViewModule.setKeyCode(GLFW.GLFW_KEY_I);
        InventoryViewModule.setKeyName("I");
        InventoryViewModule.setMouseButton(false);
        InventoryViewModule.setMode(InventoryViewModule.ActivationMode.TOGGLE);
        InventoryViewModule.setStyle(InventoryViewModule.InventoryStyle.MOO_CLIENT);
        InventoryViewModule.setShowBackground(true);
        InventoryViewModule.setShowEmptySlots(true);
        InventoryViewModule.setHolding(false);
    }

    @Test
    public void testDefaultConfiguration() {
        assertEquals(GLFW.GLFW_KEY_I, InventoryViewModule.getKeyCode());
        assertEquals("I", InventoryViewModule.getKeyName());
        assertFalse(InventoryViewModule.isMouseButton());
        assertEquals(InventoryViewModule.ActivationMode.TOGGLE, InventoryViewModule.getMode());
        assertEquals(InventoryViewModule.InventoryStyle.MOO_CLIENT, InventoryViewModule.getStyle());
        assertTrue(InventoryViewModule.isShowBackground());
        assertTrue(InventoryViewModule.isShowEmptySlots());
        assertFalse(InventoryViewModule.isModuleEnabled());
        assertFalse(InventoryViewModule.shouldRender());
    }

    @Test
    public void testModuleDisabledBlocksRenderAndKeybind() {
        // Disabled module should never render
        InventoryViewModule.setModuleEnabled(false);
        InventoryViewModule.setActive(true);
        assertFalse(InventoryViewModule.shouldRender());

        // toggleActive when disabled should not change active
        InventoryViewModule.toggleActive();
        assertTrue(InventoryViewModule.isActive());
        assertFalse(InventoryViewModule.shouldRender());
    }

    @Test
    public void testToggleAndRenderWhenEnabled() {
        InventoryViewModule.setModuleEnabled(true);
        InventoryViewModule.setActive(true);
        assertTrue(InventoryViewModule.shouldRender());

        InventoryViewModule.toggleActive();
        assertFalse(InventoryViewModule.isActive());
        assertFalse(InventoryViewModule.shouldRender());

        InventoryViewModule.toggleActive();
        assertTrue(InventoryViewModule.isActive());
        assertTrue(InventoryViewModule.shouldRender());
    }

    @Test
    public void testHoldMode() {
        InventoryViewModule.setMode(InventoryViewModule.ActivationMode.HOLD);
        InventoryViewModule.setModuleEnabled(true);
        assertFalse(InventoryViewModule.shouldRender());

        InventoryViewModule.setHolding(true);
        assertTrue(InventoryViewModule.shouldRender());

        InventoryViewModule.setHolding(false);
        assertFalse(InventoryViewModule.shouldRender());

        // Disabled module should not render even if holding
        InventoryViewModule.setModuleEnabled(false);
        InventoryViewModule.setHolding(true);
        assertFalse(InventoryViewModule.shouldRender());
    }

    @Test
    public void testSlotDimensionsAndScales() {
        InventoryViewModule.setStyle(InventoryViewModule.InventoryStyle.MOO_CLIENT);
        assertEquals(20, InventoryViewModule.getSlotSize());
        assertEquals(180, InventoryViewModule.calculateUnscaledWidth()); // 9 * 20
        assertEquals(60, InventoryViewModule.calculateUnscaledHeight()); // 3 * 20
        assertEquals(180, InventoryViewModule.calculateBoxWidth(1.0f));
        assertEquals(60, InventoryViewModule.calculateBoxHeight(1.0f));

        // Compact style
        InventoryViewModule.setStyle(InventoryViewModule.InventoryStyle.COMPACT);
        assertEquals(18, InventoryViewModule.getSlotSize());
        assertEquals(162, InventoryViewModule.calculateUnscaledWidth()); // 9 * 18
        assertEquals(54, InventoryViewModule.calculateUnscaledHeight()); // 3 * 18
    }
}
