package com.mooclient.emote;

import com.mooclient.emote.animation.BoneTransform;
import com.mooclient.emote.animation.IEmoteAnimation;
import com.mooclient.interaction.SceneTransform;
import net.minecraft.util.math.MathHelper;

import java.util.HashMap;
import java.util.Map;

/**
 * Przechowuje stan animacji i renderingu emotki dla danego gracza (lokalnego lub zdalnego).
 * Wszystkie transformacje są w 100% odczytywane z pliku animacji Blockbench (.bbmodel / .json).
 * Brak jakichkolwiek hardkodowanych nazw czy procedur w kodzie Java.
 */
public class EmotePlayerState {

    private Emote activeEmote = null;
    private int activeTicks = 0;
    private int lastActiveTicks = 0;
    private int slotIndex = 0;
    private long startEpochMs = 0L;

    private boolean stopping = false;
    private int stopTicks = 0;
    private static final int STOP_TRANSITION_TICKS = 5; // ~0.25s płynnego opuszczania
    private final Map<String, BoneTransform> stopStartTransforms = new HashMap<>();

    private final Map<String, BoneTransform> currentBoneTransforms = new HashMap<>();
    private final Map<String, BoneTransform> nextBoneTransforms = new HashMap<>();
    private final Map<String, BoneTransform> renderBoneTransforms = new HashMap<>();

    private SceneTransform customSceneTransform = null;

    public EmotePlayerState() {
    }

    public synchronized void startEmote(Emote emote, int slotIndex, long startEpochMs) {
        this.activeEmote = emote;
        this.activeTicks = 0;
        this.lastActiveTicks = 0;
        this.slotIndex = slotIndex;
        this.startEpochMs = startEpochMs > 0 ? startEpochMs : System.currentTimeMillis();
        this.stopping = false;
        this.stopTicks = 0;
        this.stopStartTransforms.clear();

        currentBoneTransforms.clear();
        nextBoneTransforms.clear();
        renderBoneTransforms.clear();

        if (emote != null && emote.getAnimation() != null) {
            emote.getAnimation().sample(0.0f, currentBoneTransforms);
            emote.getAnimation().sample(0.05f, nextBoneTransforms);
        }
    }

    private static float wrapRad(float rad) {
        float twoPi = (float) (2.0 * Math.PI);
        float wrapped = rad % twoPi;
        if (wrapped > (float) Math.PI) wrapped -= twoPi;
        if (wrapped < (float) -Math.PI) wrapped += twoPi;
        return wrapped;
    }

    public synchronized void stopEmote() {
        if (activeEmote != null && !stopping) {
            stopping = true;
            stopTicks = 0;
            stopStartTransforms.clear();
            Map<String, BoneTransform> source = !renderBoneTransforms.isEmpty() ? renderBoneTransforms : currentBoneTransforms;
            if (source.isEmpty() && activeEmote.getAnimation() != null) {
                float timeSec = activeTicks * 0.05f;
                activeEmote.getAnimation().sample(timeSec, source);
            }
            for (Map.Entry<String, BoneTransform> entry : source.entrySet()) {
                BoneTransform bt = entry.getValue().copy();
                bt.pitch = wrapRad(bt.pitch);
                bt.yaw = wrapRad(bt.yaw);
                bt.roll = wrapRad(bt.roll);
                stopStartTransforms.put(entry.getKey(), bt);
            }
            if (stopStartTransforms.isEmpty()) {
                forceStop();
            }
            return;
        }
        forceStop();
    }

    public synchronized void forceStop() {
        this.activeEmote = null;
        this.activeTicks = 0;
        this.lastActiveTicks = 0;
        this.startEpochMs = 0L;
        this.customSceneTransform = null;
        this.stopping = false;
        this.stopTicks = 0;
        this.stopStartTransforms.clear();

        currentBoneTransforms.clear();
        nextBoneTransforms.clear();
        renderBoneTransforms.clear();
    }

    public boolean isPlaying() {
        return activeEmote != null && !stopping;
    }

    public boolean isRendering() {
        return activeEmote != null;
    }

    public boolean isStopping() {
        return stopping;
    }

    public Emote getActiveEmote() {
        return activeEmote;
    }

    public int getActiveTicks() {
        return activeTicks;
    }

    public int getSlotIndex() {
        return slotIndex;
    }

    public long getStartEpochMs() {
        return startEpochMs;
    }

    public void setCustomSceneTransform(SceneTransform sceneTransform) {
        this.customSceneTransform = sceneTransform;
    }

    public synchronized void onTick() {
        if (activeEmote == null) return;

        if (stopping) {
            stopTicks++;
            if (stopTicks >= STOP_TRANSITION_TICKS) {
                forceStop();
            }
            return;
        }

        this.lastActiveTicks = this.activeTicks;
        this.activeTicks++;

        // Czas animacji dla próbkowania klatek z pliku Blockbench
        float timeSec = activeTicks * 0.05f;
        float nextTimeSec = (activeTicks + 1) * 0.05f;

        IEmoteAnimation anim = activeEmote.getAnimation();
        if (anim != null) {
            anim.sample(timeSec, currentBoneTransforms);
            anim.sample(nextTimeSec, nextBoneTransforms);
        }

        // Sprawdzenie naturalnego zakończenia animacji nie-zapętlonej
        if (!activeEmote.isLooping() && activeEmote.getDurationTicks() > 0) {
            if (activeTicks >= activeEmote.getDurationTicks()) {
                forceStop();
            }
        }
    }

    public synchronized void updateRenderTransforms(float tickDelta) {
        if (activeEmote == null) {
            renderBoneTransforms.clear();
            return;
        }

        if (stopping) {
            float progress = MathHelper.clamp((stopTicks + tickDelta) / (float) STOP_TRANSITION_TICKS, 0.0f, 1.0f);
            float factor = 1.0f - progress;
            // Smooth easing curve
            factor = factor * factor * (3.0f - 2.0f * factor);

            renderBoneTransforms.clear();
            for (Map.Entry<String, BoneTransform> entry : stopStartTransforms.entrySet()) {
                BoneTransform start = entry.getValue();
                BoneTransform res = new BoneTransform(
                        start.pitch * factor,
                        start.yaw * factor,
                        start.roll * factor,
                        start.posX * factor,
                        start.posY * factor,
                        start.posZ * factor,
                        MathHelper.lerp(factor, 1.0f, start.scaleX),
                        MathHelper.lerp(factor, 1.0f, start.scaleY),
                        MathHelper.lerp(factor, 1.0f, start.scaleZ)
                );
                renderBoneTransforms.put(entry.getKey(), res);
            }
            return;
        }

        // Płynna interpolacja klatek pomiędzy tickami
        for (Map.Entry<String, BoneTransform> entry : currentBoneTransforms.entrySet()) {
            String bone = entry.getKey();
            BoneTransform cur = entry.getValue();
            BoneTransform next = nextBoneTransforms.get(bone);
            renderBoneTransforms.put(bone, BoneTransform.lerp(cur, next, tickDelta));
        }
    }

    public BoneTransform getBoneTransform(String boneName) {
        return renderBoneTransforms.get(boneName);
    }

    public float getVisualXOffset(float tickDelta) {
        BoneTransform root = renderBoneTransforms.get("root");
        return root != null ? (root.posX / 16.0f) : 0.0f;
    }

    public float getVisualYOffset(float tickDelta) {
        BoneTransform root = renderBoneTransforms.get("root");
        return root != null ? (root.posY / 16.0f) : 0.0f;
    }

    public float getVisualZOffset(float tickDelta) {
        BoneTransform root = renderBoneTransforms.get("root");
        return root != null ? (root.posZ / 16.0f) : 0.0f;
    }

    public float getVisualPitch(float tickDelta) {
        BoneTransform root = renderBoneTransforms.get("root");
        if (root != null) {
            // Przeliczenie z radianów na stopnie z uwzględnieniem osi obrotu MC
            return -(float) Math.toDegrees(root.pitch);
        }
        return 0.0f;
    }

    public float getVisualYaw(float tickDelta) {
        BoneTransform root = renderBoneTransforms.get("root");
        float baseYaw = (root != null) ? (float) Math.toDegrees(root.yaw) : 0.0f;
        if (customSceneTransform != null) {
            baseYaw += customSceneTransform.visualYaw;
        }
        return baseYaw;
    }

    public float getVisualRoll(float tickDelta) {
        BoneTransform root = renderBoneTransforms.get("root");
        if (root != null) {
            return (float) Math.toDegrees(root.roll);
        }
        return 0.0f;
    }

    public float getSceneOffsetX() {
        return customSceneTransform != null ? customSceneTransform.visualOffsetX : 0.0f;
    }

    public float getSceneOffsetY() {
        return customSceneTransform != null ? customSceneTransform.visualOffsetY : 0.0f;
    }

    public float getSceneOffsetZ() {
        return customSceneTransform != null ? customSceneTransform.visualOffsetZ : 0.0f;
    }
}
