package com.mooclient.emote;

import com.mooclient.emote.animation.IEmoteAnimation;
import com.mooclient.interaction.InteractionSceneConfig;
import com.mooclient.util.MooLanguage;
import net.minecraft.util.Identifier;

/**
 * Definicja pojedynczej emotki (obiekt danych).
 */
public class Emote {

    private final String id;
    private final String nameKey;
    private Identifier icon;
    private EmoteType type;
    private int participantCount;
    private int durationTicks;
    private boolean looping;
    private boolean free;
    private boolean forcesThirdPerson;
    private boolean local = false;
    private IEmoteAnimation animation;
    private InteractionSceneConfig sceneConfig;

    public Emote(String id, String nameKey, Identifier icon, EmoteType type,
                 int participantCount, int durationTicks, boolean looping,
                 boolean free, boolean forcesThirdPerson,
                 IEmoteAnimation animation, InteractionSceneConfig sceneConfig) {
        this.id = id;
        this.nameKey = nameKey;
        this.icon = icon;
        this.type = type;
        this.participantCount = participantCount;
        this.durationTicks = durationTicks;
        this.looping = looping;
        this.free = free;
        this.forcesThirdPerson = forcesThirdPerson;
        this.animation = animation;
        this.sceneConfig = sceneConfig;
    }

    public String getId() {
        return id;
    }

    public String getNameKey() {
        return nameKey;
    }

    public String getDisplayName() {
        if (nameKey != null && !nameKey.isEmpty()) {
            String translated = MooLanguage.get(nameKey);
            if (!translated.equals(nameKey) && !translated.isEmpty()) {
                return translated;
            }
        }
        if (id != null && !id.isEmpty()) {
            String fromWheel = MooLanguage.get("emotes_wheel_" + id);
            if (!fromWheel.equals("emotes_wheel_" + id) && !fromWheel.isEmpty()) {
                return fromWheel;
            }
            return formatId(id);
        }
        return "";
    }

    private static String formatId(String rawId) {
        if (rawId == null || rawId.isEmpty()) return "";
        String[] parts = rawId.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(Character.toUpperCase(part.charAt(0)));
                if (part.length() > 1) {
                    sb.append(part.substring(1).toLowerCase());
                }
            }
        }
        return sb.toString();
    }

    public Identifier getIcon() {
        return icon;
    }

    public void setIcon(Identifier icon) {
        if (icon != null) {
            this.icon = icon;
        }
    }

    public EmoteType getType() {
        return type;
    }

    public void setType(EmoteType type) {
        this.type = type;
    }

    public int getParticipantCount() {
        return participantCount;
    }

    public void setParticipantCount(int participantCount) {
        this.participantCount = participantCount;
    }

    public int getDurationTicks() {
        return durationTicks;
    }

    public void setDurationTicks(int durationTicks) {
        this.durationTicks = durationTicks;
    }

    public boolean isLooping() {
        return looping;
    }

    public void setLooping(boolean looping) {
        this.looping = looping;
    }

    public boolean isFree() {
        return free;
    }

    public void setFree(boolean free) {
        this.free = free;
    }

    public boolean isLocal() {
        return local;
    }

    public void setLocal(boolean local) {
        this.local = local;
    }

    public boolean isForcesThirdPerson() {
        return forcesThirdPerson;
    }

    public void setForcesThirdPerson(boolean forcesThirdPerson) {
        this.forcesThirdPerson = forcesThirdPerson;
    }

    public boolean isMultiplayer() {
        return type == EmoteType.MULTIPLAYER;
    }

    public IEmoteAnimation getAnimation() {
        return animation;
    }

    public void setAnimation(IEmoteAnimation animation) {
        this.animation = animation;
    }

    public InteractionSceneConfig getSceneConfig() {
        return sceneConfig;
    }

    public void setSceneConfig(InteractionSceneConfig sceneConfig) {
        this.sceneConfig = sceneConfig;
    }
}
