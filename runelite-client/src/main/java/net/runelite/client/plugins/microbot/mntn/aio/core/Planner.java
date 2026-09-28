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
 * 4. Apply human-like penalties: no consecutive duplicates, skill/category switching,
 *    exponential recency decay.
 * 5. Weighted random selection from scored candidates using stateful PRNG.
 * 6. Return null when no configured strategy is currently available.
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
import java.util.stream.Collectors;

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

        // Re-roll priority jitter on each replan to simulate changing priorities
        sessionContext.rerollAllGoalPriorityJitter();

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
            // Apply jitter once per replan (re-rolled above)
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

        // Apply human-like penalties and filtering
        candidates = applyHumanLikePenalties(candidates, sessionContext);

        if (candidates.isEmpty())
        {
            return null;
        }

        // Weighted random selection using stateful PRNG
        return selectWeightedRandom(candidates, sessionContext);
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

    /**
     * Applies human-like penalties and the "no consecutive duplicates" rule.
     * Returns filtered and re-scored candidates.
     */
    private List<ScoredCandidate> applyHumanLikePenalties(
            List<ScoredCandidate> candidates,
            PlannerSessionContext sessionContext)
    {
        // CORE RULE: Exclude the immediately preceding strategy unless it's the ONLY option
        String lastStrategy = sessionContext.getLastStrategyName();
        List<ScoredCandidate> nonDuplicate = candidates.stream()
                .filter(c -> lastStrategy == null || !c.strategy.getName().equals(lastStrategy))
                .collect(Collectors.toList());

        List<ScoredCandidate> finalCandidates = nonDuplicate.isEmpty() ? candidates : nonDuplicate;

        // Apply additional penalties for skill/category recency and recency decay
        // Create new candidates with adjusted scores since score is final
        List<ScoredCandidate> penalizedCandidates = new ArrayList<>();
        for (ScoredCandidate c : finalCandidates)
        {
            double score = c.score;

            // Skill-level switching penalty (prefer different skill)
            Goal goal = c.goal;
            if (goal.getType() == GoalType.SKILL_LEVEL)
            {
                double skillPenalty = sessionContext.getSkillRecencyPenalty(goal.getSkill());
                score *= (1.0 - skillPenalty * 0.6); // 60% of penalty applied
            }

            // Category switching penalty (e.g., "mining" vs "woodcutting")
            String category = inferCategory(c.strategy);
            if (category != null)
            {
                double catPenalty = sessionContext.getCategoryRecencyPenalty(category);
                score *= (1.0 - catPenalty * 0.5); // 50% of penalty applied
            }

            // Exponential recency decay penalty (multi-session fatigue)
            double recencyPenalty = sessionContext.getRecencyPenalty(c.strategy.getName());
            score *= (1.0 - recencyPenalty * 0.8); // 80% of penalty applied

            // Create new candidate with adjusted score (score is final)
            double adjustedScore = Math.max(score, 0.01); // Floor at 1% to avoid zero
            penalizedCandidates.add(new ScoredCandidate(c.goal, c.strategy, adjustedScore));
        }

        return penalizedCandidates;
    }

    /**
     * Infers a broad category from strategy name for skill/category switching.
     */
    private String inferCategory(AccountStrategy strategy)
    {
        String name = strategy.getName().toLowerCase();
        if (name.contains("mining")) return "mining";
        if (name.contains("woodcutting") || name.contains("tree")) return "woodcutting";
        if (name.contains("fishing")) return "fishing";
        if (name.contains("cooking")) return "cooking";
        if (name.contains("firemaking")) return "firemaking";
        if (name.contains("smithing")) return "smithing";
        if (name.contains("crafting")) return "crafting";
        if (name.contains("tinderbox") || name.contains("looting")) return "moneymaking";
        if (name.contains("quest")) return "quest";
        return null;
    }

    private Plan selectWeightedRandom(List<ScoredCandidate> candidates, PlannerSessionContext sessionContext)
    {
        // Use stateful PRNG from session context (advances on each call)
        Random rng = sessionContext.getRng();

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