/*
 * Role:
 * Represents the Planner's selected goal and strategy combination.
 *
 * Purpose:
 * Keeps the active goal connected to the exact strategy selected to
 * work on it. The main script retains this Plan between scheduler ticks.
 *
 * Plan does not execute work itself. The script calls:
 * plan.getStrategy().tick(context, plan.getGoal())
 */
package net.runelite.client.plugins.microbot.mntn.aio.core;

import net.runelite.client.plugins.microbot.mntn.aio.core.AccountStrategy;

import java.util.Objects;

public final class Plan
{
    private final Goal goal;
    private final AccountStrategy strategy;

    public Plan(Goal goal, AccountStrategy strategy)
    {
        this.goal = Objects.requireNonNull(goal, "goal");
        this.strategy = Objects.requireNonNull(strategy, "strategy");
    }

    public Goal getGoal()
    {
        return goal;
    }

    public AccountStrategy getStrategy()
    {
        return strategy;
    }

    @Override
    public String toString()
    {
        return "Plan{" +
                "goal=" + goal +
                ", strategy=" + strategy.getName() +
                '}';
    }
}