package com.mooclient.interaction;

/**
 * Kontrolowany automat stanowy dla interakcji multiplayerowych.
 */
public enum InteractionState {
    NONE,
    REQUESTED,      // Wysłano / odebrano zaproszenie (Pending)
    ACCEPTED,       // Zaakceptowano zaproszenie
    AUTHORIZED,     // Zautoryzowano uprawnienia przez backend
    PREPARING,      // Przygotowywanie sceny i synchronizacja czasu
    STARTED,        // Aktywne odtwarzanie animacji (Active Scene)
    COMPLETED,      // Pomyślnie zakończono interakcję

    // Stany terminalne / błędy:
    DECLINED,       // Odrzucono zaproszenie
    EXPIRED,        // Zaproszenie wygasło (np. gracz odszedł >3.0m lub minął czas)
    CANCELLED,      // Anulowano manualnie
    INTERRUPTED,    // Przerwano akcją gracza (skok, atak, obrażenia, sneak)
    DISCONNECTED,   // Gracz rozłączył się z serwerem
    FAILED;         // Błąd autoryzacji lub synchronizacji

    public boolean isTerminal() {
        return this == COMPLETED || this == DECLINED || this == EXPIRED || this == CANCELLED
                || this == INTERRUPTED || this == DISCONNECTED || this == FAILED;
    }

    public boolean isPending() {
        return this == REQUESTED;
    }

    public boolean isActive() {
        return this == ACCEPTED || this == AUTHORIZED || this == PREPARING || this == STARTED;
    }

    public boolean canTransitionTo(InteractionState next) {
        if (next == null || isTerminal()) return false;
        if (this == next) return true;

        return switch (this) {
            case NONE -> next == REQUESTED;
            case REQUESTED -> next == ACCEPTED || next == DECLINED || next == EXPIRED || next == CANCELLED || next == FAILED;
            case ACCEPTED -> next == AUTHORIZED || next == CANCELLED || next == FAILED || next == INTERRUPTED || next == DISCONNECTED;
            case AUTHORIZED -> next == PREPARING || next == CANCELLED || next == FAILED || next == INTERRUPTED || next == DISCONNECTED;
            case PREPARING -> next == STARTED || next == CANCELLED || next == INTERRUPTED || next == DISCONNECTED || next == FAILED;
            case STARTED -> next == COMPLETED || next == CANCELLED || next == INTERRUPTED || next == DISCONNECTED;
            default -> false;
        };
    }
}
