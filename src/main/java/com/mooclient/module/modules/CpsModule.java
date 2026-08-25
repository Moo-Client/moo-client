package com.mooclient.module.modules;

import com.mooclient.module.Module;

import java.util.ArrayList;
import java.util.List;

/**
 * CPS (Clicks Per Second) HUD module displaying real-time LMB, RMB, or Both CPS.
 */
public class CpsModule extends Module {

    public enum CpsDisplayMode {
        BOTH("LPM | PPM"),
        LEFT_ONLY("Tylko LPM"),
        RIGHT_ONLY("Tylko PPM");

        private final String displayName;

        CpsDisplayMode(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public enum CpsStyle {
        MOO_CLIENT("Moo Client Look"),
        SIMPLE("Czysty / Simple"),
        BRACKETS("[ 12 | 14 CPS ]");

        private final String displayName;

        CpsStyle(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    private static boolean enabled = false;
    private static CpsDisplayMode displayMode = CpsDisplayMode.BOTH;
    private static CpsStyle style = CpsStyle.MOO_CLIENT;
    private static boolean showBackground = true;
    private static boolean textShadow = true;
    private static boolean showPrefix = true;

    // Draggable coordinates & anchor (default below Ping widget)
    public static com.mooclient.util.MooHudPositionHelper.WidgetPosition position =
            new com.mooclient.util.MooHudPositionHelper.WidgetPosition(
                    com.mooclient.util.MooHudPositionHelper.HudAnchorX.LEFT,
                    com.mooclient.util.MooHudPositionHelper.HudAnchorY.TOP,
                    6, 48);
    public static int posX = 6;
    public static int posY = 48;
    public static int width = 64;
    public static int height = 12;

    // Real-time click timestamps
    private static final List<Long> leftClicks = new ArrayList<>();
    private static final List<Long> rightClicks = new ArrayList<>();

    public CpsModule() {
        super("CPS", "Wyświetla liczbę kliknięć na sekundę (CPS) dla LPM i PPM", Category.HUD, false);
    }

    @Override
    public void onEnable() {
        enabled = true;
    }

    @Override
    public void onDisable() {
        enabled = false;
    }

    public static boolean isCpsEnabled() {
        return enabled;
    }

    public static void setCpsEnabled(boolean state) {
        enabled = state;
    }

    public static CpsDisplayMode getDisplayMode() {
        return displayMode;
    }

    public static void setDisplayMode(CpsDisplayMode mode) {
        displayMode = mode;
    }

    public static void cycleDisplayMode() {
        CpsDisplayMode[] modes = CpsDisplayMode.values();
        int next = (displayMode.ordinal() + 1) % modes.length;
        setDisplayMode(modes[next]);
    }

    public static CpsStyle getStyle() {
        return style;
    }

    public static void setStyle(CpsStyle newStyle) {
        style = newStyle;
    }

    public static void cycleStyle() {
        CpsStyle[] styles = CpsStyle.values();
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

    public static synchronized void registerLeftClick() {
        leftClicks.add(System.currentTimeMillis());
    }

    public static synchronized void registerRightClick() {
        rightClicks.add(System.currentTimeMillis());
    }

    public static synchronized int getLeftCps() {
        long now = System.currentTimeMillis();
        leftClicks.removeIf(time -> now - time > 1000L);
        return leftClicks.size();
    }

    public static synchronized int getRightCps() {
        long now = System.currentTimeMillis();
        rightClicks.removeIf(time -> now - time > 1000L);
        return rightClicks.size();
    }

    public static String getFormattedText(int leftCps, int rightCps) {
        String countText;
        if (displayMode == CpsDisplayMode.LEFT_ONLY) {
            countText = String.valueOf(leftCps);
        } else if (displayMode == CpsDisplayMode.RIGHT_ONLY) {
            countText = String.valueOf(rightCps);
        } else {
            countText = leftCps + " | " + rightCps;
        }

        if (style == CpsStyle.BRACKETS) {
            return showPrefix ? "[ " + countText + " CPS ]" : "[ " + countText + " ]";
        } else if (showPrefix) {
            return "CPS: " + countText;
        } else {
            return countText;
        }
    }
}
