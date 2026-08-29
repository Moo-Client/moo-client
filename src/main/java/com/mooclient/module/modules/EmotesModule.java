package com.mooclient.module.modules;

import com.mooclient.module.Module;
import com.mooclient.network.MooEmotePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.lwjgl.glfw.GLFW;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Emotes module for Moo Client.
 * Supports smooth acrobatic frontflips, backflips, and hands-up animations,
 * synchronized over the network across all Moo Client players in multiplayer.
 * Includes optional F5 third-person perspective forcing exclusively for wheel emotes
 * and full mouse button keybinding support.
 */
public class EmotesModule extends Module {

    public enum ActivationMode {
        HOLD,
        TOGGLE
    }

    public enum FlipDirection {
        NONE,
        FRONT,
        BACK
    }

    private static boolean enabled = true;

    // --- Local & Multiplayer States ---
    private static final PlayerEmoteState localPlayerState = new PlayerEmoteState(null);
    private static final Map<UUID, PlayerEmoteState> playerStates = new ConcurrentHashMap<>();

    // --- F5 Perspective Restoration (Wheel only) ---
    private static Perspective previousPerspective = null;

    // --- Keybinds and Mode ---
    private static ActivationMode mode = ActivationMode.TOGGLE;
    private static int keyCode = GLFW.GLFW_KEY_R;
    private static String keyName = "R";
    private static boolean isMouseButton = false;

    private static int frontflipKeyCode = GLFW.GLFW_KEY_V;
    private static String frontflipKeyName = "V";
    private static boolean frontflipIsMouseButton = false;

    private static int backflipKeyCode = GLFW.GLFW_KEY_B;
    private static String backflipKeyName = "B";
    private static boolean backflipIsMouseButton = false;

    private static int wheelKeyCode = GLFW.GLFW_KEY_B;
    private static String wheelKeyName = "B";
    private static boolean wheelIsMouseButton = false;

    public EmotesModule() {
        super("Emotes", "Player animations (Hands Up, Frontflip, Backflip)", Category.RENDER, true);
    }

    @Override
    public void onEnable() {
        enabled = true;
    }

    @Override
    public void onDisable() {
        enabled = false;
        localPlayerState.stopEmotes();
        restorePerspective();
        sendPayload(MooEmotePayload.TYPE_STOP);
    }

    public static void onTick() {
        // Tick local player state
        boolean wasFlipping = localPlayerState.isFlipping();
        localPlayerState.onTick();
        if (wasFlipping && !localPlayerState.isFlipping()) {
            restorePerspective();
        }

        // Tick all remote player states
        if (!playerStates.isEmpty()) {
            playerStates.entrySet().removeIf(entry -> {
                PlayerEmoteState state = entry.getValue();
                state.onTick();
                return state.isIdle();
            });
        }
    }

    // ==========================================
    // Perspective Forcing (Wheel Only)
    // ==========================================

    public static void forceF5Perspective() {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.options != null) {
                Perspective current = client.options.getPerspective();
                if (current.isFirstPerson()) {
                    previousPerspective = current;
                    client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
                }
            }
        } catch (Exception ignored) {}
    }

    public static void restorePerspective() {
        try {
            if (previousPerspective != null) {
                MinecraftClient client = MinecraftClient.getInstance();
                if (client != null && client.options != null) {
                    client.options.setPerspective(previousPerspective);
                }
                previousPerspective = null;
            }
        } catch (Exception ignored) {}
    }

    // ==========================================
    // Multiplayer State Retrieval
    // ==========================================

    public static PlayerEmoteState getLocalPlayerState() {
        return localPlayerState;
    }

    public static PlayerEmoteState getPlayerState(UUID uuid) {
        if (uuid == null) return null;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null && uuid.equals(client.player.getUuid())) {
            return localPlayerState;
        }
        return playerStates.get(uuid);
    }

    public static PlayerEmoteState getOrCreateRemoteState(UUID uuid) {
        if (uuid == null) return null;
        return playerStates.computeIfAbsent(uuid, PlayerEmoteState::new);
    }

    public static PlayerEmoteState getPlayerState(int entityId) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null && client.player.getId() == entityId) {
            return localPlayerState;
        }
        if (client.world != null) {
            Entity entity = client.world.getEntityById(entityId);
            if (entity instanceof PlayerEntity player) {
                return getPlayerState(player.getUuid());
            }
        }
        return null;
    }

    // ==========================================
    // Network Packet Sender
    // ==========================================

    private static void sendPayload(byte emoteType) {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null) {
                UUID uuid = client.player.getUuid();
                if (uuid != null) {
                    // 1. Universal Real-Time Broadcast via MQTT Broker
                    com.mooclient.network.MooNetworkHandler.sendEmoteBroadcast(uuid, emoteType);

                    // 2. Fabric Plugin Channel Broadcast (for Fabric/Proxy companion channels)
                    if (client.getNetworkHandler() != null && ClientPlayNetworking.canSend(MooEmotePayload.ID)) {
                        ClientPlayNetworking.send(new MooEmotePayload(uuid, emoteType));
                    }
                }
            }
        } catch (Throwable ignored) {
            // Safe fallback if networking is not available
        }
    }

    public static void handleIncomingPayload(UUID uuid, byte emoteType) {
        if (uuid == null) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null && uuid.equals(client.player.getUuid())) {
            return; // Ignore echo of own packet
        }

        PlayerEmoteState state = getOrCreateRemoteState(uuid);
        if (state == null) return;

        switch (emoteType) {
            case MooEmotePayload.TYPE_HANDS_UP_START -> state.triggerHandsUp(true);
            case MooEmotePayload.TYPE_HANDS_UP_STOP -> state.triggerHandsUp(false);
            case MooEmotePayload.TYPE_FRONTFLIP -> state.triggerFrontflip();
            case MooEmotePayload.TYPE_BACKFLIP -> state.triggerBackflip();
            case MooEmotePayload.TYPE_STOP -> state.stopEmotes();
        }
    }

    // ==========================================
    // Local Action Triggers
    // ==========================================

    public static void setHandsUp(boolean state) {
        if (!enabled && state) return;
        localPlayerState.triggerHandsUp(state);
        sendPayload(state ? MooEmotePayload.TYPE_HANDS_UP_START : MooEmotePayload.TYPE_HANDS_UP_STOP);
    }

    public static void toggleHandsUp() {
        if (!enabled) return;
        setHandsUp(!localPlayerState.isHandsUp);
    }

    public static boolean isHandsUp() {
        return enabled && localPlayerState.isHandsUp;
    }

    public static void triggerFrontflip() {
        if (!enabled) return;
        localPlayerState.triggerFrontflip();
        sendPayload(MooEmotePayload.TYPE_FRONTFLIP);
    }

    public static void triggerBackflip() {
        if (!enabled) return;
        localPlayerState.triggerBackflip();
        sendPayload(MooEmotePayload.TYPE_BACKFLIP);
    }

    public static void triggerFrontflipFromWheel() {
        forceF5Perspective();
        triggerFrontflip();
    }

    public static void triggerBackflipFromWheel() {
        forceF5Perspective();
        triggerBackflip();
    }

    public static void stopEmotesFromWheel() {
        setHandsUp(false);
        restorePerspective();
    }

    public static boolean isFlipping() {
        return enabled && localPlayerState.isFlipping();
    }

    public static float getInterpolatedProgress(float tickDelta) {
        return localPlayerState.getInterpolatedHandsUpProgress(tickDelta);
    }

    public static float getFlipRotationDegrees(float tickDelta) {
        return localPlayerState.getFlipRotationDegrees(tickDelta);
    }

    public static float getFlipJumpHeight(float tickDelta) {
        return localPlayerState.getFlipJumpHeight(tickDelta);
    }

    public static float getFlipTuckFactor(float tickDelta) {
        return localPlayerState.getFlipTuckFactor(tickDelta);
    }

    // ==========================================
    // Module State, Keybinds & Config
    // ==========================================

    public static boolean isEmotesEnabled() {
        return enabled;
    }

    public static void setEmotesEnabled(boolean state) {
        enabled = state;
        if (!state) {
            localPlayerState.stopEmotes();
            restorePerspective();
            sendPayload(MooEmotePayload.TYPE_STOP);
        }
    }

    public static ActivationMode getMode() {
        return mode;
    }

    public static void setMode(ActivationMode newMode) {
        mode = newMode;
    }

    public static void cycleMode() {
        ActivationMode[] values = ActivationMode.values();
        mode = values[(mode.ordinal() + 1) % values.length];
    }

    // Hands Up Keybind
    public static int getKeyCode() {
        return keyCode;
    }

    public static String getKeyName() {
        return keyName;
    }

    public static boolean isMouseButton() {
        return isMouseButton;
    }

    public static void setKeybind(int key, String name) {
        setKeybind(key, name, false);
    }

    public static void setKeybind(int key, String name, boolean isMouse) {
        keyCode = key;
        keyName = name;
        isMouseButton = isMouse;
    }

    // Frontflip Keybind
    public static int getFrontflipKeyCode() {
        return frontflipKeyCode;
    }

    public static String getFrontflipKeyName() {
        return frontflipKeyName;
    }

    public static boolean isFrontflipMouseButton() {
        return frontflipIsMouseButton;
    }

    public static void setFrontflipKeybind(int key, String name) {
        setFrontflipKeybind(key, name, false);
    }

    public static void setFrontflipKeybind(int key, String name, boolean isMouse) {
        frontflipKeyCode = key;
        frontflipKeyName = name;
        frontflipIsMouseButton = isMouse;
    }

    // Backflip Keybind
    public static int getBackflipKeyCode() {
        return backflipKeyCode;
    }

    public static String getBackflipKeyName() {
        return backflipKeyName;
    }

    public static boolean isBackflipMouseButton() {
        return backflipIsMouseButton;
    }

    public static void setBackflipKeybind(int key, String name) {
        setBackflipKeybind(key, name, false);
    }

    public static void setBackflipKeybind(int key, String name, boolean isMouse) {
        backflipKeyCode = key;
        backflipKeyName = name;
        backflipIsMouseButton = isMouse;
    }

    // Emote Radial Wheel Keybind
    public static int getWheelKeyCode() {
        return wheelKeyCode;
    }

    public static String getWheelKeyName() {
        return wheelKeyName;
    }

    public static boolean isWheelMouseButton() {
        return wheelIsMouseButton;
    }

    public static void setWheelKeybind(int key, String name) {
        setWheelKeybind(key, name, false);
    }

    public static void setWheelKeybind(int key, String name, boolean isMouse) {
        wheelKeyCode = key;
        wheelKeyName = name;
        wheelIsMouseButton = isMouse;
    }
}
