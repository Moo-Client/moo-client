package com.mooclient.emote.animation;

/**
 * Pojedyncza klatka kluczowa animacji w czasie t (w sekundach).
 */
public class Keyframe {

    public enum Interpolation {
        LINEAR,
        SMOOTH, // Catmull-Rom / Cosine
        STEP
    }

    public final float timeSeconds;
    public final float x;
    public final float y;
    public final float z;
    public final Interpolation interpolation;

    public Keyframe(float timeSeconds, float x, float y, float z, Interpolation interpolation) {
        this.timeSeconds = timeSeconds;
        this.x = x;
        this.y = y;
        this.z = z;
        this.interpolation = interpolation != null ? interpolation : Interpolation.LINEAR;
    }

    public Keyframe(float timeSeconds, float x, float y, float z) {
        this(timeSeconds, x, y, z, Interpolation.LINEAR);
    }
}
