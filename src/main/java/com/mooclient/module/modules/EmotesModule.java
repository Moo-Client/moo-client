package com.mooclient.module.modules;

import com.mooclient.module.Module;
import org.lwjgl.glfw.GLFW;

/**
 * Emotes Module — Player animations (e.g. Hands Up / Ręce w górę).
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

    private static boolean enabled = true;
    public static boolean isHandsUp = false;
    private static ActivationMode mode = ActivationMode.TOGGLE;

    // Smooth animation progress (0.0 = hands down, 1.0 = hands up)
    private static float currentProgress = 0.0f;
    private static float lastProgress = 0.0f;

    // Configurable Keybind (Default: R key)
    private static int keyCode = GLFW.GLFW_KEY_R;
    private static String keyName = "R";

    public EmotesModule() {
        super("Emotes", "Player animations (e.g. Hands Up / Ręce w górę)", Category.RENDER, true);
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
    }

    public static void onTick() {
        lastProgress = currentProgress;
        float target = (enabled && isHandsUp) ? 1.0f : 0.0f;

        // Płynna interpolacja (lerp) co tick (~200ms na pełny ruch)
        currentProgress = net.minecraft.util.math.MathHelper.lerp(0.35f, currentProgress, target);

        if (Math.abs(currentProgress - target) < 0.002f) {
            currentProgress = target;
        }
    }

    /**
     * Zwraca wygładzony postęp animacji od 0.0 do 1.0 dla bieżącej klatki renderowania.
     * Wykorzystuje funkcję Smoothstep f(x) = x * x * (3 - 2 * x) dla naturalnego przyspieszenia i hamowania.
     */
    public static float getInterpolatedProgress(float tickDelta) {
        float raw = net.minecraft.util.math.MathHelper.lerp(tickDelta, lastProgress, currentProgress);
        raw = net.minecraft.util.math.MathHelper.clamp(raw, 0.0f, 1.0f);
        return raw * raw * (3.0f - 2.0f * raw);
    }

    public static boolean isEmotesEnabled() {
        return enabled;
    }

    public static void setEmotesEnabled(boolean state) {
        enabled = state;
        if (!state) {
            isHandsUp = false;
        }
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
}
