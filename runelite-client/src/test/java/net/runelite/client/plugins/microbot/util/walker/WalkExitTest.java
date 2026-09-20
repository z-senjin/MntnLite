package net.runelite.client.plugins.microbot.util.walker;

import net.runelite.client.plugins.microbot.util.walker.state.WalkExit;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The meaning of every walk-loop exit reason, held as data.
 *
 * <p>This began as a characterization against the string predicates {@link WalkExit} replaced, which
 * is what made that refactor provably inert. Those predicates have now been deleted and their
 * classification lives here instead: three explicit sets, checked exhaustively against every
 * constant, so a reason cannot change meaning — or be added without one — unnoticed.
 *
 * <p>When a classification is deliberately corrected, the expectation moves <em>here</em>, and the
 * diff to this file is the record of exactly what changed — which is precisely what the string
 * version could never provide. Do not "fix" a failure by editing the enum until you have written
 * down why the new answer is the right one.
 */
public class WalkExitTest
{
	/**
	 * The fourteen reasons whose route-progress classification was deliberately corrected once the
	 * enum made the set enumerable. Every one of them means the walker either just advanced the
	 * route or is waiting on movement it issued itself, yet all fourteen were charged against the
	 * partial-retry budget — three of them in a row on a partial route reported UNREACHABLE and
	 * aborted a walk that was working.
	 *
	 * <p>Kept as an explicit list rather than folded away, because this set <em>is</em> the
	 * behaviour change. Adding to it later means saying which reason and why.
	 */
	private static final Set<WalkExit> RECLASSIFIED_AS_PROGRESS = new HashSet<>(Arrays.asList(
		// recovery resolved the blocked frontier
		WalkExit.FRONTIER_OBSTACLE_HANDLED,
		WalkExit.TRANSPORT_HANDLED_LOCAL_REACHABILITY,
		// a click was issued and the player is walking
		WalkExit.LOCAL_RECOVERY_CLICK,
		WalkExit.DOOR_SUPPRESSED_APPROACH_CLICK,
		WalkExit.RECENT_DOOR_EDGE_NUDGE,
		WalkExit.ROUTE_MOVE_IN_FLIGHT,
		// the door actually opened
		WalkExit.DOOR_EDGE_RESOLVED_FAST_CLICK,
		WalkExit.DOOR_EDGE_RESOLVED_AFTER_WAIT,
		WalkExit.DOOR_EDGE_RESOLVED_AFTER_NEARBY_WAIT,
		// waiting on an action we issued
		WalkExit.DOOR_SETTLING_YIELD,
		WalkExit.DOOR_TRAVERSAL_PENDING_YIELD,
		WalkExit.TRANSPORT_SETTLING_YIELD,
		WalkExit.RECOVERY_CLICK_PREEMPTED_BY_ACTION,
		// the pass was abandoned because the player moved
		WalkExit.RECOVERY_POSITION_STALE));

	/**
	 * The full progress classification, as data.
	 *
	 * <p>This started as a comparison against the string predicates the enum replaced. Those have now
	 * been deleted, so the historical baseline lives here instead: every reason is either listed as
	 * progress or it is not, and a constant that changes side has to change this list too.
	 */
	private static final Set<WalkExit> PROGRESS = new HashSet<>(Arrays.asList(
		// an obstacle handler acted
		WalkExit.DOOR_HANDLED,
		WalkExit.DOOR_HANDLED_BEFORE_MINIMAP_CLICK,
		WalkExit.DOOR_HANDLED_DURING_INTERIM,
		WalkExit.DOOR_HANDLED_LOCAL_REACHABILITY,
		WalkExit.DOOR_HANDLED_LOCAL_REACHABILITY_RAW_SCAN,
		WalkExit.DOOR_HANDLED_NEARBY_ROUTE_DOOR,
		WalkExit.DOOR_HANDLED_PATH_ADJ_SCAN,
		WalkExit.PATH_BLOCKER_HANDLED,
		WalkExit.ROCKFALL_HANDLED,
		WalkExit.TRANSPORT_HANDLED,
		WalkExit.CURRENT_TILE_TRANSPORT_HANDLED,
		WalkExit.POST_CLICK_CURRENT_TILE_TRANSPORT_HANDLED,
		WalkExit.RAW_PATH_SCENE_OBJECT_HANDLED,
		WalkExit.POST_CLICK_RAW_PATH_SCENE_OBJECT_HANDLED,
		WalkExit.ROUTE_FOLD_CONTINUATION_CLICK,
		// recovery resolved the blocked frontier, or issued movement
		WalkExit.FRONTIER_OBSTACLE_HANDLED,
		WalkExit.TRANSPORT_HANDLED_LOCAL_REACHABILITY,
		WalkExit.LOCAL_RECOVERY_CLICK,
		WalkExit.DOOR_SUPPRESSED_APPROACH_CLICK,
		WalkExit.RECENT_DOOR_EDGE_NUDGE,
		WalkExit.RECOVERY_POSITION_STALE,
		// the door opened
		WalkExit.DOOR_EDGE_RESOLVED_FAST_CLICK,
		WalkExit.DOOR_EDGE_RESOLVED_AFTER_WAIT,
		WalkExit.DOOR_EDGE_RESOLVED_AFTER_NEARBY_WAIT,
		// waiting on an action we issued
		WalkExit.INTERIM_IN_FLIGHT_ROUTE,
		WalkExit.INTERIM_IN_FLIGHT_RECOVERY,
		WalkExit.INTERIM_IN_FLIGHT_CLICK,
		WalkExit.RECOVERY_MOVE_IN_FLIGHT,
		WalkExit.ROUTE_MOVE_IN_FLIGHT,
		WalkExit.DOOR_SETTLING_YIELD,
		WalkExit.DOOR_TRAVERSAL_PENDING_YIELD,
		WalkExit.TRANSPORT_SETTLING_YIELD,
		WalkExit.RECOVERY_CLICK_PREEMPTED_BY_ACTION));

	@Test
	public void progressClassificationIsExactlyThisSet()
	{
		for (WalkExit exit : WalkExit.values())
		{
			assertEquals(exit.name() + " changed its route-progress meaning; if that is deliberate, "
					+ "move it in PROGRESS and say why in the commit",
				PROGRESS.contains(exit), exit.isProgress());
		}
	}

	/** Every reason listed as reclassified must in fact be progress; the list documents the change. */
	@Test
	public void theReclassifiedReasonsAreAllProgress()
	{
		for (WalkExit exit : RECLASSIFIED_AS_PROGRESS)
		{
			assertTrue(exit.name() + " was reclassified as route progress and must report it",
				exit.isProgress());
			assertTrue(exit.name() + " must also appear in the full PROGRESS set",
				PROGRESS.contains(exit));
		}
	}

	/**
	 * The budget must still drain on the reasons that genuinely mean "not advancing", or a truly
	 * unreachable goal never terminates and the walk spins until the tail cap trips.
	 */
	@Test
	public void reasonsThatMeanStuckStillConsumeTheBudget()
	{
		for (WalkExit stuck : new WalkExit[]{
			WalkExit.END_OF_PATH,
			WalkExit.NOT_NEAR_PATH,
			WalkExit.PLAYER_LOCATION_NULL,
			WalkExit.CLICK_FAILED_OFF_MINIMAP,
			WalkExit.DOOR_EDGE_WAITING_RETRY,
			WalkExit.DOOR_EDGE_NEARBY_WAITING_RETRY,
			WalkExit.DOOR_RECOVERY_SUPPRESSED,
			WalkExit.LOCAL_REACHABILITY_MISS_NO_CLICK,
			WalkExit.RECOVERY_TARGET_WALLED_REPLAN,
			WalkExit.RECOVERY_TARGET_WALLED_WAITING,
			WalkExit.ROUTE_FOLD_CONTINUATION_PENDING})
		{
			assertFalse(stuck.name() + " does not advance the route and must still spend a retry",
				stuck.isProgress());
		}
	}

	/** Benign yields that refund their own tail charge, so long waits cannot exhaust the cap. */
	private static final Set<WalkExit> TAIL_EXEMPT = new HashSet<>(Arrays.asList(
		WalkExit.INTERIM_IN_FLIGHT_ROUTE,
		WalkExit.INTERIM_IN_FLIGHT_RECOVERY,
		WalkExit.INTERIM_IN_FLIGHT_CLICK,
		WalkExit.RECOVERY_MOVE_IN_FLIGHT,
		WalkExit.ROUTE_MOVE_IN_FLIGHT,
		WalkExit.ROUTE_FOLD_CONTINUATION_CLICK,
		WalkExit.OFF_PATH_DEFERRED));

	/** Exits that owe the post-door canvas nudge and its minimap hold-off. */
	private static final Set<WalkExit> DOOR_LIKE = new HashSet<>(Arrays.asList(
		WalkExit.DOOR_HANDLED,
		WalkExit.DOOR_HANDLED_BEFORE_MINIMAP_CLICK,
		WalkExit.DOOR_HANDLED_DURING_INTERIM,
		WalkExit.DOOR_HANDLED_LOCAL_REACHABILITY,
		WalkExit.DOOR_HANDLED_LOCAL_REACHABILITY_RAW_SCAN,
		WalkExit.DOOR_HANDLED_NEARBY_ROUTE_DOOR,
		WalkExit.DOOR_HANDLED_PATH_ADJ_SCAN,
		WalkExit.RAW_PATH_SCENE_OBJECT_HANDLED,
		WalkExit.POST_CLICK_RAW_PATH_SCENE_OBJECT_HANDLED));

	@Test
	public void tailExemptionIsExactlyThisSet()
	{
		for (WalkExit exit : WalkExit.values())
		{
			assertEquals(exit.name() + " changed its tail-exemption meaning",
				TAIL_EXEMPT.contains(exit), exit.isTailExempt());
		}
	}

	@Test
	public void doorLikeClassificationIsExactlyThisSet()
	{
		for (WalkExit exit : WalkExit.values())
		{
			assertEquals(exit.name() + " changed its door-like meaning",
				DOOR_LIKE.contains(exit), exit.isDoorLike());
		}
	}

	/**
	 * The whole point of the enum is that the set of reasons is enumerable. Two of them
	 * ({@code door-edge-resolved-after-wait}, {@code door-edge-waiting-retry}) were produced inside a
	 * ternary and never appeared in a search for {@code exitReason = "…"}, so the reason set could not
	 * be recovered by reading the code. Pin the full set so a new value has to be added here too.
	 */
	@Test
	public void theReasonSetIsComplete()
	{
		Set<String> expected = new HashSet<>(Arrays.asList(
			"end-of-path",
			"door-handled",
			"door-handled-before-minimap-click",
			"door-handled-during-interim",
			"door-handled-local-reachability",
			"door-handled-local-reachability-raw-scan",
			"door-handled-nearby-route-door",
			"door-handled-path-adj-scan",
			"path-blocker-handled",
			"rockfall-handled",
			"transport-handled",
			"current-tile-transport-handled",
			"post-click-current-tile-transport-handled",
			"raw-path-scene-object-handled",
			"post-click-raw-path-scene-object-handled",
			"frontier-obstacle-handled",
			"transport-handled-local-reachability",
			"local-recovery-click",
			"local-reachability-miss-no-click",
			"recent-door-edge-nudge",
			"door-suppressed-approach-click",
			"door-recovery-suppressed",
			"recovery-position-stale",
			"recovery-click-preempted-by-action",
			"recovery-target-walled-replan",
			"recovery-target-walled-waiting",
			"door-edge-resolved-fast-click",
			"door-edge-resolved-after-wait",
			"door-edge-resolved-after-nearby-wait",
			"door-edge-waiting-retry",
			"door-edge-nearby-waiting-retry",
			"interim-in-flight:route",
			"interim-in-flight:recovery",
			"interim-in-flight:click",
			"recovery-move-in-flight",
			"route-move-in-flight",
			"door-settling-yield",
			"door-traversal-pending-yield",
			"transport-settling-yield",
			"route-fold-continuation-click",
			"route-fold-continuation-pending",
			"off-path-deferred",
			"not-near-path",
			"click-failed-off-minimap",
			"player-location-null"));

		Set<String> actual = new HashSet<>();
		for (WalkExit exit : WalkExit.values())
		{
			actual.add(exit.wireName());
		}
		assertEquals("the set of walker exit reasons changed", expected, actual);
		assertEquals("wire names must be unique", WalkExit.values().length, actual.size());
	}

	/** The parameterized reason has to rebuild the exact string the log consumers expect. */
	@Test
	public void offPathDeferredKeepsItsDetailSuffix()
	{
		assertEquals("off-path-deferred:recent-click",
			WalkExit.OFF_PATH_DEFERRED.wireName("recent-click"));
		assertEquals("off-path-deferred:", WalkExit.OFF_PATH_DEFERRED.wireName(null));
		assertTrue(WalkExit.OFF_PATH_DEFERRED.wireName("x").startsWith("off-path-deferred:"));
	}

	/** A detail on any other reason is meaningless and must not corrupt its wire name. */
	@Test
	public void detailIsIgnoredForNonParameterizedReasons()
	{
		assertEquals("door-handled", WalkExit.DOOR_HANDLED.wireName("ignored"));
	}
}
