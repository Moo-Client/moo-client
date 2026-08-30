package com.mooclient.interaction;

/**
 * Czysto wizualna transformacja gracza w scenie (przesunięcie i rotacja w MatrixStack).
 * WAŻNE: Nigdy nie modyfikuje rzeczywistej pozycji gracza ani jego hitboxa w świecie Minecraft.
 */
public class SceneTransform {

    public float visualOffsetX = 0.0f;
    public float visualOffsetY = 0.0f;
    public float visualOffsetZ = 0.0f;

    public float visualYaw = 0.0f;
    public float visualPitch = 0.0f;
    public float visualRoll = 0.0f;

    public SceneTransform() {
    }

    public SceneTransform(float visualOffsetX, float visualOffsetY, float visualOffsetZ, float visualYaw) {
        this.visualOffsetX = visualOffsetX;
        this.visualOffsetY = visualOffsetY;
        this.visualOffsetZ = visualOffsetZ;
        this.visualYaw = visualYaw;
    }

    public void reset() {
        this.visualOffsetX = 0.0f;
        this.visualOffsetY = 0.0f;
        this.visualOffsetZ = 0.0f;
        this.visualYaw = 0.0f;
        this.visualPitch = 0.0f;
        this.visualRoll = 0.0f;
    }

    public SceneTransform copy() {
        SceneTransform c = new SceneTransform(visualOffsetX, visualOffsetY, visualOffsetZ, visualYaw);
        c.visualPitch = this.visualPitch;
        c.visualRoll = this.visualRoll;
        return c;
    }
}
