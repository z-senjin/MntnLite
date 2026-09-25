/*
 * Role:
 * Selects the next available strategy for the highest-priority
 * unfinished goal.
 *
 * Purpose:
 * The Planner makes decisions but does not execute game actions.
 * It returns a Plan containing the selected Goal and AccountStrategy.
 *
 * Selection order (with human-like randomization):
 * 1. Apply session priority jitter to goals, sort by effective priority.
 * 2. For each goal, find all strategies that support it and can start.
 * 3. Score each valid (goal, strategy) pair using session weights, variety bonus,
 *    level appropriateness, and priority.
 * 4. Weighted random selection from scored candidates.
 * 5. Return null when no configured strategy is currently available.
 */
package net.runelite.client.plugins.microbot.mntn.aio.core;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.mntn.aio.core.AccountStrategy;
import net.runelite.client.plugins.microbot.mntn.aio.core.Goal;
import net.runelite.client.plugins.microbot.mntn.aio.core.AccountContext;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Random;

public final class Planner
{
    public Plan choose(
            List<Goal> goals,
            List<AccountStrategy> strategies,
            AccountContext context)
    {
        return choose(goals, strategies, context, null);
    }

    public Plan choose(
            List<Goal> goals,
            List<AccountStrategy> strategies,
            AccountContext context,
            PlannerSessionContext sessionContext)
    {
        Objects.requireNonNull(goals, "goals");
        Objects.requireNonNull(strategies, "strategies");
        Objects.requireNonNull(context, "context");

        if (sessionContext == null)
        {
            return chooseLegacy(goals, strategies, context);
        }

        // Build candidate list with scores
        List<ScoredCandidate> candidates = new ArrayList<>();

        // Apply priority jitter and copy goals
        List<Goal> orderedGoals = new ArrayList<>(goals);
        for (Goal goal : orderedGoals)
        {
            if (goal == null)
            {
                continue;
            }
            // Apply jitter once per session
            if (!sessionContext.getGoalPriorityJitterMap().containsKey(goal.hashCode()))
            {
                int jitter = Rs2Random.betweenInclusive(-2, 2);
                sessionContext.setGoalPriorityJitter(goal, jitter);
            }
        }

        orderedGoals.sort(Comparator.comparingInt(sessionContext::getEffectivePriority));

        for (Goal goal : orderedGoals)
        {
            if (goal == null || goal.isComplete(context))
            {
                continue;
            }

            for (AccountStrategy strategy : strategies)
            {
                if (strategy == null || !strategy.supports(goal))
                {
                    continue;
                }

                if (!strategy.canStart(context, goal))
                {
                    continue;
                }

                // Calculate score for this (goal, strategy) pair
                double score = calculateScore(goal, strategy, context, sessionContext);
                candidates.add(new ScoredCandidate(goal, strategy, score));
            }
        }

        if (candidates.isEmpty())
        {
            return null;
        }

        // Weighted random selection
        return selectWeightedRandom(candidates, sessionContext.getSessionSeed());
    }

    private Plan chooseLegacy(
            List<Goal> goals,
            List<AccountStrategy> strategies,
            AccountContext context)
    {
        // Original deterministic behavior for backward compatibility
        List<Goal> orderedGoals = new ArrayList<>(goals);
        orderedGoals.sort(Comparator.comparingInt(Goal::getPriority));

        for (Goal goal : orderedGoals)
        {
            if (goal == null || goal.isComplete(context))
            {
                continue;
            }

            for (AccountStrategy strategy : strategies)
            {
                if (strategy == null || !strategy.supports(goal))
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

        return null;
    }

    private double calculateScore(
            Goal goal,
            AccountStrategy strategy,
            AccountContext context,
            PlannerSessionContext sessionContext)
    {
        double score = 1.0;

        // 1. Base priority score (lower priority number = higher score)
        int effectivePriority = sessionContext.getEffectivePriority(goal);
        score *= 1.0 / (effectivePriority + 1); // Priority 1 -> 0.5, Priority 2 -> 0.33, etc.

        // 2. Skill weight from session (0.7-1.3)
        if (goal.getType() == GoalType.SKILL_LEVEL)
        {
            score *= sessionContext.getSkillWeight(goal.getSkill());
        }

        // 3. Variety bonus (penalizes recently used strategies)
        score *= sessionContext.getVarietyBonus(strategy.getName());

        // 4. Level appropriateness (prefers strategies matching current level)
        if (goal.getType() == GoalType.SKILL_LEVEL)
        {
            int currentLevel = context.getLevel(goal.getSkill());
            score *= sessionContext.getLevelAppropriateness(goal.getSkill(), currentLevel, (int) goal.getTarget());
        }

        // 5. Goal type modifier
        switch (goal.getType())
        {
            case SKILL_LEVEL:
                score *= 1.0;
                break;
            case CASH:
                score *= 0.9; // Slightly lower priority for cash goals
                break;
            case QUEST:
                score *= 1.1; // Slightly higher for quests
                break;
        }

        return score;
    }

    private Plan selectWeightedRandom(List<ScoredCandidate> candidates, long sessionSeed)
    {
        // Add small deterministic tiebreaker based on session seed
        Random rng = new Random(sessionSeed + candidates.size());

        double totalWeight = 0.0;
        for (ScoredCandidate c : candidates)
        {
            totalWeight += c.score;
        }

        if (totalWeight <= 0)
        {
            return candidates.get(0).toPlan();
        }

        double roll = rng.nextDouble() * totalWeight;
        double cumulative = 0.0;

        for (ScoredCandidate c : candidates)
        {
            cumulative += c.score;
            if (roll <= cumulative)
            {
                return c.toPlan();
            }
        }

        // Fallback (shouldn't happen)
        return candidates.get(candidates.size() - 1).toPlan();
    }

    private static class ScoredCandidate
    {
        final Goal goal;
        final AccountStrategy strategy;
        final double score;

        ScoredCandidate(Goal goal, AccountStrategy strategy, double score)
        {
            this.goal = goal;
            this.strategy = strategy;
            this.score = score;
        }

        Plan toPlan()
        {
            return new Plan(goal, strategy);
        }
    }
}