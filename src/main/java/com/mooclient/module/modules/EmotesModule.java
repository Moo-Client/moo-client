package com.mooclient.module.modules;

import com.mooclient.emote.EmoteEngine;
import com.mooclient.module.Module;
import org.lwjgl.glfw.GLFW;

/**
 * Moduł emotek Moo Client — panel ustawień i bindów klawiatury dla koła i gestów.
 * Cała logika wykonawcza i interpolacja animacji delegowana jest do EmoteEngine.
 */
public class EmotesModule extends Module {

    public enum ActivationMode {
        HOLD,
        TOGGLE
    }

    private static boolean enabled = true;
    private static boolean restorePerspective = false; // Domyślnie false (pozostaje w F5)

    // --- Keybinds and Mode ---
    private static ActivationMode mode = ActivationMode.TOGGLE;
    private static int keyCode = GLFW.GLFW_KEY_R;
    private static String keyName = "R";
    private static boolean isMouseButton = false;

    private static int frontflipKeyCode = GLFW.GLFW_KEY_V;
    private static String frontflipKeyName = "V";
    private static boolean frontflipIsMouseButton = false;

    private static int backflipKeyCode = GLFW.GLFW_KEY_B;
    private static String backflipKeyName = "B";
    private static boolean backflipIsMouseButton = false;

    private static int wheelKeyCode = GLFW.GLFW_KEY_B;
    private static String wheelKeyName = "B";
    private static boolean wheelIsMouseButton = false;

    private static int acceptKeyCode = GLFW.GLFW_KEY_Y;
    private static String acceptKeyName = "Y";
    private static boolean acceptIsMouseButton = false;

    private static int declineKeyCode = GLFW.GLFW_KEY_N;
    private static String declineKeyName = "N";
    private static boolean declineIsMouseButton = false;

    public EmotesModule() {
        super("Emotes", "Player animations (Gestures, Dances, Flips, Interactions)", Category.RENDER, true);
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public void onEnable() {
    }

    @Override
    public void onDisable() {
        // Emotki są zawsze włączone w kliencie
    }

    public static void onTick() {
        // Ticked via EmoteEngine in MooClient
    }

    // ==========================================
    // Local Action Triggers
    // ==========================================

    public static void setHandsUp(boolean state) {
        if (!enabled) return;
        boolean isPlayingHandsUp = EmoteEngine.getInstance().isLocalEmotePlaying()
                && "hands_up".equalsIgnoreCase(EmoteEngine.getInstance().getLocalPlayingEmoteId());
        if (state) {
            if (!isPlayingHandsUp) {
                EmoteEngine.getInstance().playLocalEmote("hands_up");
            }
        } else {
            if (isPlayingHandsUp) {
                EmoteEngine.getInstance().stopLocalEmote();
            }
        }
    }

    public static void toggleHandsUp() {
        if (!enabled) return;
        boolean isPlayingHandsUp = EmoteEngine.getInstance().isLocalEmotePlaying()
                && "hands_up".equalsIgnoreCase(EmoteEngine.getInstance().getLocalPlayingEmoteId());
        if (isPlayingHandsUp) {
            EmoteEngine.getInstance().stopLocalEmote();
        } else {
            EmoteEngine.getInstance().playLocalEmote("hands_up");
        }
    }

    public static boolean isHandsUp() {
        return enabled && EmoteEngine.getInstance().isLocalEmotePlaying()
                && "hands_up".equalsIgnoreCase(EmoteEngine.getInstance().getLocalPlayingEmoteId());
    }

    public static void triggerFrontflip() {
        if (!enabled) return;
        EmoteEngine.getInstance().playLocalEmote("frontflip");
    }

    public static void triggerBackflip() {
        if (!enabled) return;
        EmoteEngine.getInstance().playLocalEmote("backflip");
    }

    public static void triggerFrontflipFromWheel() {
        triggerFrontflip();
    }

    public static void triggerBackflipFromWheel() {
        triggerBackflip();
    }

    public static void triggerHandsUpFromWheel() {
        toggleHandsUp();
    }

    public static void stopEmotesFromWheel() {
        EmoteEngine.getInstance().stopLocalEmote();
    }

    public static boolean hasActiveLoopingEmote() {
        return EmoteEngine.getInstance().isLocalEmotePlaying();
    }

    // ==========================================
    // Module State, Keybinds & Config
    // ==========================================

    public static boolean isEmotesEnabled() {
        return true;
    }

    public static void setEmotesEnabled(boolean state) {
        // Emotki są na stałe włączone
    }

    public static boolean isRestorePerspective() {
        return restorePerspective;
    }

    public static void setRestorePerspective(boolean val) {
        restorePerspective = val;
    }

    public static void toggleRestorePerspective() {
        restorePerspective = !restorePerspective;
    }

    public static ActivationMode getMode() {
        return mode;
    }

    public static void setMode(ActivationMode newMode) {
        mode = newMode;
    }

    public static void cycleMode() {
        ActivationMode[] values = ActivationMode.values();
        mode = values[(mode.ordinal() + 1) % values.length];
    }

    // Hands Up Keybind
    public static int getKeyCode() {
        return keyCode;
    }

    public static String getKeyName() {
        return keyName;
    }

    public static boolean isMouseButton() {
        return isMouseButton;
    }

    public static void setKeybind(int key, String name) {
        setKeybind(key, name, false);
    }

    public static void setKeybind(int key, String name, boolean isMouse) {
        keyCode = key;
        keyName = name;
        isMouseButton = isMouse;
    }

    // Frontflip Keybind
    public static int getFrontflipKeyCode() {
        return frontflipKeyCode;
    }

    public static String getFrontflipKeyName() {
        return frontflipKeyName;
    }

    public static boolean isFrontflipMouseButton() {
        return frontflipIsMouseButton;
    }

    public static void setFrontflipKeybind(int key, String name) {
        setFrontflipKeybind(key, name, false);
    }

    public static void setFrontflipKeybind(int key, String name, boolean isMouse) {
        frontflipKeyCode = key;
        frontflipKeyName = name;
        frontflipIsMouseButton = isMouse;
    }

    // Backflip Keybind
    public static int getBackflipKeyCode() {
        return backflipKeyCode;
    }

    public static String getBackflipKeyName() {
        return backflipKeyName;
    }

    public static boolean isBackflipMouseButton() {
        return backflipIsMouseButton;
    }

    public static void setBackflipKeybind(int key, String name) {
        setBackflipKeybind(key, name, false);
    }

    public static void setBackflipKeybind(int key, String name, boolean isMouse) {
        backflipKeyCode = key;
        backflipKeyName = name;
        backflipIsMouseButton = isMouse;
    }

    // Emote Radial Wheel Keybind
    public static int getWheelKeyCode() {
        return wheelKeyCode;
    }

    public static String getWheelKeyName() {
        return wheelKeyName;
    }

    public static boolean isWheelMouseButton() {
        return wheelIsMouseButton;
    }

    public static void setWheelKeybind(int key, String name) {
        setWheelKeybind(key, name, false);
    }

    public static void setWheelKeybind(int key, String name, boolean isMouse) {
        wheelKeyCode = key;
        wheelKeyName = name;
        wheelIsMouseButton = isMouse;
    }

    // Accept Interaction Invitation Keybind
    public static int getAcceptKeyCode() {
        return acceptKeyCode;
    }

    public static String getAcceptKeyName() {
        return acceptKeyName;
    }

    public static boolean isAcceptMouseButton() {
        return acceptIsMouseButton;
    }

    public static void setAcceptKeybind(int key, String name) {
        setAcceptKeybind(key, name, false);
    }

    public static void setAcceptKeybind(int key, String name, boolean isMouse) {
        acceptKeyCode = key;
        acceptKeyName = name;
        acceptIsMouseButton = isMouse;
    }

    // Decline Interaction Invitation Keybind
    public static int getDeclineKeyCode() {
        return declineKeyCode;
    }

    public static String getDeclineKeyName() {
        return declineKeyName;
    }

    public static boolean isDeclineMouseButton() {
        return declineIsMouseButton;
    }

    public static void setDeclineKeybind(int key, String name) {
        setDeclineKeybind(key, name, false);
    }

    public static void setDeclineKeybind(int key, String name, boolean isMouse) {
        declineKeyCode = key;
        declineKeyName = name;
        declineIsMouseButton = isMouse;
    }
}
