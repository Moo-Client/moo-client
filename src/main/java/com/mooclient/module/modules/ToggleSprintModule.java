package com.mooclient.module.modules;

import com.mooclient.module.Module;
import org.lwjgl.glfw.GLFW;

/**
 * Toggle Sprint module — automatically keeps the player sprinting and displays HUD status.
 */
public class ToggleSprintModule extends Module {

    public enum SprintStyle {
        MOO_CLIENT("Moo Client Look"),
        SIMPLE("Czysty / Simple"),
        BRACKETS("[ Sprint ]");

        private final String displayName;

        SprintStyle(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    private static boolean enabled = true;
    private static boolean active = true; // In-game runtime toggle state
    private static SprintStyle style = SprintStyle.MOO_CLIENT;
    private static boolean showBackground = true;
    private static boolean textShadow = true;

    // Configurable Keybind (Default: Left Control)
    private static int keyCode = GLFW.GLFW_KEY_LEFT_CONTROL;
    private static String keyName = "LCONTROL";

    // Draggable coordinates & anchor
    public static com.mooclient.util.MooHudPositionHelper.WidgetPosition position =
            new com.mooclient.util.MooHudPositionHelper.WidgetPosition(
                    com.mooclient.util.MooHudPositionHelper.HudAnchorX.LEFT,
                    com.mooclient.util.MooHudPositionHelper.HudAnchorY.TOP,
                    6, 20);
    public static int posX = 6;
    public static int posY = 22;
    public static int width = 90;
    public static int height = 12;

    public ToggleSprintModule() {
        super("Sprint", "Automatyczny ciągły bieg gracza", Category.HUD);
        setEnabled(true);
        active = true;
    }

    @Override
    public void onEnable() {
        enabled = true;
        active = true;
    }

    @Override
    public void onDisable() {
        enabled = false;
        active = false;
    }

    public static boolean isSprintEnabled() {
        return enabled;
    }

    public static void setSprintEnabled(boolean state) {
        enabled = state;
        active = state;
    }

    public static boolean isSprintActive() {
        return active;
    }

    public static void toggleSprintActive() {
        active = !active;
    }

    public static void setSprintActive(boolean state) {
        active = state;
    }

    public static boolean shouldSprint() {
        return enabled && active;
    }

    public static SprintStyle getStyle() {
        return style;
    }

    public static void setStyle(SprintStyle newStyle) {
        style = newStyle;
        if (style == SprintStyle.MOO_CLIENT) {
            showBackground = true;
        } else if (style == SprintStyle.SIMPLE) {
            showBackground = false;
        }
    }

    public static void cycleStyle() {
        SprintStyle[] styles = SprintStyle.values();
        int next = (style.ordinal() + 1) % styles.length;
        setStyle(styles[next]);
    }

    public static boolean isShowBackground() {
        return showBackground;
    }

    public static void toggleShowBackground() {
        showBackground = !showBackground;
    }

    public static void setShowBackground(boolean state) {
        showBackground = state;
    }

    public static boolean isTextShadow() {
        return textShadow;
    }

    public static void toggleTextShadow() {
        textShadow = !textShadow;
    }

    public static void setTextShadow(boolean state) {
        textShadow = state;
    }

    public static int getKeyCode() {
        return keyCode;
    }

    public static String getKeyName() {
        return keyName;
    }

    public static void setKeybind(int code, String name) {
        keyCode = code;
        keyName = name;
    }
}
