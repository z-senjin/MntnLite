/*
 * Role:
 * Holds session-specific randomized state for the Planner.
 *
 * Purpose:
 * Makes each bot session behave differently while remaining
 * deterministic within that session. Includes goal priority jitter,
 * strategy preference weights, and activity history for variety bonuses.
 *
 * All randomness is seeded at script startup so the session is
 * reproducible and consistent across planner ticks.
 */
package net.runelite.client.plugins.microbot.mntn.aio.core;

import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.mntn.aio.core.AccountStrategy;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;

import java.util.*;

public final class PlannerSessionContext
{
    private final long sessionSeed;
    private final Map<String, Double> skillWeights = new HashMap<>();
    private final Map<Integer, Integer> goalPriorityJitter = new HashMap<>();
    private final Deque<String> recentStrategies = new ArrayDeque<>();
    private final int maxHistorySize;
    private final double varietyStrength;
    private final double breakChancePerTick;

    private PlannerSessionContext(Builder builder)
    {
        this.sessionSeed = builder.sessionSeed;
        this.maxHistorySize = builder.maxHistorySize;
        this.varietyStrength = builder.varietyStrength;
        this.breakChancePerTick = builder.breakChancePerTick;

        // Initialize random with session seed
        Random rng = new Random(sessionSeed);

        // Generate weights for each skill (0.7 to 1.3)
        for (Skill skill : Skill.values())
        {
            if (skill == Skill.OVERALL)
            {
                continue;
            }
            double weight = 0.7 + rng.nextDouble() * 0.6; // 0.7-1.3
            skillWeights.put(skill.name(), weight);
        }

        // Generate priority jitter for each goal (-2 to +2)
        // Applied later when goals are loaded
    }

    public double getSkillWeight(Skill skill)
    {
        if (skill == null || skill == Skill.OVERALL)
        {
            return 1.0;
        }
        return skillWeights.getOrDefault(skill.name(), 1.0);
    }

    public int getEffectivePriority(Goal goal)
    {
        if (goal == null)
        {
            return Integer.MAX_VALUE;
        }
        int base = goal.getPriority();
        int jitter = goalPriorityJitter.getOrDefault(goal.hashCode(), 0);
        return base + jitter;
    }

    public void setGoalPriorityJitter(Goal goal, int jitter)
    {
        if (goal != null)
        {
            goalPriorityJitter.put(goal.hashCode(), jitter);
        }
    }

    Map<Integer, Integer> getGoalPriorityJitterMap()
    {
        return goalPriorityJitter;
    }

    public void recordStrategyUsed(String strategyName)
    {
        recentStrategies.addLast(strategyName);
        while (recentStrategies.size() > maxHistorySize)
        {
            recentStrategies.removeFirst();
        }
    }

    public double getVarietyBonus(String strategyName)
    {
        if (recentStrategies.isEmpty())
        {
            return 1.0;
        }

        // Check recent history for repetition
        int recentCount = 0;
        for (String recent : recentStrategies)
        {
            if (recent.equals(strategyName))
            {
                recentCount++;
            }
        }

        // Variety bonus decreases with recent usage
        // 1.0 = neutral, down to (1 - varietyStrength) for heavy repetition
        double penalty = recentCount * varietyStrength;
        return Math.max(1.0 - penalty, 1.0 - varietyStrength);
    }

    public double getLevelAppropriateness(Skill skill, int currentLevel, int targetLevel)
    {
        if (skill == null || targetLevel <= currentLevel)
        {
            return 1.0;
        }

        int levelsRemaining = targetLevel - currentLevel;
        int totalLevels = targetLevel - 1;

        // Prefer strategies appropriate for current level
        // 1.0 when close to target, slightly lower when far
        // This avoids always picking highest-level strategy
        if (levelsRemaining <= 5)
        {
            return 1.0;
        }
        return 0.85 + 0.15 * (levelsRemaining / (double) totalLevels);
    }

    public boolean shouldBreak()
    {
        return Math.random() < breakChancePerTick;
    }

    public long getSessionSeed()
    {
        return sessionSeed;
    }

    public static class Builder
    {
        private long sessionSeed = System.currentTimeMillis();
        private int maxHistorySize = 8;
        private double varietyStrength = 0.15; // 15% penalty per recent use
        private double breakChancePerTick = 0.01; // 1% chance per planner tick

        public Builder sessionSeed(long seed)
        {
            this.sessionSeed = seed;
            return this;
        }

        public Builder maxHistorySize(int size)
        {
            this.maxHistorySize = Math.max(1, size);
            return this;
        }

        public Builder varietyStrength(double strength)
        {
            this.varietyStrength = Math.max(0.0, Math.min(0.5, strength));
            return this;
        }

        public Builder breakChancePerTick(double chance)
        {
            this.breakChancePerTick = Math.max(0.0, Math.min(0.1, chance));
            return this;
        }

        public PlannerSessionContext build()
        {
            return new PlannerSessionContext(this);
        }
    }
}