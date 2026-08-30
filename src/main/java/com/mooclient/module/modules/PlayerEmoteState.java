package com.mooclient.module.modules;

import com.mooclient.emote.EmoteEngine;
import com.mooclient.emote.EmotePlayerState;

import java.util.UUID;

/**
 * @deprecated Zastąpione przez {@link com.mooclient.emote.EmotePlayerState} i {@link com.mooclient.emote.EmoteEngine}.
 */
@Deprecated
public class PlayerEmoteState {

    private final UUID uuid;

    public PlayerEmoteState(UUID uuid) {
        this.uuid = uuid;
    }

    public UUID getUuid() {
        return uuid;
    }

    public boolean isIdle() {
        EmotePlayerState state = EmoteEngine.getInstance().getPlayerStateIfExists(uuid);
        return state == null || !state.isRendering();
    }

    public void onTick() {
    }

    public void stopEmotes() {
        if (uuid != null) {
            EmoteEngine.getInstance().stopRemoteEmote(uuid);
        } else {
            EmoteEngine.getInstance().stopLocalEmote();
        }
    }
}
