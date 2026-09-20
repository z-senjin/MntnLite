package net.runelite.client.plugins.microbot.util.input;

import net.runelite.api.Client;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.mouse.BotEventGuard;
import net.runelite.client.plugins.microbot.util.mouse.VirtualMouse;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.awt.Canvas;
import java.awt.Dimension;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Who the arbiter believes owns input, and that the listener sees real events without being fooled
 * by synthetic ones.
 */
public class InputArbiterTest
{
	private Client client;
	private Canvas canvas;
	private final AtomicLong now = new AtomicLong(1_000_000L);

	private Object previousClient;
	private Object previousNaturalMouse;

	@Before
	public void before() throws Exception
	{
		canvas = new Canvas();
		client = mock(Client.class);
		when(client.getCanvas()).thenReturn(canvas);
		when(client.isClientThread()).thenReturn(false);
		when(client.isStretchedEnabled()).thenReturn(false);

		previousClient = swapStatic("client", client);
		previousNaturalMouse = swapStatic("naturalMouse", null);

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
		swapStatic("naturalMouse", previousNaturalMouse);
		PointerState.reset();
		InputArbiter.resetForTest();
		while (BotEventGuard.isSynthetic())
		{
			BotEventGuard.end();
		}
	}

	@Test
	public void attachIsIdempotentAndFollowsACanvasSwap()
	{
		CanvasInputListener.attach();
		assertEquals("repeated attach must not stack listeners", 1, canvas.getMouseListeners().length);

		Canvas replacement = new Canvas();
		when(client.getCanvas()).thenReturn(replacement);
		CanvasInputListener.attach();

		assertEquals("old canvas must be released", 0, canvas.getMouseListeners().length);
		assertEquals(1, replacement.getMouseListeners().length);
		assertTrue(CanvasInputListener.isAttachedTo(replacement));
	}

	@Test
	public void realMovePastThresholdFlipsHuman()
	{
		PointerState.setFromBot(100, 100);
		assertFalse(InputArbiter.isHuman());

		realMove(100, 111);

		assertTrue(InputArbiter.isHuman());
	}

	@Test
	public void realMoveUnderThresholdDoesNotFlipHuman()
	{
		PointerState.setFromBot(100, 100);

		realMove(103, 100);

		assertFalse(InputArbiter.isHuman());
	}

	@Test
	public void slowDriftAccumulatesBecauseTheReferenceIsTheBotPoint()
	{
		PointerState.setFromBot(100, 100);

		// Measured against the previous real event, no single delta crosses the threshold.
		for (int i = 1; i <= 20; i++)
		{
			realMove(100 + i * 3, 100);
		}

		assertTrue("60px of real travel must be seen even though no single delta exceeded 10px",
			InputArbiter.isHuman());
	}

	@Test
	public void motionBeforeAnyBotEmitDoesNotFlipHuman()
	{
		// No bot point means no reference, and nothing in flight to abort.
		realMove(900, 900);

		assertFalse(InputArbiter.isHuman());
	}

	@Test
	public void realKeyFlipsHumanAndSyntheticKeysDoNot()
	{
		PointerState.setFromBot(100, 100);

		Rs2Keyboard.keyPress(KeyEvent.VK_A);
		assertFalse("Rs2Keyboard hand-delivers to this same listener", InputArbiter.isHuman());

		realKeyPressed(KeyEvent.VK_A);
		assertTrue(InputArbiter.isHuman());
	}

	@Test
	public void syntheticClickDoesNotFlipHuman()
	{
		PointerState.setFromBot(10, 10);

		new VirtualMouse().click(new net.runelite.api.Point(400, 300), false);

		assertFalse("the emitter's own events must not read as a takeover", InputArbiter.isHuman());
	}

	@Test
	public void idleWindowReturnsToBot()
	{
		PointerState.setFromBot(100, 100);
		realMove(100, 200);
		assertTrue(InputArbiter.isHuman());

		advanceMs(1799);
		assertTrue("still inside the 1800ms window", InputArbiter.isHuman());

		advanceMs(2);
		assertFalse(InputArbiter.isHuman());
	}

	@Test
	public void heldButtonSuppressesIdleResume()
	{
		PointerState.setFromBot(100, 100);
		realButtonPressed(MouseEvent.BUTTON1);

		advanceMs(60_000);

		assertTrue("a held button generates no further events, so the idle window alone would "
			+ "resume under the user's hand", InputArbiter.isHuman());

		realButtonReleased(MouseEvent.BUTTON1);
		advanceMs(1801);
		assertFalse(InputArbiter.isHuman());
	}

	@Test
	public void heldKeySuppressesIdleResume()
	{
		PointerState.setFromBot(100, 100);
		realKeyPressed(KeyEvent.VK_SHIFT);

		advanceMs(60_000);
		assertTrue(InputArbiter.isHuman());

		realKeyReleased(KeyEvent.VK_SHIFT);
		advanceMs(1801);
		assertFalse(InputArbiter.isHuman());
	}

	@Test
	public void killSwitchForcesBot()
	{
		PointerState.setFromBot(100, 100);
		InputArbiter.setDisabled(true);

		realMove(500, 500);
		realButtonPressed(MouseEvent.BUTTON1);

		assertFalse("with yielding disabled no real input may flip HUMAN", InputArbiter.isHuman());

		InputArbiter.setDisabled(false);
		assertTrue("re-enabling must not lose the button that is still held", InputArbiter.isHuman());
	}

	@Test
	public void realEventsAreConvertedToCanvasSpaceBeforeBeingRecorded()
	{
		when(client.isStretchedEnabled()).thenReturn(true);
		when(client.getStretchedDimensions()).thenReturn(new Dimension(1600, 1200));
		when(client.getRealDimensions()).thenReturn(new Dimension(800, 600));

		realMove(400, 600);

		assertEquals("PointerState is canvas space, never the component pair off the wire", 200, PointerState.getX());
		assertEquals(300, PointerState.getY());
	}

	@Test
	public void whileHumanASyntheticEmitDoesNotClobberTheHumanPoint()
	{
		PointerState.setFromBot(100, 100);
		realMove(640, 480);
		assertTrue(InputArbiter.isHuman());

		AwtEmitter.moved(20, 20);

		assertEquals("real events win; a late synthetic must not move the recorded point", 640, PointerState.getX());
		assertEquals(480, PointerState.getY());
	}

	@Test
	public void aClockStepBackwardsDoesNotPinHumanForever()
	{
		PointerState.setFromBot(100, 100);
		realMove(100, 200);
		assertTrue(InputArbiter.isHuman());

		// A wall clock does this on NTP correction or a VM resuming, and the elapsed comparison
		// then reads as "inside the idle window". The production clock is monotonic, so this
		// drives the second guard: a negative elapsed is treated as expired.
		now.addAndGet(-600_000L * 1_000_000L);

		assertFalse("a backwards clock step must not strand the bot in HUMAN", InputArbiter.isHuman());
	}

	/** The arbiter's clock is nanos. */
	private void advanceMs(long millis)
	{
		now.addAndGet(millis * 1_000_000L);
	}

	private void realMove(int componentX, int componentY)
	{
		dispatch(new MouseEvent(canvas, MouseEvent.MOUSE_MOVED, now.get(), 0, componentX, componentY, 0, false));
	}

	private void realButtonPressed(int button)
	{
		dispatch(new MouseEvent(canvas, MouseEvent.MOUSE_PRESSED, now.get(), 0, 0, 0, 1, false, button));
	}

	private void realButtonReleased(int button)
	{
		dispatch(new MouseEvent(canvas, MouseEvent.MOUSE_RELEASED, now.get(), 0, 0, 0, 1, false, button));
	}

	// Key events cannot go through dispatchEvent here: AWT routes them via the KeyboardFocusManager,
	// which drops them for a component that is not the focus owner, and a test Canvas never is.
	// Invoking the listeners is what AWT does once a key event reaches a focused component.
	//
	// That same focus requirement is why keys typed into another window never yield.
	private void realKeyPressed(int keyCode)
	{
		KeyEvent event = new KeyEvent(canvas, KeyEvent.KEY_PRESSED, now.get(), 0, keyCode, KeyEvent.CHAR_UNDEFINED);
		for (java.awt.event.KeyListener listener : canvas.getKeyListeners())
		{
			listener.keyPressed(event);
		}
	}

	private void realKeyReleased(int keyCode)
	{
		KeyEvent event = new KeyEvent(canvas, KeyEvent.KEY_RELEASED, now.get(), 0, keyCode, KeyEvent.CHAR_UNDEFINED);
		for (java.awt.event.KeyListener listener : canvas.getKeyListeners())
		{
			listener.keyReleased(event);
		}
	}

	// No guard raised, which is what makes these real rather than synthetic.
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
