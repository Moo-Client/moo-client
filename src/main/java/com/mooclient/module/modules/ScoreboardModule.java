package com.mooclient.module.modules;

import com.mooclient.module.Module;

/**
 * Scoreboard HUD Module.
 * Allows repositioning, text shadow toggle, background toggle, and score/number toggle.
 */
public class ScoreboardModule extends Module {

    private static boolean enabled = false;
    private static boolean textShadow = true;
    private static boolean showBackground = true;
    private static boolean showScores = false; // false = hide numbers on right, true = show numbers

    // Draggable position & anchor
    public static com.mooclient.util.MooHudPositionHelper.WidgetPosition position =
            new com.mooclient.util.MooHudPositionHelper.WidgetPosition(
                    com.mooclient.util.MooHudPositionHelper.HudAnchorX.RIGHT,
                    com.mooclient.util.MooHudPositionHelper.HudAnchorY.CENTER,
                    6, 0);
    public static int posX = -1;
    public static int posY = -1;
    public static int width = 120;
    public static int height = 80;

    public ScoreboardModule() {
        super("Scoreboard", "Dostosuj i przesuwaj tablicę wyników (Scoreboard)", Category.HUD, false);
    }

    @Override
    public void onEnable() {
        enabled = true;
    }

    @Override
    public void onDisable() {
        enabled = false;
    }

    public static boolean isScoreboardEnabled() {
        return enabled;
    }

    public static void setScoreboardEnabled(boolean state) {
        enabled = state;
    }

    public static boolean isTextShadow() {
        return textShadow;
    }

    public static void setTextShadow(boolean state) {
        textShadow = state;
    }

    public static void toggleTextShadow() {
        textShadow = !textShadow;
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

    public static boolean isShowScores() {
        return showScores;
    }

    public static void setShowScores(boolean state) {
        showScores = state;
    }

    public static void toggleShowScores() {
        showScores = !showScores;
    }

    public static void resetPosition() {
        position = new com.mooclient.util.MooHudPositionHelper.WidgetPosition(
                com.mooclient.util.MooHudPositionHelper.HudAnchorX.RIGHT,
                com.mooclient.util.MooHudPositionHelper.HudAnchorY.CENTER,
                6, 0);
        posX = -1;
        posY = -1;
    }
}
