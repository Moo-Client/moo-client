package com.mooclient.emote.animation;

import java.util.Map;

/**
 * Interfejs animacji emotki w systemie Moo Client.
 */
public interface IEmoteAnimation {

    /**
     * Długość trwania animacji w sekundach.
     */
    float getLengthSeconds();

    /**
     * Czy animacja jest zapętlona.
     */
    boolean isLooping();

    /**
     * Oblicza transformacje wszystkich kości w zadanym czasie t (w sekundach).
     *
     * @param timeSeconds czas w sekundach
     * @param targetMap   mapa docelowa (nazwa kości -> BoneTransform)
     */
    void sample(float timeSeconds, Map<String, BoneTransform> targetMap);
}
