package net.runelite.client.plugins.microbot.util.input;

import net.runelite.api.Point;

import java.awt.event.KeyEvent;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;

/**
 * Live arbiter state, for verifying the yield against a running client. Off unless
 * {@code -Dmicrobot.inputDebug=true}.
 *
 * <p>A yield fault has one visible symptom and three causes: the listener never attached, the
 * threshold never tripped, or the waits never observed the flag. This separates them.
 */
public final class InputDiagnostics
{
	private static final boolean ENABLED = Boolean.getBoolean("microbot.inputDebug");

	private InputDiagnostics()
	{
	}

	public static boolean isEnabled()
	{
		return ENABLED;
	}

	/** Label to value, in display order. Safe before anything has happened. */
	public static Map<String, String> readout()
	{
		Map<String, String> out = new LinkedHashMap<>();
		out.put("owner", owner());
		out.put("listener", CanvasInputListener.isAttachedToLiveCanvas() ? "attached" : "DETACHED");
		// A held key with focus lost is a different situation from a held key while playing, and
		// they are indistinguishable without this.
		out.put("focus", CanvasInputListener.isCanvasFocused() ? "canvas" : "elsewhere");

		Point pointer = PointerState.get();
		out.put("pointer", pointer.getX() + "," + pointer.getY());

		if (PointerState.hasBotPoint())
		{
			Point botPoint = PointerState.lastBotPoint();
			out.put("bot point", botPoint.getX() + "," + botPoint.getY());
			out.put("drift", distance(pointer, botPoint) + " / " + InputArbiter.motionThresholdPx() + "px");
		}
		else
		{
			// No reference point yet, so motion alone cannot flip HUMAN. Stated rather than
			// printing a distance from (-1,-1) that would read as a bug.
			out.put("bot point", "none yet");
			out.put("drift", "n/a until first emit");
		}

		long since = InputArbiter.millisSinceRealActivity();
		out.put("last real", since < 0 ? "never" : since + " / " + InputArbiter.idleResumeMs() + "ms");
		out.put("real held", held());
		return out;
	}

	private static String owner()
	{
		if (InputArbiter.isDisabled())
		{
			return "BOT (yielding off)";
		}
		return InputArbiter.isHuman() ? "HUMAN" : "BOT";
	}

	private static String held()
	{
		Set<Integer> buttons = InputArbiter.realButtonsDown();
		Set<Integer> keys = InputArbiter.realKeysDown();
		if (buttons.isEmpty() && keys.isEmpty())
		{
			return "none";
		}
		StringJoiner joiner = new StringJoiner(" ");
		for (Integer button : buttons)
		{
			joiner.add("btn" + button);
		}
		for (Integer key : keys)
		{
			joiner.add(KeyEvent.getKeyText(key));
		}
		return joiner.toString();
	}

	private static long distance(Point a, Point b)
	{
		long dx = a.getX() - b.getX();
		long dy = a.getY() - b.getY();
		return Math.round(Math.sqrt((double) dx * dx + (double) dy * dy));
	}
}
