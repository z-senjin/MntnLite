/*
 * Role:
 * Selects the next available strategy for the highest-priority
 * unfinished goal.
 *
 * Purpose:
 * The Planner makes decisions but does not execute game actions.
 * It returns a Plan containing the selected Goal and AccountStrategy.
 *
 * Selection order:
 * 1. Sort goals by priority, with lower numbers first.
 * 2. Skip completed goals.
 * 3. Check strategies in their configured list order.
 * 4. Return the first strategy that supports the goal and can start.
 * 5. Return null when no configured strategy is currently available.
 */
package net.runelite.client.plugins.microbot.mntn.aio.core;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.mntn.aio.core.AccountStrategy;
import net.runelite.client.plugins.microbot.mntn.aio.core.Goal;
import net.runelite.client.plugins.microbot.mntn.aio.core.AccountContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class Planner
{
    public Plan choose(
            List<Goal> goals,
            List<AccountStrategy> strategies,
            AccountContext context)
    {
        Objects.requireNonNull(goals, "goals");
        Objects.requireNonNull(strategies, "strategies");
        Objects.requireNonNull(context, "context");

        /*
         * Copy the goal list so sorting does not change the list owned
         * by AccountBuilderScript.
         */
        List<Goal> orderedGoals = new ArrayList<>(goals);

        orderedGoals.sort(
                Comparator.comparingInt(Goal::getPriority)
        );

        for (Goal goal : orderedGoals)
        {
            if (goal == null)
            {
                continue;
            }

            if (goal.isComplete(context))
            {
                continue;
            }

            for (AccountStrategy strategy : strategies)
            {
                if (strategy == null)
                {
                    continue;
                }

                if (!strategy.supports(goal))
                {
                    continue;
                }

                if (!strategy.canStart(context, goal))
                {
                    continue;
                }

                return new Plan(goal, strategy);
            }
        }

        /*
         * An unfinished goal may exist even when no strategy can
         * currently start.
         */
        return null;
    }
}