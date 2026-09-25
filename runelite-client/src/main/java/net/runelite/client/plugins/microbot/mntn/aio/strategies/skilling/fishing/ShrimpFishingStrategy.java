/*
 * Role:
 * Trains low-level Fishing using a small net for shrimp/anchovies.
 *
 * Purpose:
 * Acts as the fallback Fishing strategy from levels 1 through 19.
 * Once Fishing reaches level 20, it returns REPLAN so the Planner
 * can switch to FlyFishingStrategy.
 */
package net.runelite.client.plugins.microbot.mntn.aio.strategies.skilling.fishing;

import net.runelite.api.ItemID;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.mntn.aio.*;
import net.runelite.client.plugins.microbot.mntn.aio.core.*;
import net.runelite.client.plugins.microbot.mntn.aio.strategies.skilling.fishing.items.Items;
import net.runelite.client.plugins.microbot.mntn.aio.utils.FishingUtils;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.Arrays;
import java.util.List;

import static net.runelite.api.ItemID.SMALL_FISHING_NET;
import static net.runelite.client.plugins.microbot.mntn.aio.strategies.skilling.fishing.items.Items.FISHING_RODS_ID;
import static net.runelite.client.plugins.microbot.mntn.aio.strategies.skilling.fishing.items.Items.FISHING_RODS_NAME;
import static net.runelite.client.plugins.microbot.util.Global.sleep;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

public final class ShrimpFishingStrategy
        implements AccountStrategy
{
    private enum State
    {
        PREPARE,
        TRAVEL_TO_FISH,
        FISH,
        TRAVEL_TO_BANK,
        BANK
    }

    /*
     * Locations are ordered from most preferred to least preferred.
     * selectLocation() returns the first currently usable location.
     */
    private final List<FishingLocation> locations =
            Arrays.asList(
                    new FishingLocation(
                            "Draynor Village Net Fishing",
                            new WorldPoint(3086, 3232, 0),
                            new WorldPoint(3093, 3243, 0),
                            context -> Rs2Player.getCombatLevel() > 20
                    ),

//                    new FishingLocation(
//                            "Lumbridge River Net Fishing",
//                            new WorldPoint(3243, 3151, 0),
//                            new WorldPoint(3208, 3219, 2),
//                            context -> true
//                    ),

                    new FishingLocation(
                            "Al Kharid Net Fishing",
                            new WorldPoint(3268, 3150, 0),
                            new WorldPoint(3270, 3166, 0),
                            context -> true
                    )
            );

    private State state = State.PREPARE;
    private FishingLocation currentLocation;

    @Override
    public String getName()
    {
        if (currentLocation == null)
        {
            return "Shrimp fishing";
        }

        return "Shrimp fishing - " +
                currentLocation.getName();
    }

    @Override
    public boolean supports(Goal goal)
    {
        return goal.getType() == GoalType.SKILL_LEVEL &&
                goal.getSkill() == Skill.FISHING;
    }

    @Override
    public boolean canStart(
            AccountContext context,
            Goal goal)
    {
        int fishingLevel =
                context.getLevel(Skill.FISHING);

        return supports(goal) &&
                fishingLevel >= 1 &&
                fishingLevel < 20 &&
                fishingLevel < goal.getTarget() &&
                context.getBankCache().hasItem(SMALL_FISHING_NET) &&
                selectLocation(context) != null;
    }

    @Override
    public StepResult tick(
            AccountContext context,
            Goal goal)
    {
        /*
         * The requested Fishing target has been reached.
         */
        if (goal.isComplete(context))
        {
            return StepResult.COMPLETE;
        }

        /*
         * Net fishing is no longer the preferred method at level 20.
         * Ask the Planner to choose again.
         */
        if (context.getLevel(Skill.FISHING) >= 20)
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
                 * Equip or withdraw the fishing rod here.
                 */
                state = State.TRAVEL_TO_BANK;
                break;

            case TRAVEL_TO_FISH:
                if (travelToFish())
                {
                    state = State.FISH;
                }
                break;

            case FISH:
                if (inventoryIsFull() || (!Rs2Inventory.hasItem(SMALL_FISHING_NET)))
                {
                    state = State.TRAVEL_TO_BANK;
                }
                else
                {
                    fishShrimp();
                }
                break;

            case TRAVEL_TO_BANK:
                if (travelToBank())
                {
                    state = State.BANK;
                }
                break;

            case BANK:
                if (bankFish())
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

                    state = State.TRAVEL_TO_FISH;
                }
                break;
        }

        return StepResult.RUNNING;
    }

    /**
     * Returns the first currently accessible location.
     */
    private FishingLocation selectLocation(
            AccountContext context)
    {
        for (FishingLocation location : locations)
        {
            if (location.canUse(context))
            {
                return location;
            }
        }

        return null;
    }

    private boolean travelToFish()
    {
        if (currentLocation == null)
        {
            return false;
        }

        if (Rs2Player.getWorldLocation().distanceTo(currentLocation.getFishDestination()) <= 5)
        {
            return true;
        }

        Rs2Walker.walkTo(
                currentLocation.getFishDestination()
        );

        return false;
    }

    private boolean inventoryIsFull()
    {
        return Rs2Inventory.isFull();
    }

    private void fishShrimp()
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

        Rs2NpcModel spot = FishingUtils.findNearestNetSpot();

        if(spot == null) return;

        if(!spot.isReachable()) return;

        spot.click("Small Net");
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

    private boolean bankFish()
    {
        if (!Rs2Bank.isOpen())
        {
            sleepUntil(
                    Rs2Bank::openBank,
                    Rs2Random.between(800, 3000)
            );

            return false;
        }

        // Deposit everything except our fishing rod
        sleepUntil(
                () -> Rs2Bank.depositAllExcept(SMALL_FISHING_NET),
                Rs2Random.between(400, 1200)
        );

        if(Rs2Inventory.isEmpty()){
            sleepUntil(() -> Rs2Bank.withdrawOne(SMALL_FISHING_NET), Rs2Random.between(800, 2000));
        }

        return !Rs2Inventory.isEmpty();
    }

    private String getBestRod(int fishingLevel)
    {
        if (fishingLevel >= 40 && Rs2Bank.hasItem("Pearl fishing rod"))
        {
            return "Pearl fishing rod";
        }
        if (fishingLevel >= 30 && Rs2Bank.hasItem("Oily fishing rod"))
        {
            return "Oily fishing rod";
        }
        if (fishingLevel >= 20 && Rs2Bank.hasItem("Fly fishing rod"))
        {
            return "Fly fishing rod";
        }
        return "Fishing rod";
    }

    private boolean canUseRod(String rod, int fishingLevel)
    {
        if (rod.equals("Pearl fishing rod") || rod.equals("Oily fishing rod"))
        {
            return fishingLevel >= 30;
        }
        if (rod.equals("Fly fishing rod"))
        {
            return fishingLevel >= 20;
        }
        return true; // Basic fishing rod
    }

    @Override
    public void reset()
    {
        state = State.PREPARE;
        currentLocation = null;
    }
}