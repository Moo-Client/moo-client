package com.mooclient.interaction;

import java.util.*;

/**
 * Reprezentacja pojedynczej sesji interakcji multiplayerowej.
 */
public class Interaction {

    private final UUID interactionId;
    private final String emoteId;
    private final UUID initiatorUuid;
    private final String initiatorName;
    private final UUID targetUuid;
    private final String targetName;
    private final Map<Integer, UUID> participants = new HashMap<>();

    private InteractionState state = InteractionState.REQUESTED;
    private final long createdAtMs;
    private long startEpochMs = 0L;
    private InteractionSceneConfig sceneConfig;

    public Interaction(UUID interactionId, String emoteId,
                       UUID initiatorUuid, String initiatorName,
                       UUID targetUuid, String targetName,
                       InteractionSceneConfig sceneConfig) {
        this.interactionId = interactionId != null ? interactionId : UUID.randomUUID();
        this.emoteId = emoteId;
        this.initiatorUuid = initiatorUuid;
        this.initiatorName = initiatorName != null ? initiatorName : "Player";
        this.targetUuid = targetUuid;
        this.targetName = targetName != null ? targetName : "Player";
        this.createdAtMs = System.currentTimeMillis();
        this.sceneConfig = sceneConfig;

        if (initiatorUuid != null) {
            participants.put(0, initiatorUuid);
        }
        if (targetUuid != null) {
            participants.put(1, targetUuid);
        }
    }

    public UUID getInteractionId() {
        return interactionId;
    }

    public String getEmoteId() {
        return emoteId;
    }

    public UUID getInitiatorUuid() {
        return initiatorUuid;
    }

    public String getInitiatorName() {
        return initiatorName;
    }

    public UUID getTargetUuid() {
        return targetUuid;
    }

    public String getTargetName() {
        return targetName;
    }

    public Map<Integer, UUID> getParticipants() {
        return Collections.unmodifiableMap(participants);
    }

    public int getSlotForPlayer(UUID uuid) {
        if (uuid == null) return -1;
        for (Map.Entry<Integer, UUID> entry : participants.entrySet()) {
            if (uuid.equals(entry.getValue())) {
                return entry.getKey();
            }
        }
        return -1;
    }

    public InteractionState getState() {
        return state;
    }

    public void setState(InteractionState state) {
        this.state = state;
    }

    public long getCreatedAtMs() {
        return createdAtMs;
    }

    public long getStartEpochMs() {
        return startEpochMs;
    }

    public void setStartEpochMs(long startEpochMs) {
        this.startEpochMs = startEpochMs;
    }

    public InteractionSceneConfig getSceneConfig() {
        return sceneConfig;
    }

    public void setSceneConfig(InteractionSceneConfig sceneConfig) {
        this.sceneConfig = sceneConfig;
    }

    public boolean isExpired(long timeoutMs) {
        return (System.currentTimeMillis() - createdAtMs) > timeoutMs;
    }

    public boolean isActive() {
        return state == InteractionState.ACCEPTED
                || state == InteractionState.AUTHORIZED
                || state == InteractionState.PREPARING
                || state == InteractionState.STARTED;
    }
}
