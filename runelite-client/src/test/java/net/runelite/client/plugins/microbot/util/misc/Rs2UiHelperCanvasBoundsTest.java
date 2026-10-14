package net.runelite.client.plugins.microbot.util.misc;

import org.junit.Test;

import static org.junit.Assert.assertFalse;

public class Rs2UiHelperCanvasBoundsTest {

	@Test
	public void nullRectangleIsNotWithinCanvas() {
		assertFalse(Rs2UiHelper.isRectangleWithinCanvas(null));
	}
}
