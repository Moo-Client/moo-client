package com.mooclient.module.modules;

import com.mooclient.module.Module;
import net.minecraft.client.MinecraftClient;

/**
 * Gamma / Fullbright module — makes everything fully lit in darkness without torches.
 */
public class FullbrightModule extends Module {

    private static boolean fullbrightActive = false;

    public FullbrightModule() {
        super("Gamma", "Widzenie w ciemności bez pochodni", Category.RENDER);
    }

    @Override
    public void onEnable() {
        fullbrightActive = true;
        markLightmapDirty();
    }

    @Override
    public void onDisable() {
        fullbrightActive = false;
        markLightmapDirty();
    }

    public static boolean isFullbrightActive() {
        return fullbrightActive;
    }

    public static void setFullbrightActive(boolean state) {
        fullbrightActive = state;
        markLightmapDirty();
    }

    private static void markLightmapDirty() {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.gameRenderer != null && client.gameRenderer.getLightmapTextureManager() != null) {
//? if <1.21.11 {
                /*client.gameRenderer.getLightmapTextureManager().enable();
*///?}
            }
        } catch (Throwable ignored) {}
    }
}
