/*
 * Role:
 * Trains Mining using iron after the account reaches level 15.
 *
 * Purpose:
 * Becomes the preferred Mining strategy at level 15 and continues
 * until the configured Mining goal is complete or the strategy
 * becomes unavailable.
 */
package net.runelite.client.plugins.microbot.mntn.aio.strategies.skilling.mining;

import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.mntn.aio.*;
import net.runelite.client.plugins.microbot.mntn.aio.core.*;
import net.runelite.client.plugins.microbot.mntn.aio.strategies.skilling.mining.MiningLocation;

import java.util.Arrays;
import java.util.List;

public final class IronMiningStrategy
        implements AccountStrategy
{
    private enum State
    {
        PREPARE,
        TRAVEL_TO_MINE,
        MINE,
        TRAVEL_TO_BANK,
        BANK
    }

    /*
     * Put the preferred location first and fallbacks afterward.
     */
    private final List<MiningLocation> locations =
            Arrays.asList(
                    new MiningLocation(
                            "Preferred iron mine",
                            new WorldPoint(0, 0, 0),
                            new WorldPoint(0, 0, 0),
                            context ->
                                    context.getLevel(
                                            Skill.MINING
                                    ) >= 30
                    ),

                    new MiningLocation(
                            "Basic iron mine",
                            new WorldPoint(0, 0, 0),
                            new WorldPoint(0, 0, 0),
                            context ->
                                    context.getLevel(
                                            Skill.MINING
                                    ) >= 15
                    )
            );

    private State state = State.PREPARE;
    private MiningLocation currentLocation;

    @Override
    public String getName()
    {
        if (currentLocation == null)
        {
            return "Iron mining";
        }

        return "Iron mining - " +
                currentLocation.getName();
    }

    @Override
    public boolean supports(Goal goal)
    {
        return goal.getType() == GoalType.SKILL_LEVEL &&
                goal.getSkill() == Skill.MINING;
    }

    @Override
    public boolean canStart(
            AccountContext context,
            Goal goal)
    {
        int miningLevel =
                context.getLevel(Skill.MINING);

        return supports(goal) &&
                miningLevel >= 15 &&
                miningLevel < goal.getTarget() &&
                context.hasUsablePickaxe() &&
                selectLocation(context) != null;
    }

    @Override
    public StepResult tick(
            AccountContext context,
            Goal goal)
    {
        if (goal.isComplete(context))
        {
            return StepResult.COMPLETE;
        }

        if (!context.hasUsablePickaxe())
        {
            return StepResult.REPLAN;
        }

        if (currentLocation == null)
        {
            currentLocation =
                    selectLocation(context);

            if (currentLocation == null)
            {
                return StepResult.REPLAN;
            }
        }

        switch (state)
        {
            case PREPARE:
                state = State.TRAVEL_TO_MINE;
                break;

            case TRAVEL_TO_MINE:
                if (travelToMine())
                {
                    state = State.MINE;
                }
                break;

            case MINE:
                if (inventoryIsFull())
                {
                    state = State.TRAVEL_TO_BANK;
                }
                else
                {
                    mineIron();
                }
                break;

            case TRAVEL_TO_BANK:
                if (travelToBank())
                {
                    state = State.BANK;
                }
                break;

            case BANK:
                if (bankOres())
                {
                    /*
                     * This will select the preferred level-30 location
                     * once it becomes available.
                     */
                    currentLocation =
                            selectLocation(context);

                    if (currentLocation == null)
                    {
                        return StepResult.REPLAN;
                    }

                    state = State.TRAVEL_TO_MINE;
                }
                break;
        }

        return StepResult.RUNNING;
    }

    private MiningLocation selectLocation(
            AccountContext context)
    {
        for (MiningLocation location : locations)
        {
            if (location.canUse(context))
            {
                return location;
            }
        }

        return null;
    }

    private boolean travelToMine()
    {
        return false;
    }

    private boolean inventoryIsFull()
    {
        return false;
    }

    private void mineIron()
    {
    }

    private boolean travelToBank()
    {
        return false;
    }

    private boolean bankOres()
    {
        return false;
    }

    @Override
    public void reset()
    {
        state = State.PREPARE;
        currentLocation = null;
    }
}