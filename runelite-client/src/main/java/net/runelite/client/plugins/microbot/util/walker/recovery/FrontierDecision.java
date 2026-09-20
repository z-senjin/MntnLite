package net.runelite.client.plugins.microbot.util.walker.recovery;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.walker.state.WalkExit;

import java.util.List;
import java.util.Map;

/**
 * The blocked-frontier cascade's pure decisions: WHERE the route is actually blocked, and which raw
 * edge that frontier corresponds to.
 *
 * <p>Functional core of the recovery cascade — the same split {@link RouteRecovery} and
 * {@code segment.SegmentGate} already use. The caller keeps every interaction (waiting on doors,
 * mining, clicking); this only answers questions about the route and the reachable set, so the
 * answers can be pinned in a decision table instead of rediscovered on a live walk.
 */
public final class FrontierDecision
{
	/** No route tile before the miss is blocked. */
	public static final int NO_EARLIER_BLOCKED_INDEX = -1;

	private FrontierDecision()
	{
	}

	/**
	 * The recovery scan anchor: the first route index whose raw mapping is at or past the player's
	 * own raw position. Route tiles mapped BEHIND the player are spent — the walk never needs to
	 * stand on them again — and recovery must not chase them.
	 *
	 * <p>The smoothed closest index cannot express "one raw tile past a door". At the Stronghold of
	 * Security's paired gates (2026-08-12) a moves-you gate carried the player one raw tile through;
	 * the next smoothed point was nine tiles out, so the closest smoothed index stayed on the
	 * near-side start tile, which now read unreachable through the auto-closed gate. Recovery chased
	 * it, clicked the same gate from the far side, and the gate carried the player straight back —
	 * a two-sided bounce that repeated every ~6 seconds for five minutes.
	 *
	 * <p>Unmapped entries ({@code smoothedToRaw[i] < 0}) stop the advance: no evidence of "behind"
	 * must not read as "spent".
	 */
	public static int forwardScanStartIndex(int[] smoothedToRaw, int startIndex, int playerRawIdx)
	{
		if (smoothedToRaw == null || startIndex < 0 || startIndex >= smoothedToRaw.length
			|| playerRawIdx <= 0)
		{
			return startIndex;
		}
		int index = startIndex;
		while (index < smoothedToRaw.length - 1
			&& smoothedToRaw[index] >= 0
			&& smoothedToRaw[index] < playerRawIdx)
		{
			index++;
		}
		return index;
	}

	/**
	 * The earliest route tile at or after {@code fromIndex} and before {@code missIndex} that the
	 * player cannot reach, or {@link #NO_EARLIER_BLOCKED_INDEX}.
	 *
	 * <p>Anti-end-camping rewind. The near-player reachability check skips far-away route tiles, so
	 * on a route whose tail folds back beside the player — Clock Tower — the miss fires on the GOAL
	 * (Euclidean-near, index at the end) while the REAL blocked frontier, the door tiles at
	 * mid-route, was never examined. Recovery then camps on the end: door scans probe the wrong raw
	 * segment and the recovery target anchors at the goal. The earliest unreachable tile is the first
	 * edge the walk genuinely cannot cross, which is where the obstacle really is.
	 *
	 * <p>Tiles on another plane are skipped rather than treated as blocked: a route that climbs a
	 * staircase legitimately contains tiles the player's plane cannot reach, and rewinding onto one
	 * would send recovery at a staircase that is working.
	 *
	 * @param reachable player-origin reachability; {@code null} disables the rewind entirely, because
	 *                  "no evidence" must not read as "everything is blocked"
	 */
	public static int earliestBlockedIndex(List<WorldPoint> path,
										   int fromIndex,
										   int missIndex,
										   int playerPlane,
										   Map<WorldPoint, Integer> reachable)
	{
		if (path == null || reachable == null)
		{
			return NO_EARLIER_BLOCKED_INDEX;
		}
		for (int index = Math.max(0, fromIndex); index < missIndex && index < path.size(); index++)
		{
			WorldPoint tile = path.get(index);
			if (tile != null
				&& tile.getPlane() == playerPlane
				&& !reachable.containsKey(tile))
			{
				return index;
			}
		}
		return NO_EARLIER_BLOCKED_INDEX;
	}

	/**
	 * Whether an unreachable route tile is too far ahead for fresh evidence to matter this pass.
	 *
	 * <p>Every tile past a closed frontier is unreachable, so on a gated route the unreachable
	 * branch runs for the WHOLE forward tail — dozens of tiles per pass, each paying the loop
	 * snapshot's client-thread hops and, whenever the player has moved since the last capture, a
	 * full-radius reachability BFS. Fresh evidence has exactly one consumer, the local-recovery
	 * branch, and that is gated to {@code nearGate}; a tile that no plausible position drift could
	 * bring inside that gate needs no fresh reads at all. Measured 2026-08-14 (Lumbridge→Varlamore):
	 * ~45s of a 288s walk stood still at obstacles, most of it in these hops — recaptures fired for
	 * tiles on the far side of an NPC travel.
	 *
	 * <p>{@code lastKnownPlayerLoc} is the pass-stale snapshot position, so {@code stalenessMargin}
	 * covers ground run since it was read: borderline tiles still take the fresh-capture path, and a
	 * null position (no snapshot yet) never skips. {@code distanceTo2D} ignores plane, which errs
	 * safe — an overhead tile reads as near and takes the fresh path.
	 */
	public static boolean shouldSkipFarUnreachableTile(WorldPoint tile,
		WorldPoint lastKnownPlayerLoc, int nearGate, int stalenessMargin)
	{
		return tile != null && lastKnownPlayerLoc != null
			&& tile.distanceTo2D(lastKnownPlayerLoc) > nearGate + stalenessMargin;
	}

	/**
	 * What a wait on a recently-attempted door concluded.
	 *
	 * <p>Every outcome but {@link #FALL_THROUGH} ends the pass — the walk goes round again and
	 * re-derives from wherever the door left the player.
	 */
	public enum DoorWaitOutcome
	{
		/** Edge opened and the follow-through click landed. */
		RESOLVED_FAST_CLICK(WalkExit.DOOR_EDGE_RESOLVED_FAST_CLICK),
		/** Edge opened; no follow-through click was issued. */
		RESOLVED_AFTER_WAIT(WalkExit.DOOR_EDGE_RESOLVED_AFTER_WAIT),
		/** Edge did not open in the budget: go round and try again. */
		WAITING_RETRY(WalkExit.DOOR_EDGE_WAITING_RETRY),
		/** A door NEAR this edge opened and the player moved through it. */
		RESOLVED_AFTER_NEARBY_WAIT(WalkExit.DOOR_EDGE_RESOLVED_AFTER_NEARBY_WAIT),
		/** A door near this edge did not open in the budget. */
		NEARBY_WAITING_RETRY(WalkExit.DOOR_EDGE_NEARBY_WAITING_RETRY),
		/**
		 * A door near this edge opened but the player did not move. The wait proved nothing about
		 * THIS frontier — some other door resolved — so the cascade must carry on to the settle
		 * checks and the real recovery rather than reporting progress it did not make.
		 */
		FALL_THROUGH(null);

		private final WalkExit exit;

		DoorWaitOutcome(WalkExit exit)
		{
			this.exit = exit;
		}

		/** The exit to record, or {@code null} for {@link #FALL_THROUGH}. */
		public WalkExit exit()
		{
			return exit;
		}

		/** Whether this outcome ends the pass. */
		public boolean endsPass()
		{
			return this != FALL_THROUGH;
		}
	}

	/**
	 * Whether a follow-through click is worth issuing after a wait on THIS edge.
	 *
	 * <p>Split from {@link #afterEdgeWait} so the caller performs the click only when it is wanted:
	 * the click is an interaction and cannot live in a pure decision.
	 */
	public static boolean shouldFastClickAfterEdgeWait(boolean edgeResolved)
	{
		return edgeResolved;
	}

	/**
	 * As {@link #shouldFastClickAfterEdgeWait}, for a door near — but not on — this edge.
	 *
	 * <p>Movement is required as well as resolution. A nearby door that opened while the player
	 * stayed put says nothing about the frontier in front of us.
	 */
	public static boolean shouldFastClickAfterNearbyWait(boolean nearbyResolved, boolean playerMoved)
	{
		return nearbyResolved && playerMoved;
	}

	/**
	 * @param fastClicked whether the follow-through click landed; must be {@code false} when
	 *                    {@link #shouldFastClickAfterEdgeWait} said not to attempt one
	 */
	public static DoorWaitOutcome afterEdgeWait(boolean edgeResolved, boolean fastClicked)
	{
		if (!edgeResolved)
		{
			return DoorWaitOutcome.WAITING_RETRY;
		}
		return fastClicked ? DoorWaitOutcome.RESOLVED_FAST_CLICK : DoorWaitOutcome.RESOLVED_AFTER_WAIT;
	}

	/**
	 * @param playerMoved whether the player's tile changed across the wait
	 * @param fastClicked whether the follow-through click landed; must be {@code false} when
	 *                    {@link #shouldFastClickAfterNearbyWait} said not to attempt one
	 */
	public static DoorWaitOutcome afterNearbyWait(boolean nearbyResolved,
												  boolean playerMoved,
												  boolean fastClicked)
	{
		if (!nearbyResolved)
		{
			return DoorWaitOutcome.NEARBY_WAITING_RETRY;
		}
		if (!playerMoved)
		{
			return DoorWaitOutcome.FALL_THROUGH;
		}
		return fastClicked
			? DoorWaitOutcome.RESOLVED_FAST_CLICK
			: DoorWaitOutcome.RESOLVED_AFTER_NEARBY_WAIT;
	}

	/**
	 * Why the cascade yields instead of acting on the blocked frontier, in precedence order.
	 *
	 * <p>All three mean "an action of ours is already in flight; probing again would fight it".
	 * They were three sequential {@code if}s whose ORDER was the policy and was documented nowhere.
	 */
	public enum FrontierYield
	{
		/** Nothing in flight: run the door and blocker handlers. */
		NONE(null),
		/**
		 * A door interaction is still settling, or the per-pass door-skip is cooling down. Probing
		 * now re-enters the resolver mid-settle, which loops.
		 */
		DOOR_SETTLING(WalkExit.DOOR_SETTLING_YIELD),
		/**
		 * A door opened moments ago and the player has not started through it. Let the one-shot
		 * traversal finish before falling back to path-adjacent probing or recovery clicks.
		 */
		DOOR_TRAVERSAL_PENDING(WalkExit.DOOR_TRAVERSAL_PENDING_YIELD),
		/** A recovery interim click is still being walked. */
		INTERIM_IN_FLIGHT(WalkExit.INTERIM_IN_FLIGHT_RECOVERY);

		private final WalkExit exit;

		FrontierYield(WalkExit exit)
		{
			this.exit = exit;
		}

		/** The exit to record, or {@code null} for {@link #NONE}. */
		public WalkExit exit()
		{
			return exit;
		}

		public boolean yields()
		{
			return this != NONE;
		}
	}

	/**
	 * Whether to yield the frontier this pass, and why.
	 *
	 * <p>Precedence is settling → traversal-pending → interim, preserved from the original
	 * sequential ifs. Settling wins because it is the broadest "we just touched a door" window;
	 * asking the narrower questions first would let a probe through during it.
	 *
	 * @param recentDoorAgeMs      ms since the last door attempt near this edge; NEGATIVE means
	 *                             there was none, and must not be read as "zero ms ago"
	 * @param playerMoving         a player already moving is traversing the door they opened, so
	 *                             there is nothing to wait for — the yield is for the stationary case
	 * @param interimRecoveryActive a recovery interim click is still in flight
	 */
	public static FrontierYield yieldBeforeDoorActions(boolean doorInteractionSettling,
													   boolean doorEdgePassCoolingDown,
													   long recentDoorAgeMs,
													   long doorTraversalBlockMs,
													   boolean playerMoving,
													   boolean interimRecoveryActive)
	{
		if (doorInteractionSettling || doorEdgePassCoolingDown)
		{
			return FrontierYield.DOOR_SETTLING;
		}
		boolean pendingTraversal = recentDoorAgeMs >= 0
			&& recentDoorAgeMs <= doorTraversalBlockMs
			&& !playerMoving;
		if (pendingTraversal)
		{
			return FrontierYield.DOOR_TRAVERSAL_PENDING;
		}
		return interimRecoveryActive ? FrontierYield.INTERIM_IN_FLIGHT : FrontierYield.NONE;
	}

	/**
	 * Clamps a recovery index so it can neither go backwards along the route nor off the end.
	 *
	 * <p>The floor is the later of the pass's route position and the frontier: recovering to a tile
	 * BEHIND the blockage would walk the player away from the goal, which is the retreat behaviour
	 * the walled-route net exists to refuse.
	 */
	public static int clampRecoveryIndex(int candidateIndex, int routePositionIndex, int frontierIndex,
										 int pathSize)
	{
		int floor = Math.max(routePositionIndex, frontierIndex);
		return Math.min(Math.max(candidateIndex, floor), pathSize - 1);
	}

	/**
	 * Walks the recovery index back along the route until it leaves a hazard, stopping at
	 * {@code minIndex}.
	 *
	 * <p>Recovery must not park the player next to an aggressive NPC. The planner avoids those, but
	 * this runtime fallback would otherwise strand the walk in melee.
	 *
	 * <p>Deliberately CAN return a hazardous index: if every tile back to the floor is dangerous the
	 * index stops at the floor rather than retreating past the frontier. Walking backwards off the
	 * route is the worse failure, and the caller's click decision still has its own guards.
	 */
	public static int stepBackFromDanger(List<WorldPoint> path, int recoverIndex, int minIndex,
										 java.util.function.Predicate<WorldPoint> dangerous)
	{
		if (path == null || dangerous == null)
		{
			return recoverIndex;
		}
		int safeIndex = recoverIndex;
		while (safeIndex > minIndex
			&& safeIndex >= 0 && safeIndex < path.size()
			&& dangerous.test(path.get(safeIndex)))
		{
			safeIndex--;
		}
		return safeIndex;
	}

	/**
	 * The final recovery click target, in precedence order.
	 *
	 * <p>Three candidates compete and the order is the policy:
	 *
	 * <ol>
	 *   <li>{@code base} — the furthest clickable route tile (or an interpolated point near the
	 *       minimap edge when that tile is beyond the clip).</li>
	 *   <li>{@code rawGated} — the furthest RAW-path point the walled-click net vouches for. Finer
	 *       grained than the smoothed route, so it tracks the actual corridor.</li>
	 *   <li>{@code walkToOrigin} — a transport or agility-shortcut origin resolved at the frontier.
	 *       Wins outright: the transport only dispatches while the player STANDS on its origin, so
	 *       clicking the far side of a shortcut loops on the near bank forever (the stepping-stone
	 *       incident). Stepping onto the origin lets the normal transport handler cross next tick.</li>
	 * </ol>
	 *
	 * <p>Note the asymmetry, preserved from the original: {@code rawGated} must clear the hazard
	 * predicate, {@code walkToOrigin} is not hazard-checked. A shortcut origin beside an aggressive
	 * NPC is therefore still chosen. That is existing behaviour, not an endorsement — changing it is
	 * a behaviour change and belongs in its own commit with its own live evidence.
	 *
	 * @param playerLoc a candidate equal to where we already stand is no recovery at all
	 */
	public static WorldPoint chooseRecoveryTarget(WorldPoint base,
												  WorldPoint rawGated,
												  WorldPoint walkToOrigin,
												  WorldPoint playerLoc,
												  java.util.function.Predicate<WorldPoint> dangerous)
	{
		WorldPoint chosen = base;
		if (rawGated != null
			&& !rawGated.equals(playerLoc)
			&& (dangerous == null || !dangerous.test(rawGated)))
		{
			chosen = rawGated;
		}
		if (walkToOrigin != null && !walkToOrigin.equals(playerLoc))
		{
			chosen = walkToOrigin;
		}
		return chosen;
	}

	/**
	 * Which recovery-click outcomes end the pass, and with what exit.
	 *
	 * <p>Two of the five continue and they do so for different reasons: {@code CLICK} continues
	 * because the click is about to be issued, {@code NO_TARGET} because there is nothing worth
	 * clicking and the rejoin logic below should get its turn. {@code NO_TARGET} was never mentioned
	 * in the loop at all — it fell through the three {@code if}s by omission, which reads
	 * identically to a forgotten case.
	 *
	 * <p>The caller still performs {@code REPLAN_WALLED}'s side effects (cooldown stamp, replan);
	 * this only says what the pass reports.
	 *
	 * @return the exit to record, or {@code null} when the cascade continues
	 */
	public static WalkExit exitForRecoveryClick(RouteRecovery.RecoveryClickAction action)
	{
		if (action == null)
		{
			return null;
		}
		switch (action)
		{
			case YIELD_ACTION_IN_FLIGHT:
				return WalkExit.RECOVERY_CLICK_PREEMPTED_BY_ACTION;
			case REPLAN_WALLED:
				return WalkExit.RECOVERY_TARGET_WALLED_REPLAN;
			case WAIT_WALLED:
				return WalkExit.RECOVERY_TARGET_WALLED_WAITING;
			case CLICK:
			case NO_TARGET:
			default:
				return null;
		}
	}

	/**
	 * Whether the canvas-click fallback is worth trying after the minimap click failed to land.
	 *
	 * <p>Last resort, deliberately narrow: only on the FINAL approach, when the goal is essentially
	 * underfoot and the minimap click may simply have missed the clip because everything is too
	 * close together. Widening either bound turns a rescue into a second click source competing with
	 * the minimap on ordinary walks.
	 *
	 * <p>Pure: the caller still runs the reachability probe and the click, both of which touch the
	 * client. This only answers whether they are worth spending.
	 */
	public static boolean shouldTrySceneClickFallback(WorldPoint playerLoc,
													  WorldPoint goal,
													  WorldPoint recoverTarget,
													  int arrivalDistance,
													  int finalAdjacentChebyshev,
													  int maxTargetDistance)
	{
		if (playerLoc == null || goal == null || recoverTarget == null)
		{
			return false;
		}
		int nearGoal = Math.max(2, arrivalDistance + finalAdjacentChebyshev);
		return playerLoc.distanceTo2D(goal) <= nearGoal
			&& playerLoc.distanceTo2D(recoverTarget) <= maxTargetDistance;
	}

	/** The raw-path edge a smoothed frontier index corresponds to. */
	public static final class FrontierEdge
	{
		private final int edgeIndex;
		private final int rawStart;
		private final int rawEndExclusive;
		private final WorldPoint from;
		private final WorldPoint to;

		FrontierEdge(int edgeIndex, int rawStart, int rawEndExclusive, WorldPoint from, WorldPoint to)
		{
			this.edgeIndex = edgeIndex;
			this.rawStart = rawStart;
			this.rawEndExclusive = rawEndExclusive;
			this.from = from;
			this.to = to;
		}

		public int edgeIndex()
		{
			return edgeIndex;
		}

		public int rawStart()
		{
			return rawStart;
		}

		public int rawEndExclusive()
		{
			return rawEndExclusive;
		}

		/** Raw tile the blocked edge leaves from, or {@code null} when the raw path cannot supply it. */
		public WorldPoint from()
		{
			return from;
		}

		/** Raw tile the blocked edge leads to, or {@code null}. */
		public WorldPoint to()
		{
			return to;
		}
	}

	/**
	 * Maps the frontier index onto the raw path, which is what every door and obstacle handler in the
	 * cascade is addressed by.
	 *
	 * <p>The edge starts one smoothed index BEFORE the frontier — the blocked edge is the step INTO
	 * the unreachable tile, not the step out of it — clamped so it can never precede the route
	 * position the pass started from.
	 */
	public static FrontierEdge frontierEdge(List<WorldPoint> rawPath,
											int[] smoothedToRaw,
											int fromIndex,
											int frontierIndex)
	{
		int rawSize = rawPath == null ? 0 : rawPath.size();
		int edgeIndex = Math.max(fromIndex, frontierIndex - 1);
		int rawStart = smoothedToRaw != null && edgeIndex < smoothedToRaw.length
			? smoothedToRaw[edgeIndex]
			: 0;
		int rawEndExclusive = smoothedToRaw != null && frontierIndex < smoothedToRaw.length
			? smoothedToRaw[frontierIndex] + 1
			: rawSize;
		WorldPoint from = rawStart >= 0 && rawStart < rawSize ? rawPath.get(rawStart) : null;
		WorldPoint to = rawEndExclusive - 1 >= 0 && rawEndExclusive - 1 < rawSize
			? rawPath.get(rawEndExclusive - 1)
			: null;
		return new FrontierEdge(edgeIndex, rawStart, rawEndExclusive, from, to);
	}
}
