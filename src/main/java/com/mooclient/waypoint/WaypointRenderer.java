package com.mooclient.waypoint;

import com.mooclient.module.modules.WaypointsModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector4f;

import java.util.List;

/**
 * Handles in-game HUD waypoint rendering with mathematically exact 3D-to-2D clip space projection,
 * full view-bobbing / dynamic-FOV / zoom motion compensation, customizable scaling, background toggle, text shadow, and zero-GC optimization.
 */
public class WaypointRenderer {

    public static final Matrix4f worldProjectionMatrix = new Matrix4f();

    // Reusable math objects to eliminate memory allocations in the render loop
    private static final Quaternionf tempCamRotInv = new Quaternionf();
    private static final Vector4f tempPos = new Vector4f();

    public static void init() {
        // No-op
    }

    /**
     * Projects and renders all active waypoints directly onto the game HUD using the exact world projection matrix.
     */
    public static void renderHudWaypoints(DrawContext context, MinecraftClient client, float tickDelta) {
        if (client == null || client.world == null || client.player == null) return;
        if (!WaypointsModule.isWaypointsEnabled()) return;

        List<Waypoint> activeWaypoints = WaypointManager.getInstance().getWaypointsForCurrentWorld(client);
        if (activeWaypoints.isEmpty()) return;

        Camera camera = client.gameRenderer.getCamera();
        if (camera == null) return;

        Vec3d cameraPos = camera.getPos();
        tempCamRotInv.set(camera.getRotation()).conjugate();

        int screenW = context.getScaledWindowWidth();
        int screenH = context.getScaledWindowHeight();

        float userScale = WaypointsModule.getScale();
        boolean showDist = WaypointsModule.isShowDistance();
        boolean showBg = WaypointsModule.isShowBackground();
        boolean shadow = WaypointsModule.isTextShadow();

        for (int i = 0; i < activeWaypoints.size(); i++) {
            Waypoint wp = activeWaypoints.get(i);
            if (!wp.isVisible()) continue;

            double wpX = wp.getX() + 0.5;
            double wpY = wp.getY() + 1.2;
            double wpZ = wp.getZ() + 0.5;

            double dx = wpX - cameraPos.x;
            double dy = wpY - cameraPos.y;
            double dz = wpZ - cameraPos.z;

            // 1. Transform world coordinates relative to camera orientation
            tempPos.set((float) dx, (float) dy, (float) dz, 1.0f);
            tempPos.rotate(tempCamRotInv);

            // 2. Transform into clip space using Minecraft's exact world projection matrix (with bobbing, tilt & zoom)
            worldProjectionMatrix.transform(tempPos);

            // 3. Skip points behind camera
            if (tempPos.w <= 0.05f) continue;

            // 4. Normalized Device Coordinates -> HUD Screen Space
            float ndcX = tempPos.x / tempPos.w;
            float ndcY = tempPos.y / tempPos.w;

            int sx = Math.round((ndcX + 1.0f) * 0.5f * screenW);
            int sy = Math.round((1.0f - ndcY) * 0.5f * screenH);

            // Fast rejection: Skip waypoints outside screen view bounds
            if (sx < -150 || sx > screenW + 150 || sy < -100 || sy > screenH + 100) continue;

            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            String name = wp.getName();
            int distInt = (int) Math.round(distance);
            String distStr = distInt >= 1000 ? String.format(java.util.Locale.US, "%.1fkm", distInt / 1000.0) : distInt + "m";

            int nameW = client.textRenderer.getWidth(name);
            int distW = client.textRenderer.getWidth(distStr);

            int contentW = Math.max(nameW, showDist ? distW : 0);
            int boxW = contentW + 10;
            int boxH = showDist ? 22 : 13;
            int halfW = boxW / 2;
            int halfH = boxH / 2;

            int wpColor = wp.getColor() | 0xFF000000;

            context.getMatrices().push();
            context.getMatrices().translate(sx, sy, 0);
            if (userScale != 1.0f) {
                context.getMatrices().scale(userScale, userScale, 1.0f);
            }

            int boxX = -halfW;
            int boxY = -halfH;

            if (showBg) {
                // Draw dark background pill
                context.fill(boxX, boxY, boxX + boxW, boxY + boxH, 0x880C0C14);
                // Draw colored accent top border
                context.fill(boxX, boxY, boxX + boxW, boxY + 1, wpColor);
                // Draw borders
                context.fill(boxX, boxY, boxX + 1, boxY + boxH, 0x44FFFFFF);
                context.fill(boxX + boxW - 1, boxY, boxX + boxW, boxY + boxH, 0x44FFFFFF);
                context.fill(boxX, boxY + boxH - 1, boxX + boxW, boxY + boxH, 0x44FFFFFF);
            }

            // Top Line: Waypoint Name
            int nameX = -nameW / 2;
            int nameY = showBg ? (boxY + 2) : (showDist ? -10 : -4);
            context.drawText(client.textRenderer, name, nameX, nameY, wpColor, shadow);

            // Bottom Line: Distance in meters
            if (showDist) {
                int distX = -distW / 2;
                int distY = showBg ? (boxY + 11) : (nameY + 10);
                context.drawText(client.textRenderer, distStr, distX, distY, 0xFFE0E0E0, shadow);
            }

            context.getMatrices().pop();
        }
    }
}
