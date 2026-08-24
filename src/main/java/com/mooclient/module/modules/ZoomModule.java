package com.mooclient.module.modules;

import com.mooclient.module.Module;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

/**
 * Zoom Module — OptiFine/Lunar style smooth cinematic zoom with customizable keybind,
 * mouse button / scroll click binding, and zoom factor.
 */
public class ZoomModule extends Module {

    public enum ZoomFactor {
        X2("2x", 2.0f),
        X3("3x", 3.0f),
        X4("4x", 4.0f),
        X5("5x", 5.0f),
        X6("6x", 6.0f);

        private final String label;
        private final float factor;

        ZoomFactor(String label, float factor) {
            this.label = label;
            this.factor = factor;
        }

        public String getLabel() {
            return label;
        }

        public float getFactor() {
            return factor;
        }
    }

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
    private static boolean active = false;
    private static ZoomFactor factor = ZoomFactor.X4;
    private static ActivationMode mode = ActivationMode.HOLD;
    private static boolean smoothZoom = true;

    // Configurable Keybind or Mouse Button (Default: C key)
    private static int keyCode = GLFW.GLFW_KEY_C;
    private static String keyName = "C";
    private static boolean isMouseButton = false;

    // Zoom animation progress (1.0 = normal, factor = fully zoomed)
    private static float currentZoom = 1.0f;
    private static float lastZoom = 1.0f;

    public ZoomModule() {
        super("Zoom", "Przybliżenie widoku z płynnym powiększeniem", Category.RENDER, true);
    }

    @Override
    public void onEnable() {
        enabled = true;
    }

    @Override
    public void onDisable() {
        enabled = false;
        active = false;
        currentZoom = 1.0f;
        lastZoom = 1.0f;
    }

    public static boolean isZoomEnabled() {
        return enabled;
    }

    public static void setZoomEnabled(boolean state) {
        enabled = state;
        if (!enabled) {
            active = false;
            currentZoom = 1.0f;
            lastZoom = 1.0f;
        }
        com.mooclient.module.ModuleManager.getInstance().getModule("Zoom").ifPresent(m -> {
            if (m.isEnabled() != state) {
                m.setEnabled(state);
            }
        });
    }

    public static boolean isZooming() {
        return enabled && active;
    }

    public static boolean isActive() {
        return enabled && active;
    }

    public static void start() {
        if (!enabled) return;
        active = true;
    }

    public static void stop() {
        active = false;
        // Instantly reset zoom on release so there is no delay/animation on unzoom
        currentZoom = 1.0f;
        lastZoom = 1.0f;
    }

    public static void toggleZoomActive() {
        if (active) {
            stop();
        } else {
            start();
        }
    }

    public static void onTick() {
        lastZoom = currentZoom;
        if (!active) {
            currentZoom = 1.0f;
            lastZoom = 1.0f;
            return;
        }

        float target = factor.getFactor();
        if (smoothZoom) {
            currentZoom = MathHelper.lerp(0.35f, currentZoom, target);
        } else {
            currentZoom = target;
        }
    }

    /**
     * Calculates zoomed FOV based on base FOV and interpolation.
     */
    public static float calculateZoomFov(float baseFov, float tickDelta) {
        float interpolatedZoom = MathHelper.lerp(tickDelta, lastZoom, currentZoom);
        if (interpolatedZoom <= 1.0f) {
            return baseFov;
        }
        return baseFov / interpolatedZoom;
    }

    /**
     * Returns mouse sensitivity divisor for zoomed aiming precision.
     */
    public static double getZoomDivisor() {
        return currentZoom;
    }

    public static ZoomFactor getFactor() {
        return factor;
    }

    public static void setFactor(ZoomFactor newFactor) {
        factor = newFactor;
    }

    public static ActivationMode getMode() {
        return mode;
    }

    public static void setMode(ActivationMode newMode) {
        mode = newMode;
    }

    public static boolean isSmoothZoom() {
        return smoothZoom;
    }

    public static void setSmoothZoom(boolean state) {
        smoothZoom = state;
    }

    public static void toggleSmoothZoom() {
        smoothZoom = !smoothZoom;
    }

    public static int getKeyCode() {
        return keyCode;
    }

    public static String getKeyName() {
        return keyName;
    }

    public static boolean isMouseButton() {
        return isMouseButton;
    }

    public static void setMouseButton(boolean mouseButton) {
        isMouseButton = mouseButton;
    }

    public static void setKeybind(int code, String name) {
        keyCode = code;
        keyName = name;
        isMouseButton = false;
    }

    public static void setKeybind(int code, String name, boolean isMouse) {
        keyCode = code;
        keyName = name;
        isMouseButton = isMouse;
    }
}
