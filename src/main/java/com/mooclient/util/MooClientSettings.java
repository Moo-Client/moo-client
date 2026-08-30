package com.mooclient.util;

import com.mooclient.gui.InvitationUIManager;
import com.mooclient.module.ModuleManager;
import com.mooclient.module.modules.FpsModule;
import com.mooclient.module.modules.FullbrightModule;
import com.mooclient.module.modules.ToggleSprintModule;
import com.mooclient.module.modules.PotionEffectsModule;
import com.mooclient.module.modules.NametagsModule;
import com.mooclient.module.modules.ZoomModule;
import com.mooclient.module.modules.FreelookModule;
import com.mooclient.module.modules.ChatModule;
import com.mooclient.module.modules.PingModule;
import com.mooclient.module.modules.MacroModule;

import java.awt.Color;

/**
 * Global settings manager for Moo Client (Accent Colors, HUD Snapping/Reset,
 * GUI Appearance, Profiles, Invitation UI Variants).
 */
public class MooClientSettings {

    public enum AccentColorPreset {
        MOO_GREEN("Moo Green", 0xFF2ECC71),
        LUNAR_BLUE("Lunar Blue", 0xFF38BDF8),
        PURPLE("Amethyst", 0xFFA855F7),
        RED("Crimson Red", 0xFFEF4444),
        GOLD("Amber Gold", 0xFFF59E0B),
        CYAN("Neon Cyan", 0xFF06B6D4),
        PINK("Hot Pink", 0xFFEC4899),
        WHITE("Monochrome", 0xFFEEEEEE),
        CHROMA("RGB Chroma", 0),
        CUSTOM("Własny (Custom)", 0);

        private final String displayName;
        private final int color;

        AccentColorPreset(String displayName, int color) {
            this.displayName = displayName;
            this.color = color;
        }

        public String getDisplayName() {
            return displayName;
        }

        public int getColor() {
            return color;
        }
    }

    public enum ProfileType {
        DEFAULT("Domyślny", "Standardowy zestaw modów dla każdego gracza"),
        PVP("PvP Master", "FPS, Sprint, Ping, Freelook, Potki na ekranie"),
        SURVIVAL("Survival", "Gamma, FPS, Sprint, Nametags, Chat, Zoom"),
        CLEAN("Czysty HUD", "Ukryty cały HUD, aktywne tylko mody użytkowe");

        private final String title;
        private final String description;

        ProfileType(String title, String description) {
            this.title = title;
            this.description = description;
        }

        public String getTitle() {
            return title;
        }

        public String getDescription() {
            return description;
        }
    }

    // --- State Variables ---
    private static AccentColorPreset accentPreset = AccentColorPreset.MOO_GREEN;
    private static int customRed = 46;
    private static int customGreen = 204;
    private static int customBlue = 113;

    private static boolean hudSnapping = true;
    private static int hudScale = 100; // 0% - 100%
    private static boolean globalTextShadow = true;
    private static int menuBackgroundDim = 1; // 0 = 30% (Light), 1 = 50% (Medium), 2 = 75% (Dark)
    private static boolean guiAnimations = true;
    private static ProfileType activeProfile = ProfileType.DEFAULT;
    private static InvitationUIManager.UiVariant invitationUiVariant = InvitationUIManager.UiVariant.FLOATING_CENTER;

    // --- Accent Color Getters ---

    public static int getAccentColor() {
        if (accentPreset == AccentColorPreset.CHROMA) {
            float hue = (System.currentTimeMillis() % 4000L) / 4000.0f;
            int rgb = Color.HSBtoRGB(hue, 0.85f, 1.0f);
            return 0xFF000000 | rgb;
        }
        if (accentPreset == AccentColorPreset.CUSTOM) {
            return 0xFF000000 | ((customRed & 0xFF) << 16) | ((customGreen & 0xFF) << 8) | (customBlue & 0xFF);
        }
        return accentPreset.getColor();
    }

    public static int getAccentHoverColor() {
        int base = getAccentColor();
        int r = Math.min(255, ((base >> 16) & 0xFF) + 25);
        int g = Math.min(255, ((base >> 8) & 0xFF) + 25);
        int b = Math.min(255, (base & 0xFF) + 25);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    public static int getAccentGlowColor(int alpha) {
        int base = getAccentColor();
        return ((alpha & 0xFF) << 24) | (base & 0x00FFFFFF);
    }

    public static AccentColorPreset getAccentPreset() {
        return accentPreset;
    }

    public static void setAccentPreset(AccentColorPreset preset) {
        accentPreset = preset;
        MooConfig.save();
    }

    public static int getCustomRed() {
        return customRed;
    }

    public static void setCustomRed(int r) {
        customRed = Math.max(0, Math.min(255, r));
        MooConfig.save();
    }

    public static int getCustomGreen() {
        return customGreen;
    }

    public static void setCustomGreen(int g) {
        customGreen = Math.max(0, Math.min(255, g));
        MooConfig.save();
    }

    public static int getCustomBlue() {
        return customBlue;
    }

    public static void setCustomBlue(int b) {
        customBlue = Math.max(0, Math.min(255, b));
        MooConfig.save();
    }

    // --- HUD Management ---

    public static void resetHudPositions() {
        FpsModule.position = new com.mooclient.util.MooHudPositionHelper.WidgetPosition(
                com.mooclient.util.MooHudPositionHelper.HudAnchorX.LEFT,
                com.mooclient.util.MooHudPositionHelper.HudAnchorY.TOP, 10, 10);
        FpsModule.posX = 10;
        FpsModule.posY = 10;

        ToggleSprintModule.position = new com.mooclient.util.MooHudPositionHelper.WidgetPosition(
                com.mooclient.util.MooHudPositionHelper.HudAnchorX.LEFT,
                com.mooclient.util.MooHudPositionHelper.HudAnchorY.TOP, 10, 26);
        ToggleSprintModule.posX = 10;
        ToggleSprintModule.posY = 26;

        PingModule.position = new com.mooclient.util.MooHudPositionHelper.WidgetPosition(
                com.mooclient.util.MooHudPositionHelper.HudAnchorX.LEFT,
                com.mooclient.util.MooHudPositionHelper.HudAnchorY.TOP, 10, 42);
        PingModule.posX = 10;
        PingModule.posY = 42;

        com.mooclient.module.modules.CpsModule.position = new com.mooclient.util.MooHudPositionHelper.WidgetPosition(
                com.mooclient.util.MooHudPositionHelper.HudAnchorX.LEFT,
                com.mooclient.util.MooHudPositionHelper.HudAnchorY.TOP, 10, 58);
        com.mooclient.module.modules.CpsModule.posX = 10;
        com.mooclient.module.modules.CpsModule.posY = 58;

        PotionEffectsModule.position = new com.mooclient.util.MooHudPositionHelper.WidgetPosition(
                com.mooclient.util.MooHudPositionHelper.HudAnchorX.LEFT,
                com.mooclient.util.MooHudPositionHelper.HudAnchorY.TOP, 10, 74);
        PotionEffectsModule.posX = 10;
        PotionEffectsModule.posY = 74;

        com.mooclient.module.modules.ScoreboardModule.resetPosition();

        MooConfig.save();
    }

    public static boolean isHudSnapping() {
        return hudSnapping;
    }

    public static void setHudSnapping(boolean snapping) {
        hudSnapping = snapping;
        MooConfig.save();
    }

    public static void toggleHudSnapping() {
        setHudSnapping(!hudSnapping);
    }

    public static int getHudScale() {
        return hudScale;
    }

    public static void setHudScale(int scale) {
        hudScale = Math.max(0, Math.min(100, scale));
        MooConfig.save();
    }

    public static float getHudScaleFactor() {
        return Math.max(0.2f, hudScale / 100.0f);
    }

    public static boolean isGlobalTextShadow() {
        return globalTextShadow;
    }

    public static void setGlobalTextShadow(boolean shadow) {
        globalTextShadow = shadow;
        FpsModule.setTextShadow(shadow);
        ToggleSprintModule.setTextShadow(shadow);
        PingModule.setTextShadow(shadow);
        PotionEffectsModule.setTextShadow(shadow);
        NametagsModule.setTextShadow(shadow);
        ChatModule.setTextShadow(shadow);
        MooConfig.save();
    }

    public static void toggleGlobalTextShadow() {
        setGlobalTextShadow(!globalTextShadow);
    }

    public static int getMenuBackgroundDim() {
        return menuBackgroundDim;
    }

    public static void setMenuBackgroundDim(int dim) {
        menuBackgroundDim = dim;
        MooConfig.save();
    }

    public static int getBackgroundDimColor() {
        return switch (menuBackgroundDim) {
            case 0 -> 0x55000000; // 33%
            case 2 -> 0xC0000000; // 75%
            default -> 0x88000000; // 53%
        };
    }

    public static boolean isGuiAnimations() {
        return guiAnimations;
    }

    public static void setGuiAnimations(boolean anim) {
        guiAnimations = anim;
        MooConfig.save();
    }

    public static void toggleGuiAnimations() {
        setGuiAnimations(!guiAnimations);
    }

    public static InvitationUIManager.UiVariant getInvitationUiVariant() {
        return invitationUiVariant != null ? invitationUiVariant : InvitationUIManager.UiVariant.FLOATING_CENTER;
    }

    public static void setInvitationUiVariant(InvitationUIManager.UiVariant variant) {
        if (variant != null) {
            invitationUiVariant = variant;
            MooConfig.save();
        }
    }

    public static ProfileType getActiveProfile() {
        return activeProfile;
    }

    public static void applyProfile(ProfileType profile) {
        activeProfile = profile;
        switch (profile) {
            case DEFAULT -> {
                FullbrightModule.setFullbrightActive(false);
                FpsModule.setFpsEnabled(true);
                ToggleSprintModule.setSprintEnabled(true);
                PingModule.setPingEnabled(true);
                PotionEffectsModule.setModuleEnabled(true);
                ZoomModule.setZoomEnabled(true);
                NametagsModule.setNametagsEnabled(true);
                ChatModule.setModuleEnabled(true);
                FreelookModule.setFreelookEnabled(true);
                MacroModule.setMacroEnabled(true);
            }
            case PVP -> {
                FullbrightModule.setFullbrightActive(true);
                FpsModule.setFpsEnabled(true);
                ToggleSprintModule.setSprintEnabled(true);
                PingModule.setPingEnabled(true);
                PotionEffectsModule.setModuleEnabled(true);
                ZoomModule.setZoomEnabled(true);
                NametagsModule.setNametagsEnabled(true);
                ChatModule.setModuleEnabled(true);
                FreelookModule.setFreelookEnabled(true);
                MacroModule.setMacroEnabled(true);
            }
            case SURVIVAL -> {
                FullbrightModule.setFullbrightActive(true);
                FpsModule.setFpsEnabled(true);
                ToggleSprintModule.setSprintEnabled(true);
                PingModule.setPingEnabled(false);
                PotionEffectsModule.setModuleEnabled(true);
                ZoomModule.setZoomEnabled(true);
                NametagsModule.setNametagsEnabled(true);
                ChatModule.setModuleEnabled(true);
                FreelookModule.setFreelookEnabled(false);
                MacroModule.setMacroEnabled(false);
            }
            case CLEAN -> {
                FullbrightModule.setFullbrightActive(false);
                FpsModule.setFpsEnabled(false);
                ToggleSprintModule.setSprintEnabled(false);
                PingModule.setPingEnabled(false);
                PotionEffectsModule.setModuleEnabled(false);
                ZoomModule.setZoomEnabled(true);
                NametagsModule.setNametagsEnabled(true);
                ChatModule.setModuleEnabled(true);
                FreelookModule.setFreelookEnabled(false);
                MacroModule.setMacroEnabled(false);
            }
        }

        // Synchronize module state in ModuleManager
        ModuleManager.getInstance().getModule("Gamma")
                .ifPresent(m -> m.setEnabled(FullbrightModule.isFullbrightActive()));
        ModuleManager.getInstance().getModule("FPS").ifPresent(m -> m.setEnabled(FpsModule.isFpsEnabled()));
        ModuleManager.getInstance().getModule("Sprint")
                .ifPresent(m -> m.setEnabled(ToggleSprintModule.isSprintEnabled()));
        ModuleManager.getInstance().getModule("Ping").ifPresent(m -> m.setEnabled(PingModule.isPingEnabled()));
        ModuleManager.getInstance().getModule("Potion Effects")
                .ifPresent(m -> m.setEnabled(PotionEffectsModule.isModuleEnabled()));
        ModuleManager.getInstance().getModule("Zoom").ifPresent(m -> m.setEnabled(ZoomModule.isZoomEnabled()));
        ModuleManager.getInstance().getModule("Nametags")
                .ifPresent(m -> m.setEnabled(NametagsModule.isNametagsEnabled()));
        ModuleManager.getInstance().getModule("Chat").ifPresent(m -> m.setEnabled(ChatModule.isModuleEnabled()));
        ModuleManager.getInstance().getModule("Freelook")
                .ifPresent(m -> m.setEnabled(FreelookModule.isFreelookEnabled()));
        ModuleManager.getInstance().getModule("Macro").ifPresent(m -> m.setEnabled(MacroModule.isMacroEnabled()));

        MooConfig.save();
    }
}
