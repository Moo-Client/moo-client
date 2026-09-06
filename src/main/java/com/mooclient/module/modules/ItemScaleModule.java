package com.mooclient.module.modules;

import com.mooclient.module.Module;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;

import java.util.*;

/**
 * Item Scale Module — dynamically scales dropped items in the world.
 * Highlights valuable items (Totems, Golden Apples, Pearls, Enchanted Gear)
 * during PvP fights by rendering them larger than regular drops.
 */
public class ItemScaleModule extends Module {

    public static class ScaleProfile {
        private String name;
        private float highlightScale; // e.g. 2.2f
        private float defaultScale;   // e.g. 1.0f
        private boolean scaleEnchanted;
        private final Set<String> targetItemIds = new LinkedHashSet<>();

        public ScaleProfile(String name, float highlightScale, float defaultScale, boolean scaleEnchanted, Collection<String> defaultItems) {
            this.name = name;
            this.highlightScale = highlightScale;
            this.defaultScale = defaultScale;
            this.scaleEnchanted = scaleEnchanted;
            if (defaultItems != null) {
                this.targetItemIds.addAll(defaultItems);
            }
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public float getHighlightScale() { return highlightScale; }
        public void setHighlightScale(float highlightScale) {
            this.highlightScale = Math.max(1.0f, Math.min(4.0f, highlightScale));
        }

        public float getDefaultScale() { return defaultScale; }
        public void setDefaultScale(float defaultScale) {
            this.defaultScale = Math.max(0.5f, Math.min(2.0f, defaultScale));
        }

        public boolean isScaleEnchanted() { return scaleEnchanted; }
        public void setScaleEnchanted(boolean scaleEnchanted) { this.scaleEnchanted = scaleEnchanted; }
        public void toggleScaleEnchanted() { this.scaleEnchanted = !this.scaleEnchanted; }

        public Set<String> getTargetItemIds() { return targetItemIds; }

        public boolean containsItem(String itemId) {
            return targetItemIds.contains(itemId);
        }

        public void toggleItem(String itemId) {
            if (targetItemIds.contains(itemId)) {
                targetItemIds.remove(itemId);
            } else {
                targetItemIds.add(itemId);
            }
        }

        public void addItem(String itemId) {
            targetItemIds.add(itemId);
        }

        public void removeItem(String itemId) {
            targetItemIds.remove(itemId);
        }
    }

    // Popular items for quick selection in GUI
    public static final List<String[]> PRESET_ITEMS = Arrays.asList(
            new String[]{"minecraft:totem_of_undying", "Totem Nieśmiertelności"},
            new String[]{"minecraft:enchanted_golden_apple", "Zaklęte Złote Jabłko (Kox)"},
            new String[]{"minecraft:golden_apple", "Złote Jabłko"},
            new String[]{"minecraft:ender_pearl", "Perła Endu"},
            new String[]{"minecraft:potion", "Mikstura"},
            new String[]{"minecraft:splash_potion", "Rzucana Mikstura"},
            new String[]{"minecraft:netherite_sword", "Netherytowy Miecz"},
            new String[]{"minecraft:diamond_sword", "Diamentowy Miecz"},
            new String[]{"minecraft:mace", "Buzdygan (Mace)"},
            new String[]{"minecraft:bow", "Łuk"},
            new String[]{"minecraft:crossbow", "Kusza"},
            new String[]{"minecraft:wind_charge", "Kula Wiatru (Wind Charge)"},
            new String[]{"minecraft:netherite_chestplate", "Netherytowa Klata"},
            new String[]{"minecraft:diamond_chestplate", "Diamentowa Klata"},
            new String[]{"minecraft:shield", "Tarcza"}
    );

    private static boolean enabled = true;
    private static final List<ScaleProfile> profiles = new ArrayList<>();
    private static int activeProfileIndex = 0;

    static {
        // Profile 0: PvP (Default)
        List<String> pvpItems = Arrays.asList(
                "minecraft:totem_of_undying",
                "minecraft:enchanted_golden_apple",
                "minecraft:golden_apple",
                "minecraft:ender_pearl",
                "minecraft:potion",
                "minecraft:splash_potion",
                "minecraft:netherite_sword",
                "minecraft:diamond_sword",
                "minecraft:mace",
                "minecraft:bow",
                "minecraft:crossbow"
        );
        profiles.add(new ScaleProfile("PvP", 2.2f, 1.0f, true, pvpItems));

        // Profile 1: Enchanty (Focuses on any enchanted drop + weapons)
        profiles.add(new ScaleProfile("Enchanty", 2.5f, 1.0f, true, Arrays.asList("minecraft:totem_of_undying", "minecraft:enchanted_golden_apple")));

        // Profile 2: Custom
        profiles.add(new ScaleProfile("Custom", 2.0f, 1.0f, false, Arrays.asList("minecraft:totem_of_undying", "minecraft:golden_apple")));
    }

    public ItemScaleModule() {
        super("Item Scale", "Powiększanie leżących itemów (Totemy, Koxy, Zbroje) podczas PvP", Category.RENDER, true);
    }

    public static boolean isItemScaleEnabled() {
        return enabled;
    }

    public static void setItemScaleEnabled(boolean state) {
        enabled = state;
    }

    public static void toggleItemScaleEnabled() {
        enabled = !enabled;
    }

    public static List<ScaleProfile> getProfiles() {
        return profiles;
    }

    public static int getActiveProfileIndex() {
        if (activeProfileIndex < 0 || activeProfileIndex >= profiles.size()) {
            activeProfileIndex = 0;
        }
        return activeProfileIndex;
    }

    public static void setActiveProfileIndex(int index) {
        if (index >= 0 && index < profiles.size()) {
            activeProfileIndex = index;
        }
    }

    public static ScaleProfile getActiveProfile() {
        return profiles.get(getActiveProfileIndex());
    }

    public static void nextProfile() {
        if (profiles.isEmpty()) return;
        activeProfileIndex = (activeProfileIndex + 1) % profiles.size();
    }

    public static float getScaleForItem(ItemStack stack) {
        if (!enabled || stack == null || stack.isEmpty()) {
            return 1.0f;
        }
        ScaleProfile profile = getActiveProfile();
        if (profile == null) return 1.0f;

        // 1. Check enchantments
        if (profile.isScaleEnchanted() && stack.hasEnchantments()) {
            return profile.getHighlightScale();
        }

        // 2. Check item id
        String id = Registries.ITEM.getId(stack.getItem()).toString();
        if (profile.containsItem(id)) {
            return profile.getHighlightScale();
        }

        return profile.getDefaultScale();
    }
}
