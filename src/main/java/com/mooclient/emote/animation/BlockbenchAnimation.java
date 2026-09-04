package com.mooclient.emote.animation;

import java.util.HashMap;
import java.util.Map;

/**
 * Reprezentacja załadowanej animacji w formacie Blockbench / Minecraft Bedrock.
 * Posiada pełną zgodność z modelem matematycznym Blockbencha i Minecraft Java ModelPart.
 * 
 * Zarówno Blockbench (wewnętrzny format .bbmodel), jak i silnik Minecraft ModelPart
 * stosują kolejność obrotów Eulera ZYX (Quaternionf.rotationZYX / Matrix3f.rotationZYX).
 * W Minecraft Java ModelPart oś pionowa Y wskazuje w dół (w przeciwieństwie do Blockbencha,
 * gdzie oś Y wskazuje w górę). Odwrócenie osi Y implikuje zmianę znaku kątów obrotu
 * w osiach Y (yaw) oraz Z (roll), podczas gdy obrót w osi X (pitch - pochylenie w przód/górę)
 * zachowuje ten sam znak:
 *   pitch = radX
 *   yaw   = -radY
 *   roll  = -radZ
 * 
 * Dzięki temu wszystkie emotki (w tym złożone, trójwymiarowe pozycje jak facepalm,
 * friendly_wave czy meditation) trafiają dokładnie w zamierzone punkty ciała (czoło, twarz).
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
                // Konwersja kątów z układu Blockbench na Minecraft Java ModelPart:
                // pitch (X) zachowuje zwrot, yaw (Y) i roll (Z) są odwracane ze względu na odwróconą oś Y (w dół w MC).
                float pitch = (float) Math.toRadians(rotDeg[0]);
                float rawY = rotDeg[1];
                float rawZ = rotDeg[2];

                // W uścisku dłoni (Handshake) prawe ramię u obu graczy musi iść w lewo (yaw < 0),
                // aby obie prawe dłonie spotkały się idealnie w osi symetrii pośrodku między graczami.
                if ("handshake".equalsIgnoreCase(this.name) && "right_arm".equals(boneName)) {
                    if (rawY < 0.0f) rawY = -rawY;
                    if (rawY < 25.0f && rawY > 0.0f) rawY = 33.0f;
                    if (rawZ < 0.0f) rawZ = -10.0f;
                }

                transform.pitch = pitch;
                transform.yaw = -(float) Math.toRadians(rawY);
                transform.roll = -(float) Math.toRadians(rawZ);
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
