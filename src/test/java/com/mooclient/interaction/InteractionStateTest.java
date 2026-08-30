package com.mooclient.interaction;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class InteractionStateTest {

    @Test
    public void testValidStateTransitions() {
        // REQUESTED -> ACCEPTED -> AUTHORIZED -> PREPARING -> STARTED -> COMPLETED
        Assertions.assertTrue(InteractionState.REQUESTED.canTransitionTo(InteractionState.ACCEPTED));
        Assertions.assertTrue(InteractionState.ACCEPTED.canTransitionTo(InteractionState.AUTHORIZED));
        Assertions.assertTrue(InteractionState.AUTHORIZED.canTransitionTo(InteractionState.PREPARING));
        Assertions.assertTrue(InteractionState.PREPARING.canTransitionTo(InteractionState.STARTED));
        Assertions.assertTrue(InteractionState.STARTED.canTransitionTo(InteractionState.COMPLETED));

        // Cancellations / Interruptions from active states
        Assertions.assertTrue(InteractionState.REQUESTED.canTransitionTo(InteractionState.DECLINED));
        Assertions.assertTrue(InteractionState.REQUESTED.canTransitionTo(InteractionState.EXPIRED));
        Assertions.assertTrue(InteractionState.ACCEPTED.canTransitionTo(InteractionState.CANCELLED));
        Assertions.assertTrue(InteractionState.STARTED.canTransitionTo(InteractionState.INTERRUPTED));
        Assertions.assertTrue(InteractionState.STARTED.canTransitionTo(InteractionState.DISCONNECTED));
    }

    @Test
    public void testTerminalStatesCannotTransition() {
        Assertions.assertTrue(InteractionState.COMPLETED.isTerminal());
        Assertions.assertTrue(InteractionState.DECLINED.isTerminal());
        Assertions.assertTrue(InteractionState.CANCELLED.isTerminal());
        Assertions.assertTrue(InteractionState.EXPIRED.isTerminal());
        Assertions.assertTrue(InteractionState.INTERRUPTED.isTerminal());
        Assertions.assertTrue(InteractionState.FAILED.isTerminal());

        Assertions.assertFalse(InteractionState.COMPLETED.canTransitionTo(InteractionState.STARTED));
        Assertions.assertFalse(InteractionState.DECLINED.canTransitionTo(InteractionState.ACCEPTED));
        Assertions.assertFalse(InteractionState.INTERRUPTED.canTransitionTo(InteractionState.STARTED));
    }
}
