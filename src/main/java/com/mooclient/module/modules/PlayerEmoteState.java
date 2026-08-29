package com.mooclient.module.modules;

import net.minecraft.util.math.MathHelper;
import java.util.UUID;

/**
 * State and interpolation tracker for an individual player's emotes and acrobatics.
 * Used for both local player and remote players across multiplayer servers.
 */
public class PlayerEmoteState {

    private final UUID uuid;
    private int entityId = -1;

    public boolean isHandsUp = false;
    public float handsUpCurrentProgress = 0.0f;
    public float handsUpLastProgress = 0.0f;

    public EmotesModule.FlipDirection flipDirection = EmotesModule.FlipDirection.NONE;
    public int flipTicks = 0;
    public static final int TOTAL_FLIP_TICKS = 15;
    public float flipCurrentProgress = 0.0f;
    public float flipLastProgress = 0.0f;

    public PlayerEmoteState(UUID uuid) {
        this.uuid = uuid;
    }

    public UUID getUuid() {
        return uuid;
    }

    public int getEntityId() {
        return entityId;
    }

    public void setEntityId(int entityId) {
        this.entityId = entityId;
    }

    public void triggerHandsUp(boolean handsUp) {
        this.isHandsUp = handsUp;
    }

    public void triggerFrontflip() {
        this.flipDirection = EmotesModule.FlipDirection.FRONT;
        this.flipTicks = 0;
        this.flipCurrentProgress = 0.0f;
        this.flipLastProgress = 0.0f;
    }

    public void triggerBackflip() {
        this.flipDirection = EmotesModule.FlipDirection.BACK;
        this.flipTicks = 0;
        this.flipCurrentProgress = 0.0f;
        this.flipLastProgress = 0.0f;
    }

    public void stopEmotes() {
        this.isHandsUp = false;
        this.flipDirection = EmotesModule.FlipDirection.NONE;
        this.flipTicks = 0;
        this.flipCurrentProgress = 0.0f;
        this.flipLastProgress = 0.0f;
    }

    public void onTick() {
        // Hands up progress tick
        handsUpLastProgress = handsUpCurrentProgress;
        float target = isHandsUp ? 1.0f : 0.0f;
        float speed = 0.18f;
        if (handsUpCurrentProgress < target) {
            handsUpCurrentProgress = Math.min(handsUpCurrentProgress + speed, target);
        } else if (handsUpCurrentProgress > target) {
            handsUpCurrentProgress = Math.max(handsUpCurrentProgress - speed, target);
        }

        // Flip progress tick
        flipLastProgress = flipCurrentProgress;
        if (flipDirection != EmotesModule.FlipDirection.NONE) {
            flipTicks++;
            flipCurrentProgress = (float) flipTicks / (float) TOTAL_FLIP_TICKS;
            if (flipTicks >= TOTAL_FLIP_TICKS) {
                flipDirection = EmotesModule.FlipDirection.NONE;
                flipTicks = 0;
                flipCurrentProgress = 0.0f;
                flipLastProgress = 0.0f;
            }
        }
    }

    public float getInterpolatedHandsUpProgress(float tickDelta) {
        if (!isHandsUp && handsUpCurrentProgress == 0.0f) return 0.0f;
        return MathHelper.clamp(MathHelper.lerp(tickDelta, handsUpLastProgress, handsUpCurrentProgress), 0.0f, 1.0f);
    }

    public float getInterpolatedFlipProgress(float tickDelta) {
        if (flipDirection == EmotesModule.FlipDirection.NONE && flipCurrentProgress == 0.0f) return 0.0f;
        return MathHelper.clamp(MathHelper.lerp(tickDelta, flipLastProgress, flipCurrentProgress), 0.0f, 1.0f);
    }

    public float getFlipRotationDegrees(float tickDelta) {
        float p = getInterpolatedFlipProgress(tickDelta);
        if (p <= 0.0001f || flipDirection == EmotesModule.FlipDirection.NONE) return 0.0f;
        float eased = (float) (0.5 - 0.5 * Math.cos(p * Math.PI));
        float total = (flipDirection == EmotesModule.FlipDirection.FRONT) ? 360.0f : -360.0f;
        return eased * total;
    }

    public float getFlipJumpHeight(float tickDelta) {
        float p = getInterpolatedFlipProgress(tickDelta);
        if (p <= 0.0001f || p >= 0.9999f || flipDirection == EmotesModule.FlipDirection.NONE) return 0.0f;
        return (float) (Math.sin(p * Math.PI) * 1.05f);
    }

    public float getFlipTuckFactor(float tickDelta) {
        float p = getInterpolatedFlipProgress(tickDelta);
        if (p <= 0.0001f || p >= 0.9999f || flipDirection == EmotesModule.FlipDirection.NONE) return 0.0f;
        return (float) Math.sin(p * Math.PI);
    }

    public boolean isFlipping() {
        return flipDirection != EmotesModule.FlipDirection.NONE || flipCurrentProgress > 0.001f;
    }

    public boolean isIdle() {
        return !isHandsUp && handsUpCurrentProgress <= 0.001f && flipDirection == EmotesModule.FlipDirection.NONE && flipCurrentProgress <= 0.001f;
    }
}
