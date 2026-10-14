package net.runelite.client.plugins.microbot.util.events;

import java.lang.reflect.Field;
import java.util.Optional;
import java.util.concurrent.Callable;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class BankTutorialEventTest
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
	public void informationBoxIdMatchesLegacyComponent()
	{
		assertEquals(43515912, InterfaceID.Screenhighlight.INFORMATION_BOX);
	}

	@Test
	public void doesNotTouchClientThreadWhenLoggedOut()
	{
		for (GameState state : new GameState[]{GameState.LOGIN_SCREEN, GameState.LOGGING_IN, GameState.LOADING, GameState.CONNECTION_LOST, GameState.HOPPING})
		{
			when(client.getGameState()).thenReturn(state);
			assertFalse(new BankTutorialEvent().validate());
		}
		verifyNoInteractions(clientThread);
	}

	@Test
	public void clientThreadTimeoutIsNotVisible()
	{
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(clientThread.runOnClientThreadOptional(any())).thenReturn(Optional.empty());

		assertFalse(new BankTutorialEvent().validate());
	}

	@Test
	public void validatesWhenLoggedInAndCloseButtonShown() throws Exception
	{
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		runClientThreadInline();
		Widget box = boxWithChild(textWidget(BankTutorialEvent.CLOSE_TEXT));
		when(client.getWidget(InterfaceID.Screenhighlight.INFORMATION_BOX)).thenReturn(box);

		assertTrue(new BankTutorialEvent().validate());
	}

	@Test
	public void informationBoxWithoutCloseButtonDoesNotRefire() throws Exception
	{
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		runClientThreadInline();
		Widget box = boxWithChild(textWidget("Next"));
		when(client.getWidget(InterfaceID.Screenhighlight.INFORMATION_BOX)).thenReturn(box);

		BankTutorialEvent event = new BankTutorialEvent();
		assertFalse(event.validate());
		assertTrue(event.execute());
	}

	@Test
	public void executeWithoutButtonCompletesWithoutWaiting()
	{
		when(client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);

		assertTrue(new BankTutorialEvent().execute());
		verify(client, never()).getWidget(anyInt());
	}

	@Test
	public void findCloseButtonHandlesMissingAndHiddenBox()
	{
		assertNull(BankTutorialEvent.findCloseButton(null));

		when(client.getWidget(InterfaceID.Screenhighlight.INFORMATION_BOX)).thenReturn(null);
		assertNull(BankTutorialEvent.findCloseButton(client));

		Widget hidden = boxWithChild(textWidget(BankTutorialEvent.CLOSE_TEXT));
		when(hidden.isHidden()).thenReturn(true);
		when(client.getWidget(InterfaceID.Screenhighlight.INFORMATION_BOX)).thenReturn(hidden);
		assertNull(BankTutorialEvent.findCloseButton(client));
	}

	@Test
	public void findCloseButtonReturnsButton()
	{
		Widget button = textWidget("<col=ffffff>Close</col>");
		Widget box = boxWithChild(button);
		when(client.getWidget(InterfaceID.Screenhighlight.INFORMATION_BOX)).thenReturn(box);

		assertSame(button, BankTutorialEvent.findCloseButton(client));
	}

	@SuppressWarnings("unchecked")
	private void runClientThreadInline() throws Exception
	{
		when(clientThread.runOnClientThreadOptional(any())).thenAnswer(invocation ->
			Optional.ofNullable(((Callable<Object>) invocation.getArgument(0)).call()));
	}

	private static Widget boxWithChild(Widget child)
	{
		Widget box = textWidget("");
		when(box.getChildren()).thenReturn(new Widget[]{child});
		return box;
	}

	private static Widget textWidget(String text)
	{
		Widget widget = mock(Widget.class);
		when(widget.getText()).thenReturn(text);
		when(widget.getName()).thenReturn("");
		return widget;
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
