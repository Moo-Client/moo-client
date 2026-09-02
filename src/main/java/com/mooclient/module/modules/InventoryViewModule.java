package com.mooclient.module.modules;

import com.mooclient.module.Module;
import com.mooclient.util.MooHudPositionHelper;
import org.lwjgl.glfw.GLFW;

/**
 * Inventory View Module.
 * Displays 3 rows of main inventory on HUD (slots 9 to 35).
 * Only operates when the module is ENABLED.
 * Toggleable / holdable via custom keybind (Default: "I").
 */
public class InventoryViewModule extends Module {

    public enum InventoryStyle {
        MOO_CLIENT("Moo Client"),
        COMPACT("Compact"),
        VANILLA("Vanilla");

        private final String displayName;

        InventoryStyle(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public enum ActivationMode {
        HOLD("Przytrzymaj (Hold)"),
        TOGGLE("Przełącznik (Toggle)");

        private final String displayName;

        ActivationMode(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    private static boolean enabled = false;
    private static boolean active = true;
    private static boolean isHolding = false;
    private static int keyCode = GLFW.GLFW_KEY_I;
    private static String keyName = "I";
    private static boolean isMouseButton = false;
    private static ActivationMode mode = ActivationMode.TOGGLE;

    public static MooHudPositionHelper.WidgetPosition position =
            new MooHudPositionHelper.WidgetPosition(
                    MooHudPositionHelper.HudAnchorX.RIGHT,
                    MooHudPositionHelper.HudAnchorY.BOTTOM,
                    10, 60);

    public static int width = 180;
    public static int height = 60;

    private static InventoryStyle style = InventoryStyle.MOO_CLIENT;
    private static boolean showBackground = true;
    private static boolean showEmptySlots = true;

    public InventoryViewModule() {
        super("Inventory View", "Podgląd 3 rzędów głównego ekwipunku na ekranie", Category.HUD, false);
    }

    @Override
    public void onEnable() {
        enabled = true;
    }

    @Override
    public void onDisable() {
        enabled = false;
        isHolding = false;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled, boolean saveConfig) {
        super.setEnabled(enabled, saveConfig);
        InventoryViewModule.enabled = enabled;
        if (!enabled) {
            isHolding = false;
        }
    }

    public static boolean isModuleEnabled() {
        return enabled;
    }

    public static void setModuleEnabled(boolean state) {
        enabled = state;
        if (!state) {
            isHolding = false;
        }
        com.mooclient.module.ModuleManager.getInstance().getModule("Inventory View").ifPresent(m -> {
            if (m.isEnabled() != state) {
                m.setEnabled(state);
            }
        });
    }

    public static boolean isActive() {
        return active;
    }

    public static void setActive(boolean state) {
        active = state;
    }

    public static void toggleActive() {
        if (!enabled) return;
        active = !active;
    }

    public static boolean shouldRender() {
        if (!enabled) {
            return false;
        }
        if (mode == ActivationMode.HOLD) {
            return isHolding;
        }
        return active;
    }

    public static boolean isHolding() {
        return isHolding;
    }

    public static void setHolding(boolean holding) {
        if (!enabled) {
            isHolding = false;
            return;
        }
        isHolding = holding;
    }

    public static int getKeyCode() {
        return keyCode;
    }

    public static void setKeyCode(int code) {
        keyCode = code;
    }

    public static String getKeyName() {
        return keyName;
    }

    public static void setKeyName(String name) {
        keyName = name;
    }

    public static boolean isMouseButton() {
        return isMouseButton;
    }

    public static void setMouseButton(boolean mouseButton) {
        isMouseButton = mouseButton;
    }

    public static ActivationMode getMode() {
        return mode;
    }

    public static void setMode(ActivationMode newMode) {
        mode = newMode;
    }

    public static InventoryStyle getStyle() {
        return style;
    }

    public static void setStyle(InventoryStyle newStyle) {
        style = newStyle;
    }

    public static boolean isShowBackground() {
        return showBackground;
    }

    public static void setShowBackground(boolean state) {
        showBackground = state;
    }

    public static void toggleShowBackground() {
        showBackground = !showBackground;
    }

    public static boolean isShowEmptySlots() {
        return showEmptySlots;
    }

    public static void setShowEmptySlots(boolean state) {
        showEmptySlots = state;
    }

    public static void toggleShowEmptySlots() {
        showEmptySlots = !showEmptySlots;
    }

    public static int getSlotSize() {
        return (style == InventoryStyle.COMPACT) ? 18 : 20;
    }

    public static int calculateUnscaledWidth() {
        return 9 * getSlotSize();
    }

    public static int calculateUnscaledHeight() {
        return 3 * getSlotSize();
    }

    public static int calculateBoxWidth(float hudScale) {
        return Math.round(calculateUnscaledWidth() * hudScale);
    }

    public static int calculateBoxHeight(float hudScale) {
        return Math.round(calculateUnscaledHeight() * hudScale);
    }
}
