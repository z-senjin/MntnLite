package net.runelite.client.plugins.microbot.util.input;

import net.runelite.api.Client;
import net.runelite.client.plugins.microbot.Microbot;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.awt.Canvas;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Only the formatting the readout does itself. What it reports on is {@link InputArbiterTest}'s
 * subject, and asserting the display strings again there would pin wording rather than behaviour.
 */
public class InputDiagnosticsTest
{
	private Object previousClient;

	@Before
	public void before() throws Exception
	{
		Client client = mock(Client.class);
		when(client.getCanvas()).thenReturn(new Canvas());
		previousClient = swapStatic("client", client);

		PointerState.reset();
		InputArbiter.resetForTest();
		CanvasInputListener.detach();
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
	public void isOffUnlessTheSystemPropertyIsSet()
	{
		assertFalse("must stay invisible in normal use", InputDiagnostics.isEnabled());
	}

	@Test
	public void readsCleanlyBeforeAnythingHasHappened()
	{
		Map<String, String> readout = InputDiagnostics.readout();

		assertEquals("none yet", readout.get("bot point"));
		assertEquals("a distance from (-1,-1) would read as a bug", "n/a until first emit",
			readout.get("drift"));
		assertEquals("never", readout.get("last real"));
		assertEquals("none", readout.get("real held"));
	}

	@Test
	public void namesWhatIsPhysicallyHeld()
	{
		PointerState.setFromBot(100, 100);
		InputArbiter.onRealButtonPressed(MouseEvent.BUTTON1);
		InputArbiter.onRealKeyPressed(KeyEvent.VK_SHIFT);

		assertEquals("btn1 Shift", InputDiagnostics.readout().get("real held"));

		InputArbiter.onRealButtonReleased(MouseEvent.BUTTON1);
		InputArbiter.onRealKeyReleased(KeyEvent.VK_SHIFT);
		assertEquals("none", InputDiagnostics.readout().get("real held"));
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
