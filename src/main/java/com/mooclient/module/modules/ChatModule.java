package com.mooclient.module.modules;

import com.mooclient.module.Module;
import net.minecraft.util.math.MathHelper;

/**
 * Chat Module for Moo Client.
 * - Transparent Chat Background (option to remove dark background behind chat)
 * - Unlimited Chat History (extends 100 lines limit to unlimited)
 * - Smooth Chat Animation (smooth sliding transition when new messages arrive)
 */
public class ChatModule extends Module {

    private static boolean enabled = true;
    private static boolean transparentBackground = false;
    private static boolean unlimitedChat = true;
    private static boolean smoothChat = true;
    private static boolean textShadow = true;

    // Smooth chat animation state
    private static float animOffset = 0.0f;

    public ChatModule() {
        super("Chat", "Ulepszenia czatu: przezroczystość, cień tekstu, nielimitowana historia, animacja", Category.RENDER, true);
    }

    @Override
    public void onEnable() {
        enabled = true;
    }

    @Override
    public void onDisable() {
        enabled = false;
        animOffset = 0.0f;
    }

    public static boolean isModuleEnabled() {
        return enabled;
    }

    public static void setModuleEnabled(boolean state) {
        enabled = state;
        com.mooclient.module.ModuleManager.getInstance().getModule("Chat").ifPresent(m -> {
            if (m.isEnabled() != state) {
                m.setEnabled(state);
            }
        });
    }

    public static boolean isTransparentBackground() {
        return enabled && transparentBackground;
    }

    public static void setTransparentBackground(boolean state) {
        transparentBackground = state;
    }

    public static void toggleTransparentBackground() {
        transparentBackground = !transparentBackground;
    }

    public static boolean isUnlimitedChat() {
        return enabled && unlimitedChat;
    }

    public static void setUnlimitedChat(boolean state) {
        unlimitedChat = state;
    }

    public static void toggleUnlimitedChat() {
        unlimitedChat = !unlimitedChat;
    }

    public static boolean isSmoothChat() {
        return enabled && smoothChat;
    }

    public static void setSmoothChat(boolean state) {
        smoothChat = state;
    }

    public static void toggleSmoothChat() {
        smoothChat = !smoothChat;
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

    public static void onMessageAdded() {
        if (enabled && smoothChat) {
            animOffset = 9.0f;
        }
    }

    public static void updateAnimation(float delta) {
        if (animOffset > 0.001f) {
            animOffset = MathHelper.lerp(delta * 0.45f, animOffset, 0.0f);
        } else {
            animOffset = 0.0f;
        }
    }

    public static float getAnimOffset() {
        return (enabled && smoothChat) ? animOffset : 0.0f;
    }
}
