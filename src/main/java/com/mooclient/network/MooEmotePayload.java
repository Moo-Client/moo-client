package com.mooclient.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.UUID;

/**
 * Custom Fabric network payload for synchronizing player emotes across clients.
 */
public record MooEmotePayload(UUID playerUuid, byte emoteType) implements CustomPayload {

    public static final byte TYPE_STOP = 0;
    public static final byte TYPE_HANDS_UP_START = 1;
    public static final byte TYPE_HANDS_UP_STOP = 2;
    public static final byte TYPE_FRONTFLIP = 3;
    public static final byte TYPE_BACKFLIP = 4;
    public static final byte TYPE_MEDITATION = 5;
    public static final byte TYPE_FRIENDLY_WAVE = 6;
    public static final byte TYPE_FACEPALM = 7;
    public static final byte TYPE_HANDSHAKE = 8;

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

    public static byte fromEmoteId(String emoteId) {
        if (emoteId == null) return TYPE_STOP;
        return switch (emoteId.toLowerCase()) {
            case "hands_up" -> TYPE_HANDS_UP_START;
            case "frontflip" -> TYPE_FRONTFLIP;
            case "backflip" -> TYPE_BACKFLIP;
            case "meditation" -> TYPE_MEDITATION;
            case "friendly_wave" -> TYPE_FRIENDLY_WAVE;
            case "facepalm" -> TYPE_FACEPALM;
            case "handshake" -> TYPE_HANDSHAKE;
            default -> TYPE_STOP;
        };
    }

    public static String toEmoteId(byte type) {
        return switch (type) {
            case TYPE_MEDITATION -> "meditation";
            case TYPE_FRIENDLY_WAVE -> "friendly_wave";
            case TYPE_FACEPALM -> "facepalm";
            case TYPE_FRONTFLIP -> "frontflip";
            case TYPE_BACKFLIP -> "backflip";
            case TYPE_HANDS_UP_START -> "hands_up";
            case TYPE_HANDSHAKE -> "handshake";
            default -> null;
        };
    }
}
