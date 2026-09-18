/*
 * Role:
 * Represents one account-building objective, such as reaching a skill
 * level, obtaining coins, or completing a quest.
 *
 * Purpose:
 * Stores the goal's target and priority and determines whether that goal
 * has already been completed using the current AccountContext.
 *
 * Priority:
 * Lower numbers run first. Priority 1 is considered more important
 * than priority 2.
 */
package net.runelite.client.plugins.microbot.mntn.aio.core;

import net.runelite.api.Quest;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.mntn.aio.core.GoalType;
import net.runelite.client.plugins.microbot.mntn.aio.core.AccountContext;

import java.util.Objects;

public final class Goal
{
    private final GoalType type;
    private final Skill skill;
    private final Quest quest;
    private final long target;
    private final int priority;

    private Goal(
            GoalType type,
            Skill skill,
            Quest quest,
            long target,
            int priority)
    {
        this.type = Objects.requireNonNull(type, "type");
        this.skill = skill;
        this.quest = quest;
        this.target = target;
        this.priority = priority;
    }

    /**
     * Creates a goal for reaching a particular skill level.
     */
    public static Goal skill(Skill skill, int level, int priority)
    {
        if (level < 1)
        {
            throw new IllegalArgumentException(
                    "Skill level must be at least 1"
            );
        }

        return new Goal(
                GoalType.SKILL_LEVEL,
                Objects.requireNonNull(skill, "skill"),
                null,
                level,
                priority
        );
    }

    /**
     * Creates a goal for obtaining a particular number of coins.
     */
    public static Goal cash(long amount, int priority)
    {
        if (amount < 0)
        {
            throw new IllegalArgumentException(
                    "Cash amount cannot be negative"
            );
        }

        return new Goal(
                GoalType.CASH,
                null,
                null,
                amount,
                priority
        );
    }

    /**
     * Creates a goal for completing a particular quest.
     */
    public static Goal quest(Quest quest, int priority)
    {
        return new Goal(
                GoalType.QUEST,
                null,
                Objects.requireNonNull(quest, "quest"),
                1,
                priority
        );
    }

    /**
     * Checks the live account state to determine whether this goal
     * has already been completed.
     */
    public boolean isComplete(AccountContext context)
    {
        Objects.requireNonNull(context, "context");

        switch (type)
        {
            case SKILL_LEVEL:
                return context.getLevel(skill) >= target;

            case CASH:
                return context.getCoins() >= target;

            case QUEST:
                return context.isQuestComplete(quest);

            default:
                throw new IllegalStateException(
                        "Unsupported goal type: " + type
                );
        }
    }

    public GoalType getType()
    {
        return type;
    }

    public Skill getSkill()
    {
        return skill;
    }

    public Quest getQuest()
    {
        return quest;
    }

    public long getTarget()
    {
        return target;
    }

    public int getPriority()
    {
        return priority;
    }

    @Override
    public String toString()
    {
        switch (type)
        {
            case SKILL_LEVEL:
                return skill + " level " + target;

            case CASH:
                return target + " coins";

            case QUEST:
                return "Complete " + quest;

            default:
                return type.toString();
        }
    }
}