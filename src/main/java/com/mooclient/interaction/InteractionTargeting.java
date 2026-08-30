package com.mooclient.interaction;

import com.mooclient.util.MooUserManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Wyszukiwanie celu interakcji w stożku wzroku gracza (Target Cone).
 * Kąt: do ~45° FOV, Zasięg: do 5.0 bloków z obsługą bezpośredniego celowania w bounding box gracza.
 */
public class InteractionTargeting {

    public static final double MAX_DISTANCE = 5.0; // Maksymalny zasięg interakcji (5 bloków)
    public static final double MAX_CONE_ANGLE_DEG = 45.0; // Szeroki, wygodny stożek celowania

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
        Vec3d reachEnd = eyePos.add(lookVec.multiply(MAX_DISTANCE));

        List<? extends PlayerEntity> nearbyPlayers = client.world.getPlayers();
        List<TargetResult> candidates = new ArrayList<>();

        for (PlayerEntity other : nearbyPlayers) {
            if (other == localPlayer || other.isSpectator() || !other.isAlive()) {
                continue;
            }

            Box targetBox = other.getBoundingBox().expand(0.3);
            Vec3d targetCenter = targetBox.getCenter();
            Vec3d targetEyePos = other.getEyePos();

            double dist = eyePos.distanceTo(targetCenter);
            if (dist > MAX_DISTANCE) {
                continue;
            }

            // 1. Sprawdzenie bezpośredniego przecięcia promienia wzroku z Bounding Boxem (Crosshair Raycast)
            Optional<Vec3d> rayHit = targetBox.raycast(eyePos, reachEnd);
            double angleDeg;

            if (rayHit.isPresent()) {
                angleDeg = 0.0; // Bezpośrednie trafienie w celownik
            } else {
                // 2. Kąt do środka ciała i do oczu celu
                Vec3d toCenter = targetCenter.subtract(eyePos).normalize();
                Vec3d toEye = targetEyePos.subtract(eyePos).normalize();

                double dotCenter = Math.max(-1.0, Math.min(1.0, lookVec.dotProduct(toCenter)));
                double dotEye = Math.max(-1.0, Math.min(1.0, lookVec.dotProduct(toEye)));

                double angleCenter = Math.toDegrees(Math.acos(dotCenter));
                double angleEye = Math.toDegrees(Math.acos(dotEye));

                angleDeg = Math.min(angleCenter, angleEye);
            }

            if (angleDeg <= MAX_CONE_ANGLE_DEG) {
                boolean isMoo = MooUserManager.isMooUser(other.getUuid())
                        || (other.getName() != null && MooUserManager.isMooUser(other.getName().getString()));

                candidates.add(new TargetResult(other, dist, angleDeg, isMoo));
            }
        }

        if (candidates.isEmpty()) {
            return null;
        }

        // Sortowanie: najpierw najmniejszy kąt (najbliżej środka celownika), a przy równym kącie najmniejszy dystans
        candidates.sort(Comparator.comparingDouble((TargetResult c) -> c.angleDeg).thenComparingDouble(c -> c.distance));
        return candidates.get(0);
    }
}
