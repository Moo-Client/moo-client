package com.mooclient.module.modules;

import com.mooclient.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

/**
 * Freelook Module — allows 360° third-person free camera rotation without rotating the player character.
 */
public class FreelookModule extends Module {

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
    private static ActivationMode mode = ActivationMode.HOLD;
    private static boolean invertPitch = false;

    // Configurable Keybind (Default: V key)
    private static int keyCode = GLFW.GLFW_KEY_V;
    private static String keyName = "V";

    // Camera state
    private static float cameraYaw = 0.0f;
    private static float cameraPitch = 0.0f;
    private static Perspective previousPerspective = Perspective.FIRST_PERSON;

    public FreelookModule() {
        super("Freelook", "Swobodny widok 360° kamery", Category.RENDER, true);
    }

    @Override
    public void onEnable() {
        enabled = true;
    }

    @Override
    public void onDisable() {
        enabled = false;
        stop();
    }

    public static boolean isFreelookEnabled() {
        return enabled;
    }

    public static void setFreelookEnabled(boolean state) {
        enabled = state;
        if (!enabled) {
            stop();
        }
        com.mooclient.module.ModuleManager.getInstance().getModule("Freelook").ifPresent(m -> {
            if (m.isEnabled() != state) {
                m.setEnabled(state);
            }
        });
    }

    public static boolean isActive() {
        return enabled && active;
    }

    public static void start() {
        if (!enabled || active) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        previousPerspective = client.options.getPerspective();
        client.options.setPerspective(Perspective.THIRD_PERSON_BACK);

        cameraYaw = client.player.getYaw();
        cameraPitch = client.player.getPitch();
        active = true;
    }

    public static void stop() {
        if (!active) return;
        active = false;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.options != null) {
            client.options.setPerspective(previousPerspective);
        }
    }

    public static void toggleFreelookActive() {
        if (active) {
            stop();
        } else {
            start();
        }
    }

    public static void onMouseLook(double cursorDeltaX, double cursorDeltaY) {
        float yawDelta = (float) cursorDeltaX * 0.15F;
        float pitchDelta = (float) cursorDeltaY * 0.15F;

        if (invertPitch) {
            pitchDelta = -pitchDelta;
        }

        cameraYaw += yawDelta;
        cameraPitch = MathHelper.clamp(cameraPitch + pitchDelta, -90.0F, 90.0F);
    }

    public static float getCameraYaw() {
        return cameraYaw;
    }

    public static float getCameraPitch() {
        return cameraPitch;
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

    public static boolean isInvertPitch() {
        return invertPitch;
    }

    public static void toggleInvertPitch() {
        invertPitch = !invertPitch;
    }

    public static void setInvertPitch(boolean state) {
        invertPitch = state;
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
