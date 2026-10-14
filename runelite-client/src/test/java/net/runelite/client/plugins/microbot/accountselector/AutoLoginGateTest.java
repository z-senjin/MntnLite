package net.runelite.client.plugins.microbot.accountselector;

import net.runelite.api.GameState;
import net.runelite.client.plugins.microbot.breakhandler.breakhandlerv2.BreakHandlerV2State;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class AutoLoginGateTest {
    @Before @After public void resetBreakHandlers() {
        BreakHandlerV2State.setState(BreakHandlerV2State.WAITING_FOR_BREAK);
    }

    @Test public void autoLoginRunsWhenNoBreakIsActive() {
        assertFalse(AutoLoginScript.isBreakHandlerOwningLogin());
    }

    @Test public void autoLoginDefersToBreakHandlerV2DuringItsBreak() {
        for (BreakHandlerV2State state : new BreakHandlerV2State[] {
                BreakHandlerV2State.LOGOUT_REQUESTED, BreakHandlerV2State.LOGGED_OUT,
                BreakHandlerV2State.LOGIN_REQUESTED, BreakHandlerV2State.LOGGING_IN,
                BreakHandlerV2State.LOGIN_EXTENDED_SLEEP}) {
            BreakHandlerV2State.setState(state);
            assertTrue(state.name(), AutoLoginScript.isBreakHandlerOwningLogin());
        }
    }

    @Test public void onlyTheLoginScreenStartsALogin() {
        assertTrue(AutoLoginScript.canStartLogin(GameState.LOGIN_SCREEN));
        for (GameState state : new GameState[] {GameState.HOPPING, GameState.LOADING,
                GameState.LOGGING_IN, GameState.CONNECTION_LOST, GameState.LOGGED_IN,
                GameState.STARTING, GameState.UNKNOWN}) {
            assertFalse(state.name(), AutoLoginScript.canStartLogin(state));
        }
    }
}
