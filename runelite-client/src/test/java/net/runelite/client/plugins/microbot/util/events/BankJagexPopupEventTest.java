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

public class BankJagexPopupEventTest
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
	public void doesNotTouchClientThreadOnLoginScreen()
	{
		for (GameState state : new GameState[]{GameState.LOGIN_SCREEN, GameState.LOGGING_IN, GameState.LOADING, GameState.CONNECTION_LOST})
		{
			when(client.getGameState()).thenReturn(state);
			assertFalse(new BankJagexPopupEvent().validate());
		}
		verifyNoInteractions(clientThread);
	}

	@Test
	public void clientThreadTimeoutIsNotVisible()
	{
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(clientThread.runOnClientThreadOptional(any())).thenReturn(Optional.empty());

		assertFalse(new BankJagexPopupEvent().validate());
	}

	@Test
	public void validatesWhenLoggedInAndNotNowButtonShown() throws Exception
	{
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		runClientThreadInline();
		Widget popup = popupWithChild(textWidget(BankJagexPopupEvent.NOT_NOW_TEXT));
		when(client.getWidget(InterfaceID.Popupoverlay.CONTAINER)).thenReturn(popup);

		assertTrue(new BankJagexPopupEvent().validate());
	}

	@Test
	public void popupWithoutNotNowButtonDoesNotValidate() throws Exception
	{
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		runClientThreadInline();
		Widget popup = popupWithChild(textWidget("Close"));
		when(client.getWidget(InterfaceID.Popupoverlay.CONTAINER)).thenReturn(popup);

		BankJagexPopupEvent event = new BankJagexPopupEvent();
		assertFalse(event.validate());
		assertTrue(event.execute());
	}

	@Test
	public void executeWithoutButtonCompletesWithoutWaiting()
	{
		when(client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);

		assertTrue(new BankJagexPopupEvent().execute());
		verify(client, never()).getWidget(anyInt());
	}

	@Test
	public void findNotNowButtonHandlesMissingAndHiddenPopup()
	{
		assertNull(BankJagexPopupEvent.findNotNowButton(null));

		when(client.getWidget(InterfaceID.Popupoverlay.CONTAINER)).thenReturn(null);
		assertNull(BankJagexPopupEvent.findNotNowButton(client));

		Widget hidden = popupWithChild(textWidget(BankJagexPopupEvent.NOT_NOW_TEXT));
		when(hidden.isHidden()).thenReturn(true);
		when(client.getWidget(InterfaceID.Popupoverlay.CONTAINER)).thenReturn(hidden);
		assertNull(BankJagexPopupEvent.findNotNowButton(client));
	}

	@Test
	public void findNotNowButtonReturnsButton()
	{
		Widget button = textWidget("<col=ffffff>Not now</col>");
		Widget popup = popupWithChild(button);
		when(client.getWidget(InterfaceID.Popupoverlay.CONTAINER)).thenReturn(popup);

		assertSame(button, BankJagexPopupEvent.findNotNowButton(client));
	}

	@SuppressWarnings("unchecked")
	private void runClientThreadInline() throws Exception
	{
		when(clientThread.runOnClientThreadOptional(any())).thenAnswer(invocation ->
			Optional.ofNullable(((Callable<Object>) invocation.getArgument(0)).call()));
	}

	private static Widget popupWithChild(Widget child)
	{
		Widget popup = textWidget("");
		when(popup.getChildren()).thenReturn(new Widget[]{child});
		return popup;
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
