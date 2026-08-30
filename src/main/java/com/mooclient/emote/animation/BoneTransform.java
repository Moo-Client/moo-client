package com.mooclient.emote.animation;

import net.minecraft.util.math.MathHelper;

/**
 * Reprezentuje transformację pojedynczej kości modelu gracza
 * (rotacje w radianach, przesunięcia w pikselach/blokach, skalowanie).
 */
public class BoneTransform {

    public float pitch = 0.0f; // Rotacja X
    public float yaw = 0.0f;   // Rotacja Y
    public float roll = 0.0f;  // Rotacja Z

    public float posX = 0.0f;
    public float posY = 0.0f;
    public float posZ = 0.0f;

    public float scaleX = 1.0f;
    public float scaleY = 1.0f;
    public float scaleZ = 1.0f;

    public BoneTransform() {
    }

    public BoneTransform(float pitch, float yaw, float roll) {
        this.pitch = pitch;
        this.yaw = yaw;
        this.roll = roll;
    }

    public BoneTransform(float pitch, float yaw, float roll, float posX, float posY, float posZ) {
        this.pitch = pitch;
        this.yaw = yaw;
        this.roll = roll;
        this.posX = posX;
        this.posY = posY;
        this.posZ = posZ;
    }

    public BoneTransform(float pitch, float yaw, float roll, float posX, float posY, float posZ, float scaleX, float scaleY, float scaleZ) {
        this.pitch = pitch;
        this.yaw = yaw;
        this.roll = roll;
        this.posX = posX;
        this.posY = posY;
        this.posZ = posZ;
        this.scaleX = scaleX;
        this.scaleY = scaleY;
        this.scaleZ = scaleZ;
    }

    public void reset() {
        this.pitch = 0.0f;
        this.yaw = 0.0f;
        this.roll = 0.0f;
        this.posX = 0.0f;
        this.posY = 0.0f;
        this.posZ = 0.0f;
        this.scaleX = 1.0f;
        this.scaleY = 1.0f;
        this.scaleZ = 1.0f;
    }

    public void set(BoneTransform other) {
        if (other == null) {
            reset();
            return;
        }
        this.pitch = other.pitch;
        this.yaw = other.yaw;
        this.roll = other.roll;
        this.posX = other.posX;
        this.posY = other.posY;
        this.posZ = other.posZ;
        this.scaleX = other.scaleX;
        this.scaleY = other.scaleY;
        this.scaleZ = other.scaleZ;
    }

    public static BoneTransform lerp(BoneTransform a, BoneTransform b, float delta) {
        if (a == null && b == null) return new BoneTransform();
        if (a == null) return b.copy();
        if (b == null) return a.copy();

        BoneTransform result = new BoneTransform();
        result.pitch = MathHelper.lerp(delta, a.pitch, b.pitch);
        result.yaw = MathHelper.lerp(delta, a.yaw, b.yaw);
        result.roll = MathHelper.lerp(delta, a.roll, b.roll);

        result.posX = MathHelper.lerp(delta, a.posX, b.posX);
        result.posY = MathHelper.lerp(delta, a.posY, b.posY);
        result.posZ = MathHelper.lerp(delta, a.posZ, b.posZ);

        result.scaleX = MathHelper.lerp(delta, a.scaleX, b.scaleX);
        result.scaleY = MathHelper.lerp(delta, a.scaleY, b.scaleY);
        result.scaleZ = MathHelper.lerp(delta, a.scaleZ, b.scaleZ);
        return result;
    }

    public BoneTransform copy() {
        BoneTransform c = new BoneTransform(pitch, yaw, roll, posX, posY, posZ);
        c.scaleX = this.scaleX;
        c.scaleY = this.scaleY;
        c.scaleZ = this.scaleZ;
        return c;
    }
}
