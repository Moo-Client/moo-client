package com.mooclient.module.modules;

import com.mooclient.module.Module;
import com.mooclient.util.MooHudPositionHelper;

/**
 * Armor HUD module displaying equipped armor set (Helmet, Chestplate, Leggings, Boots),
 * bottom durability bars, and optional top text (% and numeric values) with Moo Client styling.
 */
public class ArmorModule extends Module {

    public enum ArmorStyle {
        MOO_CLIENT("Moo Client Look"),
        SIMPLE("Czysty / Simple"),
        COMPACT("Kompaktowy / Compact");

        private final String displayName;

        ArmorStyle(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public enum ArmorOrientation {
        HORIZONTAL("Poziomo / Horizontal"),
        VERTICAL("Pionowo / Vertical");

        private final String displayName;

        ArmorOrientation(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public enum DurabilityTextMode {
        NONE("Brak / None"),
        PERCENT("Procenty / %"),
        VALUE("Wartość / Value");

        private final String displayName;

        DurabilityTextMode(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public enum DurabilityMode {
        BAR("Pasek / Bar"),
        PERCENT("Procenty / %"),
        NUMERIC("Wartość / Value"),
        NONE("Brak / None");

        private final String displayName;

        DurabilityMode(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    private static boolean enabled = false;
    private static ArmorStyle style = ArmorStyle.MOO_CLIENT;
    private static ArmorOrientation orientation = ArmorOrientation.HORIZONTAL;
    private static DurabilityTextMode durabilityTextMode = DurabilityTextMode.NONE;
    private static boolean showDurabilityBar = true;
    private static boolean lowDurabilityWarning = true;
    private static long lastWarningSoundTime = 0L;
    private static boolean showBackground = true;
    private static boolean showEmptySlots = true;
    private static boolean showOffhand = false;
    private static boolean showMainHand = false;

    // Draggable coordinates & anchor (default LEFT, TOP just like FPS, Ping, Sprint)
    public static MooHudPositionHelper.WidgetPosition position =
            new MooHudPositionHelper.WidgetPosition(
                    MooHudPositionHelper.HudAnchorX.LEFT,
                    MooHudPositionHelper.HudAnchorY.TOP,
                    10, 106);
    public static int posX = 10;
    public static int posY = 106;
    public static int width = 86;
    public static int height = 20;

    public ArmorModule() {
        super("Armor HUD", "Wyświetla stan zbroi (Armor) i wytrzymałość na ekranie", Category.HUD, false);
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean state) {
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

    public static boolean isArmorEnabled() {
        return enabled;
    }

    public static void setArmorEnabled(boolean state) {
        enabled = state;
    }

    public static ArmorStyle getStyle() {
        return style;
    }

    public static void setStyle(ArmorStyle newStyle) {
        if (newStyle != null) {
            style = newStyle;
        }
    }

    public static void cycleStyle() {
        ArmorStyle[] styles = ArmorStyle.values();
        style = styles[(style.ordinal() + 1) % styles.length];
    }

    public static ArmorOrientation getOrientation() {
        return orientation;
    }

    public static void setOrientation(ArmorOrientation newOrientation) {
        if (newOrientation != null) {
            orientation = newOrientation;
        }
    }

    public static void cycleOrientation() {
        ArmorOrientation[] orientations = ArmorOrientation.values();
        orientation = orientations[(orientation.ordinal() + 1) % orientations.length];
    }

    public static DurabilityTextMode getDurabilityTextMode() {
        return durabilityTextMode;
    }

    public static void setDurabilityTextMode(DurabilityTextMode mode) {
        if (mode != null) {
            durabilityTextMode = mode;
        }
    }

    public static void cycleDurabilityTextMode() {
        DurabilityTextMode[] modes = DurabilityTextMode.values();
        durabilityTextMode = modes[(durabilityTextMode.ordinal() + 1) % modes.length];
    }

    public static DurabilityMode getDurabilityMode() {
        return switch (durabilityTextMode) {
            case PERCENT -> DurabilityMode.PERCENT;
            case VALUE -> DurabilityMode.NUMERIC;
            case NONE -> showDurabilityBar ? DurabilityMode.BAR : DurabilityMode.NONE;
        };
    }

    public static void setDurabilityMode(DurabilityMode mode) {
        if (mode == null) return;
        switch (mode) {
            case BAR -> {
                showDurabilityBar = true;
                durabilityTextMode = DurabilityTextMode.NONE;
            }
            case PERCENT -> {
                showDurabilityBar = true;
                durabilityTextMode = DurabilityTextMode.PERCENT;
            }
            case NUMERIC -> {
                showDurabilityBar = true;
                durabilityTextMode = DurabilityTextMode.VALUE;
            }
            case NONE -> {
                showDurabilityBar = false;
                durabilityTextMode = DurabilityTextMode.NONE;
            }
        }
    }

    public static void cycleDurabilityMode() {
        cycleDurabilityTextMode();
    }

    public static boolean isShowDurabilityBar() {
        return showDurabilityBar;
    }

    public static void setShowDurabilityBar(boolean state) {
        showDurabilityBar = state;
    }

    public static void toggleShowDurabilityBar() {
        showDurabilityBar = !showDurabilityBar;
    }

    private static final boolean[] slotAlerted = new boolean[6];

    public static boolean isLowDurabilityWarning() {
        return lowDurabilityWarning;
    }

    public static void setLowDurabilityWarning(boolean state) {
        lowDurabilityWarning = state;
        if (!state) {
            resetWarningState();
        }
    }

    public static void toggleLowDurabilityWarning() {
        setLowDurabilityWarning(!lowDurabilityWarning);
    }

    public static boolean checkAndTriggerWarning(java.util.List<net.minecraft.item.ItemStack> stacks) {
        if (!lowDurabilityWarning || stacks == null) {
            return false;
        }

        boolean shouldPlay = false;
        for (int i = 0; i < 6; i++) {
            net.minecraft.item.ItemStack stack = (i < stacks.size()) ? stacks.get(i) : null;
            if (stack != null && !stack.isEmpty() && stack.isDamageable() && stack.getMaxDamage() > 0) {
                int remaining = stack.getMaxDamage() - stack.getDamage();
                if (remaining <= 50) {
                    if (!slotAlerted[i]) {
                        slotAlerted[i] = true;
                        shouldPlay = true;
                    }
                } else {
                    slotAlerted[i] = false;
                }
            } else {
                slotAlerted[i] = false;
            }
        }
        return shouldPlay;
    }

    public static void resetWarningState() {
        java.util.Arrays.fill(slotAlerted, false);
    }

    public static boolean canPlayWarningSound() {
        return true;
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

    public static boolean isShowEmptySlots() {
        return showEmptySlots;
    }

    public static void setShowEmptySlots(boolean state) {
        showEmptySlots = state;
    }

    public static void toggleShowEmptySlots() {
        showEmptySlots = !showEmptySlots;
    }

    public static boolean isShowOffhand() {
        return showOffhand;
    }

    public static void setShowOffhand(boolean state) {
        showOffhand = state;
    }

    public static void toggleShowOffhand() {
        showOffhand = !showOffhand;
    }

    public static boolean isShowMainHand() {
        return showMainHand;
    }

    public static void setShowMainHand(boolean state) {
        showMainHand = state;
    }

    public static void toggleShowMainHand() {
        showMainHand = !showMainHand;
    }

    public static int getSlotCount() {
        int count = 4; // Head, Chest, Legs, Feet
        if (showOffhand) count++;
        if (showMainHand) count++;
        return count;
    }

    public static int getSlotSize() {
        return 20;
    }

    public static int getSlotGap() {
        return (style == ArmorStyle.COMPACT) ? 0 : 2;
    }

    public static int calculateUnscaledWidth() {
        int slotCount = getSlotCount();
        int slotSize = getSlotSize();
        int gap = getSlotGap();
        if (orientation == ArmorOrientation.HORIZONTAL) {
            return slotCount * slotSize + (slotCount - 1) * gap;
        } else {
            return (durabilityTextMode != DurabilityTextMode.NONE) ? slotSize + 28 : slotSize;
        }
    }

    public static int calculateUnscaledHeight() {
        int slotCount = getSlotCount();
        int slotSize = getSlotSize();
        int gap = getSlotGap();
        if (orientation == ArmorOrientation.HORIZONTAL) {
            return (durabilityTextMode != DurabilityTextMode.NONE) ? slotSize + 10 : slotSize;
        } else {
            return slotCount * slotSize + (slotCount - 1) * gap;
        }
    }

    public static int calculateBoxWidth(float hudScale) {
        return Math.round(calculateUnscaledWidth() * hudScale);
    }

    public static int calculateBoxHeight(float hudScale) {
        return Math.round(calculateUnscaledHeight() * hudScale);
    }
}
