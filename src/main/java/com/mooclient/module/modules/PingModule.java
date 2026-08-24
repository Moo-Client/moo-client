package com.mooclient.module.modules;

import com.mooclient.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;

/**
 * Ping HUD module displaying the player's latency in real-time, customizable & draggable.
 */
public class PingModule extends Module {

    public enum PingStyle {
        MOO_CLIENT("Moo Client Look"),
        SIMPLE("Czysty / Simple"),
        BRACKETS("[ 12 ms ]");

        private final String displayName;

        PingStyle(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    private static boolean enabled = true;
    private static PingStyle style = PingStyle.MOO_CLIENT;
    private static boolean showBackground = true;
    private static boolean textShadow = true;
    private static boolean showPrefix = true;

    // Draggable coordinates & anchor (default below ToggleSprint widget)
    public static com.mooclient.util.MooHudPositionHelper.WidgetPosition position =
            new com.mooclient.util.MooHudPositionHelper.WidgetPosition(
                    com.mooclient.util.MooHudPositionHelper.HudAnchorX.LEFT,
                    com.mooclient.util.MooHudPositionHelper.HudAnchorY.TOP,
                    6, 34);
    public static int posX = 6;
    public static int posY = 34;
    public static int width = 54;
    public static int height = 12;

    public PingModule() {
        super("Ping", "Wyświetla aktualny ping na ekranie", Category.HUD);
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

    public static boolean isPingEnabled() {
        return enabled;
    }

    public static void setPingEnabled(boolean state) {
        enabled = state;
    }

    public static PingStyle getStyle() {
        return style;
    }

    public static void setStyle(PingStyle newStyle) {
        style = newStyle;
    }

    public static void cycleStyle() {
        PingStyle[] styles = PingStyle.values();
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

    /**
     * Gets the current local player latency (ms).
     */
    public static int getCurrentPing() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null && client.getNetworkHandler() != null) {
            PlayerListEntry entry = client.getNetworkHandler().getPlayerListEntry(client.player.getUuid());
            if (entry != null) {
                return Math.max(0, entry.getLatency());
            }
        }
        return 0;
    }
}
