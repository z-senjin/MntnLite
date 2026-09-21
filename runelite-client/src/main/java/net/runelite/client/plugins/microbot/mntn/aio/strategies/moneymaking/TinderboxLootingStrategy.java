package net.runelite.client.plugins.microbot.mntn.aio.strategies.moneymaking;

import net.runelite.api.ItemID;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.mntn.aio.*;
import net.runelite.client.plugins.microbot.mntn.aio.core.*;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;

import static net.runelite.client.plugins.microbot.util.Global.sleep;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;


public final class TinderboxLootingStrategy
        implements AccountStrategy
{

    private int tinderboxCount = 0;

    private enum State
    {
        PREPARE,
        TRAVEL_TO_OLDMAN,
        LOOT,
        TRAVEL_TO_BANK,
        BANK
    }


    private State state = State.PREPARE;

    @Override
    public String getName()
    {

        return "Tinderbox Looting";
    }

    @Override
    public boolean supports(Goal goal)
    {
        return goal.getType() == GoalType.CASH;
    }

    @Override
    public boolean canStart(
            AccountContext context,
            Goal goal)
    {

        return supports(goal) &&
                context.getBankCache().getCount(ItemID.COINS) < 20000;
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

        Microbot.log("State: " + state);

        switch (state)
        {
            case PREPARE:
                state = State.TRAVEL_TO_BANK;
                break;

            case TRAVEL_TO_OLDMAN:
                if (travelToOldMan())
                {
                    state = State.LOOT;
                }
                break;

            case LOOT:
                if (inventoryIsFull())
                {
                    state = State.TRAVEL_TO_BANK;
                }
                else
                {
                    lootTinderboxes();
                }
                break;

            case TRAVEL_TO_BANK:
                if (travelToBank())
                {
                    state = State.BANK;
                }
                break;

            case BANK:
                if (bankTinderboxes())
                {
                    state = State.TRAVEL_TO_OLDMAN;
                }
                break;
        }

        return StepResult.RUNNING;
    }

    private boolean travelToOldMan()
    {
        WorldPoint oldman = new WorldPoint(3088, 3253, 0);

        if (Rs2Player.getWorldLocation().distanceTo(oldman) <= 5)
        {
            return true;
        }

        Rs2Walker.walkTo(
                oldman, 1
        );

        return false;
    }

    private boolean inventoryIsFull()
    {
        return Rs2Inventory.isFull();
    }

    private void lootTinderboxes()
    {


        if(Rs2Player.isMoving() || Rs2Player.isAnimating()){
            return;
        }

        sleep(400, 1200);

        if(Rs2Player.isMoving() || Rs2Player.isAnimating()){
            return;
        }

        if(tinderboxCount >= 28){
            Rs2GroundItem.loot(ItemID.TINDERBOX);
            return;
        }

        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        Rs2TileObjectModel bookshelf = Arrays.stream(new int[]{7079})
                .mapToObj(id ->
                        Microbot.getRs2TileObjectCache()
                                .query()
                                .withId(id)
                                .nearest()
                )
                .filter(Objects::nonNull)
                .min(Comparator.comparingInt(object ->
                        playerLocation.distanceTo(object.getWorldLocation())
                ))
                .orElse(null);

        if(bookshelf == null || !bookshelf.isReachable()) return;

        bookshelf.click();
        sleep(400, 800);
        if(Rs2Inventory.hasItem(ItemID.TINDERBOX)){
            sleepUntil(() -> Rs2Inventory.drop(ItemID.TINDERBOX), 800);
            sleep(100, 1200);
            tinderboxCount++;
        }

    }

    private boolean travelToBank()
    {
        WorldPoint draynorBank = new WorldPoint(3094, 3243, 0);
        if (Rs2Player.getWorldLocation().distanceTo(draynorBank) <= 3)
        {
            return true;
        }

        Rs2Walker.walkTo(
                draynorBank
        );

        return false;
    }

    private boolean bankTinderboxes()
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
                Rs2Bank::depositAll,
                Rs2Random.between(400, 1200)
        );

        tinderboxCount = 0;
        return Rs2Inventory.isEmpty();
    }

    @Override
    public void reset()
    {
        state = State.PREPARE;
    }
}