package com.mooclient.interaction;

import com.mooclient.util.MooUserManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Wyszukiwanie celu interakcji w stożku wzroku gracza (Target Cone).
 * Kąt: ~18° FOV, Zasięg: dokładnie do 3.0 bloków.
 */
public class InteractionTargeting {

    public static final double MAX_DISTANCE = 3.0; // Dokładnie 3.0 bloki
    public static final double MAX_CONE_ANGLE_DEG = 18.0; // Stożek 18 stopni

    public static class TargetResult {
        public final PlayerEntity player;
        public final double distance;
        public final double angleDeg;
        public final boolean isMooUser;

        public TargetResult(PlayerEntity player, double distance, double angleDeg, boolean isMooUser) {
            this.player = player;
            this.distance = distance;
            this.angleDeg = angleDeg;
            this.isMooUser = isMooUser;
        }
    }

    /**
     * Wyszukuje najlepszego gracza pod celownikiem spełniającego kryteria stożka i dystansu.
     */
    public static TargetResult findLookTarget(MinecraftClient client) {
        if (client == null || client.player == null || client.world == null) {
            return null;
        }

        ClientPlayerEntity localPlayer = client.player;
        Vec3d eyePos = localPlayer.getEyePos();
        Vec3d lookVec = localPlayer.getRotationVec(1.0f).normalize();

        List<? extends PlayerEntity> nearbyPlayers = client.world.getPlayers();
        List<TargetResult> candidates = new ArrayList<>();

        for (PlayerEntity other : nearbyPlayers) {
            if (other == localPlayer || other.isSpectator() || !other.isAlive()) {
                continue;
            }

            Vec3d targetEyePos = other.getEyePos();
            double dist = eyePos.distanceTo(targetEyePos);

            if (dist > MAX_DISTANCE) {
                continue;
            }

            Vec3d toTarget = targetEyePos.subtract(eyePos).normalize();
            double dot = lookVec.dotProduct(toTarget);
            dot = Math.max(-1.0, Math.min(1.0, dot));
            double angleDeg = Math.toDegrees(Math.acos(dot));

            if (angleDeg <= MAX_CONE_ANGLE_DEG) {
                boolean isMoo = MooUserManager.isMooUser(other.getUuid())
                        || (other.getName() != null && MooUserManager.isMooUser(other.getName().getString()));

                candidates.add(new TargetResult(other, dist, angleDeg, isMoo));
            }
        }

        if (candidates.isEmpty()) {
            return null;
        }

        candidates.sort(Comparator.comparingDouble(c -> c.angleDeg));
        return candidates.get(0);
    }
}
