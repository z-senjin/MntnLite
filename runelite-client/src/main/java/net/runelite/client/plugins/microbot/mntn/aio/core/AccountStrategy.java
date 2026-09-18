/*
 * Role:
 * Defines the behavior required from every account-building strategy.
 *
 * Purpose:
 * Allows the Planner to compare different strategies without knowing
 * their internal implementation. Mining, fishing, money-making, and
 * quest strategies all implement this same interface.
 *
 * Important:
 * supports() and canStart() should only inspect state. They should not
 * click, walk, open the bank, or perform other game actions.
 */
package net.runelite.client.plugins.microbot.mntn.aio.core;

import net.runelite.client.plugins.microbot.mntn.aio.core.StepResult;
import net.runelite.client.plugins.microbot.mntn.aio.core.AccountContext;

public interface AccountStrategy
{
    /**
     * Human-readable name used for logs, status text, and overlays.
     */
    String getName();

    /**
     * Returns true when this strategy knows how to work on the goal.
     *
     * Example:
     * A mining strategy supports a MINING skill-level goal.
     */
    boolean supports(Goal goal);

    /**
     * Returns true when the account currently meets the requirements
     * needed to begin this strategy.
     *
     * This method should only inspect account state.
     */
    boolean canStart(AccountContext context, Goal goal);

    /**
     * Performs one small unit of work and returns control to the main
     * scheduled script.
     */
    StepResult tick(AccountContext context, Goal goal);

    /**
     * Restores the strategy's internal state when it is stopped,
     * completed, or replaced by another strategy.
     */
    default void reset()
    {
        // Strategies with internal states should override this method.
    }
}