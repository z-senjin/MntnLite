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
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.mntn.aio.*;
import net.runelite.client.plugins.microbot.mntn.aio.core.*;
import net.runelite.client.plugins.microbot.mntn.aio.strategies.skilling.mining.MiningLocation;
import net.runelite.client.plugins.microbot.mntn.aio.strategies.skilling.mining.items.Items;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import static net.runelite.client.plugins.microbot.mntn.aio.strategies.skilling.mining.items.Items.PICKAXES_ID;
import static net.runelite.client.plugins.microbot.mntn.aio.strategies.skilling.mining.items.Items.PICKAXES_NAME;
import static net.runelite.client.plugins.microbot.util.Global.sleep;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

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
                            new WorldPoint(3302, 3283, 0),
                            new WorldPoint(3269, 3166, 0),
                            context ->
                                    context.getLevel(
                                            Skill.MINING
                                    ) >= 30 && Rs2Player.getCombatLevel() > 29
                    ),

                    new MiningLocation(
                            "Citharede Abbey Iron Mine",
                            new WorldPoint(3401, 3170, 0),
                            new WorldPoint(3269, 3166, 0),
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
                context.getBankCache().hasAnyItem(PICKAXES_ID) &&
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

    private void mineIron()
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

        Rs2TileObjectModel rock = findNearestRock();

        if(rock == null) return;

        if(!rock.isReachable()) return;

        rock.click("Mine");

        // Find an ore rock near the player to mine

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
                sleepUntil(() -> Rs2Bank.withdrawOne(bestPickaxe), Rs2Random.between(800, 2000));
            } else {
                for(String pickaxe : PICKAXES_NAME){
                    if(Rs2Bank.hasItem(pickaxe) && canUsePickaxe(pickaxe, miningLevel)){
                        sleepUntil(() -> Rs2Bank.withdrawOne(pickaxe), Rs2Random.between(800, 3000));
                        break;
                    }
                }
            }

        }

        return !Rs2Inventory.isEmpty();
    }


    // Helpers
    private Rs2TileObjectModel findNearestRock() {
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        //TODO TODO TODO
        return Stream.of(
                        Microbot.getRs2TileObjectCache()
                                .query()
                                .withId(11365)
                                .nearest(),

                        Microbot.getRs2TileObjectCache()
                                .query()
                                .withId(11364)
                                .nearest()
                )
                .filter(Objects::nonNull)
                .min(Comparator.comparingInt(object ->
                        playerLocation.distanceTo(object.getWorldLocation())))
                .orElse(null);
    }

    //TODO: move these helpers to a util file and add iron pick support
    private String getBestPickaxe(int miningLevel)
    {
        if (miningLevel >= 41)
            return "Rune pickaxe";

        if (miningLevel >= 31)
            return "Adamant pickaxe";

        if (miningLevel >= 21)
            return "Mithril pickaxe";

        if (miningLevel >= 11)
            return "Black pickaxe";

        if (miningLevel >= 6)
            return "Steel pickaxe";

        if (miningLevel >= 1)
            return "Bronze pickaxe";

        return null;
    }

    private boolean canUsePickaxe(String pickaxe, int miningLevel)
    {
        if (pickaxe.equals("Rune pickaxe"))
            return miningLevel >= 41;

        if (pickaxe.equals("Adamant pickaxe"))
            return miningLevel >= 31;

        if (pickaxe.equals("Mithril pickaxe"))
            return miningLevel >= 21;

        if (pickaxe.equals("Black pickaxe"))
            return miningLevel >= 11;

        if (pickaxe.equals("Steel pickaxe"))
            return miningLevel >= 6;

        return miningLevel >= 1;
    }

    @Override
    public void reset()
    {
        state = State.PREPARE;
        currentLocation = null;
    }
}