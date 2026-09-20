package net.runelite.client.plugins.microbot.util.input;

import net.runelite.api.Client;
import net.runelite.client.plugins.microbot.Microbot;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.awt.Canvas;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A key held when the window deactivates never delivers its KEY_RELEASED, and since a held key
 * suppresses idle resume the stale entry pins HUMAN forever.
 *
 * <p>Observed live: Ctrl held for a screenshot, cleared only by pressing Ctrl again.
 */
public class FocusLossReleasesHeldInputTest
{
	private Canvas canvas;
	private final AtomicLong now = new AtomicLong(1_000_000L);
	private Object previousClient;

	@Before
	public void before() throws Exception
	{
		canvas = new Canvas();
		Client client = mock(Client.class);
		when(client.getCanvas()).thenReturn(canvas);
		when(client.isStretchedEnabled()).thenReturn(false);
		previousClient = swapStatic("client", client);

		PointerState.reset();
		InputArbiter.resetForTest();
		InputArbiter.setClockForTest(now::get);
		CanvasInputListener.detach();
		CanvasInputListener.attach();
	}

	@After
	public void after() throws Exception
	{
		CanvasInputListener.detach();
		swapStatic("client", previousClient);
		PointerState.reset();
		InputArbiter.resetForTest();
	}

	@Test
	public void aKeyHeldWhenFocusLeavesDoesNotPinHumanForever()
	{
		PointerState.setFromBot(100, 100);
		pressKey(KeyEvent.VK_CONTROL);
		assertTrue(InputArbiter.isHuman());

		// Long past the idle window; without the fix this stays HUMAN indefinitely.
		advanceMs(60_000);
		assertTrue("a held key suppresses resume while focus is on the canvas", InputArbiter.isHuman());

		loseFocus();

		assertEquals("none", InputDiagnostics.readout().get("real held"));
		advanceMs(1_801);
		assertFalse("the bot must resume once the releases stop coming", InputArbiter.isHuman());
	}

	@Test
	public void focusLossStartsTheIdleWindowFreshRatherThanResumingInstantly()
	{
		PointerState.setFromBot(100, 100);
		pressKey(KeyEvent.VK_CONTROL);
		advanceMs(60_000);

		loseFocus();

		assertTrue("the user was interacting a moment ago", InputArbiter.isHuman());
		advanceMs(1_799);
		assertTrue(InputArbiter.isHuman());
		advanceMs(2);
		assertFalse(InputArbiter.isHuman());
	}

	@Test
	public void aHeldMouseButtonIsDroppedToo()
	{
		PointerState.setFromBot(100, 100);
		dispatch(new MouseEvent(canvas, MouseEvent.MOUSE_PRESSED, now.get(), 0, 10, 10, 1, false,
			MouseEvent.BUTTON1));
		assertTrue(InputArbiter.isRealButtonOrKeyDown());

		loseFocus();

		assertFalse("alt-tabbing mid-drag loses the release too", InputArbiter.isRealButtonOrKeyDown());
	}

	@Test
	public void focusLossWithNothingHeldChangesNothing()
	{
		PointerState.setFromBot(100, 100);
		assertFalse(InputArbiter.isHuman());

		loseFocus();

		assertFalse("clearing nothing must not look like activity", InputArbiter.isHuman());
	}

	@Test
	public void temporaryFocusLossCountsBecauseWindowDeactivationReportsOne()
	{
		PointerState.setFromBot(100, 100);
		pressKey(KeyEvent.VK_SHIFT);

		for (FocusListener listener : canvas.getFocusListeners())
		{
			listener.focusLost(new FocusEvent(canvas, FocusEvent.FOCUS_LOST, true));
		}

		assertEquals("none", InputDiagnostics.readout().get("real held"));
	}

	private void advanceMs(long millis)
	{
		now.addAndGet(millis * 1_000_000L);
	}

	private void pressKey(int keyCode)
	{
		KeyEvent event = new KeyEvent(canvas, KeyEvent.KEY_PRESSED, now.get(), 0, keyCode,
			KeyEvent.CHAR_UNDEFINED);
		for (KeyListener listener : canvas.getKeyListeners())
		{
			listener.keyPressed(event);
		}
	}

	private void loseFocus()
	{
		for (FocusListener listener : canvas.getFocusListeners())
		{
			listener.focusLost(new FocusEvent(canvas, FocusEvent.FOCUS_LOST, false));
		}
	}

	private void dispatch(java.awt.AWTEvent event)
	{
		canvas.dispatchEvent(event);
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
