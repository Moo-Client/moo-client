package com.mooclient.emote.animation;

import java.util.HashMap;
import java.util.Map;

/**
 * Reprezentacja załadowanej animacji w formacie Blockbench / Minecraft Bedrock.
 */
public class BlockbenchAnimation implements IEmoteAnimation {

    public static class BoneTracks {
        public final KeyframeTrack rotation = new KeyframeTrack();
        public final KeyframeTrack position = new KeyframeTrack();
        public final KeyframeTrack scale = new KeyframeTrack();
    }

    private final String name;
    private final float lengthSeconds;
    private final boolean looping;
    private final Map<String, BoneTracks> bones = new HashMap<>();

    public BlockbenchAnimation(String name, float lengthSeconds, boolean looping) {
        this.name = name;
        this.lengthSeconds = lengthSeconds;
        this.looping = looping;
    }

    public String getName() {
        return name;
    }

    @Override
    public float getLengthSeconds() {
        return lengthSeconds;
    }

    public int getDurationTicks() {
        return Math.round(lengthSeconds * 20.0f);
    }

    @Override
    public boolean isLooping() {
        return looping;
    }

    public Map<String, BoneTracks> getBones() {
        return bones;
    }

    public BoneTracks getOrCreateBone(String boneName) {
        return bones.computeIfAbsent(normalizeBoneName(boneName), k -> new BoneTracks());
    }

    public static String normalizeBoneName(String raw) {
        if (raw == null) return "";
        String s = raw.toLowerCase().trim();
        switch (s) {
            case "head":
            case "head_part":
                return "head";
            case "body":
            case "torso":
            case "chest":
                return "body";
            case "right_arm":
            case "rightarm":
            case "arm_right":
            case "r_arm":
                return "right_arm";
            case "left_arm":
            case "leftarm":
            case "arm_left":
            case "l_arm":
                return "left_arm";
            case "right_leg":
            case "rightleg":
            case "leg_right":
            case "r_leg":
                return "right_leg";
            case "left_leg":
            case "leftleg":
            case "leg_left":
            case "l_leg":
                return "left_leg";
            case "root":
            case "player":
            case "base":
                return "root";
            default:
                return s;
        }
    }

    public BoneTransform sampleBone(String boneName, float timeSeconds) {
        Map<String, BoneTransform> map = new HashMap<>();
        sample(timeSeconds, map);
        return map.get(normalizeBoneName(boneName));
    }

    @Override
    public void sample(float timeSeconds, Map<String, BoneTransform> targetMap) {
        float t = timeSeconds;
        if (looping && lengthSeconds > 0.001f) {
            t = t % lengthSeconds;
        } else if (t > lengthSeconds) {
            t = lengthSeconds;
        }

        for (Map.Entry<String, BoneTracks> entry : bones.entrySet()) {
            String boneName = entry.getKey();
            BoneTracks tracks = entry.getValue();

            BoneTransform transform = targetMap.computeIfAbsent(boneName, k -> new BoneTransform());

            if (!tracks.rotation.isEmpty()) {
                float[] rotDeg = tracks.rotation.sample(t);
                transform.pitch = (float) Math.toRadians(rotDeg[0]);
                transform.yaw = (float) Math.toRadians(rotDeg[1]);
                // Konwersja osi Z z formatu Blockbench Bedrock na Minecraft Java ModelPart
                transform.roll = -(float) Math.toRadians(rotDeg[2]);
            }

            if (!tracks.position.isEmpty()) {
                float[] pos = tracks.position.sample(t);
                transform.posX = pos[0];
                transform.posY = pos[1];
                transform.posZ = pos[2];
            }

            if (!tracks.scale.isEmpty()) {
                float[] sc = tracks.scale.sample(t);
                transform.scaleX = sc[0] != 0.0f ? sc[0] : 1.0f;
                transform.scaleY = sc[1] != 0.0f ? sc[1] : 1.0f;
                transform.scaleZ = sc[2] != 0.0f ? sc[2] : 1.0f;
            }
        }
    }
}
