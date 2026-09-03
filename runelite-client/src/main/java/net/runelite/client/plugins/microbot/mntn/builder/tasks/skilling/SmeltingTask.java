package net.runelite.client.plugins.microbot.mntn.builder.tasks.skilling;

import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.mntn.builder.activities.smithing.SmeltingStrategy;
import net.runelite.client.plugins.microbot.mntn.builder.core.AccountContext;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.Task;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.TaskStatus;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.banking.BankingTask;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.awt.event.KeyEvent;

import static net.runelite.client.plugins.microbot.util.Global.sleep;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntilTrue;

public class SmeltingTask implements Task {

    private static final int[] FURNACE_OBJECT_IDS = {24009};

    private enum Phase {
        WALK_TO_FURNACE, SMELTING, BANKING
    }

    private final SmeltingStrategy.Bar bar;
    private Phase phase = Phase.WALK_TO_FURNACE;
    private BankingTask bankingTask;

    public SmeltingTask(SmeltingStrategy.Bar bar) {
        this.bar = bar;
    }

    @Override
    public TaskStatus tick(AccountContext context) {
        if (!context.isLoggedIn()) {
            return TaskStatus.BLOCKED;
        }

        switch (phase) {
            case WALK_TO_FURNACE:
                return handleWalk(context);
            case SMELTING:
                return handleSmelt(context);
            case BANKING:
                return handleBank(context);
            default:
                return TaskStatus.RUNNING;
        }
    }

    private TaskStatus handleWalk(AccountContext context) {
        if (!hasAllIngredients(context)) {
            phase = Phase.BANKING;
            return TaskStatus.RUNNING;
        }

        if (context.isNear(bar.furnaceLocation, 10)) {
            phase = Phase.SMELTING;
            return TaskStatus.RUNNING;
        }

        Rs2Walker.walkTo(bar.furnaceLocation);
        return TaskStatus.RUNNING;
    }

    private TaskStatus handleSmelt(AccountContext context) {
        if (!hasAllIngredients(context)) {
            phase = Phase.BANKING;
            return TaskStatus.RUNNING;
        }

        if (Rs2Player.isAnimating() || Rs2Player.isMoving()) {
            return TaskStatus.RUNNING;
        }

        Rs2TileObjectModel furnace = Microbot.getRs2TileObjectCache().query()
                .withIds(FURNACE_OBJECT_IDS)
                .within(15)
                .nearest();

        if (furnace == null) {
            phase = Phase.WALK_TO_FURNACE;
            return TaskStatus.RUNNING;
        }

        if (Microbot.getClient().getLocalPlayer() != null) {

            boolean isMovingOrAnimating = Rs2Player.isAnimating() || Rs2Player.isMoving();

            sleep(300, 1000);

            boolean isMovingOrAnimatingAgain = Rs2Player.isAnimating() || Rs2Player.isMoving();


            if (isMovingOrAnimating || isMovingOrAnimatingAgain) {
                return TaskStatus.RUNNING;
            } else {
                furnace.click("Smelt");
            }
        }


        boolean open = sleepUntilTrue(Rs2Widget::isProductionWidgetOpen, 200, 6000);
        if (open) {
            sleep(300, 600);
            Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
            sleep(600, 1000);
        }

        return TaskStatus.RUNNING;
    }

    private TaskStatus handleBank(AccountContext context) {
        if (bankingTask == null) {
            BankingTask.ItemWithdrawal[] withdrawals = new BankingTask.ItemWithdrawal[bar.ingredients.length];
            for (int i = 0; i < bar.ingredients.length; i++) {
                SmeltingStrategy.OreRequirement req = bar.ingredients[i];
                withdrawals[i] = new BankingTask.ItemWithdrawal(req.itemName, req.withdrawAmount);
            }

            bankingTask = new BankingTask(BankingTask.Mode.DEPOSIT_ALL_AND_WITHDRAW, null, withdrawals);
        }

        TaskStatus bankStatus = bankingTask.tick(context);

        if (bankStatus == TaskStatus.COMPLETE) {
            bankingTask = null;

            if (!hasAllIngredients(context)) {
                // Not enough ores left in bank to continue smelting this bar
                return TaskStatus.REPLAN;
            }

            phase = Phase.WALK_TO_FURNACE;
            return TaskStatus.RUNNING;
        }

        if (bankStatus == TaskStatus.FAILED || bankStatus == TaskStatus.REPLAN) {
            bankingTask = null;
            return bankStatus;
        }

        return TaskStatus.RUNNING;
    }

    private boolean hasAllIngredients(AccountContext context) {
        for (SmeltingStrategy.OreRequirement req : bar.ingredients) {
            if (!context.inventory().hasItem(req.itemName)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean needsReplan(AccountContext context) {
        return context.getRealLevel(Skill.SMITHING) < bar.requiredLevel;
    }

    @Override
    public String describe() {
        return "Smelting (" + bar.name() + ") - " + phase;
    }
}
