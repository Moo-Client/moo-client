package com.mooclient.emote.animation;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mooclient.MooClient;

import java.util.Map;

/**
 * Parser formatu animacji Blockbench (Minecraft Bedrock Animation JSON oraz projekty .bbmodel).
 */
public class BlockbenchAnimationParser {

    public static BlockbenchAnimation parseJsonString(String jsonContent) {
        return parse(jsonContent);
    }

    /**
     * Parsuje plik JSON animacji Blockbench (zarówno Bedrock .json jak i projekt .bbmodel)
     * i zwraca pierwszą znalezioną animację.
     */
    public static BlockbenchAnimation parse(String jsonContent) {
        if (jsonContent == null || jsonContent.trim().isEmpty()) {
            return null;
        }
        try {
            JsonObject root = JsonParser.parseString(jsonContent).getAsJsonObject();
            if (!root.has("animations")) {
                return null;
            }

            JsonElement animsElem = root.get("animations");

            // Przypadek 1: Standardowy plik animacji Bedrock JSON ("animations": { "animation.name": { ... } })
            if (animsElem.isJsonObject()) {
                JsonObject animationsObj = animsElem.getAsJsonObject();
                for (Map.Entry<String, JsonElement> entry : animationsObj.entrySet()) {
                    String animName = entry.getKey();
                    if (entry.getValue().isJsonObject()) {
                        return parseSingleAnimation(animName, entry.getValue().getAsJsonObject());
                    }
                }
            }
            // Przypadek 2: Plik projektu Blockbench .bbmodel ("animations": [ { "name": "...", "animators": { ... } } ])
            else if (animsElem.isJsonArray()) {
                JsonArray animsArray = animsElem.getAsJsonArray();
                for (JsonElement item : animsArray) {
                    if (item.isJsonObject()) {
                        return parseBbmodelAnimation(item.getAsJsonObject());
                    }
                }
            }
        } catch (Exception e) {
            MooClient.LOGGER.error("Błąd parsowania animacji Blockbench", e);
        }
        return null;
    }

    /**
     * Parsuje pojedynczą animację z pliku projektu Blockbench (.bbmodel).
     */
    public static BlockbenchAnimation parseBbmodelAnimation(JsonObject animObj) {
        String name = animObj.has("name") ? animObj.get("name").getAsString() : "animation";
        float length = animObj.has("length") ? animObj.get("length").getAsFloat() : 1.0f;
        boolean loop = false;
        if (animObj.has("loop")) {
            JsonElement loopElem = animObj.get("loop");
            if (loopElem.isJsonPrimitive()) {
                if (loopElem.getAsJsonPrimitive().isBoolean()) {
                    loop = loopElem.getAsBoolean();
                } else if (loopElem.getAsJsonPrimitive().isString()) {
                    String s = loopElem.getAsString().toLowerCase();
                    loop = s.equals("true") || s.equals("loop");
                }
            }
        }

        BlockbenchAnimation animation = new BlockbenchAnimation(name, length, loop);

        if (animObj.has("animators") && animObj.get("animators").isJsonObject()) {
            JsonObject animatorsObj = animObj.getAsJsonObject("animators");
            for (Map.Entry<String, JsonElement> entry : animatorsObj.entrySet()) {
                if (!entry.getValue().isJsonObject()) continue;
                JsonObject boneAnimator = entry.getValue().getAsJsonObject();
                String boneName = boneAnimator.has("name") ? boneAnimator.get("name").getAsString() : entry.getKey();
                BlockbenchAnimation.BoneTracks boneTracks = animation.getOrCreateBone(boneName);

                if (boneAnimator.has("keyframes") && boneAnimator.get("keyframes").isJsonArray()) {
                    JsonArray kfArray = boneAnimator.getAsJsonArray("keyframes");
                    for (JsonElement kfElem : kfArray) {
                        if (!kfElem.isJsonObject()) continue;
                        JsonObject kf = kfElem.getAsJsonObject();
                        String channel = kf.has("channel") ? kf.get("channel").getAsString().toLowerCase() : "rotation";
                        float time = kf.has("time") ? kf.get("time").getAsFloat() : 0.0f;

                        Keyframe.Interpolation interp = Keyframe.Interpolation.SMOOTH;
                        if (kf.has("interpolation")) {
                            String interpStr = kf.get("interpolation").getAsString().toLowerCase();
                            if (interpStr.contains("step")) {
                                interp = Keyframe.Interpolation.STEP;
                            } else if (interpStr.contains("linear")) {
                                interp = Keyframe.Interpolation.LINEAR;
                            } else {
                                interp = Keyframe.Interpolation.SMOOTH;
                            }
                        }

                        float[] values = new float[]{0.0f, 0.0f, 0.0f};
                        if (kf.has("data_points") && kf.get("data_points").isJsonArray()) {
                            JsonArray dataPoints = kf.getAsJsonArray("data_points");
                            if (!dataPoints.isEmpty() && dataPoints.get(0).isJsonObject()) {
                                JsonObject dp = dataPoints.get(0).getAsJsonObject();
                                values[0] = dp.has("x") ? dp.get("x").getAsFloat() : 0.0f;
                                values[1] = dp.has("y") ? dp.get("y").getAsFloat() : 0.0f;
                                values[2] = dp.has("z") ? dp.get("z").getAsFloat() : 0.0f;
                            }
                        }

                        Keyframe keyframe = new Keyframe(time, values[0], values[1], values[2], interp);
                        if ("rotation".equals(channel)) {
                            boneTracks.rotation.addKeyframe(keyframe);
                        } else if ("position".equals(channel)) {
                            boneTracks.position.addKeyframe(keyframe);
                        } else if ("scale".equals(channel)) {
                            boneTracks.scale.addKeyframe(keyframe);
                        }
                    }
                }
            }
        }

        return animation;
    }

    /**
     * Parsuje pojedynczą animację z formatu Bedrock Animation JSON.
     */
    public static BlockbenchAnimation parseSingleAnimation(String name, JsonObject animData) {
        float length = animData.has("animation_length") ? animData.get("animation_length").getAsFloat() : 1.0f;
        boolean loop = false;
        if (animData.has("loop")) {
            JsonElement loopElem = animData.get("loop");
            if (loopElem.isJsonPrimitive()) {
                if (loopElem.getAsJsonPrimitive().isBoolean()) {
                    loop = loopElem.getAsBoolean();
                } else if (loopElem.getAsJsonPrimitive().isString()) {
                    String s = loopElem.getAsString().toLowerCase();
                    loop = s.equals("true") || s.equals("loop");
                }
            }
        }

        BlockbenchAnimation animation = new BlockbenchAnimation(name, length, loop);

        if (animData.has("bones")) {
            JsonObject bonesObj = animData.getAsJsonObject("bones");
            for (Map.Entry<String, JsonElement> boneEntry : bonesObj.entrySet()) {
                String boneName = boneEntry.getKey();
                JsonObject channels = boneEntry.getValue().getAsJsonObject();
                BlockbenchAnimation.BoneTracks boneTracks = animation.getOrCreateBone(boneName);

                if (channels.has("rotation")) {
                    parseChannel(channels.get("rotation"), boneTracks.rotation);
                }
                if (channels.has("position")) {
                    parseChannel(channels.get("position"), boneTracks.position);
                }
                if (channels.has("scale")) {
                    parseChannel(channels.get("scale"), boneTracks.scale);
                }
            }
        }

        return animation;
    }

    private static void parseChannel(JsonElement channelElement, KeyframeTrack track) {
        if (channelElement == null) return;

        // Przypadek 1: Tablica statyczna [x, y, z] dla czasu 0.0s
        if (channelElement.isJsonArray()) {
            float[] values = parseVec3Array(channelElement.getAsJsonArray());
            track.addKeyframe(new Keyframe(0.0f, values[0], values[1], values[2], Keyframe.Interpolation.SMOOTH));
            return;
        }

        // Przypadek 2: Obiekt klatek kluczowych {"0.0": [x, y, z], "0.5": ...}
        if (channelElement.isJsonObject()) {
            JsonObject kfObj = channelElement.getAsJsonObject();
            for (Map.Entry<String, JsonElement> kfEntry : kfObj.entrySet()) {
                try {
                    float time = Float.parseFloat(kfEntry.getKey());
                    JsonElement valElement = kfEntry.getValue();

                    if (valElement.isJsonArray()) {
                        float[] v = parseVec3Array(valElement.getAsJsonArray());
                        track.addKeyframe(new Keyframe(time, v[0], v[1], v[2], Keyframe.Interpolation.SMOOTH));
                    } else if (valElement.isJsonObject()) {
                        JsonObject detailedObj = valElement.getAsJsonObject();
                        Keyframe.Interpolation interp = Keyframe.Interpolation.SMOOTH;

                        if (detailedObj.has("lerp_mode")) {
                            String mode = detailedObj.get("lerp_mode").getAsString().toLowerCase();
                            if (mode.contains("step")) {
                                interp = Keyframe.Interpolation.STEP;
                            } else if (mode.contains("linear")) {
                                interp = Keyframe.Interpolation.LINEAR;
                            } else {
                                interp = Keyframe.Interpolation.SMOOTH;
                            }
                        }

                        if (detailedObj.has("post") && detailedObj.get("post").isJsonArray()) {
                            float[] v = parseVec3Array(detailedObj.getAsJsonArray("post"));
                            track.addKeyframe(new Keyframe(time, v[0], v[1], v[2], interp));
                        } else if (detailedObj.has("pre") && detailedObj.get("pre").isJsonArray()) {
                            float[] v = parseVec3Array(detailedObj.getAsJsonArray("pre"));
                            track.addKeyframe(new Keyframe(time, v[0], v[1], v[2], interp));
                        }
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
    }

    private static float[] parseVec3Array(JsonArray array) {
        float[] res = new float[]{0.0f, 0.0f, 0.0f};
        for (int i = 0; i < Math.min(3, array.size()); i++) {
            try {
                res[i] = array.get(i).getAsFloat();
            } catch (Exception ignored) {
            }
        }
        return res;
    }
}
