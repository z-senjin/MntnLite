package net.runelite.client.plugins.microbot.util.input;

import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.mouse.BotEventGuard;
import net.runelite.client.plugins.microbot.util.mouse.VirtualMouse;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.awt.Canvas;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** A gesture interrupted partway through unwinds instead of finishing. */
public class GestureAbortTest
{
	private Client client;
	private Canvas canvas;
	private final List<MouseEvent> received = new ArrayList<>();

	private Object previousClient;
	private Object previousNaturalMouse;

	@Before
	public void before() throws Exception
	{
		canvas = new Canvas();
		canvas.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				received.add(e);
			}

			@Override
			public void mouseReleased(MouseEvent e)
			{
				received.add(e);
			}

			@Override
			public void mouseClicked(MouseEvent e)
			{
				received.add(e);
			}
		});
		canvas.addMouseMotionListener(new java.awt.event.MouseMotionAdapter()
		{
			@Override
			public void mouseMoved(MouseEvent e)
			{
				received.add(e);
			}
		});

		client = mock(Client.class);
		when(client.getCanvas()).thenReturn(canvas);
		when(client.isClientThread()).thenReturn(false);
		when(client.isStretchedEnabled()).thenReturn(false);

		previousClient = swapStatic("client", client);
		previousNaturalMouse = swapStatic("naturalMouse", null);

		PointerState.reset();
		InputArbiter.resetForTest();
		Microbot.targetMenu = null;
	}

	@After
	public void after() throws Exception
	{
		swapStatic("client", previousClient);
		swapStatic("naturalMouse", previousNaturalMouse);
		PointerState.reset();
		InputArbiter.resetForTest();
		InputLoop.setLockTimeoutForTest(5_000L);
		Microbot.targetMenu = null;
		while (BotEventGuard.isSynthetic())
		{
			BotEventGuard.end();
		}
	}

	@Test
	public void midPressAbortEmitsOneReleaseAndNoClick()
	{
		PointerState.setFromBot(100, 100);

		InputLoop.Result result = InputLoop.run(emit -> {
			emit.press(100, 100, MouseEvent.BUTTON1);
			takeOver();
			emit.click(100, 100, MouseEvent.BUTTON1);
		});

		assertEquals(InputLoop.Result.ABORTED, result);
		assertEquals("PRESSED then exactly one RELEASED, and no CLICKED for a cancelled click",
			ids(MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED), receivedIds());
	}

	@Test
	public void abortReleasesAtTheCurrentPointNotTheStaleTarget()
	{
		PointerState.setFromBot(100, 100);

		InputLoop.run(emit -> {
			emit.press(100, 100, MouseEvent.BUTTON1);
			// The human moves away with the button down, as in a drag.
			takeOverAt(640, 480);
			emit.release(900, 900, MouseEvent.BUTTON1);
		});

		MouseEvent release = last();
		assertEquals(MouseEvent.MOUSE_RELEASED, release.getID());
		assertEquals("release must land where the pointer actually is", 640, release.getX());
		assertEquals(480, release.getY());
	}

	@Test
	public void abortClearsTargetMenuSoTheHumansNextClickIsNotHijacked()
	{
		PointerState.setFromBot(100, 100);
		NewMenuEntry entry = new NewMenuEntry();

		InputLoop.Result result = InputLoop.run(emit -> {
			Microbot.targetMenu = entry;
			takeOver();
			emit.press(100, 100, MouseEvent.BUTTON1);
		});

		assertEquals(InputLoop.Result.ABORTED, result);
		assertNull("an entry left armed is consumed by whatever clicks next, which is the human",
			Microbot.targetMenu);
	}

	@Test
	public void completedGestureKeepsTargetMenu()
	{
		PointerState.setFromBot(100, 100);
		NewMenuEntry entry = new NewMenuEntry();

		InputLoop.Result result = InputLoop.run(emit -> {
			Microbot.targetMenu = entry;
			emit.press(100, 100, MouseEvent.BUTTON1);
			emit.release(100, 100, MouseEvent.BUTTON1);
			emit.click(100, 100, MouseEvent.BUTTON1);
		});

		assertEquals(InputLoop.Result.COMPLETED, result);
		assertEquals("the client consumes the entry on the click it was armed for", entry, Microbot.targetMenu);
	}

	@Test
	public void aGestureStartedAfterTakeoverNeverDispatches()
	{
		PointerState.setFromBot(100, 100);
		takeOver();

		InputLoop.Result result = InputLoop.run(emit -> emit.press(100, 100, MouseEvent.BUTTON1));

		assertEquals(InputLoop.Result.ABORTED, result);
		assertTrue("no AWT at all once the human owns input", received.isEmpty());
	}

	@Test
	public void aDeferredItemQueuedBeforeTakeoverIsAbortedWhenItRuns()
	{
		PointerState.setFromBot(100, 100);

		// Stands in for the client-thread deferral: scheduled while BOT, may run after a takeover.
		Runnable deferred = () -> InputLoop.run(emit -> emit.wheel(100, 100, 2, 10));
		takeOver();
		deferred.run();

		assertTrue(received.isEmpty());
	}

	@Test
	public void realKeyDuringAMouseGestureAbortsItAndReleasesTheHeldButton()
	{
		PointerState.setFromBot(100, 100);

		InputLoop.Result result = InputLoop.run(emit -> {
			emit.press(100, 100, MouseEvent.BUTTON1);
			// A key, not a mouse event: a mouse-only abort path would miss this.
			InputArbiter.onRealKeyPressed(java.awt.event.KeyEvent.VK_A);
			emit.release(100, 100, MouseEvent.BUTTON1);
		});

		assertEquals(InputLoop.Result.ABORTED, result);
		assertEquals(ids(MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED), receivedIds());
	}

	@Test
	public void oneGestureAtATimeAcrossThreads() throws Exception
	{
		PointerState.setFromBot(100, 100);
		CountDownLatch inside = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		// Ran and ordering are separate: a probe read after the latch drops is false whether the
		// second gesture waited its turn or never started at all.
		AtomicBoolean secondRan = new AtomicBoolean(false);
		AtomicBoolean firstHadFinished = new AtomicBoolean(false);
		AtomicBoolean firstFinished = new AtomicBoolean(false);

		Thread first = new Thread(() -> InputLoop.run(emit -> {
			inside.countDown();
			try
			{
				release.await(2, TimeUnit.SECONDS);
			}
			catch (InterruptedException e)
			{
				Thread.currentThread().interrupt();
			}
			// Inside the gesture, so the lock is still held when it is set.
			firstFinished.set(true);
		}));
		first.start();
		assertTrue(inside.await(2, TimeUnit.SECONDS));

		Thread second = new Thread(() -> InputLoop.run(emit -> {
			secondRan.set(true);
			firstHadFinished.set(firstFinished.get());
		}));
		second.start();
		Thread.sleep(120);

		assertFalse("second gesture must not run while the first holds the lock", secondRan.get());
		release.countDown();
		first.join(2_000);
		second.join(2_000);

		assertTrue("it has to actually run, or the assertion above passes for the wrong reason",
			secondRan.get());
		assertTrue("second gesture ran only after the first finished", firstHadFinished.get());
	}

	@Test
	public void aSecondGestureGivesUpRatherThanBlockingForever() throws Exception
	{
		PointerState.setFromBot(100, 100);
		InputLoop.setLockTimeoutForTest(150L);
		CountDownLatch holding = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);

		Thread hog = new Thread(() -> InputLoop.run(emit -> {
			holding.countDown();
			try
			{
				release.await(30, TimeUnit.SECONDS);
			}
			catch (InterruptedException e)
			{
				Thread.currentThread().interrupt();
			}
		}));
		hog.setDaemon(true);
		hog.start();
		assertTrue(holding.await(2, TimeUnit.SECONDS));

		// Unbounded, one wedged gesture would hold every other script's input indefinitely.
		long start = System.nanoTime();
		InputLoop.Result result = InputLoop.run(emit -> emit.press(100, 100, MouseEvent.BUTTON1));
		long waitedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

		release.countDown();
		hog.join(2_000);

		assertEquals(InputLoop.Result.ABORTED, result);
		assertTrue("must give up on a timeout, waited " + waitedMs + "ms", waitedMs < 5_000);
	}

	@Test
	public void scrollMovesAndWheelsAsOneUninterruptibleGesture() throws Exception
	{
		PointerState.setFromBot(100, 100);
		canvas.addMouseWheelListener(received::add);

		Thread scroller = new Thread(() -> new VirtualMouse().scrollDown(new Point(300, 200)));
		scroller.setDaemon(true);
		scroller.start();
		scroller.join(3_000);

		// Separately, another script's click could land between the two and leave the wheel firing
		// at a point the cursor had left.
		assertEquals(ids(MouseEvent.MOUSE_MOVED, MouseEvent.MOUSE_WHEEL), receivedIds());
		for (MouseEvent event : received)
		{
			assertEquals(300, event.getX());
			assertEquals(200, event.getY());
		}
	}

	@Test(expected = IllegalStateException.class)
	public void aNestedGestureIsRejected()
	{
		PointerState.setFromBot(100, 100);

		// The inner run would get its own Emit and unwind the outer gesture's state.
		InputLoop.run(outer -> InputLoop.run(inner -> inner.move(10, 10)));
	}

	@Test
	public void facadeReturnsItselfEvenWhenTheGestureAborted()
	{
		PointerState.setFromBot(100, 100);
		takeOver();
		VirtualMouse mouse = new VirtualMouse();

		assertEquals("the facade cannot report failure; scripts re-validate on the next loop",
			mouse, mouse.click(new Point(100, 100), false));
		assertTrue(received.isEmpty());
	}

	private void takeOver()
	{
		InputArbiter.onRealButtonPressed(MouseEvent.BUTTON1);
		assertTrue(InputArbiter.isHuman());
	}

	private void takeOverAt(int canvasX, int canvasY)
	{
		PointerState.setFromReal(canvasX, canvasY);
		takeOver();
	}

	private MouseEvent last()
	{
		return received.get(received.size() - 1);
	}

	private List<Integer> receivedIds()
	{
		List<Integer> out = new ArrayList<>();
		for (MouseEvent event : received)
		{
			out.add(event.getID());
		}
		return out;
	}

	private static List<Integer> ids(int... values)
	{
		List<Integer> out = new ArrayList<>();
		for (int value : values)
		{
			out.add(value);
		}
		return out;
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
