package com.mooclient.module.modules;

import com.mooclient.module.Module;
import com.mooclient.util.MooHudPositionHelper;
import com.mooclient.util.PlayerArmorDurabilityTracker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

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
    private static boolean showMaxDurability = false;
    private static boolean lowDurabilityWarning = true;
    private static long lastWarningSoundTime = 0L;
    private static boolean showBackground = true;
    private static boolean showEmptySlots = true;
    private static boolean showOffhand = false;
    private static boolean showMainHand = false;
    private static boolean showNametagDurability = true;

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

    public static boolean isShowMaxDurability() {
        return showMaxDurability;
    }

    public static void setShowMaxDurability(boolean state) {
        showMaxDurability = state;
    }

    public static void toggleShowMaxDurability() {
        showMaxDurability = !showMaxDurability;
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

    public static boolean isItemCritical(net.minecraft.item.ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.isDamageable() || stack.getMaxDamage() <= 0) {
            return false;
        }
        int remaining = stack.getMaxDamage() - stack.getDamage();
        float ratio = (float) remaining / (float) stack.getMaxDamage();
        return ratio < 0.125f;
    }

    public static boolean checkAndTriggerWarning(java.util.List<net.minecraft.item.ItemStack> stacks) {
        if (!lowDurabilityWarning || stacks == null) {
            return false;
        }

        boolean shouldPlay = false;

        for (int i = 0; i < 6; i++) {
            net.minecraft.item.ItemStack stack = (i < stacks.size()) ? stacks.get(i) : null;
            if (isItemCritical(stack)) {
                if (!slotAlerted[i]) {
                    slotAlerted[i] = true;
                    shouldPlay = true;
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
            if (durabilityTextMode == DurabilityTextMode.NONE) {
                return slotSize;
            } else if (durabilityTextMode == DurabilityTextMode.VALUE && showMaxDurability) {
                return slotSize + 56;
            } else {
                return slotSize + 28;
            }
        }
    }

    public static int calculateUnscaledHeight() {
        int slotCount = getSlotCount();
        int slotSize = getSlotSize();
        int gap = getSlotGap();
        if (orientation == ArmorOrientation.HORIZONTAL) {
            if (durabilityTextMode == DurabilityTextMode.NONE) {
                return slotSize;
            } else if (durabilityTextMode == DurabilityTextMode.VALUE && showMaxDurability) {
                return slotSize + 18;
            } else {
                return slotSize + 10;
            }
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

    public static boolean isShowNametagDurability() {
        return showNametagDurability;
    }

    public static void setShowNametagDurability(boolean state) {
        showNametagDurability = state;
    }

    public static void toggleShowNametagDurability() {
        showNametagDurability = !showNametagDurability;
    }

    public static Formatting getDurabilityColor(int remaining, int maxDamage) {
        if (maxDamage <= 0) return Formatting.GRAY;
        float ratio = (float) remaining / (float) maxDamage;
        if (ratio > 0.70f) {
            return Formatting.GREEN;
        } else if (ratio > 0.40f) {
            return Formatting.YELLOW;
        } else if (ratio > 0.20f) {
            return Formatting.GOLD;
        } else {
            return Formatting.RED;
        }
    }

    public static void appendSlotDurabilityText(MutableText parent, ItemStack stack) {
        appendSlotDurabilityText(parent, -1, null, null, stack);
    }

    public static void appendSlotDurabilityText(MutableText parent, PlayerEntity player, EquipmentSlot slot, ItemStack stack) {
        appendSlotDurabilityText(parent, player != null ? player.getId() : -1, player, slot, stack);
    }

    public static void appendSlotDurabilityText(MutableText parent, int entityId, PlayerEntity player, EquipmentSlot slot, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            parent.append(Text.literal("-").formatted(Formatting.DARK_GRAY));
            return;
        }

        if (stack.isDamageable() && stack.getMaxDamage() > 0) {
            int max = stack.getMaxDamage();
            int remaining = -1;
            if (slot != null) {
                remaining = PlayerArmorDurabilityTracker.getRemainingDurability(entityId, player, slot, stack);
            }
            if (remaining < 0) {
                remaining = Math.max(0, max - stack.getDamage());
            }
            Formatting color = getDurabilityColor(remaining, max);
            parent.append(Text.literal(String.valueOf(remaining)).formatted(color));
        } else {
            parent.append(Text.literal("-").formatted(Formatting.GRAY));
        }
    }

    public static class NametagArmorCacheEntry {
        public net.minecraft.item.Item headItem;
        public net.minecraft.item.Item chestItem;
        public net.minecraft.item.Item legsItem;
        public net.minecraft.item.Item feetItem;
        public int headDur = -1;
        public int chestDur = -1;
        public int legsDur = -1;
        public int feetDur = -1;
        public Text cachedText;
        public float cachedWidth = -1f;
    }

    private static final java.util.Map<Integer, NametagArmorCacheEntry> NAMETAG_ARMOR_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    public static void clearNametagArmorCache(int entityId) {
        NAMETAG_ARMOR_CACHE.remove(entityId);
    }

    public static void clearAllNametagArmorCache() {
        NAMETAG_ARMOR_CACHE.clear();
    }

    public static float getNametagArmorWidth(int entityId, Text text, net.minecraft.client.font.TextRenderer textRenderer) {
        if (entityId > 0) {
            NametagArmorCacheEntry entry = NAMETAG_ARMOR_CACHE.get(entityId);
            if (entry != null && entry.cachedWidth > 0f) {
                return entry.cachedWidth;
            }
            if (entry != null && textRenderer != null && text != null) {
                entry.cachedWidth = textRenderer.getWidth(text);
                return entry.cachedWidth;
            }
        }
        return textRenderer != null && text != null ? textRenderer.getWidth(text) : 0f;
    }

    public static Text getNametagArmorText(net.minecraft.client.render.entity.state.PlayerEntityRenderState playerState) {
        if (playerState == null) {
            return null;
        }

        // Never render armor under the local player's own nametag in F5 / third person!
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.player != null) {
                if (playerState.id == client.player.getId()
                        || (playerState.name != null && playerState.name.equalsIgnoreCase(client.player.getGameProfile().getName()))) {
                    return null;
                }
            }
        } catch (Throwable ignored) {}

        ItemStack head = playerState.equippedHeadStack;
        ItemStack chest = playerState.equippedChestStack;
        ItemStack legs = playerState.equippedLegsStack;
        ItemStack feet = playerState.equippedFeetStack;

        boolean allEmpty = (head == null || head.isEmpty())
                && (chest == null || chest.isEmpty())
                && (legs == null || legs.isEmpty())
                && (feet == null || feet.isEmpty());

        if (allEmpty) {
            return null;
        }

        int entityId = playerState.id;
        int headDur = PlayerArmorDurabilityTracker.getRemainingDurability(entityId, null, EquipmentSlot.HEAD, head);
        int chestDur = PlayerArmorDurabilityTracker.getRemainingDurability(entityId, null, EquipmentSlot.CHEST, chest);
        int legsDur = PlayerArmorDurabilityTracker.getRemainingDurability(entityId, null, EquipmentSlot.LEGS, legs);
        int feetDur = PlayerArmorDurabilityTracker.getRemainingDurability(entityId, null, EquipmentSlot.FEET, feet);

        net.minecraft.item.Item headItem = (head != null && !head.isEmpty()) ? head.getItem() : null;
        net.minecraft.item.Item chestItem = (chest != null && !chest.isEmpty()) ? chest.getItem() : null;
        net.minecraft.item.Item legsItem = (legs != null && !legs.isEmpty()) ? legs.getItem() : null;
        net.minecraft.item.Item feetItem = (feet != null && !feet.isEmpty()) ? feet.getItem() : null;

        NametagArmorCacheEntry entry = NAMETAG_ARMOR_CACHE.get(entityId);
        if (entry != null && entry.cachedText != null
                && entry.headItem == headItem
                && entry.chestItem == chestItem
                && entry.legsItem == legsItem
                && entry.feetItem == feetItem
                && entry.headDur == headDur
                && entry.chestDur == chestDur
                && entry.legsDur == legsDur
                && entry.feetDur == feetDur) {
            return entry.cachedText;
        }

        MutableText result = Text.empty();
        appendSlotDurabilityWithPrecomputed(result, head, headDur);
        result.append(Text.literal(" | ").formatted(Formatting.DARK_GRAY));
        appendSlotDurabilityWithPrecomputed(result, chest, chestDur);
        result.append(Text.literal(" | ").formatted(Formatting.DARK_GRAY));
        appendSlotDurabilityWithPrecomputed(result, legs, legsDur);
        result.append(Text.literal(" | ").formatted(Formatting.DARK_GRAY));
        appendSlotDurabilityWithPrecomputed(result, feet, feetDur);

        if (NAMETAG_ARMOR_CACHE.size() > 256) {
            NAMETAG_ARMOR_CACHE.clear();
        }

        if (entry == null) {
            entry = new NametagArmorCacheEntry();
            NAMETAG_ARMOR_CACHE.put(entityId, entry);
        }
        entry.headItem = headItem;
        entry.chestItem = chestItem;
        entry.legsItem = legsItem;
        entry.feetItem = feetItem;
        entry.headDur = headDur;
        entry.chestDur = chestDur;
        entry.legsDur = legsDur;
        entry.feetDur = feetDur;
        entry.cachedText = result;
        entry.cachedWidth = -1f;

        return result;
    }

    private static void appendSlotDurabilityWithPrecomputed(MutableText parent, ItemStack stack, int durability) {
        if (stack == null || stack.isEmpty()) {
            parent.append(Text.literal("-").formatted(Formatting.DARK_GRAY));
            return;
        }

        if (stack.isDamageable() && stack.getMaxDamage() > 0) {
            int max = stack.getMaxDamage();
            int remaining = durability;
            if (remaining < 0) {
                remaining = Math.max(0, max - stack.getDamage());
            }
            Formatting color = getDurabilityColor(remaining, max);
            parent.append(Text.literal(String.valueOf(remaining)).formatted(color));
        } else {
            parent.append(Text.literal("-").formatted(Formatting.GRAY));
        }
    }
}
