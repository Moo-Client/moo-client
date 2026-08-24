package com.mooclient.module.modules;

import com.mooclient.module.Module;

/**
 * FPS HUD module with customizable appearance, draggable coordinates, and Lunar-style options.
 */
public class FpsModule extends Module {

    public enum FpsStyle {
        MOO_CLIENT("Moo Client Look"),
        SIMPLE("Czysty / Simple"),
        BRACKETS("[ 144 FPS ]");

        private final String displayName;

        FpsStyle(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    private static boolean enabled = true;
    private static FpsStyle style = FpsStyle.MOO_CLIENT;
    private static boolean showBackground = true;
    private static boolean textShadow = true;
    private static boolean showPrefix = true;

    // Draggable coordinates & anchor
    public static com.mooclient.util.MooHudPositionHelper.WidgetPosition position =
            new com.mooclient.util.MooHudPositionHelper.WidgetPosition(
                    com.mooclient.util.MooHudPositionHelper.HudAnchorX.LEFT,
                    com.mooclient.util.MooHudPositionHelper.HudAnchorY.TOP,
                    6, 6);
    public static int posX = 6;
    public static int posY = 6;
    public static int width = 54;
    public static int height = 12;

    public FpsModule() {
        super("FPS", "Wyświetla licznik FPS na ekranie", Category.HUD);
        setEnabled(true);
    }

    @Override
    public void onEnable() {
        enabled = true;
    }

    @Override
    public void onDisable() {
        enabled = false;
    }

    public static boolean isFpsEnabled() {
        return enabled;
    }

    public static void setFpsEnabled(boolean state) {
        enabled = state;
    }

    public static FpsStyle getStyle() {
        return style;
    }

    public static void setStyle(FpsStyle newStyle) {
        style = newStyle;
    }

    public static void cycleStyle() {
        FpsStyle[] styles = FpsStyle.values();
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

    public static boolean isShowPrefix() {
        return showPrefix;
    }

    public static void toggleShowPrefix() {
        showPrefix = !showPrefix;
    }

    public static void setShowPrefix(boolean state) {
        showPrefix = state;
    }
}
