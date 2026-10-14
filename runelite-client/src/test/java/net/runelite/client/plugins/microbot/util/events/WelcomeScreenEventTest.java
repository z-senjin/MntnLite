package net.runelite.client.plugins.microbot.util.events;

import java.lang.reflect.Field;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Supplier;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class WelcomeScreenEventTest
{
	private Client client;
	private ClientThread clientThread;
	private Object previousClient;
	private Object previousClientThread;

	@Before
	public void before() throws Exception
	{
		client = mock(Client.class);
		clientThread = mock(ClientThread.class);
		previousClient = swapStatic("client", client);
		previousClientThread = swapStatic("clientThread", clientThread);
	}

	@After
	public void after() throws Exception
	{
		swapStatic("client", previousClient);
		swapStatic("clientThread", previousClientThread);
	}

	@Test
	public void doesNotTouchClientThreadWhenLoggedOut()
	{
		for (GameState state : new GameState[]{GameState.LOGIN_SCREEN, GameState.LOGGING_IN, GameState.LOADING, GameState.CONNECTION_LOST, GameState.HOPPING})
		{
			when(client.getGameState()).thenReturn(state);
			assertFalse(new WelcomeScreenEvent().validate());
		}
		verifyNoInteractions(clientThread);
	}

	@Test
	public void clientThreadTimeoutIsNotVisible()
	{
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(clientThread.runOnClientThreadOptional(any())).thenReturn(Optional.empty());

		assertFalse(new WelcomeScreenEvent().validate());
	}

	@Test
	public void validatesWhenLoggedInAndPlayButtonShown() throws Exception
	{
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		runClientThreadInline();
		when(client.getWidget(InterfaceID.WelcomeScreen.PLAY)).thenReturn(mock(Widget.class));

		assertTrue(new WelcomeScreenEvent().validate());
	}

	@Test
	public void hiddenPlayButtonDoesNotValidate() throws Exception
	{
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		runClientThreadInline();
		Widget play = mock(Widget.class);
		when(play.isHidden()).thenReturn(true);
		when(client.getWidget(InterfaceID.WelcomeScreen.PLAY)).thenReturn(play);

		assertFalse(new WelcomeScreenEvent().validate());
	}

	@Test
	public void executeCompletesWhenLoggedOutBeforeRunning()
	{
		when(client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
		when(clientThread.invoke(any(Supplier.class))).thenReturn(false);

		assertTrue(new WelcomeScreenEvent().execute());
	}

	@SuppressWarnings("unchecked")
	private void runClientThreadInline() throws Exception
	{
		when(clientThread.runOnClientThreadOptional(any())).thenAnswer(invocation ->
			Optional.ofNullable(((Callable<Object>) invocation.getArgument(0)).call()));
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
