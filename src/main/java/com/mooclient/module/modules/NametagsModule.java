package com.mooclient.module.modules;

import com.mooclient.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Nametags Module.
 * Always shows own nametag in 3rd person / Freelook.
 * Displays colorful latency (ping) indicators (BESIDE or ABOVE player heads).
 * Option to toggle showing ping on self (showSelfPing).
 * Displays authentic Lunar/Badlion style Moo Client logo badge before nicknames (always active).
 * Option to remove background behind nametags.
 * Option to enable text shadow.
 */
public class NametagsModule extends Module {

    public enum PingPosition {
        BESIDE("Obok / Beside"),
        ABOVE("Nad / Above");

        private final String displayName;

        PingPosition(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    private static boolean enabled = false;
    private static boolean showLogo = true;
    private static boolean showPing = true;
    private static boolean showSelfPing = false; // Default false as requested
    private static PingPosition pingPosition = PingPosition.BESIDE;
    private static boolean removeBackground = false;
    private static boolean textShadow = true;

    public NametagsModule() {
        super("Nametags", "Wyświetla nicki, logo i kolorowy ping nad graczami", Category.RENDER, false);
    }

    @Override
    public void onEnable() {
        enabled = true;
    }

    @Override
    public void onDisable() {
        enabled = false;
    }

    public static boolean isNametagsEnabled() {
        return enabled;
    }

    public static void setNametagsEnabled(boolean state) {
        enabled = state;
        com.mooclient.module.ModuleManager.getInstance().getModule("Nametags").ifPresent(m -> {
            if (m.isEnabled() != state) {
                m.setEnabled(state);
            }
        });
    }

    public static boolean isShowLogo() {
        return showLogo;
    }

    public static void setShowLogo(boolean state) {
        showLogo = state;
    }

    public static void toggleShowLogo() {
        showLogo = !showLogo;
    }

    public static boolean isShowPing() {
        return showPing;
    }

    public static void setShowPing(boolean state) {
        showPing = state;
    }

    public static void toggleShowPing() {
        showPing = !showPing;
    }

    public static boolean isShowSelfPing() {
        return showSelfPing;
    }

    public static void setShowSelfPing(boolean state) {
        showSelfPing = state;
    }

    public static void toggleShowSelfPing() {
        showSelfPing = !showSelfPing;
    }

    public static PingPosition getPingPosition() {
        return pingPosition;
    }

    public static void setPingPosition(PingPosition pos) {
        pingPosition = pos;
    }

    public static void cyclePingPosition() {
        pingPosition = (pingPosition == PingPosition.BESIDE) ? PingPosition.ABOVE : PingPosition.BESIDE;
    }

    public static boolean isRemoveBackground() {
        return removeBackground;
    }

    public static void setRemoveBackground(boolean state) {
        removeBackground = state;
    }

    public static void toggleRemoveBackground() {
        removeBackground = !removeBackground;
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

    /**
     * Checks whether the target entity represents the local client player.
     */
    public static boolean isLocalPlayer(int entityId, String playerName) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return false;
        if (entityId > 0 && client.player.getId() == entityId) return true;
        if (playerName != null && !playerName.trim().isEmpty()) {
            if (client.getSession() != null && playerName.equalsIgnoreCase(client.getSession().getUsername())) {
                return true;
            }
            if (client.player.getName() != null && playerName.equalsIgnoreCase(client.player.getName().getString())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Retrieves colored latency Text indicator for the given entity ID and playerName.
     */
    public static Text getPingText(int entityId, String playerName) {
        if (!enabled || !showPing) {
            return null;
        }

        // Check if self-ping is disabled
        if (!showSelfPing && isLocalPlayer(entityId, playerName)) {
            return null;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getNetworkHandler() == null) {
            return null;
        }

        int ping = -1;

        // 1. Try by entityId in world -> player UUID
        if (client.world != null) {
            Entity entity = client.world.getEntityById(entityId);
            if (entity instanceof PlayerEntity player && player.getUuid() != null) {
                PlayerListEntry entry = client.getNetworkHandler().getPlayerListEntry(player.getUuid());
                if (entry != null) {
                    ping = entry.getLatency();
                }
            }
        }

        // 2. Fallback: Search player list by username
        if (ping < 0 && playerName != null && !playerName.trim().isEmpty()) {
            for (PlayerListEntry entry : client.getNetworkHandler().getPlayerList()) {
                if (entry.getProfile() != null && playerName.equalsIgnoreCase(entry.getProfile().getName())) {
                    ping = entry.getLatency();
                    break;
                }
            }
        }

        if (ping < 0) {
            return null;
        }

        Formatting pingColor;
        if (ping <= 50) {
            pingColor = Formatting.GREEN;      // §a (0-50ms)
        } else if (ping <= 100) {
            pingColor = Formatting.DARK_GREEN; // §2 (51-100ms)
        } else if (ping <= 150) {
            pingColor = Formatting.YELLOW;     // §e (101-150ms)
        } else if (ping <= 250) {
            pingColor = Formatting.GOLD;       // §6 (151-250ms)
        } else {
            pingColor = Formatting.RED;        // §c (250ms+)
        }

        return Text.literal("[" + ping + "ms]").formatted(pingColor);
    }

    public static Text getPingText(int entityId) {
        return getPingText(entityId, null);
    }

    /**
     * Formats player nametag with colorful latency indicator when in BESIDE mode.
     * Prevents double ping rendering if server already has an inline ping indicator.
     */
    public static Text formatNametag(Text originalText, int entityId, String playerName) {
        if (!enabled || originalText == null) {
            return originalText;
        }

        // If self ping is disabled, do not append ping to local player
        if (!showSelfPing && isLocalPlayer(entityId, playerName)) {
            return originalText;
        }

        String raw = originalText.getString();
        boolean hasServerPing = raw != null && raw.matches(".*\\[\\d+\\s*ms\\].*");

        if (showPing && pingPosition == PingPosition.BESIDE) {
            if (!hasServerPing) {
                Text pingText = getPingText(entityId, playerName);
                if (pingText != null) {
                    return originalText.copy().append(Text.literal(" ")).append(pingText);
                }
            }
            return originalText;
        }

        if (showPing && pingPosition == PingPosition.ABOVE) {
            // In ABOVE mode: if the server already embedded "[XXms]" in the nametag,
            // remove that duplicate text from the nametag line so it's cleanly shown ONLY above!
            if (hasServerPing) {
                String cleaned = raw.replaceAll("\\s*\\[\\d+\\s*ms\\]", "");
                return Text.literal(cleaned).setStyle(originalText.getStyle());
            }
        }

        return originalText;
    }

    public static Text formatNametag(Text originalText, int entityId) {
        return formatNametag(originalText, entityId, null);
    }
}
