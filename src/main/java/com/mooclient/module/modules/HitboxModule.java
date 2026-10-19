package com.mooclient.module.modules;

import com.mooclient.module.Module;
import com.mooclient.util.MooClientSettings;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.MerchantEntity;

/**
 * Hitboxes module:
 * - Customize hitbox bounding box color (White, Red, Green, Blue, Aqua, Yellow, Purple, Orange, Accent, Custom RGB).
 * - Separate custom color for villagers and wandering traders.
 * - Toggle red eye-height line around the head.
 * - Toggle blue look direction arrow / vector.
 * - Option to render hitboxes ONLY when an entity is using elytra (gliding) or swimming.
 */
public class HitboxModule extends Module {

    public enum HitboxColor {
        WHITE("Biały / White", 1.0f, 1.0f, 1.0f, 0xFFFFFFFF),
        RED("Czerwony / Red", 1.0f, 0.23f, 0.19f, 0xFFFF3B30),
        GREEN("Zielony / Green", 0.2f, 0.78f, 0.35f, 0xFF34C759),
        BLUE("Niebieski / Blue", 0.0f, 0.48f, 1.0f, 0xFF007AFF),
        AQUA("Morski / Aqua", 0.35f, 0.78f, 0.98f, 0xFF5AC8FA),
        YELLOW("Żółty / Yellow", 1.0f, 0.8f, 0.0f, 0xFFFFCC00),
        PURPLE("Fioletowy / Purple", 0.69f, 0.32f, 0.87f, 0xFFAF52DE),
        ORANGE("Pomarańczowy / Orange", 1.0f, 0.58f, 0.0f, 0xFFFF9500),
        ACCENT("Akcent / Moo Accent", 0.0f, 0.0f, 0.0f, 0xFFFFFFFF),
        CUSTOM("Własny / Custom RGB", 1.0f, 1.0f, 1.0f, 0xFFFFFFFF);

        private final String displayName;
        private final float r;
        private final float g;
        private final float b;
        private final int previewColor;

        HitboxColor(String displayName, float r, float g, float b, int previewColor) {
            this.displayName = displayName;
            this.r = r;
            this.g = g;
            this.b = b;
            this.previewColor = previewColor;
        }

        public String getDisplayName() {
            return displayName;
        }

        public int getPreviewColor() {
            if (this == ACCENT) {
                return MooClientSettings.getAccentColor();
            }
            if (this == CUSTOM) {
                return ((customR & 0xFF) << 16) | ((customG & 0xFF) << 8) | (customB & 0xFF);
            }
            return previewColor;
        }

        public float getRed() {
            if (this == ACCENT) {
                int col = MooClientSettings.getAccentColor();
                return ((col >> 16) & 0xFF) / 255.0f;
            }
            if (this == CUSTOM) {
                return (customR & 0xFF) / 255.0f;
            }
            return r;
        }

        public float getGreen() {
            if (this == ACCENT) {
                int col = MooClientSettings.getAccentColor();
                return ((col >> 8) & 0xFF) / 255.0f;
            }
            if (this == CUSTOM) {
                return (customG & 0xFF) / 255.0f;
            }
            return g;
        }

        public float getBlue() {
            if (this == ACCENT) {
                int col = MooClientSettings.getAccentColor();
                return (col & 0xFF) / 255.0f;
            }
            if (this == CUSTOM) {
                return (customB & 0xFF) / 255.0f;
            }
            return b;
        }
    }

    private static boolean enabled = false;
    private static HitboxColor color = HitboxColor.WHITE;
    private static int customR = 255;
    private static int customG = 255;
    private static int customB = 255;

    // Villager specific hitbox color
    private static boolean villagerColorEnabled = true;
    private static HitboxColor villagerColor = HitboxColor.GREEN;
    private static int villagerCustomR = 85;
    private static int villagerCustomG = 255;
    private static int villagerCustomB = 85;

    private static boolean showEyeHeight = false; // Default false (wyłączona czerwona linia obok głowy)
    private static boolean showLookVector = false; // Default false (wyłączona niebieska strzałka)
    private static boolean onlyGlidingOrSwimming = false; // Toggle: hitbox tylko jeśli elytra lub pływanie

    public HitboxModule() {
        super("Hitboxes", "Własne kolory hitboxów, ukrywanie linii oczu i strzałki, filtr elytry/pływania", Category.RENDER, false);
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean state) {
        setEnabled(state, true);
    }

    @Override
    public void setEnabled(boolean state, boolean saveConfig) {
        super.setEnabled(state, saveConfig);
        enabled = state;
    }

    @Override
    public void onEnable() {
        enabled = true;
    }

    @Override
    public void onDisable() {
        enabled = false;
    }

    public static boolean isHitboxEnabled() {
        return enabled;
    }

    public static void setHitboxEnabled(boolean state) {
        enabled = state;
        com.mooclient.module.ModuleManager.getInstance().getModule("Hitboxes").ifPresent(m -> {
            m.setEnabled(state, false);
        });
    }

    public static void toggleHitboxEnabled() {
        setHitboxEnabled(!enabled);
    }

    public static HitboxColor getColor() {
        return color;
    }

    public static void setColor(HitboxColor newColor) {
        if (newColor != null) {
            color = newColor;
        }
    }

    public static void cycleColor() {
        HitboxColor[] values = HitboxColor.values();
        color = values[(color.ordinal() + 1) % values.length];
    }

    public static int getCustomRed() {
        return customR;
    }

    public static void setCustomRed(int r) {
        customR = Math.max(0, Math.min(255, r));
    }

    public static int getCustomGreen() {
        return customG;
    }

    public static void setCustomGreen(int g) {
        customG = Math.max(0, Math.min(255, g));
    }

    public static int getCustomBlue() {
        return customB;
    }

    public static void setCustomBlue(int b) {
        customB = Math.max(0, Math.min(255, b));
    }

    // Villager hitbox color methods
    public static boolean isVillagerColorEnabled() {
        return villagerColorEnabled;
    }

    public static void setVillagerColorEnabled(boolean state) {
        villagerColorEnabled = state;
    }

    public static void toggleVillagerColorEnabled() {
        villagerColorEnabled = !villagerColorEnabled;
    }

    public static HitboxColor getVillagerColor() {
        return villagerColor;
    }

    public static void setVillagerColor(HitboxColor newColor) {
        if (newColor != null) {
            villagerColor = newColor;
        }
    }

    public static void cycleVillagerColor() {
        HitboxColor[] values = HitboxColor.values();
        villagerColor = values[(villagerColor.ordinal() + 1) % values.length];
    }

    public static int getVillagerCustomRed() {
        return villagerCustomR;
    }

    public static void setVillagerCustomRed(int r) {
        villagerCustomR = Math.max(0, Math.min(255, r));
    }

    public static int getVillagerCustomGreen() {
        return villagerCustomG;
    }

    public static void setVillagerCustomGreen(int g) {
        villagerCustomG = Math.max(0, Math.min(255, g));
    }

    public static int getVillagerCustomBlue() {
        return villagerCustomB;
    }

    public static void setVillagerCustomBlue(int b) {
        villagerCustomB = Math.max(0, Math.min(255, b));
    }

    public static boolean isVillager(Entity entity) {
        if (entity == null) return false;
        return entity instanceof MerchantEntity;
    }

    public static float getColorRed(Entity entity) {
        if (villagerColorEnabled && isVillager(entity)) {
            return getVillagerColorRed();
        }
        return getColorRed();
    }

    public static float getColorGreen(Entity entity) {
        if (villagerColorEnabled && isVillager(entity)) {
            return getVillagerColorGreen();
        }
        return getColorGreen();
    }

    public static float getColorBlue(Entity entity) {
        if (villagerColorEnabled && isVillager(entity)) {
            return getVillagerColorBlue();
        }
        return getColorBlue();
    }

    public static float getColorRed() {
        if (color == HitboxColor.CUSTOM) {
            return (customR & 0xFF) / 255.0f;
        }
        return color.getRed();
    }

    public static float getColorGreen() {
        if (color == HitboxColor.CUSTOM) {
            return (customG & 0xFF) / 255.0f;
        }
        return color.getGreen();
    }

    public static float getColorBlue() {
        if (color == HitboxColor.CUSTOM) {
            return (customB & 0xFF) / 255.0f;
        }
        return color.getBlue();
    }

    public static float getVillagerColorRed() {
        if (villagerColor == HitboxColor.CUSTOM) {
            return (villagerCustomR & 0xFF) / 255.0f;
        }
        if (villagerColor == HitboxColor.ACCENT) {
            int col = MooClientSettings.getAccentColor();
            return ((col >> 16) & 0xFF) / 255.0f;
        }
        return villagerColor.getRed();
    }

    public static float getVillagerColorGreen() {
        if (villagerColor == HitboxColor.CUSTOM) {
            return (villagerCustomG & 0xFF) / 255.0f;
        }
        if (villagerColor == HitboxColor.ACCENT) {
            int col = MooClientSettings.getAccentColor();
            return ((col >> 8) & 0xFF) / 255.0f;
        }
        return villagerColor.getGreen();
    }

    public static float getVillagerColorBlue() {
        if (villagerColor == HitboxColor.CUSTOM) {
            return (villagerCustomB & 0xFF) / 255.0f;
        }
        if (villagerColor == HitboxColor.ACCENT) {
            int col = MooClientSettings.getAccentColor();
            return (col & 0xFF) / 255.0f;
        }
        return villagerColor.getBlue();
    }

    public static float getColorAlpha() {
        return 1.0f;
    }

    public static boolean isShowEyeHeight() {
        return showEyeHeight;
    }

    public static void setShowEyeHeight(boolean state) {
        showEyeHeight = state;
    }

    public static void toggleShowEyeHeight() {
        showEyeHeight = !showEyeHeight;
    }

    public static boolean isShowLookVector() {
        return showLookVector;
    }

    public static void setShowLookVector(boolean state) {
        showLookVector = state;
    }

    public static void toggleShowLookVector() {
        showLookVector = !showLookVector;
    }

    public static boolean isOnlyGlidingOrSwimming() {
        return onlyGlidingOrSwimming;
    }

    public static void setOnlyGlidingOrSwimming(boolean state) {
        onlyGlidingOrSwimming = state;
    }

    public static void toggleOnlyGlidingOrSwimming() {
        onlyGlidingOrSwimming = !onlyGlidingOrSwimming;
    }
}
