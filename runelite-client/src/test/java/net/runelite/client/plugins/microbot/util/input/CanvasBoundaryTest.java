package net.runelite.client.plugins.microbot.util.input;

import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.client.plugins.microbot.Microbot;
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
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Alt-tab away and the canvas gets a real MOUSE_EXITED, after which the client believes there is
 * no pointer. Ignoring it left this layer reporting one wherever the human abandoned it.
 */
public class CanvasBoundaryTest
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
		MouseAdapter recorder = new MouseAdapter()
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

			@Override
			public void mouseEntered(MouseEvent e)
			{
				received.add(e);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				received.add(e);
			}
		};
		canvas.addMouseListener(recorder);
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
		// Without a size the emitter cannot tell an edge exit from a covered one and falls back to
		// exact re-entry, which would let these pass without exercising anything.
		when(client.getCanvasWidth()).thenReturn(765);
		when(client.getCanvasHeight()).thenReturn(503);

		previousClient = swapStatic("client", client);
		previousNaturalMouse = swapStatic("naturalMouse", null);

		PointerState.reset();
		InputArbiter.resetForTest();
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
	public void aRealExitIsRecordedWithoutClaimingATakeover()
	{
		PointerState.setFromBot(100, 100);

		realExit(412, 318);

		assertTrue(PointerState.isOutside());
		assertEquals(412, PointerState.getX());
		assertEquals(318, PointerState.getY());
		assertFalse("leaving the canvas is not an intent to take over", InputArbiter.isHuman());
	}

	@Test
	public void theFirstEmitAfterAnExitAnnouncesItselfFirst()
	{
		PointerState.setFromBot(100, 100);
		realExit(412, 318);
		received.clear();

		AwtEmitter.moved(500, 400);

		assertEquals("a pointer believed absent must announce its return before moving",
			ids(MouseEvent.MOUSE_ENTERED, MouseEvent.MOUSE_MOVED), receivedIds());
		// The point itself is drawn from a distribution, exercised below.
		assertTrue(received.get(0).getX() >= 0 && received.get(0).getX() < W);
		assertTrue(received.get(0).getY() >= 0 && received.get(0).getY() < H);
		assertFalse(PointerState.isOutside());
	}

	@Test
	public void onlyTheFirstEmitAnnouncesItself()
	{
		PointerState.setFromBot(100, 100);
		realExit(412, 318);

		AwtEmitter.moved(500, 400);
		received.clear();
		AwtEmitter.moved(510, 410);
		AwtEmitter.moved(520, 420);

		assertEquals(ids(MouseEvent.MOUSE_MOVED, MouseEvent.MOUSE_MOVED), receivedIds());
	}

	@Test
	public void aClickAfterAnExitStillLeadsWithTheEntry()
	{
		PointerState.setFromBot(100, 100);
		realExit(412, 318);
		received.clear();

		new VirtualMouse().click(new Point(412, 318), false);

		// The middle is not pinned: whether a MOVED appears depends on where the entry was drawn.
		List<Integer> got = receivedIds();
		assertEquals("the return announces itself before anything else",
			Integer.valueOf(MouseEvent.MOUSE_ENTERED), got.get(0));
		assertEquals(ids(MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED, MouseEvent.MOUSE_CLICKED),
			got.subList(got.size() - 3, got.size()));
	}

	@Test
	public void aRealEntryClearsTheFlagWithoutAnySyntheticEntry()
	{
		PointerState.setFromBot(100, 100);
		realExit(412, 318);

		realEnter(400, 300);
		assertFalse(PointerState.isOutside());
		assertEquals(400, PointerState.getX());

		// Cleared after the real entry, so what follows is only what the emitter itself produced.
		received.clear();
		AwtEmitter.moved(500, 400);

		assertEquals("the human already brought the pointer back; announcing it again would be a lie",
			ids(MouseEvent.MOUSE_MOVED), receivedIds());
	}

	@Test
	public void syntheticBoundaryEventsAreIgnoredByTheListener()
	{
		PointerState.setFromBot(100, 100);
		realExit(412, 318);

		// The emitted ENTERED goes out under the guard, so the listener must not read it as the
		// human bringing the pointer back.
		AwtEmitter.moved(500, 400);

		assertEquals(500, PointerState.getX());
		assertEquals(400, PointerState.getY());
	}

	@Test
	public void aBotMoveOffTheCanvasCrossesInsteadOfReportingMotionOutThere()
	{
		PointerState.setFromBot(400, 300);
		received.clear();

		new VirtualMouse().move(new Point(-1, 300));

		assertEquals("a pointer past the boundary sends the crossing and nothing else",
			ids(MouseEvent.MOUSE_EXITED), receivedIds());
		assertTrue(PointerState.isOutside());
		assertEquals("the exit coordinate is kept, so the return can be drawn from it",
			-1, PointerState.getX());
	}

	@Test
	public void furtherMovementWhileOffCanvasIsSilent()
	{
		PointerState.setFromBot(400, 300);
		new VirtualMouse().move(new Point(-1, 300));
		received.clear();

		new VirtualMouse().move(new Point(-40, 320));
		new VirtualMouse().move(new Point(-80, 340));

		assertTrue("the canvas hears nothing from a pointer that has left it", received.isEmpty());
		assertTrue(PointerState.isOutside());
	}

	@Test
	public void movingBackInAnnouncesTheReturn()
	{
		PointerState.setFromBot(400, 300);
		new VirtualMouse().move(new Point(-1, 300));
		received.clear();

		new VirtualMouse().move(new Point(420, 260));

		assertEquals(ids(MouseEvent.MOUSE_ENTERED, MouseEvent.MOUSE_MOVED), receivedIds());
		assertFalse(PointerState.isOutside());
	}

	@Test
	public void aBotExitCarriesTheBotReferenceOutWithIt()
	{
		PointerState.setFromBot(760, 300);

		new VirtualMouse().move(new Point(W + 1, 300));

		assertEquals("left behind, drift would be measured from a point the bot has left",
			W + 1, PointerState.lastBotPoint().getX());
	}

	@Test
	public void theBotCannotDragTheHumanPointerOffTheCanvas()
	{
		PointerState.setFromBot(400, 300);
		InputArbiter.onRealButtonPressed(MouseEvent.BUTTON1);
		received.clear();

		// Straight at the emitter: VirtualMouse.move returns on isHuman before reaching the guard
		// under test, so going through it would pass either way.
		AwtEmitter.moved(-1, 300);

		assertTrue("real events win, so a synthetic exit must not go out", received.isEmpty());
		assertFalse("nor may it claim the human's pointer left", PointerState.isOutside());
	}

	@Test
	public void anInBoundsMoveStillJustMoves()
	{
		PointerState.setFromBot(400, 300);
		received.clear();

		new VirtualMouse().move(new Point(W - 1, H - 1));

		assertEquals("the last pixel is inside", ids(MouseEvent.MOUSE_MOVED), receivedIds());
		assertFalse(PointerState.isOutside());
	}

	@Test
	public void anUnknownCanvasSizeKeepsMovingRatherThanGoingSilent()
	{
		when(client.getCanvasWidth()).thenReturn(0);
		when(client.getCanvasHeight()).thenReturn(0);
		PointerState.setFromBot(400, 300);
		received.clear();

		new VirtualMouse().move(new Point(-1, 300));

		assertEquals("with no size to compare against, suppressing every emit is the worse guess",
			ids(MouseEvent.MOUSE_MOVED), receivedIds());
		assertFalse(PointerState.isOutside());
	}

	// Fixed-mode size. Re-entry is random by design, so these assert the rule over many draws.
	private static final int W = 765;
	private static final int H = 503;

	@Test
	public void leavingThroughAnEdgeReturnsAlongThatEdgeButNotAlwaysAtTheSameSpot()
	{
		Random random = new Random(1);
		Set<Integer> heights = new HashSet<>();

		for (int i = 0; i < 200; i++)
		{
			Point entry = AwtEmitter.reentryPoint(W - 1, 250, W, H, random);
			assertEquals("the edge is geometry and is kept", W - 1, entry.getX());
			assertTrue(entry.getY() >= 0 && entry.getY() < H);
			heights.add(entry.getY());
		}

		// Returning to exactly 250 every time would make ENTERED and EXITED agree perfectly.
		assertTrue("the free axis must vary, got " + heights.size() + " distinct heights", heights.size() > 20);
	}

	@Test
	public void leavingThroughTheTopVariesTheOtherAxis()
	{
		Random random = new Random(2);
		Set<Integer> widths = new HashSet<>();

		for (int i = 0; i < 200; i++)
		{
			Point entry = AwtEmitter.reentryPoint(300, 0, W, H, random);
			assertEquals(0, entry.getY());
			widths.add(entry.getX());
		}

		assertTrue(widths.size() > 20);
	}

	@Test
	public void aCoveredExitReturnsBothInsideAndAcrossAnEdge()
	{
		// A mid-canvas exit means a window covered the client, and whatever the user did there they
		// probably did with the mouse. Both outcomes must occur.
		Random random = new Random(3);
		int acrossAnEdge = 0;
		int inside = 0;

		for (int i = 0; i < 400; i++)
		{
			Point entry = AwtEmitter.reentryPoint(400, 250, W, H, random);
			assertTrue(entry.getX() >= 0 && entry.getX() < W);
			assertTrue(entry.getY() >= 0 && entry.getY() < H);

			boolean onEdge = entry.getX() == 0 || entry.getX() == W - 1
				|| entry.getY() == 0 || entry.getY() == H - 1;
			if (onEdge) acrossAnEdge++;
			else inside++;
		}

		assertTrue("some returns cross an edge, got " + acrossAnEdge, acrossAnEdge > 20);
		assertTrue("some returns land inside, got " + inside, inside > 20);
	}

	@Test
	public void aCoveredExitDoesNotAlwaysReturnToTheExactExitPoint()
	{
		Random random = new Random(4);
		int exact = 0;

		for (int i = 0; i < 400; i++)
		{
			Point entry = AwtEmitter.reentryPoint(400, 250, W, H, random);
			if (entry.getX() == 400 && entry.getY() == 250) exact++;
		}

		// Still reachable, that being the user who never touched the mouse; just not the rule.
		assertTrue("returning to the exact exit point must not be the rule, got " + exact + "/400", exact < 40);
	}

	@Test
	public void everyReentryStaysInsideTheCanvas()
	{
		Random random = new Random(5);

		for (int i = 0; i < 400; i++)
		{
			for (Point exit : new Point[]{new Point(W - 1, 500), new Point(0, 3), new Point(400, 250)})
			{
				Point entry = AwtEmitter.reentryPoint(exit.getX(), exit.getY(), W, H, random);
				assertTrue("x out of bounds: " + entry.getX(), entry.getX() >= 0 && entry.getX() < W);
				assertTrue("y out of bounds: " + entry.getY(), entry.getY() >= 0 && entry.getY() < H);
			}
		}
	}

	@Test
	public void anUnknownCanvasSizeFallsBackToTheExitPoint()
	{
		Point entry = AwtEmitter.reentryPoint(764, 250, 0, 0, new Random(6));

		assertEquals(764, entry.getX());
		assertEquals(250, entry.getY());
	}

	private void realExit(int componentX, int componentY)
	{
		canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_EXITED, 0L, 0,
			componentX, componentY, 0, false));
	}

	private void realEnter(int componentX, int componentY)
	{
		canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_ENTERED, 0L, 0,
			componentX, componentY, 0, false));
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
