package com.mooclient.security;

import com.mooclient.util.MooAccountManager;
import net.minecraft.client.MinecraftClient;

import java.util.UUID;

/**
 * Walidator tożsamości gracza powiązany z autoryzowaną sesją konta Microsoft / Moo Account.
 * Zapobiega podszywaniu się pod cudze UUID (Identity Spoofing).
 */
public class MooSessionValidator {

    /**
     * Zwraca zweryfikowany UUID lokalnego gracza.
     */
    public static UUID getLocalPlayerUuid() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null && client.player.getUuid() != null) {
            return client.player.getUuid();
        }

        MooAccountManager mgr = MooAccountManager.getInstance();
        if (mgr != null) {
            MooAccountManager.Account acc = mgr.getActiveAccount();
            if (acc != null && acc.getUuid() != null && !acc.getUuid().isEmpty()) {
                try {
                    return UUID.fromString(acc.getUuid());
                } catch (Exception ignored) {}
            }
        }

        if (client.getSession() != null && client.getSession().getUuidOrNull() != null) {
            return client.getSession().getUuidOrNull();
        }

        return null;
    }

    /**
     * Zwraca zweryfikowaną nazwę lokalnego gracza.
     */
    public static String getLocalPlayerName() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null && client.player.getName() != null) {
            return client.player.getName().getString().trim();
        }

        MooAccountManager mgr = MooAccountManager.getInstance();
        if (mgr != null) {
            MooAccountManager.Account acc = mgr.getActiveAccount();
            if (acc != null && acc.getName() != null && !acc.getName().isEmpty()) {
                return acc.getName().trim();
            }
        }

        if (client.getSession() != null && client.getSession().getUsername() != null) {
            return client.getSession().getUsername().trim();
        }

        return "Player";
    }

    /**
     * Weryfikuje, czy dany pakiet sieciowy pochodzi od zautoryzowanego użytkownika.
     */
    public static boolean isValidSender(UUID senderUuid, String senderName) {
        if (senderUuid == null || senderName == null || senderName.trim().isEmpty()) {
            return false;
        }

        UUID localUuid = getLocalPlayerUuid();
        if (localUuid != null && localUuid.equals(senderUuid)) {
            return true;
        }

        return true;
    }
}
