package com.mooclient.module.modules;

import com.mooclient.module.Module;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

/**
 * Emotes Module — Player animations (Hands Up, Frontflip, Backflip).
 * In-game rendering only — strictly NO hitbox or physics modifications.
 */
public class EmotesModule extends Module {

    public enum ActivationMode {
        HOLD("Przytrzymaj / Hold"),
        TOGGLE("Przełącz / Toggle");

        private final String displayName;

        ActivationMode(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public enum FlipDirection {
        NONE,
        FRONT,
        BACK
    }

    private static boolean enabled = true;

    // --- 1. Hands Up Emote ---
    public static boolean isHandsUp = false;
    private static ActivationMode mode = ActivationMode.TOGGLE;
    private static float currentProgress = 0.0f;
    private static float lastProgress = 0.0f;
    private static int keyCode = GLFW.GLFW_KEY_R;
    private static String keyName = "R";

    // --- 2. Frontflip / Backflip Emotes ---
    private static FlipDirection flipDirection = FlipDirection.NONE;
    private static int flipTicks = 0;
    private static final int TOTAL_FLIP_TICKS = 14; // ~0.70s for full fluid rotation
    private static float flipCurrentProgress = 0.0f;
    private static float flipLastProgress = 0.0f;

    private static int frontflipKeyCode = GLFW.GLFW_KEY_V;
    private static String frontflipKeyName = "V";

    private static int backflipKeyCode = GLFW.GLFW_KEY_B;
    private static String backflipKeyName = "B";

    public EmotesModule() {
        super("Emotes", "Player animations (Hands Up, Frontflip, Backflip)", Category.RENDER, true);
    }

    @Override
    public void onEnable() {
        enabled = true;
    }

    @Override
    public void onDisable() {
        enabled = false;
        isHandsUp = false;
        currentProgress = 0.0f;
        lastProgress = 0.0f;
        flipDirection = FlipDirection.NONE;
        flipTicks = 0;
        flipCurrentProgress = 0.0f;
        flipLastProgress = 0.0f;
    }

    public static void onTick() {
        // --- Tick Hands Up Animation ---
        lastProgress = currentProgress;
        float target = (enabled && isHandsUp) ? 1.0f : 0.0f;
        currentProgress = MathHelper.lerp(0.35f, currentProgress, target);
        if (Math.abs(currentProgress - target) < 0.002f) {
            currentProgress = target;
        }

        // --- Tick Flip Animation ---
        flipLastProgress = flipCurrentProgress;
        if (flipDirection != FlipDirection.NONE) {
            flipTicks++;
            flipCurrentProgress = (float) flipTicks / (float) TOTAL_FLIP_TICKS;
            if (flipTicks >= TOTAL_FLIP_TICKS) {
                flipDirection = FlipDirection.NONE;
                flipTicks = 0;
                flipCurrentProgress = 0.0f;
                flipLastProgress = 0.0f;
            }
        }
    }

    // ==========================================
    // Hands Up Interpolation & Getters
    // ==========================================

    /**
     * Zwraca wygładzony postęp animacji podnoszenia rąk od 0.0 do 1.0 (Smoothstep).
     */
    public static float getInterpolatedProgress(float tickDelta) {
        float raw = MathHelper.lerp(tickDelta, lastProgress, currentProgress);
        raw = MathHelper.clamp(raw, 0.0f, 1.0f);
        return raw * raw * (3.0f - 2.0f * raw);
    }

    public static boolean isHandsUp() {
        return enabled && isHandsUp;
    }

    public static void setHandsUp(boolean state) {
        isHandsUp = state;
    }

    public static void toggleHandsUp() {
        if (!enabled) return;
        isHandsUp = !isHandsUp;
    }

    // ==========================================
    // Flip (Salto) Action Triggers & Calculations
    // ==========================================

    public static void triggerFrontflip() {
        if (!enabled || flipDirection != FlipDirection.NONE) return;
        flipDirection = FlipDirection.FRONT;
        flipTicks = 0;
        flipCurrentProgress = 0.0f;
        flipLastProgress = 0.0f;
    }

    public static void triggerBackflip() {
        if (!enabled || flipDirection != FlipDirection.NONE) return;
        flipDirection = FlipDirection.BACK;
        flipTicks = 0;
        flipCurrentProgress = 0.0f;
        flipLastProgress = 0.0f;
    }

    public static boolean isFlipping() {
        return enabled && flipDirection != FlipDirection.NONE;
    }

    public static FlipDirection getFlipDirection() {
        return flipDirection;
    }

    /**
     * Zwraca interpolowany postęp salta od 0.0 do 1.0 dla bieżącej klatki.
     */
    public static float getInterpolatedFlipProgress(float tickDelta) {
        if (flipDirection == FlipDirection.NONE && flipCurrentProgress == 0.0f) {
            return 0.0f;
        }
        float raw = MathHelper.lerp(tickDelta, flipLastProgress, flipCurrentProgress);
        return MathHelper.clamp(raw, 0.0f, 1.0f);
    }

    /**
     * Zwraca kąt obrotu wokół osi X (w stopniach) dla całego ciała postaci (od 0 do ±360).
     * Wykorzystuje płynną krzywą Cosinusoidalną dla miękkiego wybicia i lądowania.
     */
    public static float getFlipRotationDegrees(float tickDelta) {
        float p = getInterpolatedFlipProgress(tickDelta);
        if (p <= 0.0001f || flipDirection == FlipDirection.NONE) {
            return 0.0f;
        }
        // Krzywa Cosinusowa (0.0 -> 1.0)
        float eased = (float) (0.5 - 0.5 * Math.cos(p * Math.PI));
        float total = (flipDirection == FlipDirection.FRONT) ? 360.0f : -360.0f;
        return eased * total;
    }

    /**
     * Zwraca współczynnik zwinięcia ciała (tuck factor: 0.0 -> 1.0 w szczycie -> 0.0 przy lądowaniu).
     */
    public static float getFlipTuckFactor(float tickDelta) {
        float p = getInterpolatedFlipProgress(tickDelta);
        if (p <= 0.0001f || p >= 0.9999f || flipDirection == FlipDirection.NONE) {
            return 0.0f;
        }
        // Szczyt zwinięcia następuje w połowie salta (sinus)
        return (float) Math.sin(p * Math.PI);
    }

    // ==========================================
    // Module State, Keybinds & Config
    // ==========================================

    public static boolean isEmotesEnabled() {
        return enabled;
    }

    public static void setEmotesEnabled(boolean state) {
        enabled = state;
        if (!state) {
            isHandsUp = false;
            flipDirection = FlipDirection.NONE;
        }
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

    public static void setKeybind(int key, String name) {
        keyCode = key;
        keyName = name;
    }

    // Frontflip Keybind
    public static int getFrontflipKeyCode() {
        return frontflipKeyCode;
    }

    public static String getFrontflipKeyName() {
        return frontflipKeyName;
    }

    public static void setFrontflipKeybind(int key, String name) {
        frontflipKeyCode = key;
        frontflipKeyName = name;
    }

    // Backflip Keybind
    public static int getBackflipKeyCode() {
        return backflipKeyCode;
    }

    public static String getBackflipKeyName() {
        return backflipKeyName;
    }

    public static void setBackflipKeybind(int key, String name) {
        backflipKeyCode = key;
        backflipKeyName = name;
    }
}
