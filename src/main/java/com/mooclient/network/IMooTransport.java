package com.mooclient.network;

import java.util.UUID;

/**
 * Abstrakcja warstwy transportowej czasu rzeczywistego dla Moo Client.
 * Umożliwia łatwą wymianę brokera MQTT na dedykowany Moo Backend w przyszłości.
 */
public interface IMooTransport {

    void sendPresence(String username, UUID uuid);

    void sendSoloEmote(String emoteId, boolean start);

    void sendInteractionRequest(UUID interactionId, String emoteId, UUID targetUuid, String targetName);

    void sendInteractionResponse(UUID interactionId, boolean accepted, long startEpochMs);

    void sendInteractionCancel(UUID interactionId, String reason);
}
