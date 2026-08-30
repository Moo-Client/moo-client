package com.mooclient.emote;

import com.mooclient.emote.animation.BlockbenchAnimation;
import com.mooclient.emote.animation.BlockbenchAnimationParser;
import com.mooclient.emote.animation.BoneTransform;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class BlockbenchAnimationParserTest {

    @Test
    public void testParseSimpleBlockbenchJson() {
        String json = """
        {
          "format_version": "1.8.0",
          "animations": {
            "animation.custom.test": {
              "animation_length": 2.5,
              "loop": true,
              "bones": {
                "right_arm": {
                  "rotation": {
                    "0.0": [0, 0, 0],
                    "1.25": [-90, 0, 0],
                    "2.5": [0, 0, 0]
                  }
                },
                "head": {
                  "rotation": [0, 45, 0]
                }
              }
            }
          }
        }
        """;

        BlockbenchAnimation anim = BlockbenchAnimationParser.parseJsonString(json);
        Assertions.assertNotNull(anim, "Animacja nie powinna być null");
        Assertions.assertEquals(50, anim.getDurationTicks(), "2.5 sekundy = 50 ticków");
        Assertions.assertTrue(anim.isLooping(), "Animacja powinna być zapętlona");

        // Sprawdzenie kości głowy w t = 0 (statyczny obrót 45 stopni yaw)
        BoneTransform headTransform = anim.sampleBone("head", 0.0f);
        Assertions.assertNotNull(headTransform);
        Assertions.assertEquals((float) Math.toRadians(45.0), headTransform.yaw, 0.001f);

        // Sprawdzenie kości prawego ramienia w t = 1.25s (środek cyklu) -> pitch = -90 deg
        BoneTransform armTransform = anim.sampleBone("right_arm", 1.25f);
        Assertions.assertNotNull(armTransform);
        Assertions.assertEquals((float) Math.toRadians(-90.0), armTransform.pitch, 0.001f);
    }

    @Test
    public void testParseBbmodelFormat() {
        String bbmodelJson = """
        {
          "meta": { "format_version": "4.10", "model_format": "bedrock" },
          "name": "frontflip",
          "animations": [
            {
              "uuid": "test-uuid",
              "name": "frontflip",
              "length": 0.8,
              "loop": "once",
              "animators": {
                "bone-1": {
                  "name": "right_arm",
                  "keyframes": [
                    {
                      "channel": "rotation",
                      "time": 0.2,
                      "data_points": [{ "x": -130, "y": 0, "z": -25 }],
                      "interpolation": "catmullrom"
                    }
                  ]
                }
              }
            }
          ]
        }
        """;

        BlockbenchAnimation anim = BlockbenchAnimationParser.parse(bbmodelJson);
        Assertions.assertNotNull(anim, "Animacja .bbmodel nie powinna być null");
        Assertions.assertEquals(16, anim.getDurationTicks(), "0.8 sekundy = 16 ticków");
        Assertions.assertFalse(anim.isLooping(), "Animacja once nie powinna być zapętlona");

        BoneTransform arm = anim.sampleBone("right_arm", 0.2f);
        Assertions.assertNotNull(arm);
        Assertions.assertEquals((float) Math.toRadians(-130.0), arm.pitch, 0.001f);
    }
}
