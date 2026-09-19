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
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.mntn.aio.*;
import net.runelite.client.plugins.microbot.mntn.aio.core.*;
import net.runelite.client.plugins.microbot.mntn.aio.strategies.skilling.mining.items.Items;
import net.runelite.client.plugins.microbot.mntn.aio.utils.MiningUtils;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import static net.runelite.client.plugins.microbot.mntn.aio.strategies.skilling.mining.items.Items.*;
import static net.runelite.client.plugins.microbot.mntn.aio.utils.MiningUtils.canUsePickaxe;
import static net.runelite.client.plugins.microbot.mntn.aio.utils.MiningUtils.getBestPickaxe;
import static net.runelite.client.plugins.microbot.util.Global.sleep;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

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
                            "Al Kharid Mine",
                            new WorldPoint(3296, 3315, 0),
                            new WorldPoint(3298, 3314, 0),
                            context -> Rs2Player.getCombatLevel() > 29
                    ),

                    new MiningLocation(
                            "Lumbridge Swamp Mine",
                            new WorldPoint(3227, 3146, 0),
                            new WorldPoint(3208, 3218, 2),
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
                context.getBankCache().hasAnyItem(PICKAXES_ID) &&
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
                state = State.TRAVEL_TO_BANK;
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
        if (currentLocation == null)
        {
            return false;
        }

        if (Rs2Player.getWorldLocation().distanceTo(currentLocation.getMineDestination()) <= 5)
        {
            return true;
        }

        Rs2Walker.walkTo(
                currentLocation.getMineDestination()
        );

        return false;
    }

    private boolean inventoryIsFull()
    {
        return Rs2Inventory.isFull();
    }

    private void mineCopperOrTin()
    {
        if (currentLocation == null)
        {
            return;
        }

        if(Rs2Player.isMoving() || Rs2Player.isAnimating()){
            return;
        }

        sleep(400, 1200);

        if(Rs2Player.isMoving() || Rs2Player.isAnimating()){
            return;
        }

        Rs2TileObjectModel rock = MiningUtils.findNearestRock(10943, 11161);

        if(rock == null) return;

        if(!rock.isReachable()) return;

        rock.click("Mine");

    }

    private boolean travelToBank()
    {
        if (currentLocation == null)
        {
            return false;
        }

        if (Rs2Player.getWorldLocation().distanceTo(currentLocation.getBankDestination()) <= 5)
        {
            return true;
        }

        Rs2Walker.walkTo(
                currentLocation.getBankDestination()
        );

        return false;
    }

    private boolean bankOres()
    {
        if (!Rs2Bank.isOpen())
        {
            sleepUntil(
                    Rs2Bank::openBank,
                    Rs2Random.between(800, 3000)
            );

            return false;
        }

        // Deposit everything except our pickaxe
        sleepUntil(
                () -> Rs2Bank.depositAllExcept(PICKAXES_NAME),
                Rs2Random.between(400, 1200)
        );

        if(Rs2Inventory.isEmpty()){
            int miningLevel = Rs2Player.getRealSkillLevel(Skill.MINING);
            String bestPickaxe = getBestPickaxe(miningLevel);
            if(Rs2Bank.hasItem(bestPickaxe)){

            } else {

            }
            for(String pickaxe : PICKAXES_NAME){
                if(Rs2Bank.hasItem(pickaxe) && canUsePickaxe(pickaxe, miningLevel)){
                    sleepUntil(() -> Rs2Bank.withdrawOne(bestPickaxe), Rs2Random.between(800, 3000));
                    break;
                }
            }
        }

        return !Rs2Inventory.isEmpty();
    }


    @Override
    public void reset()
    {
        state = State.PREPARE;
        currentLocation = null;
    }
}