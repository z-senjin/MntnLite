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

    private void debugLog(AccountContext context, String message) {
        if (context.isDebugLogging()) {
            Microbot.log("[MntnBuilder][SmeltingTask][DEBUG] " + message);
        }
    }

    @Override
    public TaskStatus tick(AccountContext context) {
        debugLog(context, "tick: phase=" + phase + ", bar=" + bar.name());

        if (!context.isLoggedIn()) {
            debugLog(context, "Not logged in, returning BLOCKED");
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
                debugLog(context, "Unknown phase, returning RUNNING");
                return TaskStatus.RUNNING;
        }
    }

    private TaskStatus handleWalk(AccountContext context) {
        debugLog(context, "handleWalk: hasAllIngredients=" + hasAllIngredients(context) + ", nearFurnace=" + context.isNear(bar.furnaceLocation, 10));

        if (!hasAllIngredients(context)) {
            debugLog(context, "Missing ingredients, switching to BANKING");
            phase = Phase.BANKING;
            return TaskStatus.RUNNING;
        }

        if (context.isNear(bar.furnaceLocation, 10)) {
            debugLog(context, "Near furnace, switching to SMELTING");
            phase = Phase.SMELTING;
            return TaskStatus.RUNNING;
        }

        debugLog(context, "Walking to furnace: " + bar.furnaceLocation);
        Rs2Walker.walkTo(bar.furnaceLocation);
        return TaskStatus.RUNNING;
    }

    private TaskStatus handleSmelt(AccountContext context) {
        debugLog(context, "handleSmelt: hasAllIngredients=" + hasAllIngredients(context));

        if (!hasAllIngredients(context)) {
            debugLog(context, "Missing ingredients, switching to BANKING");
            phase = Phase.BANKING;
            return TaskStatus.RUNNING;
        }

        if (Rs2Player.isAnimating() || Rs2Player.isMoving()) {
            debugLog(context, "Already animating/moving, waiting");
            return TaskStatus.RUNNING;
        }

        Rs2TileObjectModel furnace = Microbot.getRs2TileObjectCache().query()
                .withIds(FURNACE_OBJECT_IDS)
                .within(15)
                .nearest();

        if (furnace == null) {
            debugLog(context, "Furnace not found, switching to WALK_TO_FURNACE");
            phase = Phase.WALK_TO_FURNACE;
            return TaskStatus.RUNNING;
        }

        if (Microbot.getClient().getLocalPlayer() != null) {

            boolean isMovingOrAnimating = Rs2Player.isAnimating() || Rs2Player.isMoving();

            sleep(300, 1000);

            boolean isMovingOrAnimatingAgain = Rs2Player.isAnimating() || Rs2Player.isMoving();


            if (isMovingOrAnimating || isMovingOrAnimatingAgain) {
                debugLog(context, "Already animating/moving after sleep, waiting");
                return TaskStatus.RUNNING;
            } else {
                debugLog(context, "Clicking furnace to smelt");
                furnace.click("Smelt");
            }
        }


        boolean open = sleepUntilTrue(Rs2Widget::isProductionWidgetOpen, 200, 6000);
        if (open) {
            debugLog(context, "Production widget open, pressing SPACE");
            sleep(300, 600);
            Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
            sleep(600, 1000);
        }

        return TaskStatus.RUNNING;
    }

    private TaskStatus handleBank(AccountContext context) {
        debugLog(context, "handleBank: bankingTask=" + (bankingTask != null ? bankingTask.describe() : "null"));

        if (bankingTask == null) {
            debugLog(context, "Creating DEPOSIT_ALL_AND_WITHDRAW banking task for " + bar.name());
            BankingTask.ItemWithdrawal[] withdrawals = new BankingTask.ItemWithdrawal[bar.ingredients.length];
            for (int i = 0; i < bar.ingredients.length; i++) {
                SmeltingStrategy.OreRequirement req = bar.ingredients[i];
                withdrawals[i] = new BankingTask.ItemWithdrawal(req.itemName, req.withdrawAmount);
            }

            bankingTask = new BankingTask(BankingTask.Mode.DEPOSIT_ALL_AND_WITHDRAW, null, withdrawals);
        }

        TaskStatus bankStatus = bankingTask.tick(context);

        if (bankStatus == TaskStatus.COMPLETE) {
            debugLog(context, "Banking complete");
            bankingTask = null;

            if (!hasAllIngredients(context)) {
                // Not enough ores left in bank to continue smelting this bar
                debugLog(context, "Not enough ingredients after banking, returning REPLAN");
                return TaskStatus.REPLAN;
            }

            debugLog(context, "Switching to WALK_TO_FURNACE");
            phase = Phase.WALK_TO_FURNACE;
            return TaskStatus.RUNNING;
        }

        if (bankStatus == TaskStatus.FAILED || bankStatus == TaskStatus.REPLAN) {
            debugLog(context, "Banking failed/replan: " + bankStatus);
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
        boolean levelCheck = context.getRealLevel(Skill.SMITHING) < bar.requiredLevel;
        if (levelCheck) {
            debugLog(context, "needsReplan: levelCheck=" + levelCheck + " (current=" + context.getRealLevel(Skill.SMITHING) + ", required=" + bar.requiredLevel + ")");
        }
        return levelCheck;
    }

    @Override
    public String describe() {
        return "Smelting (" + bar.name() + ") - " + phase;
    }
}
