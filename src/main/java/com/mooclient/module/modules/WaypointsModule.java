package com.mooclient.module.modules;

import com.mooclient.module.Module;
import org.lwjgl.glfw.GLFW;

/**
 * Waypoints Module — in-world navigation markers and waypoint manager GUI.
 */
public class WaypointsModule extends Module {

    private static boolean enabled = false;
    private static boolean showDistance = true;
    private static boolean showBeacons = true;
    private static boolean showBackground = true;
    private static boolean textShadow = true;
    private static boolean deathWaypoint = true;
    private static boolean showAllDimensions = false;
    private static boolean showAllServers = false;
    private static float scale = 1.0f; // Range: 0.5f - 2.0f

    // Configurable Keybind (Default: B)
    private static int keyCode = GLFW.GLFW_KEY_B;
    private static String keyName = "B";

    public WaypointsModule() {
        super("Waypoints", "Punkty nawigacyjne w świecie i radar celów", Category.RENDER, false);
    }

    @Override
    public void onEnable() {
        enabled = true;
    }

    @Override
    public void onDisable() {
        enabled = false;
    }

    public static boolean isWaypointsEnabled() {
        return enabled;
    }

    public static void setWaypointsEnabled(boolean state) {
        enabled = state;
    }

    public static boolean isShowDistance() {
        return showDistance;
    }

    public static void setShowDistance(boolean state) {
        showDistance = state;
    }

    public static void toggleShowDistance() {
        showDistance = !showDistance;
    }

    public static boolean isShowBeacons() {
        return showBeacons;
    }

    public static void setShowBeacons(boolean state) {
        showBeacons = state;
    }

    public static void toggleShowBeacons() {
        showBeacons = !showBeacons;
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

    public static boolean isTextShadow() {
        return textShadow;
    }

    public static void setTextShadow(boolean state) {
        textShadow = state;
    }

    public static void toggleTextShadow() {
        textShadow = !textShadow;
    }

    public static boolean isDeathWaypoint() {
        return deathWaypoint;
    }

    public static void setDeathWaypoint(boolean state) {
        deathWaypoint = state;
    }

    public static void toggleDeathWaypoint() {
        deathWaypoint = !deathWaypoint;
    }

    public static boolean isShowAllDimensions() {
        return showAllDimensions;
    }

    public static void setShowAllDimensions(boolean state) {
        showAllDimensions = state;
        com.mooclient.waypoint.WaypointManager.getInstance().invalidateCache();
        com.mooclient.util.MooConfig.save();
    }

    public static void toggleShowAllDimensions() {
        setShowAllDimensions(!showAllDimensions);
    }

    public static boolean isShowAllServers() {
        return showAllServers;
    }

    public static void setShowAllServers(boolean state) {
        showAllServers = state;
        com.mooclient.waypoint.WaypointManager.getInstance().invalidateCache();
        com.mooclient.util.MooConfig.save();
    }

    public static void toggleShowAllServers() {
        setShowAllServers(!showAllServers);
    }

    public static float getScale() {
        return scale;
    }

    public static int getScalePercent() {
        return Math.max(0, Math.min(100, Math.round(scale * 100.0f)));
    }

    public static void setScalePercent(int percent) {
        setScale(Math.max(0.1f, Math.min(100, percent) / 100.0f));
    }

    public static void setScale(float newScale) {
        scale = Math.max(0.1f, Math.min(2.5f, Math.round(newScale * 100.0f) / 100.0f));
    }

    private static boolean isMouseButton = false;

    public static int getKeyCode() {
        return keyCode;
    }

    public static String getKeyName() {
        return keyName;
    }

    public static boolean isMouseButton() {
        return isMouseButton;
    }

    public static void setKeybind(int code, String name) {
        setKeybind(code, name, false);
    }

    public static void setKeybind(int code, String name, boolean isMouse) {
        keyCode = code;
        keyName = name;
        isMouseButton = isMouse;
    }

    // Ostatnio wybrany kolor w panelu Waypointów (domyślnie błękit/cyjan z palety: R=85, G=255, B=255, presetIndex=2)
    private static boolean hasLastColor = false;
    private static int lastColor = 0x55FFFF;
    private static int lastColorR = 85;
    private static int lastColorG = 255;
    private static int lastColorB = 255;
    private static int lastColorPresetIndex = 2; // -1 dla custom RGB, 0..7 dla presetów

    public static boolean hasLastColor() {
        return hasLastColor;
    }

    public static int getLastColor() {
        return lastColor;
    }

    public static int getLastColorR() {
        return lastColorR;
    }

    public static int getLastColorG() {
        return lastColorG;
    }

    public static int getLastColorB() {
        return lastColorB;
    }

    public static int getLastColorPresetIndex() {
        return lastColorPresetIndex;
    }

    public static void loadLastColor(int r, int g, int b, int presetIndex, boolean hasColor) {
        lastColorR = Math.max(0, Math.min(255, r));
        lastColorG = Math.max(0, Math.min(255, g));
        lastColorB = Math.max(0, Math.min(255, b));
        lastColor = ((lastColorR & 0xFF) << 16) | ((lastColorG & 0xFF) << 8) | (lastColorB & 0xFF);
        lastColorPresetIndex = presetIndex;
        hasLastColor = hasColor;
    }

    public static void setLastColor(int r, int g, int b, int presetIndex) {
        loadLastColor(r, g, b, presetIndex, true);
        com.mooclient.util.MooConfig.save();
    }
}
