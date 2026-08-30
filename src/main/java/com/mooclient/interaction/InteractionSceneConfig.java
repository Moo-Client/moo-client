package com.mooclient.interaction;

import java.util.HashMap;
import java.util.Map;

/**
 * Konfiguracja sceny dla interakcji wieloosobowej.
 * Definiuje wzajemne pozycje wizualne poszczególnych slotów (graczy).
 */
public class InteractionSceneConfig {

    private final Map<Integer, SceneTransform> slotTransforms = new HashMap<>();
    private float defaultDistance = 1.0f;

    public InteractionSceneConfig() {
    }

    public static InteractionSceneConfig createFacingDuo(float distance) {
        InteractionSceneConfig config = new InteractionSceneConfig();
        config.defaultDistance = distance;

        // Slot 0: Inicjator (pozycja bazowa, patrzący na wprost Z+)
        config.setSlotTransform(0, new SceneTransform(0.0f, 0.0f, -distance / 2.0f, 0.0f));

        // Slot 1: Drugi gracz (naprzeciwko inicjatora, obrócony o 180 stopni, patrzący w Z-)
        config.setSlotTransform(1, new SceneTransform(0.0f, 0.0f, distance / 2.0f, 180.0f));

        return config;
    }

    public void setSlotTransform(int slotIndex, SceneTransform transform) {
        slotTransforms.put(slotIndex, transform);
    }

    public SceneTransform getSlotTransform(int slotIndex) {
        return slotTransforms.getOrDefault(slotIndex, new SceneTransform());
    }

    public float getDefaultDistance() {
        return defaultDistance;
    }
}
