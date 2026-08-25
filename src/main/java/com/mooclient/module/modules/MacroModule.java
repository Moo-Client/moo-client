package com.mooclient.module.modules;

import com.mooclient.module.Module;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Macro Module (AutoText / Command Shortcuts)
 * Allows players to bind custom commands and chat messages to keyboard keys or mouse buttons.
 */
public class MacroModule extends Module {

    public static class MacroEntry {
        private String id;
        private String command;
        private int keyCode;
        private String keyName;
        private boolean isMouseButton;
        private boolean enabled;
        private transient boolean wasPressed;

        public MacroEntry(String id, String command, int keyCode, String keyName, boolean isMouseButton, boolean enabled) {
            this.id = id;
            this.command = command;
            this.keyCode = keyCode;
            this.keyName = keyName;
            this.isMouseButton = isMouseButton;
            this.enabled = enabled;
            this.wasPressed = false;
        }

        public String getId() { return id; }
        public String getCommand() { return command; }
        public void setCommand(String command) { this.command = command; }
        public int getKeyCode() { return keyCode; }
        public void setKeyCode(int keyCode) { this.keyCode = keyCode; }
        public String getKeyName() { return keyName; }
        public void setKeyName(String keyName) { this.keyName = keyName; }
        public boolean isMouseButton() { return isMouseButton; }
        public void setMouseButton(boolean mouseButton) { isMouseButton = mouseButton; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public boolean wasPressed() { return wasPressed; }
        public void setWasPressed(boolean wasPressed) { this.wasPressed = wasPressed; }
    }

    private static boolean enabled = false;
    private static final List<MacroEntry> macros = new ArrayList<>();

    static {
        // Default macro slots
        macros.add(new MacroEntry("macro_1", "/spawn", GLFW.GLFW_KEY_KP_1, "NUM 1", false, true));
        macros.add(new MacroEntry("macro_2", "/home", GLFW.GLFW_KEY_KP_2, "NUM 2", false, true));
        macros.add(new MacroEntry("macro_3", "/g c GG", GLFW.GLFW_KEY_KP_3, "NUM 3", false, true));
        macros.add(new MacroEntry("macro_4", "/hub", GLFW.GLFW_KEY_KP_4, "NUM 4", false, false));
        macros.add(new MacroEntry("macro_5", "/heal", GLFW.GLFW_KEY_KP_5, "NUM 5", false, false));
    }

    public MacroModule() {
        super("Macro", "Automatyczne komendy i wiadomości pod klawiszami", Category.MISC);
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean value) {
        enabled = value;
    }

    public static boolean isMacroEnabled() {
        return enabled;
    }

    public static void setMacroEnabled(boolean value) {
        enabled = value;
    }

    public static List<MacroEntry> getMacros() {
        return macros;
    }

    public static void executeMacro(MacroEntry macro) {
        if (macro == null || macro.getCommand() == null || macro.getCommand().trim().isEmpty()) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.player.networkHandler == null) {
            return;
        }

        String text = macro.getCommand().trim();
        if (text.startsWith("/")) {
            // Execute as command
            String command = text.substring(1);
            client.player.networkHandler.sendChatCommand(command);
        } else {
            // Send as chat message
            client.player.networkHandler.sendChatMessage(text);
        }
    }

    /**
     * Checks all macro keybinds each tick and triggers on edge-triggered key press.
     */
    public static void onTick(MinecraftClient client) {
        if (!enabled || client.player == null || client.currentScreen != null) {
            // Reset pressed state when inside menus to avoid accidental trigger
            for (MacroEntry m : macros) {
                m.setWasPressed(false);
            }
            return;
        }

        long window = client.getWindow().getHandle();

        for (MacroEntry m : macros) {
            if (!m.isEnabled() || m.getKeyCode() == GLFW.GLFW_KEY_UNKNOWN) {
                m.setWasPressed(false);
                continue;
            }

            boolean isDown = false;
            if (m.isMouseButton()) {
                isDown = GLFW.glfwGetMouseButton(window, m.getKeyCode()) == GLFW.GLFW_PRESS;
            } else {
                isDown = GLFW.glfwGetKey(window, m.getKeyCode()) == GLFW.GLFW_PRESS;
            }

            if (isDown && !m.wasPressed()) {
                // Key pressed this tick
                m.setWasPressed(true);
                executeMacro(m);
            } else if (!isDown) {
                m.setWasPressed(false);
            }
        }
    }
}
