package com.mooclient.module.modules;

import com.mooclient.module.Module;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.*;

/**
 * Item Scale Module — dynamically scales dropped items in the world.
 * Allows creating multiple custom profiles with on/off toggles (like Macro),
 * adding specific items, and choosing custom enchantment filters per item
 * (e.g. netherite sword with Sharpness or Fire Aspect).
 */
public class ItemScaleModule extends Module {

    public static class EnchantRequirement {
        private String enchantId;
        private final Set<Integer> levels = new TreeSet<>();

        public EnchantRequirement(String enchantId) {
            this.enchantId = enchantId != null ? enchantId : "";
        }

        public EnchantRequirement(String enchantId, Collection<Integer> levels) {
            this.enchantId = enchantId != null ? enchantId : "";
            if (levels != null) {
                this.levels.addAll(levels);
            }
        }

        public String getEnchantId() { return enchantId; }
        public void setEnchantId(String enchantId) { this.enchantId = enchantId != null ? enchantId : ""; }

        public Set<Integer> getLevels() { return levels; }

        public boolean hasLevel(int level) { return levels.contains(level); }

        public void toggleLevel(int level) {
            if (levels.contains(level)) {
                levels.remove(level);
            } else {
                levels.add(level);
            }
        }

        public void clearLevels() {
            levels.clear();
        }

        public boolean matchesLevel(int actualLevel) {
            if (levels == null || levels.isEmpty()) {
                return true; // Any level matches
            }
            return levels.contains(actualLevel);
        }

        public String getLevelsDisplay() {
            if (levels == null || levels.isEmpty()) {
                return "";
            }
            List<Integer> sorted = new ArrayList<>(levels);
            Collections.sort(sorted);
            if (sorted.size() == 1) {
                return toRoman(sorted.get(0));
            }
            boolean contiguous = true;
            for (int i = 0; i < sorted.size() - 1; i++) {
                if (sorted.get(i + 1) != sorted.get(i) + 1) {
                    contiguous = false;
                    break;
                }
            }
            if (contiguous && sorted.size() > 1) {
                return toRoman(sorted.get(0)) + "-" + toRoman(sorted.get(sorted.size() - 1));
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < sorted.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(toRoman(sorted.get(i)));
            }
            return sb.toString();
        }
    }

    public static class ScaleItemEntry {
        private String itemId;
        private final List<EnchantRequirement> enchantments = new ArrayList<>();
        private boolean requireAllEnchantments = false; // false = OR (dowolne), true = AND (wszystkie)

        public ScaleItemEntry(String itemId, String enchantmentId) {
            this.itemId = itemId != null ? itemId : "";
            if (enchantmentId != null && !enchantmentId.isEmpty()) {
                this.enchantments.add(new EnchantRequirement(enchantmentId));
            }
        }

        public ScaleItemEntry(String itemId, List<EnchantRequirement> enchantments, boolean requireAllEnchantments) {
            this.itemId = itemId != null ? itemId : "";
            if (enchantments != null) {
                this.enchantments.addAll(enchantments);
            }
            this.requireAllEnchantments = requireAllEnchantments;
        }

        public String getItemId() { return itemId; }
        public void setItemId(String itemId) { this.itemId = itemId; }

        public List<EnchantRequirement> getEnchantments() { return enchantments; }

        public boolean isRequireAllEnchantments() { return requireAllEnchantments; }
        public void setRequireAllEnchantments(boolean requireAllEnchantments) { this.requireAllEnchantments = requireAllEnchantments; }
        public void toggleRequireAllEnchantments() { this.requireAllEnchantments = !this.requireAllEnchantments; }

        public String getEnchantmentId() {
            if (enchantments.isEmpty()) return "";
            return enchantments.get(0).getEnchantId();
        }

        public void setEnchantmentId(String enchantmentId) {
            enchantments.clear();
            if (enchantmentId != null && !enchantmentId.isEmpty()) {
                enchantments.add(new EnchantRequirement(enchantmentId));
            }
        }

        public EnchantRequirement getRequirement(String enchantId) {
            for (EnchantRequirement req : enchantments) {
                if (req.getEnchantId().equalsIgnoreCase(enchantId)) return req;
            }
            return null;
        }

        public boolean hasEnchantment(String enchantId) {
            return getRequirement(enchantId) != null;
        }

        public void clearEnchantments() {
            enchantments.clear();
        }

        public void toggleEnchantment(String enchantId) {
            if (enchantId == null || enchantId.isEmpty()) {
                enchantments.clear();
                return;
            }
            if (enchantId.equalsIgnoreCase("any")) {
                enchantments.clear();
                enchantments.add(new EnchantRequirement("any"));
                return;
            }
            enchantments.removeIf(e -> e.getEnchantId().equalsIgnoreCase("any") || e.getEnchantId().isEmpty());
            EnchantRequirement existing = getRequirement(enchantId);
            if (existing != null) {
                enchantments.remove(existing);
            } else {
                enchantments.add(new EnchantRequirement(enchantId));
            }
        }

        public void toggleEnchantmentLevel(String enchantId, int level) {
            if (enchantId == null || enchantId.isEmpty() || enchantId.equalsIgnoreCase("any")) {
                return;
            }
            enchantments.removeIf(e -> e.getEnchantId().equalsIgnoreCase("any") || e.getEnchantId().isEmpty());
            EnchantRequirement req = getRequirement(enchantId);
            if (req == null) {
                req = new EnchantRequirement(enchantId);
                enchantments.add(req);
            }
            req.toggleLevel(level);
        }

        public static boolean isArmorItem(String id) {
            String clean = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
            return clean.endsWith("_helmet") || clean.endsWith("_chestplate")
                    || clean.endsWith("_leggings") || clean.endsWith("_boots")
                    || clean.equals("turtle_helmet") || clean.equals("elytra");
        }

        public static boolean matchesItemPattern(String configuredPattern, String actualStackId) {
            if (configuredPattern == null || actualStackId == null) return false;
            String p = configuredPattern.toLowerCase().trim();
            String a = actualStackId.toLowerCase().trim();
            String cleanP = p.contains(":") ? p.substring(p.indexOf(':') + 1) : p;
            String cleanA = a.contains(":") ? a.substring(a.indexOf(':') + 1) : a;

            if (cleanP.equals(cleanA) || p.equals(a)) {
                return true;
            }

            // Set patterns
            if (p.startsWith("set:all_armor") || p.equals("all_armor") || p.equals("set_all_armor")) {
                return isArmorItem(cleanA);
            }
            if (p.startsWith("set:netherite") || p.equals("set_netherite") || p.equals("netherite_set")) {
                return cleanA.equals("netherite_helmet") || cleanA.equals("netherite_chestplate")
                        || cleanA.equals("netherite_leggings") || cleanA.equals("netherite_boots");
            }
            if (p.startsWith("set:diamond") || p.equals("set_diamond") || p.equals("diamond_set")) {
                return cleanA.equals("diamond_helmet") || cleanA.equals("diamond_chestplate")
                        || cleanA.equals("diamond_leggings") || cleanA.equals("diamond_boots");
            }
            if (p.startsWith("set:iron") || p.equals("set_iron") || p.equals("iron_set")) {
                return cleanA.equals("iron_helmet") || cleanA.equals("iron_chestplate")
                        || cleanA.equals("iron_leggings") || cleanA.equals("iron_boots");
            }
            if (p.startsWith("set:gold") || p.startsWith("set:golden") || p.equals("set_gold") || p.equals("gold_set")) {
                return cleanA.equals("golden_helmet") || cleanA.equals("golden_chestplate")
                        || cleanA.equals("golden_leggings") || cleanA.equals("golden_boots");
            }
            if (p.startsWith("set:chainmail") || p.equals("set_chainmail") || p.equals("chainmail_set")) {
                return cleanA.equals("chainmail_helmet") || cleanA.equals("chainmail_chestplate")
                        || cleanA.equals("chainmail_leggings") || cleanA.equals("chainmail_boots");
            }
            if (p.startsWith("set:leather") || p.equals("set_leather") || p.equals("leather_set")) {
                return cleanA.equals("leather_helmet") || cleanA.equals("leather_chestplate")
                        || cleanA.equals("leather_leggings") || cleanA.equals("leather_boots");
            }
            return false;
        }

        public boolean matches(ItemStack stack) {
            if (stack == null || stack.isEmpty()) return false;
            String id = Registries.ITEM.getId(stack.getItem()).toString();
            if (!matchesItemPattern(this.itemId, id)) {
                return false;
            }
            return stackMatchesEnchantments(stack, this.enchantments, this.requireAllEnchantments);
        }

        public String getBadgeText() {
            if (enchantments.isEmpty()) {
                return "Bez wymogu";
            }
            if (enchantments.size() == 1) {
                EnchantRequirement req = enchantments.get(0);
                if (req.getEnchantId().equalsIgnoreCase("any")) {
                    return "Dowolny enchant";
                }
                String name = getEnchantmentShortName(req.getEnchantId());
                String levels = req.getLevelsDisplay();
                if (!levels.isEmpty()) {
                    return name + " " + levels;
                }
                return name;
            }
            EnchantRequirement first = enchantments.get(0);
            String firstStr = getEnchantmentShortName(first.getEnchantId());
            String firstLv = first.getLevelsDisplay();
            if (!firstLv.isEmpty()) {
                firstStr += " " + firstLv;
            }
            return firstStr + " +" + (enchantments.size() - 1);
        }
    }

    public static class ScaleProfile {
        private String id;
        private String name;
        private boolean enabled;
        private float highlightScale; // e.g. 2.2f
        private float defaultScale;   // e.g. 1.0f
        private final List<ScaleItemEntry> items = new ArrayList<>();

        public ScaleProfile(String id, String name, boolean enabled, float highlightScale, float defaultScale, List<ScaleItemEntry> items) {
            this.id = id != null ? id : "prof_" + System.currentTimeMillis();
            this.name = name != null ? name : "Profil";
            this.enabled = enabled;
            this.highlightScale = highlightScale;
            this.defaultScale = defaultScale;
            if (items != null) {
                this.items.addAll(items);
            }
        }

        public String getId() { return id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public void toggleEnabled() { this.enabled = !this.enabled; }

        public float getHighlightScale() { return highlightScale; }
        public void setHighlightScale(float highlightScale) {
            this.highlightScale = Math.max(1.0f, Math.min(4.0f, highlightScale));
        }

        public float getDefaultScale() { return defaultScale; }
        public void setDefaultScale(float defaultScale) {
            this.defaultScale = Math.max(0.5f, Math.min(2.0f, defaultScale));
        }

        public List<ScaleItemEntry> getItems() { return items; }

        public boolean containsItem(String itemId) {
            for (ScaleItemEntry e : items) {
                if (e.getItemId().equalsIgnoreCase(itemId)) return true;
            }
            return false;
        }

        public ScaleItemEntry getItemEntry(String itemId) {
            for (ScaleItemEntry e : items) {
                if (e.getItemId().equalsIgnoreCase(itemId)) return e;
            }
            return null;
        }

        public void addItem(String itemId, String enchantId) {
            items.add(new ScaleItemEntry(itemId, enchantId));
        }

        public void duplicateItem(int index) {
            if (index >= 0 && index < items.size()) {
                ScaleItemEntry orig = items.get(index);
                List<EnchantRequirement> copyReqs = new ArrayList<>();
                for (EnchantRequirement r : orig.getEnchantments()) {
                    copyReqs.add(new EnchantRequirement(r.getEnchantId(), r.getLevels()));
                }
                items.add(index + 1, new ScaleItemEntry(orig.getItemId(), copyReqs, orig.isRequireAllEnchantments()));
            }
        }

        public void removeItem(int index) {
            if (index >= 0 && index < items.size()) {
                items.remove(index);
            }
        }

        public void removeItem(String itemId) {
            items.removeIf(e -> e.getItemId().equalsIgnoreCase(itemId));
        }

        public void toggleItem(String itemId) {
            if (containsItem(itemId)) {
                removeItem(itemId);
            } else {
                addItem(itemId, "");
            }
        }
    }

    // Popular items for quick selection in GUI
    public static final List<String[]> PRESET_ITEMS = Arrays.asList(
            new String[]{"set:netherite", "Set Netherytowy (Zbroja)"},
            new String[]{"set:diamond", "Set Diamentowy (Zbroja)"},
            new String[]{"set:all_armor", "Wszystkie Sety (Każda Zbroja)"},
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
            new String[]{"minecraft:wind_charge", "Kula Wiatru"},
            new String[]{"minecraft:netherite_helmet", "Netherytowy Hełm"},
            new String[]{"minecraft:netherite_chestplate", "Netherytowa Klata"},
            new String[]{"minecraft:netherite_leggings", "Netherytowe Spodnie"},
            new String[]{"minecraft:netherite_boots", "Netherytowe Buty"},
            new String[]{"minecraft:diamond_helmet", "Diamentowy Hełm"},
            new String[]{"minecraft:diamond_chestplate", "Diamentowa Klata"},
            new String[]{"minecraft:diamond_leggings", "Diamentowe Spodnie"},
            new String[]{"minecraft:diamond_boots", "Diamentowe Buty"},
            new String[]{"minecraft:shield", "Tarcza"}
    );

    // List of selectable enchantments for right-click filter
    public static final List<String[]> ENCHANTMENTS_LIST = Arrays.asList(
            new String[]{"", "Bez wymogu", "Powiększa każdy przedmiot tego typu"},
            new String[]{"any", "Dowolny Enchant", "Musi posiadać jakiekolwiek zaklęcie"},
            new String[]{"sharpness", "Ostrość (Sharpness)", "Miecz"},
            new String[]{"fire_aspect", "Zaklęty Ogień (Fire Aspect)", "Miecz"},
            new String[]{"knockback", "Odrzut (Knockback)", "Miecz"},
            new String[]{"looting", "Grabież (Looting)", "Miecz"},
            new String[]{"sweeping_edge", "Zamaszyste Ostrze (Sweeping)", "Miecz"},
            new String[]{"protection", "Ochrona (Protection)", "Zbroja"},
            new String[]{"fire_protection", "Odporność na ogień (Fire Prot)", "Zbroja"},
            new String[]{"blast_protection", "Odporność na wybuchy (Blast Prot)", "Zbroja"},
            new String[]{"projectile_protection", "Odporność na pociski (Proj Prot)", "Zbroja"},
            new String[]{"thorns", "Ciernie (Thorns)", "Zbroja"},
            new String[]{"feather_falling", "Powolne Opadanie (Feather Fall)", "Buty"},
            new String[]{"power", "Moc (Power)", "Łuk"},
            new String[]{"flame", "Płomień (Flame)", "Łuk"},
            new String[]{"punch", "Uderzenie (Punch)", "Łuk"},
            new String[]{"infinity", "Nieskończoność (Infinity)", "Łuk"},
            new String[]{"quick_charge", "Szybkie Ładowanie (Quick Charge)", "Kusza"},
            new String[]{"multishot", "Wielostrzał (Multishot)", "Kusza"},
            new String[]{"piercing", "Przebicie (Piercing)", "Kusza"},
            new String[]{"density", "Zagęszczenie (Density)", "Buława (Mace)"},
            new String[]{"breach", "Wyłom (Breach)", "Buława (Mace)"},
            new String[]{"wind_burst", "Podmuch Wiatru (Wind Burst)", "Buława (Mace)"},
            new String[]{"unbreaking", "Niezniszczalność (Unbreaking)", "Wszystko"},
            new String[]{"mending", "Naprawa (Mending)", "Wszystko"},
            new String[]{"efficiency", "Wydajność (Efficiency)", "Narzędzia"},
            new String[]{"fortune", "Szczęście (Fortune)", "Narzędzia"},
            new String[]{"silk_touch", "Jedwabny Dotyk (Silk Touch)", "Narzędzia"}
    );

    public static String toRoman(int level) {
        return switch (level) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(level);
        };
    }

    public static int getMaxLevelForEnchantment(String enchantId) {
        if (enchantId == null) return 5;
        String id = enchantId.replace("minecraft:", "").toLowerCase();
        return switch (id) {
            case "protection", "sharpness", "smite", "bane_of_arthropods", "density", "power", "efficiency" -> 5;
            case "fire_protection", "blast_protection", "projectile_protection",
                 "feather_falling", "piercing", "breach" -> 4;
            case "respiration", "looting", "fortune", "quick_charge", "wind_burst", "unbreaking",
                 "thorns", "depth_strider", "soul_speed", "frost_walker", "swift_sneak",
                 "loyalty", "riptide", "impaling", "luck_of_the_sea", "lure" -> 3;
            case "fire_aspect", "knockback", "punch" -> 2;
            case "aqua_affinity", "channeling", "multishot", "flame", "infinity", "mending",
                 "silk_touch" -> 1;
            default -> 5;
        };
    }

    public static String getEnchantmentShortName(String enchantId) {
        if (enchantId == null || enchantId.isEmpty()) return "Bez wymogu";
        if (enchantId.equalsIgnoreCase("any")) return "Dowolny";
        for (String[] enc : ENCHANTMENTS_LIST) {
            if (enc[0].isEmpty()) continue;
            if (enc[0].equalsIgnoreCase(enchantId) || enchantId.endsWith(":" + enc[0]) || enchantId.contains(enc[0])) {
                String full = enc[1];
                int paren = full.indexOf(" (");
                if (paren > 0) {
                    return full.substring(0, paren);
                }
                return full;
            }
        }
        return enchantId;
    }

    public static String getEnchantmentDisplayName(String enchantId) {
        if (enchantId == null || enchantId.isEmpty()) return "Bez wymogu";
        if (enchantId.equalsIgnoreCase("any")) return "Dowolny enchant";
        for (String[] enc : ENCHANTMENTS_LIST) {
            if (enc[0].equalsIgnoreCase(enchantId) || enchantId.endsWith(":" + enc[0]) || enchantId.contains(enc[0])) {
                return enc[1];
            }
        }
        return enchantId;
    }

    public static final List<ItemSearchResult> SET_PRESET_RESULTS = Arrays.asList(
            new ItemSearchResult("set:all_armor", "Wszystkie Sety (Każda Zbroja)", Items.NETHERITE_CHESTPLATE),
            new ItemSearchResult("set:netherite", "Set Netherytowy (Cała Zbroja)", Items.NETHERITE_CHESTPLATE),
            new ItemSearchResult("set:diamond", "Set Diamentowy (Cała Zbroja)", Items.DIAMOND_CHESTPLATE),
            new ItemSearchResult("set:iron", "Set Żelazny (Cała Zbroja)", Items.IRON_CHESTPLATE),
            new ItemSearchResult("set:gold", "Set Złoty (Cała Zbroja)", Items.GOLDEN_CHESTPLATE),
            new ItemSearchResult("set:chainmail", "Set Kolczy (Cała Zbroja)", Items.CHAINMAIL_CHESTPLATE)
    );

    public static class ItemSearchResult {
        private final String itemId;
        private final String displayName;
        private final Item item;

        public ItemSearchResult(String itemId, String displayName, Item item) {
            this.itemId = itemId != null ? itemId : "";
            this.displayName = displayName != null ? displayName : "";
            this.item = item;
        }

        public String getItemId() { return itemId; }
        public String getDisplayName() { return displayName; }
        public Item getItem() { return item; }
    }

    private static List<ItemSearchResult> ALL_MC_ITEMS = null;

    public static void resetItemCache() {
        ALL_MC_ITEMS = null;
    }

    public static List<ItemSearchResult> getAllMinecraftItems() {
        if (ALL_MC_ITEMS == null) {
            ALL_MC_ITEMS = new ArrayList<>();
            // Include set presets first
            ALL_MC_ITEMS.addAll(SET_PRESET_RESULTS);

            List<ItemSearchResult> mcItems = new ArrayList<>();
            for (Item item : Registries.ITEM) {
                if (item == Items.AIR) continue;
                Identifier id = Registries.ITEM.getId(item);
                if (id == null) continue;
                String itemId = id.toString();
                String name = item.getName().getString();
                mcItems.add(new ItemSearchResult(itemId, name, item));
            }
            mcItems.sort(Comparator.comparing(ItemSearchResult::getDisplayName, String.CASE_INSENSITIVE_ORDER));
            ALL_MC_ITEMS.addAll(mcItems);
        }
        return ALL_MC_ITEMS;
    }

    public static List<ItemSearchResult> searchMinecraftItems(String query) {
        List<ItemSearchResult> all = getAllMinecraftItems();
        if (query == null || query.trim().isEmpty()) {
            List<ItemSearchResult> prioritized = new ArrayList<>();
            Set<String> added = new HashSet<>();

            // 1. Armor Set Presets
            for (ItemSearchResult setRes : SET_PRESET_RESULTS) {
                prioritized.add(setRes);
                added.add(setRes.getItemId().toLowerCase());
            }

            // 2. Popular PvP Presets
            for (String[] preset : PRESET_ITEMS) {
                String pId = preset[0].toLowerCase();
                if (added.contains(pId)) continue;
                for (ItemSearchResult entry : all) {
                    if (entry.getItemId().equalsIgnoreCase(pId)) {
                        prioritized.add(entry);
                        added.add(pId);
                        break;
                    }
                }
            }

            // 3. Remaining items
            for (ItemSearchResult entry : all) {
                if (!added.contains(entry.getItemId().toLowerCase())) {
                    prioritized.add(entry);
                }
            }
            return prioritized;
        }

        String q = query.trim().toLowerCase();
        List<ItemSearchResult> results = new ArrayList<>();
        for (ItemSearchResult entry : all) {
            if (entry.getDisplayName().toLowerCase().contains(q) || entry.getItemId().toLowerCase().contains(q)) {
                results.add(entry);
            }
        }
        return results;
    }

    public static ItemStack getItemStackForEntry(String itemId) {
        if (itemId == null || itemId.isEmpty()) return ItemStack.EMPTY;
        if (itemId.startsWith("set:")) {
            String s = itemId.substring(4).toLowerCase();
            return switch (s) {
                case "diamond" -> Items.DIAMOND_CHESTPLATE.getDefaultStack();
                case "iron" -> Items.IRON_CHESTPLATE.getDefaultStack();
                case "gold", "golden" -> Items.GOLDEN_CHESTPLATE.getDefaultStack();
                case "chainmail" -> Items.CHAINMAIL_CHESTPLATE.getDefaultStack();
                case "leather" -> Items.LEATHER_CHESTPLATE.getDefaultStack();
                default -> Items.NETHERITE_CHESTPLATE.getDefaultStack();
            };
        }
        try {
            Identifier id = Identifier.tryParse(itemId);
            if (id != null) {
                Item item = Registries.ITEM.get(id);
                if (item != null && item != Items.AIR) {
                    return item.getDefaultStack();
                }
            }
        } catch (Throwable ignored) {}
        return ItemStack.EMPTY;
    }

    public static String getItemDisplayName(String itemId) {
        if (itemId == null || itemId.isEmpty()) return "";
        if (itemId.startsWith("set:")) {
            String s = itemId.substring(4).toLowerCase();
            return switch (s) {
                case "all_armor" -> "Wszystkie Sety (Każda Zbroja)";
                case "netherite" -> "Set Netherytowy (Zbroja)";
                case "diamond" -> "Set Diamentowy (Zbroja)";
                case "iron" -> "Set Żelazny (Zbroja)";
                case "gold", "golden" -> "Set Złoty (Zbroja)";
                case "chainmail" -> "Set Kolczy (Zbroja)";
                case "leather" -> "Set Skórzany (Zbroja)";
                default -> "Set " + Character.toUpperCase(s.charAt(0)) + s.substring(1);
            };
        }
        try {
            Identifier id = Identifier.tryParse(itemId);
            if (id != null) {
                Item item = Registries.ITEM.get(id);
                if (item != null && item != Items.AIR) {
                    return item.getName().getString();
                }
            }
        } catch (Exception ignored) {}
        for (String[] p : PRESET_ITEMS) {
            if (p[0].equalsIgnoreCase(itemId)) return p[1];
        }
        String clean = itemId.replace("minecraft:", "").replace("_", " ");
        if (!clean.isEmpty()) {
            return Character.toUpperCase(clean.charAt(0)) + clean.substring(1);
        }
        return itemId;
    }

    public static List<String[]> getValidEnchantmentsForItem(String itemId) {
        List<String[]> list = new ArrayList<>();
        list.add(new String[]{"", "Bez wymogu", "Powiększa każdy przedmiot tego typu"});
        list.add(new String[]{"any", "Dowolny enchant", "Powiększa tylko gdy posiada dowolne zaklęcie"});

        if (itemId == null || itemId.isEmpty()) {
            return list;
        }

        String id = itemId.replace("minecraft:", "").toLowerCase();

        boolean isSword = id.endsWith("_sword");
        boolean isMace = id.equals("mace");
        boolean isAxe = id.endsWith("_axe");
        boolean isBow = id.equals("bow");
        boolean isCrossbow = id.equals("crossbow");
        boolean isTrident = id.equals("trident");
        boolean isHelmet = id.endsWith("_helmet") || id.equals("turtle_helmet");
        boolean isChestplate = id.endsWith("_chestplate");
        boolean isLeggings = id.endsWith("_leggings");
        boolean isBoots = id.endsWith("_boots");
        boolean isPickaxe = id.endsWith("_pickaxe");
        boolean isShovel = id.endsWith("_shovel");
        boolean isHoe = id.endsWith("_hoe");
        boolean isFishingRod = id.equals("fishing_rod");
        boolean isShears = id.equals("shears");
        boolean isFlint = id.equals("flint_and_steel");
        boolean isShield = id.equals("shield");
        boolean isElytra = id.equals("elytra");
        boolean isBrush = id.equals("brush");
        boolean isBook = id.equals("book") || id.equals("enchanted_book");

        boolean isSet = id.startsWith("set:") || id.startsWith("set_") || id.contains("set") || id.equals("all_armor");
        boolean isArmor = isHelmet || isChestplate || isLeggings || isBoots || isSet;

        if (isSword) {
            list.add(new String[]{"sharpness", "Ostrość (Sharpness)", "Miecz"});
            list.add(new String[]{"fire_aspect", "Zaklęty Ogień (Fire Aspect)", "Miecz"});
            list.add(new String[]{"knockback", "Odrzut (Knockback)", "Miecz"});
            list.add(new String[]{"looting", "Grabież (Looting)", "Miecz"});
            list.add(new String[]{"sweeping_edge", "Zamaszyste Ostrze (Sweeping)", "Miecz"});
            list.add(new String[]{"smite", "Nieboszczyk (Smite)", "Miecz"});
            list.add(new String[]{"bane_of_arthropods", "Zmora Stawonogów (Bane)", "Miecz"});
            list.add(new String[]{"unbreaking", "Niezniszczalność (Unbreaking)", "Miecz"});
            list.add(new String[]{"mending", "Naprawa (Mending)", "Miecz"});
        } else if (isMace) {
            list.add(new String[]{"density", "Zagęszczenie / Gęstość (Density)", "Buława"});
            list.add(new String[]{"breach", "Wyłom (Breach)", "Buława"});
            list.add(new String[]{"wind_burst", "Podmuch Wiatru (Wind Burst)", "Buława"});
            list.add(new String[]{"fire_aspect", "Zaklęty Ogień (Fire Aspect)", "Buława"});
            list.add(new String[]{"smite", "Nieboszczyk (Smite)", "Buława"});
            list.add(new String[]{"bane_of_arthropods", "Zmora Stawonogów (Bane)", "Buława"});
            list.add(new String[]{"unbreaking", "Niezniszczalność (Unbreaking)", "Buława"});
            list.add(new String[]{"mending", "Naprawa (Mending)", "Buława"});
        } else if (isBow) {
            list.add(new String[]{"power", "Moc (Power)", "Łuk"});
            list.add(new String[]{"punch", "Uderzenie (Punch)", "Łuk"});
            list.add(new String[]{"flame", "Płomień (Flame)", "Łuk"});
            list.add(new String[]{"infinity", "Nieskończoność (Infinity)", "Łuk"});
            list.add(new String[]{"unbreaking", "Niezniszczalność (Unbreaking)", "Łuk"});
            list.add(new String[]{"mending", "Naprawa (Mending)", "Łuk"});
        } else if (isCrossbow) {
            list.add(new String[]{"quick_charge", "Szybkie Ładowanie (Quick Charge)", "Kusza"});
            list.add(new String[]{"multishot", "Wielostrzał (Multishot)", "Kusza"});
            list.add(new String[]{"piercing", "Przebijanie (Piercing)", "Kusza"});
            list.add(new String[]{"unbreaking", "Niezniszczalność (Unbreaking)", "Kusza"});
            list.add(new String[]{"mending", "Naprawa (Mending)", "Kusza"});
        } else if (isTrident) {
            list.add(new String[]{"loyalty", "Lojalność (Loyalty)", "Trójząb"});
            list.add(new String[]{"channeling", "Porażenie (Channeling)", "Trójząb"});
            list.add(new String[]{"riptide", "Torpeda (Riptide)", "Trójząb"});
            list.add(new String[]{"impaling", "Przebicie (Impaling)", "Trójząb"});
            list.add(new String[]{"unbreaking", "Niezniszczalność (Unbreaking)", "Trójząb"});
            list.add(new String[]{"mending", "Naprawa (Mending)", "Trójząb"});
        } else if (isArmor) {
            list.add(new String[]{"protection", "Ochrona (Protection)", "Zbroja"});
            list.add(new String[]{"fire_protection", "Odporność na ogień (Fire Prot)", "Zbroja"});
            list.add(new String[]{"blast_protection", "Odporność na wybuchy (Blast Prot)", "Zbroja"});
            list.add(new String[]{"projectile_protection", "Odporność na pociski (Proj Prot)", "Zbroja"});
            list.add(new String[]{"thorns", "Ciernie (Thorns)", "Zbroja"});
            if (isHelmet || isSet) {
                list.add(new String[]{"respiration", "Oddychanie (Respiration)", "Hełm"});
                list.add(new String[]{"aqua_affinity", "Wydajność podwodna (Aqua Affinity)", "Hełm"});
            }
            if (isLeggings || isSet) {
                list.add(new String[]{"swift_sneak", "Zwinne Skradanie (Swift Sneak)", "Spodnie"});
            }
            if (isBoots || isSet) {
                list.add(new String[]{"feather_falling", "Powolne Opadanie (Feather Fall)", "Buty"});
                list.add(new String[]{"depth_strider", "Głębinowy Wędrowiec (Depth Strider)", "Buty"});
                list.add(new String[]{"soul_speed", "Prędkość Dusz (Soul Speed)", "Buty"});
                list.add(new String[]{"frost_walker", "Mroźny Piechur (Frost Walker)", "Buty"});
            }
            list.add(new String[]{"unbreaking", "Niezniszczalność (Unbreaking)", "Zbroja"});
            list.add(new String[]{"mending", "Naprawa (Mending)", "Zbroja"});
        } else if (isAxe) {
            list.add(new String[]{"efficiency", "Wydajność (Efficiency)", "Siekiera"});
            list.add(new String[]{"sharpness", "Ostrość (Sharpness)", "Siekiera"});
            list.add(new String[]{"smite", "Nieboszczyk (Smite)", "Siekiera"});
            list.add(new String[]{"silk_touch", "Jedwabny Dotyk (Silk Touch)", "Siekiera"});
            list.add(new String[]{"fortune", "Szczęście (Fortune)", "Siekiera"});
            list.add(new String[]{"unbreaking", "Niezniszczalność (Unbreaking)", "Siekiera"});
            list.add(new String[]{"mending", "Naprawa (Mending)", "Siekiera"});
        } else if (isPickaxe || isShovel || isHoe) {
            list.add(new String[]{"efficiency", "Wydajność (Efficiency)", "Narzędzia"});
            list.add(new String[]{"silk_touch", "Jedwabny Dotyk (Silk Touch)", "Narzędzia"});
            list.add(new String[]{"fortune", "Szczęście (Fortune)", "Narzędzia"});
            list.add(new String[]{"unbreaking", "Niezniszczalność (Unbreaking)", "Narzędzia"});
            list.add(new String[]{"mending", "Naprawa (Mending)", "Narzędzia"});
        } else if (isFishingRod) {
            list.add(new String[]{"luck_of_the_sea", "Morska Fortuna (Luck of Sea)", "Wędka"});
            list.add(new String[]{"lure", "Przynęta (Lure)", "Wędka"});
            list.add(new String[]{"unbreaking", "Niezniszczalność (Unbreaking)", "Wędka"});
            list.add(new String[]{"mending", "Naprawa (Mending)", "Wędka"});
        } else if (isElytra || isShield || isShears || isFlint || isBrush) {
            list.add(new String[]{"unbreaking", "Niezniszczalność (Unbreaking)", "Przedmiot"});
            list.add(new String[]{"mending", "Naprawa (Mending)", "Przedmiot"});
        } else if (isBook) {
            list.addAll(ENCHANTMENTS_LIST.subList(2, ENCHANTMENTS_LIST.size()));
        }

        return list;
    }

    private static boolean enabled = true;
    private static final List<ScaleProfile> profiles = new ArrayList<>();

    public ItemScaleModule() {
        super("Item Scale", "Powiększanie leżących itemów (Totemy, Koxy, Zbroje) podczas PvP", Category.RENDER, true);
    }

    @Override
    public void setEnabled(boolean state) {
        super.setEnabled(state);
        enabled = state;
    }

    @Override
    public void toggle() {
        super.toggle();
        enabled = this.isEnabled();
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

    public static ScaleProfile addProfile(String name) {
        String id = "prof_" + (profiles.size() + 1) + "_" + System.currentTimeMillis();
        ScaleProfile newProf = new ScaleProfile(id, name != null && !name.trim().isEmpty() ? name.trim() : "Profil " + (profiles.size() + 1), true, 2.2f, 1.0f, new ArrayList<>());
        profiles.add(newProf);
        return newProf;
    }

    public static void removeProfile(int index) {
        if (index >= 0 && index < profiles.size()) {
            profiles.remove(index);
        }
    }

    public static String getEnchantmentId(net.minecraft.registry.entry.RegistryEntry<net.minecraft.enchantment.Enchantment> entry) {
        if (entry == null) return "";
        try {
            if (entry.getKey().isPresent()) {
                return entry.getKey().get().getValue().toString().toLowerCase();
            }
        } catch (Throwable ignored) {}
        try {
            String idStr = entry.getIdAsString();
            if (idStr != null && !idStr.isEmpty() && !idStr.equalsIgnoreCase("[unregistered]")) {
                return idStr.toLowerCase();
            }
        } catch (Throwable ignored) {}
        try {
            net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
            if (client != null && client.world != null) {
                Identifier regId = client.world.getRegistryManager().getOrThrow(net.minecraft.registry.RegistryKeys.ENCHANTMENT).getId(entry.value());
                if (regId != null) {
                    return regId.toString().toLowerCase();
                }
            }
        } catch (Throwable ignored) {}
        try {
            net.minecraft.text.Text desc = entry.value().description();
            if (desc != null) {
                if (desc.getContent() instanceof net.minecraft.text.TranslatableTextContent translatable) {
                    String key = translatable.getKey().toLowerCase();
                    if (key.startsWith("enchantment.minecraft.")) {
                        return "minecraft:" + key.substring("enchantment.minecraft.".length());
                    } else if (key.startsWith("enchantment.")) {
                        return key.substring("enchantment.".length());
                    }
                    return key;
                }
                String descStr = desc.getString().toLowerCase();
                for (String[] enc : ENCHANTMENTS_LIST) {
                    if (!enc[0].isEmpty() && !enc[0].equals("any")) {
                        if (descStr.contains(enc[0]) || descStr.contains(enc[1].toLowerCase())) {
                            return "minecraft:" + enc[0];
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
        return "";
    }

    public static boolean stackMatchesEnchantments(ItemStack stack, List<EnchantRequirement> requirements, boolean requireAll) {
        if (requirements == null || requirements.isEmpty()) {
            return true;
        }

        List<EnchantRequirement> activeReqs = new ArrayList<>();
        for (EnchantRequirement req : requirements) {
            if (req != null && !req.getEnchantId().isEmpty()) {
                activeReqs.add(req);
            }
        }

        if (activeReqs.isEmpty()) {
            return true;
        }

        net.minecraft.component.type.ItemEnchantmentsComponent enchants = net.minecraft.enchantment.EnchantmentHelper.getEnchantments(stack);
        boolean hasEnchants = !enchants.isEmpty();

        if (activeReqs.size() == 1 && activeReqs.get(0).getEnchantId().equalsIgnoreCase("any")) {
            return hasEnchants;
        }

        if (!hasEnchants) {
            return false;
        }

        if (requireAll) {
            for (EnchantRequirement req : activeReqs) {
                if (req.getEnchantId().equalsIgnoreCase("any")) continue;

                boolean found = false;
                String targetId = req.getEnchantId().replace("minecraft:", "").toLowerCase();

                for (it.unimi.dsi.fastutil.objects.Object2IntMap.Entry<net.minecraft.registry.entry.RegistryEntry<net.minecraft.enchantment.Enchantment>> encEntry : enchants.getEnchantmentEntries()) {
                    net.minecraft.registry.entry.RegistryEntry<net.minecraft.enchantment.Enchantment> entry = encEntry.getKey();
                    String fullId = getEnchantmentId(entry);
                    String cleanId = fullId.replace("minecraft:", "").toLowerCase();
                    if (cleanId.equals(targetId) || fullId.endsWith(":" + targetId)) {
                        int level = encEntry.getIntValue();
                        if (req.matchesLevel(level)) {
                            found = true;
                            break;
                        }
                    }
                }
                if (!found) {
                    return false;
                }
            }
            return true;
        } else {
            for (EnchantRequirement req : activeReqs) {
                if (req.getEnchantId().equalsIgnoreCase("any")) return true;

                String targetId = req.getEnchantId().replace("minecraft:", "").toLowerCase();
                for (it.unimi.dsi.fastutil.objects.Object2IntMap.Entry<net.minecraft.registry.entry.RegistryEntry<net.minecraft.enchantment.Enchantment>> encEntry : enchants.getEnchantmentEntries()) {
                    net.minecraft.registry.entry.RegistryEntry<net.minecraft.enchantment.Enchantment> entry = encEntry.getKey();
                    String fullId = getEnchantmentId(entry);
                    String cleanId = fullId.replace("minecraft:", "").toLowerCase();
                    if (cleanId.equals(targetId) || fullId.endsWith(":" + targetId)) {
                        int level = encEntry.getIntValue();
                        if (req.matchesLevel(level)) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }
    }

    public static boolean stackMatchesEnchantment(ItemStack stack, String requiredEnchantmentId) {
        if (requiredEnchantmentId == null || requiredEnchantmentId.isEmpty() || requiredEnchantmentId.equalsIgnoreCase("none")) {
            return true; // No enchant requirement, matches any item of that type
        }
        net.minecraft.component.type.ItemEnchantmentsComponent enchants = net.minecraft.enchantment.EnchantmentHelper.getEnchantments(stack);
        if (enchants.isEmpty()) {
            return false;
        }
        if (requiredEnchantmentId.equalsIgnoreCase("any")) {
            return true; // Must have any enchantment
        }
        String targetId = requiredEnchantmentId.replace("minecraft:", "").toLowerCase();
        for (net.minecraft.registry.entry.RegistryEntry<net.minecraft.enchantment.Enchantment> entry : enchants.getEnchantments()) {
            String fullId = getEnchantmentId(entry);
            String cleanId = fullId.replace("minecraft:", "").toLowerCase();
            if (cleanId.equals(targetId) || fullId.endsWith(":" + targetId)) {
                return true;
            }
        }
        return false;
    }

    public static float getScaleForItem(ItemStack stack) {
        if (!enabled || stack == null || stack.isEmpty()) {
            return 1.0f;
        }

        float bestScale = 1.0f;
        boolean matched = false;

        for (ScaleProfile prof : profiles) {
            if (!prof.isEnabled()) continue;

            for (ScaleItemEntry item : prof.getItems()) {
                if (item.matches(stack)) {
                    bestScale = Math.max(bestScale, prof.getHighlightScale());
                    matched = true;
                }
            }
        }

        if (matched) {
            return bestScale;
        }

        for (ScaleProfile prof : profiles) {
            if (prof.isEnabled()) {
                return prof.getDefaultScale();
            }
        }

        return 1.0f;
    }
}
