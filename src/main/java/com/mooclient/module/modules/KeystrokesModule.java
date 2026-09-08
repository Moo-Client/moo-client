package com.mooclient.module.modules;

import com.mooclient.module.Module;
import com.mooclient.util.MooHudPositionHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;

/**
 * Keystrokes HUD module displaying movement keys (W, A, S, D) and Spacebar.
 */
public class KeystrokesModule extends Module {

    public enum KeystrokesStyle {
        MOO_CLIENT("Moo Client"),
        SIMPLE("Simple"),
        ACCENT("Akcent / Chroma");

        private final String displayName;

        KeystrokesStyle(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public enum SpaceMode {
        BAR("Pasek (—)"),
        TEXT("Napis (SPACE)");

        private final String displayName;

        SpaceMode(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    private static boolean enabled = false;
    private static KeystrokesStyle style = KeystrokesStyle.MOO_CLIENT;
    private static SpaceMode spaceMode = SpaceMode.BAR;
    private static boolean showBackground = true;
    private static boolean textShadow = true;
    private static boolean showSpace = true;

    // Draggable position & anchor (default Top Right)
    public static MooHudPositionHelper.WidgetPosition position =
            new MooHudPositionHelper.WidgetPosition(
                    MooHudPositionHelper.HudAnchorX.RIGHT,
                    MooHudPositionHelper.HudAnchorY.TOP,
                    10, 10);
    public static int posX = 0;
    public static int posY = 0;
    public static int width = 70;
    public static int height = 62;

    public static int getBaseHeight() {
        return showSpace ? 62 : 46;
    }

    public static int getBaseWidth() {
        return 70;
    }

    public KeystrokesModule() {
        super("Keystrokes", "Wyświetla wciśnięte klawisze WSAD oraz Spację w HUD", Category.HUD, false);
    }

    @Override
    public void onEnable() {
        enabled = true;
    }

    @Override
    public void onDisable() {
        enabled = false;
    }

    public static boolean isKeystrokesEnabled() {
        return enabled;
    }

    public static void setKeystrokesEnabled(boolean state) {
        enabled = state;
    }

    public static KeystrokesStyle getStyle() {
        return style;
    }

    public static void setStyle(KeystrokesStyle newStyle) {
        if (newStyle != null) {
            style = newStyle;
        }
    }

    public static void cycleStyle() {
        KeystrokesStyle[] values = KeystrokesStyle.values();
        int next = (style.ordinal() + 1) % values.length;
        setStyle(values[next]);
    }

    public static SpaceMode getSpaceMode() {
        return spaceMode;
    }

    public static void setSpaceMode(SpaceMode mode) {
        if (mode != null) {
            spaceMode = mode;
        }
    }

    public static void cycleSpaceMode() {
        SpaceMode[] values = SpaceMode.values();
        int next = (spaceMode.ordinal() + 1) % values.length;
        setSpaceMode(values[next]);
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

    public static boolean isShowSpace() {
        return showSpace;
    }

    public static void setShowSpace(boolean state) {
        showSpace = state;
    }

    public static void toggleShowSpace() {
        showSpace = !showSpace;
    }

    public static boolean isForwardPressed() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client != null && client.options != null && client.options.forwardKey.isPressed();
    }

    public static boolean isLeftPressed() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client != null && client.options != null && client.options.leftKey.isPressed();
    }

    public static boolean isBackPressed() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client != null && client.options != null && client.options.backKey.isPressed();
    }

    public static boolean isRightPressed() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client != null && client.options != null && client.options.rightKey.isPressed();
    }

    public static boolean isJumpPressed() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client != null && client.options != null && client.options.jumpKey.isPressed();
    }

    public static String getForwardLabel() {
        return getKeyLabel(MinecraftClient.getInstance() != null && MinecraftClient.getInstance().options != null ? MinecraftClient.getInstance().options.forwardKey : null, "W");
    }

    public static String getLeftLabel() {
        return getKeyLabel(MinecraftClient.getInstance() != null && MinecraftClient.getInstance().options != null ? MinecraftClient.getInstance().options.leftKey : null, "A");
    }

    public static String getBackLabel() {
        return getKeyLabel(MinecraftClient.getInstance() != null && MinecraftClient.getInstance().options != null ? MinecraftClient.getInstance().options.backKey : null, "S");
    }

    public static String getRightLabel() {
        return getKeyLabel(MinecraftClient.getInstance() != null && MinecraftClient.getInstance().options != null ? MinecraftClient.getInstance().options.rightKey : null, "D");
    }

    public static String getJumpLabel() {
        if (spaceMode == SpaceMode.BAR) {
            return "━━━━";
        }
        return "SPACE";
    }

    private static String getKeyLabel(KeyBinding keyBinding, String fallback) {
        if (keyBinding == null) return fallback;
        try {
            String name = keyBinding.getBoundKeyLocalizedText().getString().toUpperCase();
            if (name.isEmpty() || name.length() > 6) {
                return fallback;
            }
            return name;
        } catch (Exception e) {
            return fallback;
        }
    }
}
