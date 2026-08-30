package com.mooclient.sound;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

public class MooSounds {
    public static final Identifier COW_MOO_ID = Identifier.of("mooclient", "cow_moo");
    public static final SoundEvent COW_MOO = SoundEvent.of(COW_MOO_ID);

    public static void register() {
        try {
            Registry.register(Registries.SOUND_EVENT, COW_MOO_ID, COW_MOO);
        } catch (Exception ignored) {}
    }
}
