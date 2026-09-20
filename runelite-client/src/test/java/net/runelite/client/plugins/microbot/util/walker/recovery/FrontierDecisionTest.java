package net.runelite.client.plugins.microbot.util.walker.recovery;

import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The frontier cascade's pure decisions, seeded with the incidents that produced them.
 *
 * <p>D2 slice 1 of the walker fix plan: these two answers used to be inline in a 1,600-line loop
 * with no way to exercise them except by walking to Clock Tower.
 */
public class FrontierDecisionTest
{
	private static List<WorldPoint> route(int count, int plane)
	{
		List<WorldPoint> path = new ArrayList<>();
		for (int i = 0; i < count; i++)
		{
			path.add(new WorldPoint(3200, 3200 + i, plane));
		}
		return path;
	}

	private static Map<WorldPoint, Integer> reachable(WorldPoint... tiles)
	{
		Map<WorldPoint, Integer> map = new HashMap<>();
		for (int i = 0; i < tiles.length; i++)
		{
			map.put(tiles[i], i);
		}
		return map;
	}

	// ---- forwardScanStartIndex ------------------------------------------------------------------
	//
	// THE STRONGHOLD GATE BOUNCE (2026-08-12). A moves-you gate carried the player one raw tile
	// through; the next smoothed point sat nine tiles out, so the closest smoothed index stayed on
	// the near-side start tile — which now read unreachable through the auto-closed gate. Recovery
	// chased the spent tile, clicked the same gate from the far side, and bounced every ~6s.

	/** Player one raw tile past the start: the anchor must advance off the spent tile. */
	@Test
	public void anchorAdvancesPastRouteTilesTheRawPositionHasPassed()
	{
		int[] smoothedToRaw = {0, 9, 18, 27};
		assertEquals(1, FrontierDecision.forwardScanStartIndex(smoothedToRaw, 0, 1));
		// Deeper in: raw position 19 has spent indices 0..2.
		assertEquals(3, FrontierDecision.forwardScanStartIndex(smoothedToRaw, 0, 19));
	}

	/** Standing at (or before) the start tile's raw position: nothing is spent. */
	@Test
	public void anchorHoldsWhenTheRawPositionHasNotPassedTheStart()
	{
		int[] smoothedToRaw = {0, 9, 18};
		assertEquals(0, FrontierDecision.forwardScanStartIndex(smoothedToRaw, 0, 0));
		assertEquals(0, FrontierDecision.forwardScanStartIndex(smoothedToRaw, 0, -1));
	}

	/** No evidence of "behind" must not read as "spent": an unmapped entry stops the advance. */
	@Test
	public void unmappedEntriesStopTheAdvance()
	{
		int[] smoothedToRaw = {0, -1, 18};
		assertEquals(1, FrontierDecision.forwardScanStartIndex(smoothedToRaw, 0, 19));
	}

	/** Everything behind: the anchor clamps to the last index rather than running off the route. */
	@Test
	public void anchorClampsToTheLastIndex()
	{
		int[] smoothedToRaw = {0, 9, 18};
		assertEquals(2, FrontierDecision.forwardScanStartIndex(smoothedToRaw, 0, 999));
	}

	// ---- earliestBlockedIndex -------------------------------------------------------------------

	/**
	 * THE CLOCK TOWER INCIDENT. The route's tail folds back beside the player, so the reachability
	 * miss fires on a late index while the real blockage — a door at mid-route — sits earlier and was
	 * never examined. Recovery must rewind to the earliest blocked tile or it camps on the end,
	 * probing the wrong raw segment.
	 */
	@Test
	public void rewindsToTheEarliestBlockedTileNotTheMissedOne()
	{
		List<WorldPoint> path = route(10, 0);
		// Everything reachable except index 3 (the door) — the miss was reported at index 9.
		List<WorldPoint> open = new ArrayList<>(path);
		open.remove(3);
		Map<WorldPoint, Integer> reach = reachable(open.toArray(new WorldPoint[0]));

		assertEquals(3, FrontierDecision.earliestBlockedIndex(path, 0, 9, 0, reach));
	}

	/** Nothing before the miss is blocked: the miss index stands, no rewind. */
	@Test
	public void noEarlierBlockageLeavesTheFrontierAlone()
	{
		List<WorldPoint> path = route(10, 0);
		Map<WorldPoint, Integer> reach = reachable(path.toArray(new WorldPoint[0]));

		assertEquals(FrontierDecision.NO_EARLIER_BLOCKED_INDEX,
			FrontierDecision.earliestBlockedIndex(path, 0, 9, 0, reach));
	}

	/** The scan starts at the pass's route position — tiles already walked are not re-examined. */
	@Test
	public void doesNotRewindBehindTheRoutePosition()
	{
		List<WorldPoint> path = route(10, 0);
		List<WorldPoint> open = new ArrayList<>(path);
		open.remove(1); // blocked, but behind indexOfStartPoint
		Map<WorldPoint, Integer> reach = reachable(open.toArray(new WorldPoint[0]));

		assertEquals(FrontierDecision.NO_EARLIER_BLOCKED_INDEX,
			FrontierDecision.earliestBlockedIndex(path, 5, 9, 0, reach));
	}

	/**
	 * A route that climbs a staircase legitimately holds tiles the player's plane cannot reach.
	 * Treating those as blocked would send recovery at a staircase that is working perfectly.
	 */
	@Test
	public void skipsTilesOnAnotherPlaneInsteadOfCallingThemBlocked()
	{
		List<WorldPoint> path = new ArrayList<>(route(4, 0));
		path.addAll(route(4, 1));           // indices 4-7 upstairs
		Map<WorldPoint, Integer> reach = reachable(path.get(0), path.get(1), path.get(2), path.get(3));

		// Upstairs tiles are absent from the reachable map but must NOT be chosen as the frontier.
		assertEquals(FrontierDecision.NO_EARLIER_BLOCKED_INDEX,
			FrontierDecision.earliestBlockedIndex(path, 0, 8, 0, reach));
	}

	/** No reachability evidence must not read as "everything is blocked". */
	@Test
	public void missingReachabilityDisablesTheRewind()
	{
		List<WorldPoint> path = route(10, 0);
		assertEquals(FrontierDecision.NO_EARLIER_BLOCKED_INDEX,
			FrontierDecision.earliestBlockedIndex(path, 0, 9, 0, null));
		assertEquals(FrontierDecision.NO_EARLIER_BLOCKED_INDEX,
			FrontierDecision.earliestBlockedIndex(null, 0, 9, 0, reachable()));
	}

	/** A miss at the very start has nothing before it to rewind to. */
	@Test
	public void missAtTheStartHasNoEarlierTile()
	{
		List<WorldPoint> path = route(10, 0);
		assertEquals(FrontierDecision.NO_EARLIER_BLOCKED_INDEX,
			FrontierDecision.earliestBlockedIndex(path, 0, 0, 0, reachable()));
	}

	// ---- far-unreachable pre-gate ---------------------------------------------------------------

	/**
	 * The pre-gate skips fresh reads only for tiles the recovery gate could never consume: with
	 * nearGate 15 and margin 10, the boundary tile at 25 still takes the fresh-capture path and
	 * 26 is the first skip. On a gated route this is what keeps the pass from paying client-thread
	 * hops for the entire forward tail.
	 */
	@Test
	public void tilesBeyondTheRecoveryGatePlusMarginSkipFreshReads()
	{
		WorldPoint player = new WorldPoint(3200, 3200, 0);
		assertFalse("boundary tile must still take the fresh path",
			FrontierDecision.shouldSkipFarUnreachableTile(
				new WorldPoint(3225, 3200, 0), player, 15, 10));
		assertTrue(FrontierDecision.shouldSkipFarUnreachableTile(
			new WorldPoint(3226, 3200, 0), player, 15, 10));
		assertTrue("a post-transport tile on another continent is the motivating case",
			FrontierDecision.shouldSkipFarUnreachableTile(
				new WorldPoint(1681, 3125, 0), player, 15, 10));
	}

	/** No position (or no tile) means no evidence of farness: never skip, take the fresh path. */
	@Test
	public void missingPositionNeverSkips()
	{
		assertFalse(FrontierDecision.shouldSkipFarUnreachableTile(
			new WorldPoint(1681, 3125, 0), null, 15, 10));
		assertFalse(FrontierDecision.shouldSkipFarUnreachableTile(
			null, new WorldPoint(3200, 3200, 0), 15, 10));
	}

	/**
	 * distanceTo2D ignores plane, and that is the safe direction: the tile directly above the
	 * player (other side of a staircase) reads as near and keeps its fresh capture.
	 */
	@Test
	public void anOverheadTileReadsAsNearAndIsNotSkipped()
	{
		assertFalse(FrontierDecision.shouldSkipFarUnreachableTile(
			new WorldPoint(3201, 3200, 1), new WorldPoint(3200, 3200, 0), 15, 10));
	}

	// ---- door-attempt waits ---------------------------------------------------------------------

	@Test
	public void edgeWaitMapsResolutionAndFollowThrough()
	{
		assertEquals(FrontierDecision.DoorWaitOutcome.RESOLVED_FAST_CLICK,
			FrontierDecision.afterEdgeWait(true, true));
		assertEquals(FrontierDecision.DoorWaitOutcome.RESOLVED_AFTER_WAIT,
			FrontierDecision.afterEdgeWait(true, false));
		assertEquals(FrontierDecision.DoorWaitOutcome.WAITING_RETRY,
			FrontierDecision.afterEdgeWait(false, false));
	}

	/** The click is an interaction: it must only be attempted when the edge actually opened. */
	@Test
	public void edgeWaitOnlyClicksWhenResolved()
	{
		assertTrue(FrontierDecision.shouldFastClickAfterEdgeWait(true));
		assertFalse(FrontierDecision.shouldFastClickAfterEdgeWait(false));
	}

	/**
	 * THE SUBTLE ONE. A door NEAR this edge opened but the player did not move: the wait proved
	 * nothing about the frontier in front of us, so the cascade must carry on to the settle checks
	 * and the real recovery. Every other outcome ends the pass. Reporting progress here would credit
	 * a route advance that never happened.
	 */
	@Test
	public void nearbyWaitFallsThroughWhenResolvedButNobodyMoved()
	{
		FrontierDecision.DoorWaitOutcome outcome =
			FrontierDecision.afterNearbyWait(true, false, false);

		assertEquals(FrontierDecision.DoorWaitOutcome.FALL_THROUGH, outcome);
		assertFalse("fall-through must not end the pass", outcome.endsPass());
		assertNull("fall-through records no exit", outcome.exit());
	}

	@Test
	public void nearbyWaitMapsTheResolvedAndMovedCases()
	{
		assertEquals(FrontierDecision.DoorWaitOutcome.RESOLVED_FAST_CLICK,
			FrontierDecision.afterNearbyWait(true, true, true));
		assertEquals(FrontierDecision.DoorWaitOutcome.RESOLVED_AFTER_NEARBY_WAIT,
			FrontierDecision.afterNearbyWait(true, true, false));
		assertEquals(FrontierDecision.DoorWaitOutcome.NEARBY_WAITING_RETRY,
			FrontierDecision.afterNearbyWait(false, false, false));
		assertEquals("unresolved wins over movement",
			FrontierDecision.DoorWaitOutcome.NEARBY_WAITING_RETRY,
			FrontierDecision.afterNearbyWait(false, true, false));
	}

	/** A nearby door that opened while the player stood still says nothing about this frontier. */
	@Test
	public void nearbyWaitRequiresMovementBeforeClicking()
	{
		assertTrue(FrontierDecision.shouldFastClickAfterNearbyWait(true, true));
		assertFalse(FrontierDecision.shouldFastClickAfterNearbyWait(true, false));
		assertFalse(FrontierDecision.shouldFastClickAfterNearbyWait(false, true));
	}

	/** Every outcome that records an exit must also end the pass, and vice versa. */
	@Test
	public void onlyFallThroughContinuesTheCascade()
	{
		for (FrontierDecision.DoorWaitOutcome outcome : FrontierDecision.DoorWaitOutcome.values())
		{
			assertEquals(outcome + " exit/endsPass must agree",
				outcome.endsPass(), outcome.exit() != null);
		}
	}

	// ---- frontier yields ------------------------------------------------------------------------

	private static final long BLOCK_MS = 2_200L;

	@Test
	public void noYieldWhenNothingIsInFlight()
	{
		assertEquals(FrontierDecision.FrontierYield.NONE,
			FrontierDecision.yieldBeforeDoorActions(false, false, -1L, BLOCK_MS, false, false));
	}

	/** Settling is the broadest "we just touched a door" window, so it outranks the narrower two. */
	@Test
	public void settlingOutranksTraversalAndInterim()
	{
		assertEquals(FrontierDecision.FrontierYield.DOOR_SETTLING,
			FrontierDecision.yieldBeforeDoorActions(true, false, 100L, BLOCK_MS, false, true));
		assertEquals("the pass-skip cooldown is the same window by another name",
			FrontierDecision.FrontierYield.DOOR_SETTLING,
			FrontierDecision.yieldBeforeDoorActions(false, true, 100L, BLOCK_MS, false, true));
	}

	@Test
	public void traversalPendingOutranksInterim()
	{
		assertEquals(FrontierDecision.FrontierYield.DOOR_TRAVERSAL_PENDING,
			FrontierDecision.yieldBeforeDoorActions(false, false, 100L, BLOCK_MS, false, true));
	}

	/**
	 * A player already moving is walking through the door they just opened — there is nothing to
	 * wait for, and yielding would stall the pass behind their own successful traversal.
	 */
	@Test
	public void aMovingPlayerIsNotWaitingToTraverse()
	{
		assertEquals(FrontierDecision.FrontierYield.NONE,
			FrontierDecision.yieldBeforeDoorActions(false, false, 100L, BLOCK_MS, true, false));
	}

	/** A negative age means there was NO recent attempt; reading it as "0ms ago" would yield forever. */
	@Test
	public void negativeAgeMeansNoRecentDoorNotAnInstantOne()
	{
		assertEquals(FrontierDecision.FrontierYield.NONE,
			FrontierDecision.yieldBeforeDoorActions(false, false, -1L, BLOCK_MS, false, false));
	}

	@Test
	public void traversalWindowIsInclusiveAndExpires()
	{
		assertEquals(FrontierDecision.FrontierYield.DOOR_TRAVERSAL_PENDING,
			FrontierDecision.yieldBeforeDoorActions(false, false, BLOCK_MS, BLOCK_MS, false, false));
		assertEquals(FrontierDecision.FrontierYield.NONE,
			FrontierDecision.yieldBeforeDoorActions(false, false, BLOCK_MS + 1, BLOCK_MS, false, false));
	}

	@Test
	public void interimYieldsWhenNothingDoorRelatedApplies()
	{
		assertEquals(FrontierDecision.FrontierYield.INTERIM_IN_FLIGHT,
			FrontierDecision.yieldBeforeDoorActions(false, false, -1L, BLOCK_MS, false, true));
	}

	@Test
	public void everyYieldReasonCarriesAnExitAndNoneDoesNot()
	{
		for (FrontierDecision.FrontierYield yield : FrontierDecision.FrontierYield.values())
		{
			assertEquals(yield + " exit/yields must agree", yield.yields(), yield.exit() != null);
		}
	}

	// ---- recovery target selection --------------------------------------------------------------

	/** Recovering to a tile BEHIND the blockage walks the player away from the goal. */
	@Test
	public void recoveryIndexNeverGoesBehindTheFrontierOrTheRoutePosition()
	{
		assertEquals(7, FrontierDecision.clampRecoveryIndex(3, 5, 7, 20));
		assertEquals(5, FrontierDecision.clampRecoveryIndex(2, 5, 4, 20));
		assertEquals(9, FrontierDecision.clampRecoveryIndex(9, 5, 7, 20));
	}

	@Test
	public void recoveryIndexNeverRunsOffTheEnd()
	{
		assertEquals(19, FrontierDecision.clampRecoveryIndex(999, 0, 0, 20));
	}

	/** Recovery must not park the player next to an aggressive NPC — step back along the route. */
	@Test
	public void stepsBackOutOfAHazard()
	{
		List<WorldPoint> path = route(10, 0);
		java.util.Set<WorldPoint> hazards = new java.util.HashSet<>(
			Arrays.asList(path.get(7), path.get(8), path.get(9)));

		assertEquals(6, FrontierDecision.stepBackFromDanger(path, 9, 2, hazards::contains));
	}

	/**
	 * If every tile back to the floor is hazardous the index stops AT the floor rather than
	 * retreating past the frontier — walking backwards off the route is the worse failure.
	 */
	@Test
	public void stepBackStopsAtTheFloorEvenIfStillHazardous()
	{
		List<WorldPoint> path = route(10, 0);
		assertEquals(4, FrontierDecision.stepBackFromDanger(path, 9, 4, tile -> true));
	}

	@Test
	public void stepBackLeavesASafeIndexAlone()
	{
		List<WorldPoint> path = route(10, 0);
		assertEquals(9, FrontierDecision.stepBackFromDanger(path, 9, 2, tile -> false));
		assertEquals(9, FrontierDecision.stepBackFromDanger(path, 9, 2, null));
	}

	/**
	 * THE STEPPING-STONE INCIDENT. A transport only dispatches while the player STANDS on its
	 * origin, so clicking the far side of a shortcut loops on the near bank forever. The origin
	 * therefore outranks both the route tile and the raw-gated point.
	 */
	@Test
	public void walkToOriginWinsOverEveryOtherCandidate()
	{
		WorldPoint base = new WorldPoint(3200, 3200, 0);
		WorldPoint raw = new WorldPoint(3205, 3205, 0);
		WorldPoint origin = new WorldPoint(3210, 3210, 0);
		WorldPoint player = new WorldPoint(3190, 3190, 0);

		assertEquals(origin,
			FrontierDecision.chooseRecoveryTarget(base, raw, origin, player, tile -> false));
	}

	@Test
	public void rawGatedBeatsTheBaseWhenItIsUsable()
	{
		WorldPoint base = new WorldPoint(3200, 3200, 0);
		WorldPoint raw = new WorldPoint(3205, 3205, 0);
		WorldPoint player = new WorldPoint(3190, 3190, 0);

		assertEquals(raw,
			FrontierDecision.chooseRecoveryTarget(base, raw, null, player, tile -> false));
	}

	/** A candidate equal to where we already stand is no recovery at all. */
	@Test
	public void candidatesAtThePlayersOwnTileAreIgnored()
	{
		WorldPoint base = new WorldPoint(3200, 3200, 0);
		WorldPoint player = new WorldPoint(3190, 3190, 0);

		assertEquals(base,
			FrontierDecision.chooseRecoveryTarget(base, player, player, player, tile -> false));
	}

	@Test
	public void rawGatedIsRejectedWhenHazardous()
	{
		WorldPoint base = new WorldPoint(3200, 3200, 0);
		WorldPoint raw = new WorldPoint(3205, 3205, 0);
		WorldPoint player = new WorldPoint(3190, 3190, 0);

		assertEquals(base,
			FrontierDecision.chooseRecoveryTarget(base, raw, null, player, raw::equals));
	}

	/**
	 * Documents an ASYMMETRY carried over from the original rather than endorsing it: the raw-gated
	 * candidate is hazard-checked, the shortcut origin is not. Changing that is a behaviour change
	 * and needs its own commit and its own live evidence — pinned here so it cannot drift silently.
	 */
	@Test
	public void walkToOriginIsNotHazardChecked()
	{
		WorldPoint base = new WorldPoint(3200, 3200, 0);
		WorldPoint origin = new WorldPoint(3210, 3210, 0);
		WorldPoint player = new WorldPoint(3190, 3190, 0);

		assertEquals(origin,
			FrontierDecision.chooseRecoveryTarget(base, null, origin, player, tile -> true));
	}

	// ---- recovery click outcome + scene fallback ------------------------------------------------

	@Test
	public void blockedClickOutcomesEndThePass()
	{
		assertEquals(net.runelite.client.plugins.microbot.util.walker.state.WalkExit.RECOVERY_CLICK_PREEMPTED_BY_ACTION,
			FrontierDecision.exitForRecoveryClick(RouteRecovery.RecoveryClickAction.YIELD_ACTION_IN_FLIGHT));
		assertEquals(net.runelite.client.plugins.microbot.util.walker.state.WalkExit.RECOVERY_TARGET_WALLED_REPLAN,
			FrontierDecision.exitForRecoveryClick(RouteRecovery.RecoveryClickAction.REPLAN_WALLED));
		assertEquals(net.runelite.client.plugins.microbot.util.walker.state.WalkExit.RECOVERY_TARGET_WALLED_WAITING,
			FrontierDecision.exitForRecoveryClick(RouteRecovery.RecoveryClickAction.WAIT_WALLED));
	}

	/**
	 * Two outcomes continue, for different reasons: CLICK because the click is about to happen,
	 * NO_TARGET because there is nothing worth clicking and the rejoin logic should get its turn.
	 * NO_TARGET was never mentioned in the loop — it fell through by omission, which reads exactly
	 * like a forgotten case.
	 */
	@Test
	public void clickAndNoTargetBothContinueTheCascade()
	{
		assertNull(FrontierDecision.exitForRecoveryClick(RouteRecovery.RecoveryClickAction.CLICK));
		assertNull(FrontierDecision.exitForRecoveryClick(RouteRecovery.RecoveryClickAction.NO_TARGET));
		assertNull(FrontierDecision.exitForRecoveryClick(null));
	}

	/** Every action is classified — a new one must not default into "continue" unnoticed. */
	@Test
	public void everyRecoveryClickActionIsClassified()
	{
		for (RouteRecovery.RecoveryClickAction action : RouteRecovery.RecoveryClickAction.values())
		{
			boolean continues = action == RouteRecovery.RecoveryClickAction.CLICK
				|| action == RouteRecovery.RecoveryClickAction.NO_TARGET;
			assertEquals(action + " classification",
				continues, FrontierDecision.exitForRecoveryClick(action) == null);
		}
	}

	/** The canvas fallback is a last resort for the final approach, not a second click source. */
	@Test
	public void sceneFallbackOnlyOnTheFinalApproach()
	{
		WorldPoint player = new WorldPoint(3200, 3200, 0);
		WorldPoint goalNear = new WorldPoint(3201, 3200, 0);
		WorldPoint goalFar = new WorldPoint(3230, 3200, 0);
		WorldPoint recover = new WorldPoint(3205, 3200, 0);

		assertTrue(FrontierDecision.shouldTrySceneClickFallback(player, goalNear, recover, 0, 1, 15));
		assertFalse("goal still far: the minimap owns this",
			FrontierDecision.shouldTrySceneClickFallback(player, goalFar, recover, 0, 1, 15));
	}

	@Test
	public void sceneFallbackRejectsADistantRecoveryTarget()
	{
		WorldPoint player = new WorldPoint(3200, 3200, 0);
		WorldPoint goal = new WorldPoint(3201, 3200, 0);
		WorldPoint farTarget = new WorldPoint(3230, 3200, 0);

		assertFalse(FrontierDecision.shouldTrySceneClickFallback(player, goal, farTarget, 0, 1, 15));
	}

	/** The near-goal bound never drops below 2 tiles, however tight the caller's arrival distance. */
	@Test
	public void sceneFallbackKeepsAMinimumNearGoalBound()
	{
		WorldPoint player = new WorldPoint(3200, 3200, 0);
		WorldPoint goal = new WorldPoint(3202, 3200, 0);
		WorldPoint recover = new WorldPoint(3203, 3200, 0);

		assertTrue(FrontierDecision.shouldTrySceneClickFallback(player, goal, recover, 0, 0, 15));
	}

	@Test
	public void sceneFallbackToleratesMissingInputs()
	{
		WorldPoint p = new WorldPoint(3200, 3200, 0);
		assertFalse(FrontierDecision.shouldTrySceneClickFallback(null, p, p, 0, 1, 15));
		assertFalse(FrontierDecision.shouldTrySceneClickFallback(p, null, p, 0, 1, 15));
		assertFalse(FrontierDecision.shouldTrySceneClickFallback(p, p, null, 0, 1, 15));
	}

	// ---- frontierEdge ---------------------------------------------------------------------------

	/**
	 * The blocked edge is the step INTO the unreachable tile, so it starts one smoothed index before
	 * the frontier — addressing the raw segment the door actually sits on.
	 */
	@Test
	public void edgeStartsOneIndexBeforeTheFrontier()
	{
		List<WorldPoint> raw = route(20, 0);
		int[] smoothedToRaw = {0, 4, 8, 12, 16};

		FrontierDecision.FrontierEdge edge = FrontierDecision.frontierEdge(raw, smoothedToRaw, 0, 3);

		assertEquals(2, edge.edgeIndex());
		assertEquals(8, edge.rawStart());
		assertEquals(13, edge.rawEndExclusive());
		assertEquals(raw.get(8), edge.from());
		assertEquals(raw.get(12), edge.to());
	}

	/** The edge can never precede the route position the pass started from. */
	@Test
	public void edgeIsClampedToTheRoutePosition()
	{
		List<WorldPoint> raw = route(20, 0);
		int[] smoothedToRaw = {0, 4, 8, 12, 16};

		FrontierDecision.FrontierEdge edge = FrontierDecision.frontierEdge(raw, smoothedToRaw, 3, 1);

		assertEquals("clamped to fromIndex, not frontier-1", 3, edge.edgeIndex());
	}

	/** A frontier past the mapping table falls back to the whole remaining raw path. */
	@Test
	public void frontierBeyondTheMappingUsesTheRawTail()
	{
		List<WorldPoint> raw = route(20, 0);
		int[] smoothedToRaw = {0, 4};

		FrontierDecision.FrontierEdge edge = FrontierDecision.frontierEdge(raw, smoothedToRaw, 0, 5);

		assertEquals(20, edge.rawEndExclusive());
		assertEquals(raw.get(19), edge.to());
	}

	@Test
	public void toleratesMissingInputs()
	{
		FrontierDecision.FrontierEdge edge = FrontierDecision.frontierEdge(null, null, 0, 2);
		assertEquals(0, edge.rawStart());
		assertEquals(0, edge.rawEndExclusive());
		assertNull(edge.from());
		assertNull(edge.to());

		FrontierDecision.FrontierEdge empty =
			FrontierDecision.frontierEdge(Arrays.asList(), new int[]{0}, 0, 0);
		assertNull(empty.from());
		assertNull(empty.to());
	}
}
