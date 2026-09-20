package net.runelite.client.plugins.microbot.util.walker;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.walker.state.WalkerRouteState;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * The raw watermark that feeds the stagnation clock.
 *
 * <p>Seeded from the first post-restart live log (2026-08-12, Lovakengj → Varrock): the entire
 * Varrock west approach — fifty tiles and three doors — sat inside the final smoothed segment, so
 * the smoothed progress index held one value through ~50 seconds of honest walking against a 60s
 * stagnation budget. The raw index advances tile by tile on exactly that walk; the Tithe Farm
 * ping-pong (the incident the budget exists for) still cannot advance it more than once.
 */
public class RouteProgressWatermarkTest {

	private final WalkerRouteState routeState = Rs2Walker.routeStateForTesting();

	private static final WorldPoint GOAL = new WorldPoint(3049, 3341, 0);

	/** Fifty collinear raw tiles; the smoothed path keeps only the endpoints. */
	private static List<WorldPoint> rawLine() {
		List<WorldPoint> raw = new ArrayList<>();
		for (int i = 0; i <= 49; i++) {
			raw.add(new WorldPoint(3000 + i, 3341, 0));
		}
		return raw;
	}

	private static List<WorldPoint> smoothedEndpoints() {
		return Arrays.asList(new WorldPoint(3000, 3341, 0), GOAL);
	}

	@Before
	public void reset() {
		Rs2Walker.resetWalkSessionState();
	}

	@Test
	public void rawAdvanceKeepsTheClockAliveWhileTheSmoothedIndexHolds() {
		List<WorldPoint> raw = rawLine();
		List<WorldPoint> smoothed = smoothedEndpoints();

		// First pass initializes tracking (routeChanged stamps unconditionally).
		Rs2Walker.stabilizeRouteProgressWithRawWatermark(raw, smoothed, 0, GOAL, raw.get(0));
		int smoothedIdxAtStart = routeState.routeProgressIdx;

		for (int i = 1; i <= 20; i++) {
			routeState.routeProgressAdvancedAtMs = 0L;
			Rs2Walker.stabilizeRouteProgressWithRawWatermark(raw, smoothed, 0, GOAL, raw.get(i));
			assertNotEquals("tile " + i + ": a new furthest raw tile must stamp the clock",
				0L, routeState.routeProgressAdvancedAtMs);
			assertEquals("the smoothed index is expected to hold still in this scenario",
				smoothedIdxAtStart, routeState.routeProgressIdx);
		}
		assertEquals(20, routeState.rawProgressHighIdx);
	}

	@Test
	public void oscillationStampsAtMostOnce() {
		List<WorldPoint> raw = rawLine();
		List<WorldPoint> smoothed = smoothedEndpoints();

		// Walk to tile 4, establishing the high-water mark.
		Rs2Walker.stabilizeRouteProgressWithRawWatermark(raw, smoothed, 0, GOAL, raw.get(0));
		Rs2Walker.stabilizeRouteProgressWithRawWatermark(raw, smoothed, 0, GOAL, raw.get(4));
		assertEquals(4, routeState.rawProgressHighIdx);

		// The Tithe ping-pong: bounce between tiles 2 and 4 forever. No pass may stamp.
		for (int bounce = 0; bounce < 10; bounce++) {
			WorldPoint at = raw.get(bounce % 2 == 0 ? 2 : 4);
			routeState.routeProgressAdvancedAtMs = 0L;
			Rs2Walker.stabilizeRouteProgressWithRawWatermark(raw, smoothed, 0, GOAL, at);
			assertEquals("bounce " + bounce + ": oscillation must not feed the stagnation clock",
				0L, routeState.routeProgressAdvancedAtMs);
		}
	}

	@Test
	public void aReplansNewRouteResetsTheHighWaterMark() {
		List<WorldPoint> raw = rawLine();
		List<WorldPoint> smoothed = smoothedEndpoints();
		Rs2Walker.stabilizeRouteProgressWithRawWatermark(raw, smoothed, 0, GOAL, raw.get(30));
		assertEquals(30, routeState.rawProgressHighIdx);

		// Replan: a different (shorter) route. The stale mark of 30 must not gag the watermark.
		List<WorldPoint> newRaw = raw.subList(28, 49);
		List<WorldPoint> newSmoothed = Arrays.asList(newRaw.get(0), GOAL);
		Rs2Walker.stabilizeRouteProgressWithRawWatermark(newRaw, newSmoothed, 0, GOAL, newRaw.get(1));
		assertTrue("post-replan raw indices are small again and must still stamp",
			routeState.rawProgressHighIdx >= 0 && routeState.rawProgressHighIdx <= 2);

		routeState.routeProgressAdvancedAtMs = 0L;
		Rs2Walker.stabilizeRouteProgressWithRawWatermark(newRaw, newSmoothed, 0, GOAL, newRaw.get(5));
		assertNotEquals(0L, routeState.routeProgressAdvancedAtMs);
	}
}
