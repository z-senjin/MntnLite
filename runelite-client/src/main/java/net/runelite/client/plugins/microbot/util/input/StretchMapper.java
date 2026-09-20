package net.runelite.client.plugins.microbot.util.input;

import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.client.plugins.microbot.Microbot;

import java.awt.Dimension;

/**
 * Converts between canvas space, which scripts and {@link PointerState} use, and the component
 * space an AWT event on the game canvas carries. Identity when stretched mode is off.
 */
public final class StretchMapper
{
	private StretchMapper()
	{
	}

	public static Point toComponent(int canvasX, int canvasY)
	{
		Dims dims = dims();
		if (dims == null)
		{
			return new Point(canvasX, canvasY);
		}
		return new Point(
			(int) ((long) canvasX * dims.stretchedWidth / dims.realWidth),
			(int) ((long) canvasY * dims.stretchedHeight / dims.realHeight));
	}

	public static Point toCanvas(int componentX, int componentY)
	{
		Dims dims = dims();
		if (dims == null)
		{
			return new Point(componentX, componentY);
		}
		return new Point(
			(int) ((long) componentX * dims.realWidth / dims.stretchedWidth),
			(int) ((long) componentY * dims.realHeight / dims.stretchedHeight));
	}

	/** Null means identity. Both pairs are checked for zero: each direction divides by one of them. */
	private static Dims dims()
	{
		Client client;
		try
		{
			client = Microbot.getClient();
		}
		catch (Exception ex)
		{
			return null;
		}
		if (client == null || !client.isStretchedEnabled())
		{
			return null;
		}
		Dimension stretched = client.getStretchedDimensions();
		Dimension real = client.getRealDimensions();
		if (stretched == null || real == null)
		{
			return null;
		}
		if (stretched.width == 0 || stretched.height == 0 || real.width == 0 || real.height == 0)
		{
			return null;
		}
		return new Dims(stretched.width, stretched.height, real.width, real.height);
	}

	private static final class Dims
	{
		private final int stretchedWidth;
		private final int stretchedHeight;
		private final int realWidth;
		private final int realHeight;

		private Dims(int stretchedWidth, int stretchedHeight, int realWidth, int realHeight)
		{
			this.stretchedWidth = stretchedWidth;
			this.stretchedHeight = stretchedHeight;
			this.realWidth = realWidth;
			this.realHeight = realHeight;
		}
	}
}
