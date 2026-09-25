package net.runelite.client.plugins.microbot.mntn.aio.strategies.skilling.woodcutting;

import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.mntn.aio.*;
import net.runelite.client.plugins.microbot.mntn.aio.core.*;
import net.runelite.client.plugins.microbot.mntn.aio.utils.WoodcuttingUtils;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.Arrays;
import java.util.List;

import static net.runelite.client.plugins.microbot.mntn.aio.strategies.skilling.woodcutting.items.Items.AXES_ID;
import static net.runelite.client.plugins.microbot.mntn.aio.strategies.skilling.woodcutting.items.Items.AXES_NAME;
import static net.runelite.client.plugins.microbot.mntn.aio.utils.WoodcuttingUtils.canUseAxe;
import static net.runelite.client.plugins.microbot.mntn.aio.utils.WoodcuttingUtils.getBestAxe;
import static net.runelite.client.plugins.microbot.util.Global.sleep;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;


public final class OakTreeWoodcuttingStrategy
        implements AccountStrategy
{
    private enum State
    {
        PREPARE,
        TRAVEL_TO_TREE,
        WOODCUT,
        TRAVEL_TO_BANK,
        BANK
    }

    /*
     * Put the preferred location first and fallbacks afterward.
     */
    private final List<WoodcuttingLocation> locations =
            Arrays.asList(
                    new WoodcuttingLocation(
                            "North East Varock Oak Trees",
                            new WorldPoint(3273, 3474, 0),
                            new WorldPoint(3253, 3420, 0),
                            context ->
                                    true
                    )
            );

    private State state = State.PREPARE;
    private WoodcuttingLocation currentLocation;

    @Override
    public String getName()
    {
        if (currentLocation == null)
        {
            return "Oak Tree";
        }

        return "Oak Tree - " +
                currentLocation.getName();
    }

    @Override
    public boolean supports(Goal goal)
    {
        return goal.getType() == GoalType.SKILL_LEVEL &&
                goal.getSkill() == Skill.WOODCUTTING;
    }

    @Override
    public boolean canStart(
            AccountContext context,
            Goal goal)
    {
        int woodcuttingLevel =
                context.getLevel(Skill.WOODCUTTING);

        return supports(goal) &&
                woodcuttingLevel < goal.getTarget() && woodcuttingLevel >= 15 &&
                context.getBankCache().hasAnyItem(AXES_ID) &&
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

            case TRAVEL_TO_TREE:
                if (travelToTree())
                {
                    state = State.WOODCUT;
                }
                break;

            case WOODCUT:
                if (inventoryIsFull() || !Rs2Inventory.contains(AXES_ID))
                {
                    state = State.TRAVEL_TO_BANK;
                }
                else
                {
                    chopTree();
                }
                break;

            case TRAVEL_TO_BANK:
                if (travelToBank())
                {
                    state = State.BANK;
                }
                break;

            case BANK:
                if (bankLogs())
                {
                    currentLocation =
                            selectLocation(context);

                    if (currentLocation == null)
                    {
                        return StepResult.REPLAN;
                    }

                    state = State.TRAVEL_TO_TREE;
                }
                break;
        }

        return StepResult.RUNNING;
    }

    private WoodcuttingLocation selectLocation(
            AccountContext context)
    {
        for (WoodcuttingLocation location : locations)
        {
            if (location.canUse(context))
            {
                return location;
            }
        }

        return null;
    }

    private boolean travelToTree()
    {
        if (currentLocation == null)
        {
            return false;
        }

        if (Rs2Player.getWorldLocation().distanceTo(currentLocation.getchopDestination()) <= 5)
        {
            return true;
        }

        Rs2Walker.walkTo(
                currentLocation.getchopDestination()
        );

        return false;
    }

    private boolean inventoryIsFull()
    {
        return Rs2Inventory.isFull();
    }

    private void chopTree()
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

        Rs2TileObjectModel tree =
                WoodcuttingUtils.findNearestTree(
                        10820
                );

        if(tree == null) return;

        if(!tree.isReachable()) return;

        tree.click("Chop");

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

    private boolean bankLogs()
    {
        if (!Rs2Bank.isOpen())
        {
            sleepUntil(
                    Rs2Bank::openBank,
                    Rs2Random.between(800, 3000)
            );

            return false;
        }

        sleepUntil(
                () -> Rs2Bank.depositAllExcept(Arrays.asList(AXES_NAME)),
                Rs2Random.between(400, 1200)
        );

        if(Rs2Inventory.isEmpty()){
            int woodcuttingLevel = Rs2Player.getRealSkillLevel(Skill.WOODCUTTING);
            String bestAxe = getBestAxe(woodcuttingLevel);
            if(Rs2Bank.hasItem(bestAxe)){
                sleepUntil(() -> Rs2Bank.withdrawOne(bestAxe), Rs2Random.between(800, 2000));
            } else {
                for(String axe : AXES_NAME){
                    if(Rs2Bank.hasItem(axe) && canUseAxe(axe, woodcuttingLevel)){
                        sleepUntil(() -> Rs2Bank.withdrawOne(axe), Rs2Random.between(800, 3000));
                        break;
                    }
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