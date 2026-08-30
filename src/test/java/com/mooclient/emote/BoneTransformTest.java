package com.mooclient.emote;

import com.mooclient.emote.animation.BoneTransform;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class BoneTransformTest {

    @Test
    public void testLerpInterpolation() {
        BoneTransform a = new BoneTransform(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f);
        BoneTransform b = new BoneTransform(1.0f, 2.0f, 3.0f, 10.0f, 20.0f, 30.0f, 2.0f, 2.0f, 2.0f);

        BoneTransform mid = BoneTransform.lerp(a, b, 0.5f);

        Assertions.assertEquals(0.5f, mid.pitch, 0.0001f);
        Assertions.assertEquals(1.0f, mid.yaw, 0.0001f);
        Assertions.assertEquals(1.5f, mid.roll, 0.0001f);
        Assertions.assertEquals(5.0f, mid.posX, 0.0001f);
        Assertions.assertEquals(10.0f, mid.posY, 0.0001f);
        Assertions.assertEquals(15.0f, mid.posZ, 0.0001f);
        Assertions.assertEquals(1.5f, mid.scaleX, 0.0001f);
    }
}
