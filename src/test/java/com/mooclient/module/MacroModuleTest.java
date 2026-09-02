package com.mooclient.module;

import com.mooclient.module.modules.MacroModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class MacroModuleTest {

    @BeforeEach
    public void setup() {
        MacroModule.getMacros().clear();
    }

    @Test
    public void testAddAndRemoveMacros() {
        assertEquals(0, MacroModule.getMacros().size());
        assertTrue(MacroModule.canAddMacro());

        boolean added = MacroModule.addMacro("/test1", 49, "1", false, true);
        assertTrue(added);
        assertEquals(1, MacroModule.getMacros().size());
        assertEquals("/test1", MacroModule.getMacros().get(0).getCommand());

        MacroModule.addMacro("/test2", 50, "2", false, true);
        assertEquals(2, MacroModule.getMacros().size());

        boolean removed = MacroModule.removeMacro(0);
        assertTrue(removed);
        assertEquals(1, MacroModule.getMacros().size());
        assertEquals("/test2", MacroModule.getMacros().get(0).getCommand());
    }

    @Test
    public void testMax10MacrosLimit() {
        for (int i = 1; i <= 10; i++) {
            assertTrue(MacroModule.canAddMacro());
            assertTrue(MacroModule.addMacro("/cmd" + i, i, String.valueOf(i), false, true));
        }

        assertEquals(10, MacroModule.getMacros().size());
        assertFalse(MacroModule.canAddMacro());
        assertFalse(MacroModule.addMacro("/overflow", 99, "99", false, true));
        assertEquals(10, MacroModule.getMacros().size());

        // Remove one, then adding should succeed
        assertTrue(MacroModule.removeMacro(9));
        assertEquals(9, MacroModule.getMacros().size());
        assertTrue(MacroModule.canAddMacro());
        assertTrue(MacroModule.addMacro("/new", 100, "NEW", false, true));
        assertEquals(10, MacroModule.getMacros().size());
    }
}
