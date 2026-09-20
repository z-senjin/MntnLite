package net.runelite.client.plugins.microbot.util.input;

import net.runelite.api.Point;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Live pointer position, in <b>canvas</b> space, written by both real AWT events and synthetic
 * emits.
 *
 * <p>Position only. Held buttons and keys live in {@link InputArbiter}, which needs them split
 * into real and synthetic; a union of the two answers no useful question.
 *
 * <p>One {@link AtomicLong} rather than two volatile ints, because x and y are only meaningful as
 * a pair.
 */
public final class PointerState
{
	private static final long UNSET = pack(-1, -1);

	private static final AtomicLong POSITION = new AtomicLong(UNSET);
	private static final AtomicLong LAST_BOT_POSITION = new AtomicLong(UNSET);

	/**
	 * Whether the client believes a pointer is over the canvas. Alt-tab away and it receives a real
	 * MOUSE_EXITED, after which its own tracked position is (-1,-1).
	 */
	private static volatile boolean outside;

	private PointerState()
	{
	}

	public static int getX()
	{
		return unpackX(POSITION.get());
	}

	public static int getY()
	{
		return unpackY(POSITION.get());
	}

	public static Point get()
	{
		long packed = POSITION.get();
		return new Point(unpackX(packed), unpackY(packed));
	}

	public static boolean isAt(int canvasX, int canvasY)
	{
		return POSITION.get() == pack(canvasX, canvasY);
	}

	public static void setFromReal(int canvasX, int canvasY)
	{
		POSITION.set(pack(canvasX, canvasY));
	}

	/**
	 * Records a synthetic emit, and the bot-written reference point the arbiter measures its
	 * motion threshold against.
	 */
	public static void setFromBot(int canvasX, int canvasY)
	{
		long packed = pack(canvasX, canvasY);
		POSITION.set(packed);
		LAST_BOT_POSITION.set(packed);
	}

	public static Point lastBotPoint()
	{
		long packed = LAST_BOT_POSITION.get();
		return new Point(unpackX(packed), unpackY(packed));
	}

	public static boolean hasBotPoint()
	{
		return LAST_BOT_POSITION.get() != UNSET;
	}

	public static boolean isOutside()
	{
		return outside;
	}

	/**
	 * Records a boundary crossing. Coordinates are kept rather than blanked: mirroring the client's
	 * (-1,-1) would feed nonsense to NaturalMouse and to every overlay reading a cursor position.
	 */
	public static void setOutside(int canvasX, int canvasY)
	{
		POSITION.set(pack(canvasX, canvasY));
		outside = true;
	}

	public static void setInside(int canvasX, int canvasY)
	{
		POSITION.set(pack(canvasX, canvasY));
		outside = false;
	}

	static void markInside()
	{
		outside = false;
	}

	/** Flag only. The bot's own exit records its position through {@link #setFromBot}. */
	static void markOutside()
	{
		outside = true;
	}

	public static void reset()
	{
		POSITION.set(UNSET);
		LAST_BOT_POSITION.set(UNSET);
		outside = false;
	}

	static long pack(int x, int y)
	{
		return ((long) x << 32) | (y & 0xFFFFFFFFL);
	}

	static int unpackX(long packed)
	{
		return (int) (packed >> 32);
	}

	static int unpackY(long packed)
	{
		return (int) packed;
	}
}
