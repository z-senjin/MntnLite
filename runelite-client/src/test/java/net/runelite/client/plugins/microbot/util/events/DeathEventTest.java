package net.runelite.client.plugins.microbot.util.events;

import java.lang.reflect.Field;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.playerstate.Rs2PlayerStateCache;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class DeathEventTest
{
	private static final WorldPoint DEATHS_OFFICE = new WorldPoint(3141, 5701, 0);
	private static final WorldPoint LUMBRIDGE = new WorldPoint(3222, 3218, 0);

	private Client client;
	private ClientThread clientThread;
	private Rs2PlayerStateCache playerStateCache;
	private Object previousClient;
	private Object previousClientThread;
	private Object previousPlayerStateCache;

	@Before
	public void before() throws Exception
	{
		client = mock(Client.class);
		clientThread = mock(ClientThread.class);
		playerStateCache = mock(Rs2PlayerStateCache.class);
		previousClient = swapStatic("client", client);
		previousClientThread = swapStatic("clientThread", clientThread);
		previousPlayerStateCache = swapStatic("rs2PlayerStateCache", playerStateCache);
	}

	@After
	public void after() throws Exception
	{
		swapStatic("client", previousClient);
		swapStatic("clientThread", previousClientThread);
		swapStatic("rs2PlayerStateCache", previousPlayerStateCache);
	}

	@Test
	public void constantsMatchGameval()
	{
		assertEquals(4517, VarPlayerID.TRACKING_DEATHS);
		assertEquals(12633, DEATHS_OFFICE.getRegionID());
	}

	@Test
	public void doesNotQueryStateWhenLoggedOut()
	{
		for (GameState state : new GameState[]{GameState.LOGIN_SCREEN, GameState.LOGGING_IN, GameState.LOADING, GameState.CONNECTION_LOST, GameState.HOPPING})
		{
			when(client.getGameState()).thenReturn(state);
			assertFalse(new DeathEvent().validate());
		}
		verifyNoInteractions(clientThread, playerStateCache);
	}

	@Test
	public void validatesInDeathsOfficeWithActiveDeath()
	{
		loggedInWith(1, DEATHS_OFFICE);

		assertTrue(new DeathEvent().validate());
	}

	@Test
	public void doesNotValidateWithoutActiveDeath()
	{
		loggedInWith(0, DEATHS_OFFICE);

		assertFalse(new DeathEvent().validate());
		verify(playerStateCache, never()).getLocalPlayerPosition();
	}

	@Test
	public void doesNotValidateOutsideDeathsOffice()
	{
		loggedInWith(1, LUMBRIDGE);

		assertFalse(new DeathEvent().validate());
	}

	@Test
	public void unknownLocationDoesNotValidate()
	{
		loggedInWith(1, null);

		assertFalse(new DeathEvent().validate());
	}

	private void loggedInWith(int deathVarp, WorldPoint location)
	{
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(playerStateCache.getVarpValue(VarPlayerID.TRACKING_DEATHS)).thenReturn(deathVarp);
		when(playerStateCache.getLocalPlayerPosition()).thenReturn(location);
	}

	private static Object swapStatic(String name, Object value) throws Exception
	{
		Field field = Microbot.class.getDeclaredField(name);
		field.setAccessible(true);
		Object previous = field.get(null);
		field.set(null, value);
		return previous;
	}
}
