package com.mooclient.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
//? if <1.21.11 {
/*import net.minecraft.item.SwordItem;
import net.minecraft.item.AxeItem;
*///?}

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks and estimates real-time player armor durability in multiplayer.
 * Since vanilla and Paper/Spigot servers do not broadcast equipment updates
 * on every durability hit during combat, this tracker simulates and updates
 * opponent armor damage instantly upon every hit, damage packet, and attack event,
 * while also syncing exact values between Moo Client peers.
 */
public class PlayerArmorDurabilityTracker {

    public static class TrackedSlot {
        public Item item;
        public float currentDurability;
        public int maxDurability;
        public int unbreakingLevel;

        public TrackedSlot(Item item, float currentDurability, int maxDurability, int unbreakingLevel) {
            this.item = item;
            this.currentDurability = currentDurability;
            this.maxDurability = maxDurability;
            this.unbreakingLevel = unbreakingLevel;
        }
    }

    public static class TrackedPlayerArmor {
        public final int entityId;
        public String username;
        public UUID uuid;
        public long lastDamageTimeMs;
        // 0: HEAD, 1: CHEST, 2: LEGS, 3: FEET
        public final TrackedSlot[] slots = new TrackedSlot[4];

        public TrackedPlayerArmor(int entityId, String username, UUID uuid) {
            this.entityId = entityId;
            this.username = username;
            this.uuid = uuid;
        }
    }

    private static final Map<Integer, TrackedPlayerArmor> BY_ENTITY_ID = new ConcurrentHashMap<>();
    private static final Map<String, TrackedPlayerArmor> BY_USERNAME = new ConcurrentHashMap<>();
    private static final Map<UUID, TrackedPlayerArmor> BY_UUID = new ConcurrentHashMap<>();

    private static int getSlotIndex(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> 0;
            case CHEST -> 1;
            case LEGS -> 2;
            case FEET -> 3;
            default -> -1;
        };
    }

    private static int extractUnbreakingLevel(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        try {
            ItemEnchantmentsComponent enchants = stack.getEnchantments();
            if (enchants != null && !enchants.isEmpty()) {
                for (var entry : enchants.getEnchantmentEntries()) {
                    String path = entry.getKey().getKey().map(k -> k.getValue().getPath()).orElse("");
                    if (path.contains("unbreaking")) {
                        return entry.getIntValue();
                    }
                }
            }
        } catch (Throwable ignored) {}
        return 0;
    }

    public static void registerTrackedPlayer(TrackedPlayerArmor tracked) {
        if (tracked == null) return;
        BY_ENTITY_ID.put(tracked.entityId, tracked);
        if (tracked.username != null && !tracked.username.isEmpty()) {
            BY_USERNAME.put(tracked.username.toLowerCase(), tracked);
        }
        if (tracked.uuid != null) {
            BY_UUID.put(tracked.uuid, tracked);
        }
    }

    /**
     * Resolves or registers a TrackedPlayerArmor for the given player entity.
     */
    public static TrackedPlayerArmor getOrCreate(PlayerEntity player) {
        if (player == null) return null;
        int id = player.getId();
        TrackedPlayerArmor tracked = BY_ENTITY_ID.get(id);
        if (tracked == null) {
            String name = player.getName() != null ? player.getName().getString() : "";
            UUID uuid = player.getUuid();
            tracked = new TrackedPlayerArmor(id, name, uuid);
            registerTrackedPlayer(tracked);
        }
        return tracked;
    }

    private static volatile int lastAttackedTargetId = -1;
    private static volatile ItemStack lastAttackedWeapon = null;
    private static volatile boolean lastAttackWasCrit = false;
    private static volatile long lastAttackAttemptTimeMs = 0;

    /**
     * Records that the local player attempted to attack a target.
     * We do NOT immediately decrement armor. Instead, we wait for the server
     * to confirm the hit via onEntityDamage or onEntityStatus (status 2).
     * If the target is on spawn or protected, the attack is cancelled by the server,
     * no damage packet arrives, and their armor is never falsely decremented!
     */
    public static void recordLocalAttackAttempt(int targetEntityId, ItemStack weapon, boolean isCrit) {
        lastAttackedTargetId = targetEntityId;
        lastAttackedWeapon = (weapon != null && !weapon.isEmpty()) ? weapon.copy() : null;
        lastAttackWasCrit = isCrit;
        lastAttackAttemptTimeMs = System.currentTimeMillis();
    }

    /**
     * Gets remaining durability for an armor piece.
     * For the local player, always uses authoritative local inventory.
     * For opponents, returns real-time tracked durability that updates on confirmed hits.
     */
    public static int getRemainingDurability(int entityId, PlayerEntity player, EquipmentSlot slot, ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.isDamageable() || stack.getMaxDamage() <= 0) {
            if (entityId > 0) {
                int slotIdx = getSlotIndex(slot);
                if (slotIdx >= 0) {
                    TrackedPlayerArmor tr = BY_ENTITY_ID.get(entityId);
                    if (tr != null) {
                        tr.slots[slotIdx] = null;
                    }
                }
            }
            return -1;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.player != null) {
            if ((player != null && player.getId() == client.player.getId()) || (entityId > 0 && entityId == client.player.getId())) {
                return Math.max(0, stack.getMaxDamage() - stack.getDamage());
            }
        }

        int slotIdx = getSlotIndex(slot);
        if (slotIdx < 0) return Math.max(0, stack.getMaxDamage() - stack.getDamage());

        TrackedPlayerArmor tracked = null;
        if (entityId > 0) {
            tracked = BY_ENTITY_ID.get(entityId);
        }
        if (tracked == null && player != null) {
            tracked = getOrCreate(player);
        }
        if (tracked == null && entityId > 0 && client != null && client.world != null) {
            net.minecraft.entity.Entity ent = client.world.getEntityById(entityId);
            if (ent instanceof PlayerEntity pe) {
                tracked = getOrCreate(pe);
            }
        }
        if (tracked == null) {
            return Math.max(0, stack.getMaxDamage() - stack.getDamage());
        }

        TrackedSlot ts = tracked.slots[slotIdx];
        Item item = stack.getItem();
        int max = stack.getMaxDamage();

        if (ts == null || ts.item != item || ts.maxDurability != max) {
            int initDamage = stack.getDamage();
            float initDur = (initDamage > 0) ? (max - initDamage) : (float) max;
            int unbreaking = extractUnbreakingLevel(stack);
            ts = new TrackedSlot(item, initDur, max, unbreaking);
            tracked.slots[slotIdx] = ts;
        } else {
            // If server packet gave explicit damage (> 0), adopt the real server value!
            if (stack.getDamage() > 0) {
                int serverRemaining = max - stack.getDamage();
                ts.currentDurability = serverRemaining;
            }
        }

        return Math.max(0, Math.round(ts.currentDurability));
    }

    public static int getRemainingDurability(PlayerEntity player, EquipmentSlot slot, ItemStack stack) {
        return getRemainingDurability(player != null ? player.getId() : -1, player, slot, stack);
    }

    /**
     * Records damage dealt to a player entity and decrements armor durability.
     * Only invoked upon server-confirmed damage events (onEntityDamage / onEntityStatus).
     */
    public static void recordDamage(PlayerEntity player, float rawAmount, DamageSource source) {
        if (player == null) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.player != null && player.getId() == client.player.getId()) {
            // Local player is handled by server inventory updates
            return;
        }

        TrackedPlayerArmor tracked = getOrCreate(player);
        if (tracked == null) return;

        long now = System.currentTimeMillis();
        // Prevent duplicate decrement during Minecraft's 10-tick (500ms) hurt immunity window
        if (now - tracked.lastDamageTimeMs < 250) {
            return;
        }
        tracked.lastDamageTimeMs = now;

        // Check if damage source bypasses armor in Minecraft (poison, wither, starving, drown, void, magic)
        if (source != null) {
            try {
                if (source.isIn(net.minecraft.registry.tag.DamageTypeTags.BYPASSES_ARMOR)) {
                    return;
                }
            } catch (Throwable ignored) {}
        }

        float calculatedRawDamage = rawAmount;
        if (calculatedRawDamage <= 0 && (now - lastAttackAttemptTimeMs < 600) && lastAttackedTargetId == player.getId()) {
            calculatedRawDamage = 7.0f; // Base unarmed hit
            if (lastAttackedWeapon != null && !lastAttackedWeapon.isEmpty()) {
//? if >=1.21.11 {
                if (lastAttackedWeapon.isIn(net.minecraft.registry.tag.ItemTags.SWORDS)) {
                    calculatedRawDamage = 11.0f; // Typical sword in PvP
                } else if (lastAttackedWeapon.isIn(net.minecraft.registry.tag.ItemTags.AXES)) {
                    calculatedRawDamage = 14.0f; // Typical axe in PvP
                }
//?} else {
                /*Item item = lastAttackedWeapon.getItem();
                if (item instanceof SwordItem) {
                    calculatedRawDamage = 11.0f; // Typical sword in PvP
                } else if (item instanceof AxeItem) {
                    calculatedRawDamage = 14.0f; // Typical axe in PvP
                }
*///?}
            }
            if (lastAttackWasCrit) {
                calculatedRawDamage *= 1.5f;
            }
            lastAttackedTargetId = -1;
        }

        // Calculate raw armor damage
        float damageToArmor;
        if (calculatedRawDamage > 0) {
            damageToArmor = Math.max(1.0f, calculatedRawDamage / 4.0f);
        } else if (source != null) {
            if (source.isOf(DamageTypes.FALL)) {
                // Fall damage only damages boots
                damageSlot(tracked, EquipmentSlot.FEET, player, (float) Math.max(1.0f, player.fallDistance / 4.0f));
                return;
            } else if (source.isOf(DamageTypes.EXPLOSION) || source.isOf(DamageTypes.PLAYER_EXPLOSION)) {
                damageToArmor = 15.0f;
            } else if (source.isOf(DamageTypes.THORNS)) {
                damageToArmor = 2.0f;
            } else {
                damageToArmor = 2.25f; // Standard Netherite sword hit (~9 raw damage / 4)
            }
        } else {
            damageToArmor = 2.25f;
        }

        // Apply to all 4 armor slots
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            damageSlot(tracked, slot, player, damageToArmor);
        }
    }

    private static void damageSlot(TrackedPlayerArmor tracked, EquipmentSlot slot, PlayerEntity player, float armorDamage) {
        int slotIdx = getSlotIndex(slot);
        if (slotIdx < 0) return;

        ItemStack stack = player.getEquippedStack(slot);
        if (stack == null || stack.isEmpty() || !stack.isDamageable() || stack.getMaxDamage() <= 0) {
            tracked.slots[slotIdx] = null;
            return;
        }

        TrackedSlot ts = tracked.slots[slotIdx];
        int max = stack.getMaxDamage();
        if (ts == null || ts.item != stack.getItem() || ts.maxDurability != max) {
            int initDamage = stack.getDamage();
            float initDur = (initDamage > 0) ? (max - initDamage) : (float) max;
            ts = new TrackedSlot(stack.getItem(), initDur, max, extractUnbreakingLevel(stack));
            tracked.slots[slotIdx] = ts;
        }

        // Unbreaking reduction factor:
        float factor = switch (ts.unbreakingLevel) {
            case 3 -> 0.40f;
            case 2 -> 0.55f;
            case 1 -> 0.70f;
            default -> 1.00f;
        };

        float loss = Math.max(0.85f, armorDamage * factor);
        ts.currentDurability = Math.max(0.0f, ts.currentDurability - loss);
    }

    /**
     * Called when local player attacks another player.
     * Records the pending attack attempt without immediately decrementing durability.
     */
    public static void recordLocalAttack(PlayerEntity targetPlayer, ItemStack weapon) {
        if (targetPlayer == null) return;
        MinecraftClient client = MinecraftClient.getInstance();
        boolean isCrit = false;
        if (client != null && client.player != null) {
            isCrit = client.player.fallDistance > 0.0f && !client.player.isOnGround() && !client.player.isClimbing() && !client.player.isTouchingWater();
        }
        recordLocalAttackAttempt(targetPlayer.getId(), weapon, isCrit);
    }

    /**
     * Called on entity death to clear tracked armor state.
     */
    public static void clearEntity(int entityId) {
        TrackedPlayerArmor tracked = BY_ENTITY_ID.remove(entityId);
        if (tracked != null) {
            if (tracked.username != null) BY_USERNAME.remove(tracked.username.toLowerCase());
            if (tracked.uuid != null) BY_UUID.remove(tracked.uuid);
        }
        com.mooclient.module.modules.ArmorModule.clearNametagArmorCache(entityId);
    }

    /**
     * Sets exact durability values from Moo Client presence packets.
     */
    public static void setExactDurability(String username, UUID uuid, int head, int chest, int legs, int feet) {
        TrackedPlayerArmor tracked = null;
        if (uuid != null) tracked = BY_UUID.get(uuid);
        if (tracked == null && username != null) tracked = BY_USERNAME.get(username.toLowerCase());

        if (tracked != null) {
            setSlotExact(tracked.slots[0], head);
            setSlotExact(tracked.slots[1], chest);
            setSlotExact(tracked.slots[2], legs);
            setSlotExact(tracked.slots[3], feet);
        }
    }

    private static void setSlotExact(TrackedSlot slot, int exactVal) {
        if (slot != null && exactVal >= 0) {
            slot.currentDurability = Math.min(slot.maxDurability, exactVal);
        }
    }
}
