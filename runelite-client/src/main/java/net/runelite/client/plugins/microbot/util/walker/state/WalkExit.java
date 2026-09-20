package net.runelite.client.plugins.microbot.util.walker.state;

/**
 * Why one iteration of the {@code processWalk} tail loop ended.
 *
 * <p>This replaces a bare {@code String exitReason} that carried the loop's control flow through 47
 * assignment sites and was consumed by string equality and {@code startsWith} in eight places. Three
 * downstream behaviours keyed off that string — whether the iteration counts as route progress
 * (partial-retry budget), whether it is exempt from the tail-iteration cap, and whether a canvas
 * nudge is owed after a door-like exit — and a value that no branch had classified simply fell
 * through to the default in each.
 *
 * <p>That is not hypothetical. Two of the values below ({@link #DOOR_EDGE_RESOLVED_AFTER_WAIT} and
 * {@link #DOOR_EDGE_WAITING_RETRY}) are produced inside a ternary and never appear in a search for
 * {@code exitReason = "…"}, so an audit that enumerates the reasons by grepping the assignments
 * misses them. Making the set an enum makes it enumerable, exhaustively switchable, and impossible
 * to extend without deciding what the new value means.
 *
 * <h2>Wire names are load-bearing</h2>
 * {@link #wireName()} returns the exact string the old code logged. Live walker debugging in this
 * repo is log-driven, and renaming an exit reason would blind the one diagnostic that works. Do not
 * "tidy" these strings.
 *
 * @see net.runelite.client.plugins.microbot.util.walker.Rs2Walker
 */
public enum WalkExit
{
	// ---- loop completed normally ----

	/** The segment loop ran to the end of the path without any handler acting. */
	END_OF_PATH("end-of-path", false, false, false),

	// ---- an obstacle handler acted (route progress) ----

	DOOR_HANDLED("door-handled", true, false, true),
	DOOR_HANDLED_BEFORE_MINIMAP_CLICK("door-handled-before-minimap-click", true, false, true),
	DOOR_HANDLED_DURING_INTERIM("door-handled-during-interim", true, false, true),
	DOOR_HANDLED_LOCAL_REACHABILITY("door-handled-local-reachability", true, false, true),
	DOOR_HANDLED_LOCAL_REACHABILITY_RAW_SCAN("door-handled-local-reachability-raw-scan", true, false, true),
	DOOR_HANDLED_NEARBY_ROUTE_DOOR("door-handled-nearby-route-door", true, false, true),
	DOOR_HANDLED_PATH_ADJ_SCAN("door-handled-path-adj-scan", true, false, true),
	PATH_BLOCKER_HANDLED("path-blocker-handled", true, false, false),
	ROCKFALL_HANDLED("rockfall-handled", true, false, false),
	TRANSPORT_HANDLED("transport-handled", true, false, false),
	CURRENT_TILE_TRANSPORT_HANDLED("current-tile-transport-handled", true, false, false),
	POST_CLICK_CURRENT_TILE_TRANSPORT_HANDLED("post-click-current-tile-transport-handled", true, false, false),
	RAW_PATH_SCENE_OBJECT_HANDLED("raw-path-scene-object-handled", true, false, true),
	POST_CLICK_RAW_PATH_SCENE_OBJECT_HANDLED("post-click-raw-path-scene-object-handled", true, false, true),

	// ---- recovery acted, or resolved the blocked frontier ----
	// Recovery doing its job is progress. These were all non-progress, which is how a walk that was
	// mining a rockfall, taking a shortcut or clicking its way back onto the route could spend its
	// whole retry budget and report UNREACHABLE while advancing.

	/** A rockfall was mined or an on-origin transport/shortcut was taken at the blocked frontier. */
	FRONTIER_OBSTACLE_HANDLED("frontier-obstacle-handled", true, false, false),
	/** Recovery took a transport (e.g. an agility shortcut) on the blocked edge. */
	TRANSPORT_HANDLED_LOCAL_REACHABILITY("transport-handled-local-reachability", true, false, false),
	/** A recovery click was issued and movement was confirmed to start. */
	LOCAL_RECOVERY_CLICK("local-recovery-click", true, false, false),
	LOCAL_REACHABILITY_MISS_NO_CLICK("local-reachability-miss-no-click", false, false, false),
	/** The door-edge nudge acted. */
	RECENT_DOOR_EDGE_NUDGE("recent-door-edge-nudge", true, false, false),
	/** A minimap click toward the door approach was issued; the player is walking to it. */
	DOOR_SUPPRESSED_APPROACH_CLICK("door-suppressed-approach-click", true, false, false),
	DOOR_RECOVERY_SUPPRESSED("door-recovery-suppressed", false, false, false),
	/** The pass was abandoned because the player MOVED mid-pass — movement is the definition of progress. */
	RECOVERY_POSITION_STALE("recovery-position-stale", true, false, false),
	/** Yielded because a door open / walker-owned movement is still in flight. */
	RECOVERY_CLICK_PREEMPTED_BY_ACTION("recovery-click-preempted-by-action", true, false, false),
	/** Genuinely walled: this is the "we are stuck" signal the retry budget exists for. */
	RECOVERY_TARGET_WALLED_REPLAN("recovery-target-walled-replan", false, false, false),
	RECOVERY_TARGET_WALLED_WAITING("recovery-target-walled-waiting", false, false, false),

	// ---- door edge resolution around a recent attempt ----
	// "Resolved" means the door opened. Only the waiting-retry pair is a failure to advance.

	DOOR_EDGE_RESOLVED_FAST_CLICK("door-edge-resolved-fast-click", true, false, false),
	DOOR_EDGE_RESOLVED_AFTER_WAIT("door-edge-resolved-after-wait", true, false, false),
	DOOR_EDGE_RESOLVED_AFTER_NEARBY_WAIT("door-edge-resolved-after-nearby-wait", true, false, false),
	DOOR_EDGE_WAITING_RETRY("door-edge-waiting-retry", false, false, false),
	DOOR_EDGE_NEARBY_WAITING_RETRY("door-edge-nearby-waiting-retry", false, false, false),

	// ---- yields while one of our own actions is still in flight ----
	// Waiting for an action we issued is not a failed attempt. Charging these meant three settle
	// windows at one ordinary door could exhaust the budget and abort the walk.

	/**
	 * Yielded to a live interim waypoint. Three separate places in the loop do this, and until they
	 * were told apart a log line reading {@code interim-in-flight} could mean any of them — which
	 * twice made a real stall undiagnosable from the log. The suffix names the site; the shared
	 * {@code interim-in-flight} prefix keeps one grep matching all three.
	 */
	INTERIM_IN_FLIGHT_ROUTE("interim-in-flight:route", true, true, false),
	/** The blocked-frontier recovery deferred to an interim it had already clicked. */
	INTERIM_IN_FLIGHT_RECOVERY("interim-in-flight:recovery", true, true, false),
	/** Click selection found the player still travelling to the previous interim. */
	INTERIM_IN_FLIGHT_CLICK("interim-in-flight:click", true, true, false),
	RECOVERY_MOVE_IN_FLIGHT("recovery-move-in-flight", true, true, false),
	ROUTE_MOVE_IN_FLIGHT("route-move-in-flight", true, true, false),
	DOOR_SETTLING_YIELD("door-settling-yield", true, false, false),
	DOOR_TRAVERSAL_PENDING_YIELD("door-traversal-pending-yield", true, false, false),
	TRANSPORT_SETTLING_YIELD("transport-settling-yield", true, false, false),

	// ---- route geometry / fold handling ----

	ROUTE_FOLD_CONTINUATION_CLICK("route-fold-continuation-click", true, true, false),
	ROUTE_FOLD_CONTINUATION_PENDING("route-fold-continuation-pending", false, false, false),

	// ---- the walk is not tracking the route ----

	/**
	 * Off-path, but a recent click / route progress / busy state says the player may still be
	 * advancing, so the replan was deferred. Carries a detail string naming the deferral reason;
	 * see {@link #wireName(String)}.
	 */
	OFF_PATH_DEFERRED("off-path-deferred", false, true, false),
	NOT_NEAR_PATH("not-near-path", false, false, false),
	CLICK_FAILED_OFF_MINIMAP("click-failed-off-minimap", false, false, false),
	PLAYER_LOCATION_NULL("player-location-null", false, false, false);

	private final String wireName;
	private final boolean progress;
	private final boolean tailExempt;
	private final boolean doorLike;

	WalkExit(String wireName, boolean progress, boolean tailExempt, boolean doorLike)
	{
		this.wireName = wireName;
		this.progress = progress;
		this.tailExempt = tailExempt;
		this.doorLike = doorLike;
	}

	/** The exact string this reason has always been logged as. Never change these. */
	public String wireName()
	{
		return wireName;
	}

	/**
	 * Log name including the deferral detail for {@link #OFF_PATH_DEFERRED}, which was previously
	 * built by string concatenation at the assignment site and parsed back apart downstream.
	 */
	public String wireName(String detail)
	{
		if (this != OFF_PATH_DEFERRED)
		{
			return wireName;
		}
		return wireName + ":" + (detail == null ? "" : detail);
	}

	/**
	 * The iteration ended because the walker <em>did</em> something that advances the route, or
	 * because movement it owns is already in flight — progress, not a failed attempt.
	 *
	 * <p>The partial-retry budget exists for "the goal is unreachable and we are stuck". Spending it
	 * on these conflates the two: on a partial path the budget is armed for the entire walk, so an
	 * ordinary door can exhaust it far into a working route and report UNREACHABLE while the player
	 * is still advancing. See {@code movement.md} #25.
	 */
	public boolean isProgress()
	{
		return progress;
	}

	/**
	 * Benign yields that must not consume the bounded tail-iteration budget, so long waits cannot
	 * exhaust it and EXIT a healthy walk.
	 */
	public boolean isTailExempt()
	{
		return tailExempt;
	}

	/** A door-like exit owes the post-door canvas nudge and its minimap hold-off window. */
	public boolean isDoorLike()
	{
		return doorLike;
	}
}
