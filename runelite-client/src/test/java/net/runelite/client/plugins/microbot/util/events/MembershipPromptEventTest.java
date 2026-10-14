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

public class MembershipPromptEventTest
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
	public void doesNotTouchClientThreadWhenNotLoggedIn()
	{
		for (GameState state : new GameState[]{GameState.LOGIN_SCREEN, GameState.LOGGING_IN, GameState.LOADING, GameState.HOPPING})
		{
			when(client.getGameState()).thenReturn(state);
			assertFalse(new MembershipPromptEvent().validate());
		}
		verifyNoInteractions(clientThread);
	}

	@Test
	public void clientThreadTimeoutIsNotVisible()
	{
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(clientThread.runOnClientThreadOptional(any())).thenReturn(Optional.empty());

		assertFalse(new MembershipPromptEvent().validate());
	}

	@Test
	public void validatesWhenBondRedeemPromptIsOpen() throws Exception
	{
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		runClientThreadInline();
		visible(InterfaceID.F2PBondRedeem.UNIVERSE);
		visible(InterfaceID.F2PBondRedeem.CLOSE);

		assertTrue(new MembershipPromptEvent().validate());
	}

	@Test
	public void findsTheCloseButtonOfEachPrompt()
	{
		for (int[] prompt : MembershipPromptEvent.PROMPTS)
		{
			Client promptClient = mock(Client.class);
			Widget root = widget(false);
			Widget close = widget(false);
			when(promptClient.getWidget(prompt[0])).thenReturn(root);
			when(promptClient.getWidget(prompt[1])).thenReturn(close);

			assertSame(close, MembershipPromptEvent.findCloseButton(promptClient));
		}
	}

	@Test
	public void ignoresMissingOrHiddenPrompts()
	{
		assertNull(MembershipPromptEvent.findCloseButton(null));
		assertNull(MembershipPromptEvent.findCloseButton(client));

		Widget hiddenRoot = widget(true);
		Widget visibleRoot = widget(false);
		Widget hiddenClose = widget(true);
		Widget visibleClose = widget(false);

		when(client.getWidget(InterfaceID.F2PBondRedeem.UNIVERSE)).thenReturn(hiddenRoot);
		when(client.getWidget(InterfaceID.F2PBondRedeem.CLOSE)).thenReturn(visibleClose);
		assertNull(MembershipPromptEvent.findCloseButton(client));

		when(client.getWidget(InterfaceID.F2PBondRedeem.UNIVERSE)).thenReturn(visibleRoot);
		when(client.getWidget(InterfaceID.F2PBondRedeem.CLOSE)).thenReturn(hiddenClose);
		assertNull(MembershipPromptEvent.findCloseButton(client));
	}

	@Test
	public void executeWithoutPromptCompletesWithoutWaiting()
	{
		when(client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);

		assertTrue(new MembershipPromptEvent().execute());
		verify(client, never()).getWidget(anyInt());
	}

	@SuppressWarnings("unchecked")
	private void runClientThreadInline() throws Exception
	{
		when(clientThread.runOnClientThreadOptional(any())).thenAnswer(invocation ->
			Optional.ofNullable(((Callable<Object>) invocation.getArgument(0)).call()));
	}

	private void visible(int componentId)
	{
		Widget widget = widget(false);
		when(client.getWidget(componentId)).thenReturn(widget);
	}

	private static Widget widget(boolean hidden)
	{
		Widget widget = mock(Widget.class);
		when(widget.isHidden()).thenReturn(hidden);
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
