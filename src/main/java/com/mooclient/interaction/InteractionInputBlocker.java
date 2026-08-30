package com.mooclient.interaction;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.Input;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.PlayerInput;

/**
 * Kontroler blokady ruchu oraz nasłuchiwania zdarzeń przerywających scenę interakcji
 * (Skok, Atak/LMB, Użycie/RMB, Kucanie/Sneak, Obrażenia, Knockback, Śmierć).
 */
public class InteractionInputBlocker {

    private static float lastHealth = -1.0f;

    /**
     * Modyfikuje input gracza podczas aktywnej interakcji (blokada WASD i sprintu).
     */
    public static void processPlayerInput(Input input) {
        if (input == null) return;

        if (InteractionEngine.getInstance().hasActiveInteraction()) {
            if (input.playerInput != null) {
                // Wykrycie akcji przerywających (Skok lub Kucanie)
                if (input.playerInput.jump() || input.playerInput.sneak()) {
                    InteractionEngine.getInstance().cancelCurrentInteraction("USER_ACTION");
                    return;
                }
                input.playerInput = PlayerInput.DEFAULT;
            }

            // Blokada chodzenia i sprintu
            input.movementForward = 0.0f;
            input.movementSideways = 0.0f;
        }
    }

    /**
     * Sprawdza zdarzenia fizyczne gracza (obrażenia, śmierć, knockback).
     */
    public static void checkPlayerDamageAndInterruption(ClientPlayerEntity player) {
        if (player == null) {
            lastHealth = -1.0f;
            return;
        }

        if (InteractionEngine.getInstance().hasActiveInteraction()) {
            float health = player.getHealth();
            if (lastHealth >= 0.0f && health < lastHealth) {
                // Gracz otrzymał obrażenia -> natychmiastowe przerwanie
                InteractionEngine.getInstance().cancelCurrentInteraction("DAMAGE_TAKEN");
            }

            if (!player.isAlive()) {
                // Gracz zginął -> natychmiastowe przerwanie
                InteractionEngine.getInstance().cancelCurrentInteraction("PLAYER_DIED");
            }

            if (player.hurtTime > 0) {
                InteractionEngine.getInstance().cancelCurrentInteraction("KNOCKBACK");
            }
        }

        lastHealth = player.getHealth();
    }

    /**
     * Wywoływane przy kliknięciu lewym lub prawym przyciskiem myszy (atak lub użycie).
     */
    public static void onPlayerAction() {
        if (InteractionEngine.getInstance().hasActiveInteraction()) {
            InteractionEngine.getInstance().cancelCurrentInteraction("USER_ACTION");
        }
    }
}
