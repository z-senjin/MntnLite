package net.runelite.client.plugins.microbot.util.walker.recovery;

import net.runelite.client.plugins.microbot.util.walker.state.WalkExit;

/**
 * What the walk loop does at the end of one iteration: finish, replan, give up, or go round again.
 *
 * <p>Pure and fully injected, so the interactions between the partial-retry budget, its refill rule
 * and the tail-iteration exemption can be pinned in a decision table instead of re-discovered on a
 * live walk. The caller still performs the actions — replanning, telemetry, clearing the target.
 *
 * <p>The partial branch is where this matters. A "partial path" is a route the pathfinder could not
 * run all the way to the goal, which is every long or awkward walk, and on those routes the budget
 * is armed for the entire journey. Getting the classification wrong there does not degrade the
 * walk, it aborts it.
 */
public final class TailDecision
{
	/** Consecutive failures to advance on a partial route before the goal is called unreachable. */
	public static final int MAX_PARTIAL_RETRIES = 3;

	private TailDecision()
	{
	}

	public enum TailAction
	{
		/** Within the arrival threshold. */
		ARRIVED,
		/** Partial route, and the iteration advanced it: replan, but do not spend a retry. */
		PARTIAL_PROGRESS_REPLAN,
		/** Partial route and no progress: spend a retry and replan. */
		PARTIAL_RETRY_REPLAN,
		/** Partial route, budget spent: the goal is unreachable. */
		PARTIAL_EXHAUSTED,
		/** Go round again, charging one tail iteration. */
		CONTINUE,
		/** Go round again without charging a tail iteration (a benign yield). */
		CONTINUE_TAIL_EXEMPT
	}

	/**
	 * Route progress since the last retry means the walk is working, so the budget refills.
	 *
	 * <p>Standing somewhere new is required as well as the progress timestamp: the timestamp is also
	 * bumped when the route is merely REPLACED, and every retry replans, so the timestamp alone
	 * would let a retry refill the budget it just spent. Requiring movement is what still lets the
	 * budget drain when the target is genuinely unreachable and the player has stopped.
	 */
	public static boolean shouldRefillPartialRetryBudget(int retriesSpent,
														 boolean movedSinceLastRetry,
														 long routeProgressAdvancedAtMs,
														 long lastPartialRetryAtMs)
	{
		return retriesSpent > 0
			&& movedSinceLastRetry
			&& routeProgressAdvancedAtMs > lastPartialRetryAtMs;
	}

	/**
	 * @param retriesSpent budget already spent, AFTER any refill from
	 *                     {@link #shouldRefillPartialRetryBudget}
	 */
	public static TailAction decide(boolean withinFinishThreshold,
									boolean partialPath,
									WalkExit exit,
									int retriesSpent,
									int maxRetries)
	{
		if (withinFinishThreshold)
		{
			return TailAction.ARRIVED;
		}
		if (partialPath)
		{
			if (exit != null && exit.isProgress())
			{
				return TailAction.PARTIAL_PROGRESS_REPLAN;
			}
			return retriesSpent < maxRetries
				? TailAction.PARTIAL_RETRY_REPLAN
				: TailAction.PARTIAL_EXHAUSTED;
		}
		return exit != null && exit.isTailExempt()
			? TailAction.CONTINUE_TAIL_EXEMPT
			: TailAction.CONTINUE;
	}

	/** What to do about a route whose progress index has stopped advancing. */
	public enum StagnationAction
	{
		/** Progress is recent (or there is no route yet): nothing to do. */
		NONE,
		/** Stagnant: spend one stagnation replan and restart the clock. */
		REPLAN,
		/** Stagnant with the replan budget spent: end the walk honestly. */
		EXHAUSTED
	}

	/**
	 * The oscillation bound the other two budgets cannot provide. The wall-clock budget is sized for
	 * whole journeys (minutes), and the exempt-run counter resets on any movement — so a walk that
	 * ping-pongs between two tiles forever (measured: 4+ minutes of door/recovery oscillation at the
	 * Tithe Farm door until a human cancelled it) trips neither. Movement is not progress; the route
	 * progress index is. When the index has not advanced for a full budget, the route is not working:
	 * replan it, and when replanning has been given its chances, call the goal unreachable instead of
	 * letting the loop run unbounded.
	 *
	 * <p>The budget must dwarf every legitimate index hold: ranged door waits (≤8s), transport
	 * settles (~2s), off-path deferrals (~10s) — 60s is over six times the largest.
	 *
	 * @param routeProgressAdvancedAtMs when the stabilized route index last advanced (0 = no route yet;
	 *                                  the caller restarts this clock when it spends a REPLAN, so each
	 *                                  replan gets a full budget even when the new route is identical)
	 */
	public static StagnationAction decideRouteStagnation(long routeProgressAdvancedAtMs,
														 long nowMs,
														 long stagnationBudgetMs,
														 int stagnationReplansSpent,
														 int maxStagnationReplans)
	{
		if (routeProgressAdvancedAtMs <= 0L || stagnationBudgetMs <= 0L
			|| nowMs - routeProgressAdvancedAtMs <= stagnationBudgetMs)
		{
			return StagnationAction.NONE;
		}
		return stagnationReplansSpent < maxStagnationReplans
			? StagnationAction.REPLAN
			: StagnationAction.EXHAUSTED;
	}

	/**
	 * Whether a continuation re-click at the route tail is churn rather than flow. Mid-route,
	 * clicking the next stretch while still moving is exactly how the walker chains minimap clicks —
	 * that must stay. But inside the final band the click in flight already ends at (or beside) the
	 * goal, and re-clicking every pass fights it: measured as ~10 clicks in 7 seconds on the last
	 * tile, each minimap click quantizing onto a neighbour of the goal and restarting the dance.
	 * Let the in-flight click land; a stationary miss gets one precise follow-up instead.
	 */
	public static boolean suppressTailReclick(boolean playerMoving, int distanceToGoal, int tailBandTiles)
	{
		return playerMoving && distanceToGoal >= 0 && distanceToGoal <= tailBandTiles;
	}

	/**
	 * Whether the walk has run past its wall-clock budget.
	 *
	 * <p>The loop's iteration cap is not a bound on its own: several exit reasons decrement the tail
	 * counter, so a walk that keeps producing one of them goes round forever. Nothing else in the
	 * call chain imposes a time limit either.
	 *
	 * <p>Sized to catch a livelock, not a slow walk — a budget that aborts a working long journey
	 * would be a worse bug than the one it is guarding against.
	 */
	public static boolean isWallClockExhausted(long walkStartedAtMs, long nowMs, long budgetMs)
	{
		return walkStartedAtMs > 0L && budgetMs > 0L && nowMs - walkStartedAtMs > budgetMs;
	}

	/**
	 * Companion bound to {@link #isWallClockExhausted}: an uninterrupted run of tail-exempt
	 * iterations means the loop is yielding without ever advancing, which the iteration cap cannot
	 * see because those iterations refund themselves.
	 */
	public static boolean isExemptRunTooLong(int consecutiveExemptIterations, int cap)
	{
		return cap > 0 && consecutiveExemptIterations > cap;
	}
}
