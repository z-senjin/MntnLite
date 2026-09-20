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
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Emission shape and position source, asserted on the AWT events that actually reach a listener
 * on a real {@link Canvas} rather than on calls into a mock.
 */
public class InputEmissionTest
{
	private Client client;
	private Canvas canvas;
	private final List<MouseEvent> received = new ArrayList<>();
	private final List<Boolean> syntheticDuringDispatch = new ArrayList<>();

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
				record(e);
			}

			@Override
			public void mouseReleased(MouseEvent e)
			{
				record(e);
			}

			@Override
			public void mouseClicked(MouseEvent e)
			{
				record(e);
			}
		});
		canvas.addMouseMotionListener(new java.awt.event.MouseMotionAdapter()
		{
			@Override
			public void mouseMoved(MouseEvent e)
			{
				record(e);
			}
		});

		client = mock(Client.class);
		when(client.getCanvas()).thenReturn(canvas);
		when(client.isClientThread()).thenReturn(false);
		when(client.isStretchedEnabled()).thenReturn(false);

		previousClient = swapStatic("client", client);
		// Null naturalMouse means a click emits only what handleClick itself produces.
		previousNaturalMouse = swapStatic("naturalMouse", null);

		PointerState.reset();
		// Static, so a prior test left in HUMAN would abort every click here.
		InputArbiter.resetForTest();
	}

	@After
	public void after() throws Exception
	{
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
	public void aMoveIsSuppressedWhileTheHumanOwnsInput()
	{
		PointerState.setFromBot(100, 100);
		InputArbiter.onRealButtonPressed(MouseEvent.BUTTON1);
		received.clear();

		new VirtualMouse().move(new Point(400, 300));

		// NaturalMouse drives every step of a trajectory through this method, so the guard here is
		// what stops one mid-curve rather than at the next gesture boundary.
		assertTrue(received.isEmpty());
	}

	@Test
	public void sameSpotClickEmitsTriadWithNoEnterExitOrMove()
	{
		PointerState.setFromBot(100, 50);

		new VirtualMouse().click(new Point(100, 50), false);

		assertEquals("same-spot click is the triad only; a human already at the point sends no fresh MOVED",
			ids(MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED, MouseEvent.MOUSE_CLICKED),
			receivedIds());
	}

	@Test
	public void clickFromElsewhereEmitsExactlyOneMoveThenTriad()
	{
		PointerState.setFromBot(10, 10);

		new VirtualMouse().click(new Point(100, 50), false);

		assertEquals("off-target click with no NaturalMouse must still put the pointer on the target first",
			ids(MouseEvent.MOUSE_MOVED, MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED, MouseEvent.MOUSE_CLICKED),
			receivedIds());
	}

	@Test
	public void rightClickUsesButton3()
	{
		PointerState.setFromBot(100, 50);

		new VirtualMouse().click(new Point(100, 50), true);

		for (MouseEvent event : received)
		{
			assertEquals(MouseEvent.BUTTON3, event.getButton());
		}
	}

	@Test
	public void dispatchCoordinatesAreStretchMappedWhileStateStaysCanvas()
	{
		when(client.isStretchedEnabled()).thenReturn(true);
		when(client.getStretchedDimensions()).thenReturn(new Dimension(1600, 1200));
		when(client.getRealDimensions()).thenReturn(new Dimension(800, 600));
		PointerState.setFromBot(100, 50);

		new VirtualMouse().click(new Point(100, 50), false);

		MouseEvent pressed = received.get(0);
		assertEquals("dispatched x is toComponent output", 200, pressed.getX());
		assertEquals("dispatched y is toComponent output", 100, pressed.getY());
		assertEquals("PointerState must never hold the pre-convert component pair", 100, PointerState.getX());
		assertEquals(50, PointerState.getY());
	}

	@Test
	public void stretchMappingRoundTripsAndIsIdentityWhenOff()
	{
		when(client.isStretchedEnabled()).thenReturn(true);
		when(client.getStretchedDimensions()).thenReturn(new Dimension(1600, 1200));
		when(client.getRealDimensions()).thenReturn(new Dimension(800, 600));

		Point component = StretchMapper.toComponent(100, 50);
		assertEquals(200, component.getX());
		assertEquals(100, component.getY());

		Point canvasPoint = StretchMapper.toCanvas(200, 100);
		assertEquals(100, canvasPoint.getX());
		assertEquals(50, canvasPoint.getY());

		when(client.isStretchedEnabled()).thenReturn(false);
		assertEquals(100, StretchMapper.toComponent(100, 50).getX());
		assertEquals(50, StretchMapper.toComponent(100, 50).getY());
		assertEquals(100, StretchMapper.toCanvas(100, 50).getX());
		assertEquals(50, StretchMapper.toCanvas(100, 50).getY());
	}

	@Test
	public void zeroDimensionsMapAsIdentityInBothDirections()
	{
		when(client.isStretchedEnabled()).thenReturn(true);
		when(client.getStretchedDimensions()).thenReturn(new Dimension(0, 0));
		when(client.getRealDimensions()).thenReturn(new Dimension(800, 600));

		// toCanvas divides by the stretched pair, which the outbound-only guard never checked.
		assertEquals(100, StretchMapper.toCanvas(100, 50).getX());
		assertEquals(50, StretchMapper.toCanvas(100, 50).getY());
		assertEquals(100, StretchMapper.toComponent(100, 50).getX());
	}

	@Test
	public void guardReportsSyntheticWhileTheListenerRuns()
	{
		PointerState.setFromBot(100, 50);

		new VirtualMouse().click(new Point(100, 50), false);

		assertTrue("no events were observed, so the assertion below would pass vacuously",
			!syntheticDuringDispatch.isEmpty());
		for (Boolean synthetic : syntheticDuringDispatch)
		{
			assertTrue("the guard is a ThreadLocal depth counter, so this requires dispatchEvent to "
				+ "run listeners synchronously on the dispatching thread", synthetic);
		}
		assertTrue("guard must not leak past dispatch", !BotEventGuard.isSynthetic());
	}

	@Test
	public void mousePositionFollowsRealInputNotJustBotEmits()
	{
		VirtualMouse mouse = new VirtualMouse();

		mouse.click(new Point(100, 50), false);
		assertEquals(100, mouse.getMousePosition().x);
		assertEquals(50, mouse.getMousePosition().y);

		// The write the old bot-only lastMove field never received.
		PointerState.setFromReal(640, 480);

		assertEquals(640, mouse.getMousePosition().x);
		assertEquals(480, mouse.getMousePosition().y);
	}

	private void record(MouseEvent event)
	{
		received.add(event);
		syntheticDuringDispatch.add(BotEventGuard.isSynthetic());
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
