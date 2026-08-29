package com.mooclient.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Renders realistic real-time 3D player models performing actual in-game emotes,
 * properly upright in GUI space, jumping and flipping cleanly in place around the waist.
 */
public class EmotePreviewRenderer {

    public static void render(DrawContext context, String emoteId, int cx, int cy, int size, boolean isHovered) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null) return;

        long timeMs = System.currentTimeMillis();
        float loopDurationMs = 1700.0f; // 1.7s per full cycle
        float t = (timeMs % (long) loopDurationMs) / loopDurationMs; // 0.0 to 1.0

        float jumpY = 0.0f;
        float pitchAngle = 0.0f;

        // Realistic cycle: 12% prep standing -> 76% jump & flip in air -> 12% landing standing
        if (t >= 0.10f && t <= 0.90f) {
            float f = (t - 0.10f) / 0.80f; // 0.0 to 1.0 during active flip
            float eased = (float) (0.5 - 0.5 * Math.cos(f * Math.PI));
            jumpY = (float) (Math.sin(f * Math.PI) * 0.95f); // Crisp in-air jump height

            if ("frontflip".equalsIgnoreCase(emoteId)) {
                pitchAngle = eased * 360.0f;
            } else if ("backflip".equalsIgnoreCase(emoteId)) {
                pitchAngle = -eased * 360.0f;
            }
        }

        // Waist pivot compensation: keeps rotation anchored at waist (0.9m)
        double pitchRad = Math.toRadians(pitchAngle);
        float pivotY = 0.9f;
        float transY = jumpY + (float) (pivotY - pivotY * Math.cos(pitchRad));
        float transZ = (float) (-pivotY * Math.sin(pitchRad));

        // 1. rotationZ(PI) MUST be first to make entity upright in GUI coordinate space
        // 2. rotateY for 3/4 isometric perspective
        // 3. rotateX for the pitch flip
        Quaternionf bodyRotation = new Quaternionf()
                .rotationZ((float) Math.PI)
                .rotateY((float) Math.toRadians(isHovered ? -35.0f : -25.0f))
                .rotateX((float) pitchRad);

        Vector3f translation = new Vector3f(0.0f, transY, transZ);
        float scale = size * 0.50f;

        // Feet positioned at cy + (scale * 0.85f), placing waist centered at (cx, cy)
        float renderY = cy + (scale * 0.85f);

        try {
            InventoryScreen.drawEntity(context, (float) cx, renderY, scale, translation, bodyRotation, null, player);
        } catch (Throwable ignored) {
        }
    }
}
