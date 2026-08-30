package com.mooclient.network;

import com.mooclient.module.modules.EmotesModule;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.UUID;

/**
 * Custom Fabric network payload for synchronizing all player emotes, frontflips and backflips
 * across all Moo Client players on multiplayer servers.
 */
public record MooEmotePayload(UUID playerUuid, byte emoteType) implements CustomPayload {

    public static final byte TYPE_STOP = 0;
    public static final byte TYPE_HANDS_UP_START = 1;
    public static final byte TYPE_HANDS_UP_STOP = 2;
    public static final byte TYPE_FRONTFLIP = 3;
    public static final byte TYPE_BACKFLIP = 4;
    public static final byte TYPE_WAVE = 5;
    public static final byte TYPE_DANCE = 6;
    public static final byte TYPE_LAUGH = 7;
    public static final byte TYPE_SAD = 8;
    public static final byte TYPE_POINT = 9;
    public static final byte TYPE_BRAVO = 10;
    public static final byte TYPE_CRAWL = 11;
    public static final byte TYPE_VICTORY = 12;
    public static final byte TYPE_ANGRY = 13;
    public static final byte TYPE_THINK = 14;
    public static final byte TYPE_CLAP = 15;
    public static final byte TYPE_SALUTE = 16;
    public static final byte TYPE_MEDITATION = 17;
    public static final byte TYPE_FRIENDLY_WAVE = 18;
    public static final byte TYPE_ARM_WAVE = 19;
    public static final byte TYPE_FACEPALM = 20;

    public static final CustomPayload.Id<MooEmotePayload> ID = new CustomPayload.Id<>(Identifier.of("mooclient", "emote"));

    public static final PacketCodec<PacketByteBuf, MooEmotePayload> CODEC = CustomPayload.codecOf(
        MooEmotePayload::write,
        MooEmotePayload::new
    );

    public MooEmotePayload(PacketByteBuf buf) {
        this(buf.readUuid(), buf.readByte());
    }

    public void write(PacketByteBuf buf) {
        buf.writeUuid(this.playerUuid);
        buf.writeByte(this.emoteType);
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public static byte fromEmoteType(EmotesModule.EmoteType type) {
        if (type == null) return TYPE_STOP;
        return switch (type) {
            case NONE -> TYPE_STOP;
            case HANDS_UP -> TYPE_HANDS_UP_START;
            case FRONTFLIP -> TYPE_FRONTFLIP;
            case BACKFLIP -> TYPE_BACKFLIP;
            case WAVE -> TYPE_WAVE;
            case DANCE -> TYPE_DANCE;
            case LAUGH -> TYPE_LAUGH;
            case SAD -> TYPE_SAD;
            case POINT -> TYPE_POINT;
            case BRAVO -> TYPE_BRAVO;
            case CRAWL -> TYPE_CRAWL;
            case VICTORY -> TYPE_VICTORY;
            case ANGRY -> TYPE_ANGRY;
            case THINK -> TYPE_THINK;
            case CLAP -> TYPE_CLAP;
            case SALUTE -> TYPE_SALUTE;
            case MEDITATION -> TYPE_MEDITATION;
            case FRIENDLY_WAVE -> TYPE_FRIENDLY_WAVE;
            case ARM_WAVE -> TYPE_ARM_WAVE;
            case FACEPALM -> TYPE_FACEPALM;
        };
    }

    public static EmotesModule.EmoteType toEmoteType(byte type) {
        return switch (type) {
            case TYPE_WAVE -> EmotesModule.EmoteType.WAVE;
            case TYPE_DANCE -> EmotesModule.EmoteType.DANCE;
            case TYPE_LAUGH -> EmotesModule.EmoteType.LAUGH;
            case TYPE_SAD -> EmotesModule.EmoteType.SAD;
            case TYPE_POINT -> EmotesModule.EmoteType.POINT;
            case TYPE_BRAVO -> EmotesModule.EmoteType.BRAVO;
            case TYPE_CRAWL -> EmotesModule.EmoteType.CRAWL;
            case TYPE_VICTORY -> EmotesModule.EmoteType.VICTORY;
            case TYPE_ANGRY -> EmotesModule.EmoteType.ANGRY;
            case TYPE_THINK -> EmotesModule.EmoteType.THINK;
            case TYPE_CLAP -> EmotesModule.EmoteType.CLAP;
            case TYPE_SALUTE -> EmotesModule.EmoteType.SALUTE;
            case TYPE_MEDITATION -> EmotesModule.EmoteType.MEDITATION;
            case TYPE_FRIENDLY_WAVE -> EmotesModule.EmoteType.FRIENDLY_WAVE;
            case TYPE_ARM_WAVE -> EmotesModule.EmoteType.ARM_WAVE;
            case TYPE_FACEPALM -> EmotesModule.EmoteType.FACEPALM;
            case TYPE_FRONTFLIP -> EmotesModule.EmoteType.FRONTFLIP;
            case TYPE_BACKFLIP -> EmotesModule.EmoteType.BACKFLIP;
            case TYPE_HANDS_UP_START -> EmotesModule.EmoteType.HANDS_UP;
            default -> EmotesModule.EmoteType.NONE;
        };
    }
}
