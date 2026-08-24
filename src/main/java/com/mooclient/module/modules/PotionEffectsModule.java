package com.mooclient.module.modules;

import com.mooclient.module.Module;
import net.minecraft.entity.effect.StatusEffectInstance;

/**
 * Potion Effects (Potion HUD) Module.
 * Displays active status effects with duration countdown timer in Moo Client, Simple, or Compact styles.
 */
public class PotionEffectsModule extends Module {

    public enum PotionStyle {
        MOO_CLIENT("Moo Client"),
        SIMPLE("Simple"),
        COMPACT("Compact");

        private final String displayName;

        PotionStyle(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    private static boolean enabled = true;
    // Draggable coordinates & anchor
    public static com.mooclient.util.MooHudPositionHelper.WidgetPosition position =
            new com.mooclient.util.MooHudPositionHelper.WidgetPosition(
                    com.mooclient.util.MooHudPositionHelper.HudAnchorX.LEFT,
                    com.mooclient.util.MooHudPositionHelper.HudAnchorY.TOP,
                    6, 64);
    public static int posX = 6;
    public static int posY = 64;
    public static int width = 120;
    public static int height = 80;

    private static PotionStyle style = PotionStyle.SIMPLE;
    private static boolean showBackground = false;
    private static boolean textShadow = true;
    private static boolean showIcon = true;

    public PotionEffectsModule() {
        super("Potion Effects", "Wyświetla aktywne efekty mikstur i czas", Category.HUD, true);
    }

    @Override
    public void onEnable() {
        enabled = true;
    }

    @Override
    public void onDisable() {
        enabled = false;
    }

    public static boolean isModuleEnabled() {
        return enabled;
    }

    public static void setModuleEnabled(boolean state) {
        enabled = state;
        com.mooclient.module.ModuleManager.getInstance().getModule("Potion Effects").ifPresent(m -> {
            if (m.isEnabled() != state) {
                m.setEnabled(state);
            }
        });
    }

    public static PotionStyle getStyle() {
        return style;
    }

    public static void setStyle(PotionStyle newStyle) {
        style = newStyle;
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

    public static boolean isShowIcon() {
        return showIcon;
    }

    public static void setShowIcon(boolean state) {
        showIcon = state;
    }

    public static void toggleShowIcon() {
        showIcon = !showIcon;
    }

    /**
     * Formats tick duration into M:SS format (e.g. 5:11, 0:25) or **:** for infinite.
     */
    public static String formatDuration(StatusEffectInstance effect) {
        if (effect.isInfinite() || effect.getDuration() > 32000) {
            return "**:**";
        }
        int totalSeconds = effect.getDuration() / 20;
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return minutes + ":" + String.format("%02d", seconds);
    }

    /**
     * Converts amplifier integer to Roman numerals.
     */
    public static String getAmplifierString(int amplifier) {
        switch (amplifier) {
            case 0: return "";
            case 1: return " II";
            case 2: return " III";
            case 3: return " IV";
            case 4: return " V";
            default: return " " + (amplifier + 1);
        }
    }
}
