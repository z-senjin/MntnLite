package net.runelite.client.plugins.microbot;

import net.runelite.api.GameState;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MicrobotPluginHopCountTest
{
	@Test
	public void countsEachHopAndEachLoginAttemptOnce()
	{
		assertTrue(MicrobotPlugin.isLoginServerRequest(GameState.LOGGED_IN, GameState.HOPPING));
		assertTrue(MicrobotPlugin.isLoginServerRequest(GameState.LOGIN_SCREEN, GameState.LOGGING_IN));
		assertTrue(MicrobotPlugin.isLoginServerRequest(GameState.LOGIN_SCREEN_AUTHENTICATOR, GameState.LOGGING_IN));
		assertTrue(MicrobotPlugin.isLoginServerRequest(GameState.UNKNOWN, GameState.LOGGING_IN));
		assertTrue(MicrobotPlugin.isLoginServerRequest(GameState.CONNECTION_LOST, GameState.LOGGING_IN));
	}

	@Test
	public void ignoresTransitionsThatDoNotReachTheLoginServer()
	{
		assertFalse(MicrobotPlugin.isLoginServerRequest(GameState.HOPPING, GameState.HOPPING));
		assertFalse(MicrobotPlugin.isLoginServerRequest(GameState.HOPPING, GameState.LOGGING_IN));
		assertFalse(MicrobotPlugin.isLoginServerRequest(GameState.LOGGING_IN, GameState.LOGGING_IN));
		assertFalse(MicrobotPlugin.isLoginServerRequest(GameState.HOPPING, GameState.LOADING));
		assertFalse(MicrobotPlugin.isLoginServerRequest(GameState.LOADING, GameState.LOGGED_IN));
		assertFalse(MicrobotPlugin.isLoginServerRequest(GameState.LOGGED_IN, GameState.LOGIN_SCREEN));
	}
}
