/*
 * Role:
 * Trains low-level Mining using copper and tin.
 *
 * Purpose:
 * Acts as the fallback Mining strategy from levels 1 through 14.
 * Once Mining reaches level 15, it returns REPLAN so the Planner
 * can switch to IronMiningStrategy.
 */
package net.runelite.client.plugins.microbot.mntn.aio.strategies.skilling.mining;

import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.mntn.aio.*;
import net.runelite.client.plugins.microbot.mntn.aio.core.*;
import net.runelite.client.plugins.microbot.mntn.aio.strategies.skilling.mining.MiningLocation;

import java.util.Arrays;
import java.util.List;

public final class CopperTinMiningStrategy
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
     * Locations are ordered from most preferred to least preferred.
     * selectLocation() returns the first currently usable location.
     */
    private final List<MiningLocation> locations =
            Arrays.asList(
                    new MiningLocation(
                            "Preferred copper/tin mine",
                            new WorldPoint(0, 0, 0),
                            new WorldPoint(0, 0, 0),
                            context -> true
                    ),

                    new MiningLocation(
                            "Fallback copper/tin mine",
                            new WorldPoint(0, 0, 0),
                            new WorldPoint(0, 0, 0),
                            context -> true
                    )
            );

    private State state = State.PREPARE;
    private MiningLocation currentLocation;

    @Override
    public String getName()
    {
        if (currentLocation == null)
        {
            return "Copper and tin mining";
        }

        return "Copper and tin - " +
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
                miningLevel >= 1 &&
                miningLevel < 15 &&
                context.hasUsablePickaxe() &&
                selectLocation(context) != null;
    }

    @Override
    public StepResult tick(
            AccountContext context,
            Goal goal)
    {
        /*
         * The requested Mining target has been reached.
         */
        if (goal.isComplete(context))
        {
            return StepResult.COMPLETE;
        }

        /*
         * Copper/tin is no longer the preferred method.
         * Ask the Planner to choose again.
         */
        if (context.getLevel(Skill.MINING) >= 15)
        {
            return StepResult.REPLAN;
        }

        /*
         * Losing the pickaxe makes this strategy unavailable.
         */
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
                /*
                 * Equip or withdraw the pickaxe here.
                 */
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
                    mineCopperOrTin();
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
                     * Recheck preferred locations after banking.
                     * This allows the strategy to switch locations
                     * when a new one becomes available.
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

    /**
     * Returns the first currently accessible location.
     */
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
        /*
         * Example:
         *
         * if (Rs2Player.getWorldLocation().distanceTo(
         *         currentLocation.getMineDestination()) <= 5)
         * {
         *     return true;
         * }
         *
         * Rs2Walker.walkTo(
         *         currentLocation.getMineDestination()
         * );
         *
         * return false;
         */

        return false;
    }

    private boolean inventoryIsFull()
    {
        /*
         * return Rs2Inventory.isFull();
         */
        return false;
    }

    private void mineCopperOrTin()
    {
        /*
         * Find and interact with a copper or tin rock.
         */
    }

    private boolean travelToBank()
    {
        /*
         * Walk toward:
         * currentLocation.getBankDestination()
         */
        return false;
    }

    private boolean bankOres()
    {
        /*
         * Open the bank and deposit the ores.
         */
        return false;
    }

    @Override
    public void reset()
    {
        state = State.PREPARE;
        currentLocation = null;
    }
}