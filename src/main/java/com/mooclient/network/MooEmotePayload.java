package com.mooclient.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.UUID;

/**
 * Custom Fabric network payload for synchronizing player emotes, frontflips and backflips
 * across all Moo Client players on multiplayer servers.
 */
public record MooEmotePayload(UUID playerUuid, byte emoteType) implements CustomPayload {

    public static final byte TYPE_STOP = 0;
    public static final byte TYPE_HANDS_UP_START = 1;
    public static final byte TYPE_HANDS_UP_STOP = 2;
    public static final byte TYPE_FRONTFLIP = 3;
    public static final byte TYPE_BACKFLIP = 4;

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
}
