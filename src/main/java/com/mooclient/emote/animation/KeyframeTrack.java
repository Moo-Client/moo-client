package com.mooclient.emote.animation;

import net.minecraft.util.math.MathHelper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Ścieżka klatek kluczowych dla pojedynczego kanału (np. rotacja lub pozycja kości).
 */
public class KeyframeTrack {

    private final List<Keyframe> keyframes = new ArrayList<>();

    public KeyframeTrack() {
    }

    public void addKeyframe(Keyframe keyframe) {
        keyframes.add(keyframe);
        keyframes.sort(Comparator.comparingDouble(k -> k.timeSeconds));
    }

    public boolean isEmpty() {
        return keyframes.isEmpty();
    }

    public int size() {
        return keyframes.size();
    }

    /**
     * Oblicza interpolowane wartości [x, y, z] w zadanym czasie t (w sekundach).
     */
    public float[] sample(float timeSeconds) {
        if (keyframes.isEmpty()) {
            return new float[]{0.0f, 0.0f, 0.0f};
        }

        if (keyframes.size() == 1 || timeSeconds <= keyframes.get(0).timeSeconds) {
            Keyframe first = keyframes.get(0);
            return new float[]{first.x, first.y, first.z};
        }

        Keyframe last = keyframes.get(keyframes.size() - 1);
        if (timeSeconds >= last.timeSeconds) {
            return new float[]{last.x, last.y, last.z};
        }

        // Znajdź parę klatek obejmujących czas timeSeconds
        for (int i = 0; i < keyframes.size() - 1; i++) {
            Keyframe k0 = keyframes.get(i);
            Keyframe k1 = keyframes.get(i + 1);

            if (timeSeconds >= k0.timeSeconds && timeSeconds <= k1.timeSeconds) {
                float duration = k1.timeSeconds - k0.timeSeconds;
                if (duration <= 0.0001f) {
                    return new float[]{k1.x, k1.y, k1.z};
                }

                float alpha = (timeSeconds - k0.timeSeconds) / duration;

                if (k0.interpolation == Keyframe.Interpolation.STEP) {
                    return new float[]{k0.x, k0.y, k0.z};
                }

                if (k0.interpolation == Keyframe.Interpolation.SMOOTH) {
                    // Cosine smooth easing
                    alpha = (float) (0.5 - 0.5 * Math.cos(alpha * Math.PI));
                }

                float x = MathHelper.lerp(alpha, k0.x, k1.x);
                float y = MathHelper.lerp(alpha, k0.y, k1.y);
                float z = MathHelper.lerp(alpha, k0.z, k1.z);
                return new float[]{x, y, z};
            }
        }

        return new float[]{last.x, last.y, last.z};
    }
}
