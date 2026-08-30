package com.mooclient.module.modules;

import com.mooclient.module.Module;
import com.mooclient.module.ModuleManager;

/**
 * Shulker Box Tooltip module for Moo Client.
 * Displays a 9x3 inventory preview of any Shulker Box when hovered in inventory.
 * Supports interactive Shift lock and item inspection.
 */
public class ShulkerTooltipModule extends Module {

    private static boolean shulkerEnabled = true;
    private static boolean colorMatchedBorder = true;
    private static boolean showEmptySlots = true;
    private static boolean requireShift = false;
    private static boolean inspectEnabled = true;

    public ShulkerTooltipModule() {
        super("Shulker Tooltip", "Podgląd zawartości Shulker Boxów w ekwipunku", Category.UTILITY, true);
    }

    @Override
    public void onEnable() {
        shulkerEnabled = true;
    }

    @Override
    public void onDisable() {
        shulkerEnabled = false;
    }

    @Override
    public boolean isEnabled() {
        return shulkerEnabled;
    }

    @Override
    public void setEnabled(boolean enabled, boolean saveConfig) {
        super.setEnabled(enabled, saveConfig);
        shulkerEnabled = enabled;
    }

    public static boolean isShulkerEnabled() {
        return shulkerEnabled;
    }

    public static void setShulkerEnabled(boolean enabled) {
        shulkerEnabled = enabled;
        ShulkerTooltipModule m = ModuleManager.getInstance().getModule(ShulkerTooltipModule.class);
        if (m != null && m.isEnabled() != enabled) {
            m.setEnabled(enabled, false);
        }
    }

    public static void toggleShulkerEnabled() {
        setShulkerEnabled(!shulkerEnabled);
    }

    public static boolean isColorMatchedBorder() {
        return colorMatchedBorder;
    }

    public static void setColorMatchedBorder(boolean enabled) {
        colorMatchedBorder = enabled;
    }

    public static void toggleColorMatchedBorder() {
        colorMatchedBorder = !colorMatchedBorder;
    }

    public static boolean isShowEmptySlots() {
        return showEmptySlots;
    }

    public static void setShowEmptySlots(boolean enabled) {
        showEmptySlots = enabled;
    }

    public static void toggleShowEmptySlots() {
        showEmptySlots = !showEmptySlots;
    }

    public static boolean isRequireShift() {
        return requireShift;
    }

    public static void setRequireShift(boolean enabled) {
        requireShift = enabled;
    }

    public static void toggleRequireShift() {
        requireShift = !requireShift;
    }

    public static boolean isInspectEnabled() {
        return inspectEnabled;
    }

    public static void setInspectEnabled(boolean enabled) {
        inspectEnabled = enabled;
    }

    public static void toggleInspectEnabled() {
        inspectEnabled = !inspectEnabled;
    }
}
