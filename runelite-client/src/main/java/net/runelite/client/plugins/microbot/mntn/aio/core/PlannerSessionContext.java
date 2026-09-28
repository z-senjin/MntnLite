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
    private final List<String> recentStrategies = new ArrayList<>();
    private final List<Skill> recentSkills = new ArrayList<>();
    private final List<String> recentCategories = new ArrayList<>();
    private final int maxHistorySize;
    private final double varietyStrength;
    private final double breakChancePerTick;
    private final Random rng; // Stateful PRNG that advances on each call

    private PlannerSessionContext(Builder builder)
    {
        this.sessionSeed = builder.sessionSeed;
        this.maxHistorySize = builder.maxHistorySize;
        this.varietyStrength = builder.varietyStrength;
        this.breakChancePerTick = builder.breakChancePerTick;
        this.rng = new Random(builder.sessionSeed);

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

    /**
     * Re-rolls priority jitter for all goals - called on each replan
     * to simulate changing priorities throughout the session.
     */
    public void rerollAllGoalPriorityJitter()
    {
        goalPriorityJitter.clear();
    }

    Map<Integer, Integer> getGoalPriorityJitterMap()
    {
        return goalPriorityJitter;
    }

    public void recordStrategyUsed(String strategyName, Skill skill, String category)
    {
        recentStrategies.add(strategyName);
        while (recentStrategies.size() > maxHistorySize)
        {
            recentStrategies.remove(0);
        }

        if (skill != null && skill != Skill.OVERALL)
        {
            recentSkills.add(skill);
            while (recentSkills.size() > maxHistorySize)
            {
                recentSkills.remove(0);
            }
        }

        if (category != null && !category.isEmpty())
        {
            recentCategories.add(category);
            while (recentCategories.size() > maxHistorySize)
            {
                recentCategories.remove(0);
            }
        }
    }

    public String getLastStrategyName()
    {
        return recentStrategies.isEmpty() ? null : recentStrategies.get(recentStrategies.size() - 1);
    }

    public Skill getLastSkill()
    {
        return recentSkills.isEmpty() ? null : recentSkills.get(recentSkills.size() - 1);
    }

    public String getLastCategory()
    {
        return recentCategories.isEmpty() ? null : recentCategories.get(recentCategories.size() - 1);
    }

    /**
     * Returns the recency penalty for a strategy name using exponential decay.
     * Index 0 (most recent) = 1.0 (100% penalty = excluded)
     * Index 1 = 0.75 (75% penalty)
     * Index 2 = 0.40 (40% penalty)
     * Index 3 = 0.15 (15% penalty)
     * Index 4+ = minimal
     */
    public double getRecencyPenalty(String strategyName)
    {
        if (recentStrategies.isEmpty())
        {
            return 0.0;
        }

        int index = -1;
        for (int i = recentStrategies.size() - 1; i >= 0; i--)
        {
            if (recentStrategies.get(i).equals(strategyName))
            {
                index = recentStrategies.size() - 1 - i; // 0 = most recent
                break;
            }
        }

        if (index < 0)
        {
            return 0.0; // Not in history
        }

        // Exponential decay: 0=1.0, 1=0.75, 2=0.40, 3=0.15, 4+=0.03
        if (index == 0) return 1.0;      // Just ran - hard excluded
        if (index == 1) return 0.75;     // 1 ago - 75% penalty
        if (index == 2) return 0.40;     // 2 ago - 40% penalty
        if (index == 3) return 0.15;     // 3 ago - 15% penalty
        return 0.03;                      // 4+ ago - minimal
    }

    /**
     * Returns penalty for using the same skill as recent strategies.
     * If last skill matches, returns 0.75 penalty (75%).
     * If second-to-last skill matches, returns 0.40 penalty.
     * Otherwise 0.
     */
    public double getSkillRecencyPenalty(Skill skill)
    {
        if (skill == null || skill == Skill.OVERALL || recentSkills.isEmpty())
        {
            return 0.0;
        }

        int index = -1;
        for (int i = recentSkills.size() - 1; i >= 0; i--)
        {
            if (recentSkills.get(i) == skill)
            {
                index = recentSkills.size() - 1 - i;
                break;
            }
        }

        if (index < 0)
        {
            return 0.0;
        }

        if (index == 0) return 0.75;     // Just used this skill - 75% penalty
        if (index == 1) return 0.40;     // Used 1 ago - 40% penalty
        return 0.0;
    }

    /**
     * Returns penalty for using the same category (e.g., "mining", "woodcutting", "fishing").
     */
    public double getCategoryRecencyPenalty(String category)
    {
        if (category == null || category.isEmpty() || recentCategories.isEmpty())
        {
            return 0.0;
        }

        int index = -1;
        for (int i = recentCategories.size() - 1; i >= 0; i--)
        {
            if (recentCategories.get(i).equals(category))
            {
                index = recentCategories.size() - 1 - i;
                break;
            }
        }

        if (index < 0)
        {
            return 0.0;
        }

        if (index == 0) return 0.75;
        if (index == 1) return 0.40;
        return 0.0;
    }

    /**
     * Returns the stateful PRNG that advances on each call.
     * This fixes the PRNG seed bug where new Random(seed) was created each time.
     */
    public Random getRng()
    {
        return rng;
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
        return rng.nextDouble() < breakChancePerTick;
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