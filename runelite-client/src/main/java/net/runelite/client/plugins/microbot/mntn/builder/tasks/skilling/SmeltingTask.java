package net.runelite.client.plugins.microbot.mntn.builder.tasks.skilling;

import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.mntn.builder.activities.smithing.SmeltingStrategy;
import net.runelite.client.plugins.microbot.mntn.builder.core.AccountContext;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.Task;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.TaskActionGuard;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.TaskStatus;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.TaskStopReason;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.banking.BankingTask;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.awt.event.KeyEvent;

import static net.runelite.client.plugins.microbot.util.Global.sleep;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

public class SmeltingTask implements Task {

    private static final int[] FURNACE_OBJECT_IDS = {24009};
    private static final int MAX_PRODUCTION_FAILURES = 3;

    private enum Phase {
        WALK_TO_FURNACE, SMELTING, BANKING
    }

    private final SmeltingStrategy.Bar bar;
    private Phase phase = Phase.WALK_TO_FURNACE;
    private BankingTask bankingTask;
    private TaskStopReason lastStopReason = TaskStopReason.NONE;
    private final TaskActionGuard walkGuard = new TaskActionGuard(10, 30_000, 800);
    private final TaskActionGuard furnaceGuard = new TaskActionGuard(8, 12_000, 800);
    private int failedWidgetOpens;
    private int failedSmeltAttempts;

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
            return stop(TaskStatus.BLOCKED, TaskStopReason.NOT_LOGGED_IN);
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
            resetActionGuards();
            phase = Phase.BANKING;
            return TaskStatus.RUNNING;
        }

        if (context.isNear(bar.furnaceLocation, 10)) {
            debugLog(context, "Near furnace, switching to SMELTING");
            walkGuard.reset();
            phase = Phase.SMELTING;
            return TaskStatus.RUNNING;
        }

        TaskActionGuard.Result walkResult = walkGuard.evaluate("walk to furnace " + bar.name(), false);
        if (walkResult == TaskActionGuard.Result.EXHAUSTED) {
            return stop(TaskStatus.REPLAN, TaskStopReason.TRAVEL_FAILED);
        }
        if (walkResult == TaskActionGuard.Result.READY) {
            debugLog(context, "Walking to furnace: " + bar.furnaceLocation);
            Rs2Walker.walkTo(bar.furnaceLocation);
            walkGuard.recordAttempt();
        }
        return TaskStatus.RUNNING;
    }

    private TaskStatus handleSmelt(AccountContext context) {
        debugLog(context, "handleSmelt: hasAllIngredients=" + hasAllIngredients(context));

        if (!hasAllIngredients(context)) {
            debugLog(context, "Missing ingredients, switching to BANKING");
            resetActionGuards();
            phase = Phase.BANKING;
            return TaskStatus.RUNNING;
        }

        if (Rs2Player.isAnimating() || Rs2Player.isMoving()) {
            debugLog(context, "Already animating/moving, waiting");
            return TaskStatus.RUNNING;
        }

        // Smelting animation can briefly report idle between bars. Verify that the
        // player did not resume before opening the furnace or pressing SPACE again.
        sleep(400, 800);
        if (Rs2Player.isAnimating() || Rs2Player.isMoving()) {
            debugLog(context, "Smelting resumed during idle check, waiting");
            return TaskStatus.RUNNING;
        }

        Rs2TileObjectModel furnace = Microbot.getRs2TileObjectCache().query()
                .withIds(FURNACE_OBJECT_IDS)
                .within(15)
                .nearest();

        if (furnace == null) {
            TaskActionGuard.Result findResult = furnaceGuard.evaluate("find furnace " + bar.name(), false);
            if (findResult == TaskActionGuard.Result.EXHAUSTED) {
                return stop(TaskStatus.REPLAN, TaskStopReason.RESOURCE_NOT_FOUND);
            }
            if (findResult == TaskActionGuard.Result.READY) {
                furnaceGuard.recordAttempt();
            }
            debugLog(context, "Furnace not found; waiting for a nearby furnace");
            return TaskStatus.RUNNING;
        }
        furnaceGuard.reset();

        if (!Rs2Widget.isProductionWidgetOpen()) {
            debugLog(context, "Clicking furnace to open smelting widget");
            furnace.click("Smelt");
            if (sleepUntil(Rs2Widget::isProductionWidgetOpen, 5_000)) {
                failedWidgetOpens = 0;
                debugLog(context, "Smelting widget opened");
            } else if (++failedWidgetOpens >= MAX_PRODUCTION_FAILURES) {
                return stop(TaskStatus.REPLAN, TaskStopReason.PRODUCTION_WIDGET_FAILED);
            } else {
                debugLog(context, "Smelting widget did not open after furnace click (attempt "
                        + failedWidgetOpens + ")");
            }
            return TaskStatus.RUNNING;
        }

        failedWidgetOpens = 0;
        int ingredientsBeforeSmelt = ingredientCount(context);
        sleep(800, 3000);
        Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
        if (sleepUntil(() -> ingredientCount(context) < ingredientsBeforeSmelt, 7_000)) {
            failedSmeltAttempts = 0;
            debugLog(context, "Smelted " + bar.barItemName);
        } else if (++failedSmeltAttempts >= MAX_PRODUCTION_FAILURES) {
            return stop(TaskStatus.REPLAN, TaskStopReason.PRODUCTION_WIDGET_FAILED);
        } else {
            debugLog(context, "SPACE did not consume ingredients (attempt " + failedSmeltAttempts + ")");
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
            resetActionGuards();

            if (!hasAllIngredients(context)) {
                // Not enough ores left in bank to continue smelting this bar
                debugLog(context, "Not enough ingredients after banking, returning REPLAN");
                return stop(TaskStatus.REPLAN, TaskStopReason.MISSING_SUPPLIES);
            }

            debugLog(context, "Switching to WALK_TO_FURNACE");
            phase = Phase.WALK_TO_FURNACE;
            return TaskStatus.RUNNING;
        }

        if (bankStatus.isUnsuccessfulStop()) {
            debugLog(context, "Banking failed/replan: " + bankStatus);
            bankingTask = null;
            return stop(bankStatus, TaskStopReason.BANK_FAILED);
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

    private int ingredientCount(AccountContext context) {
        int count = 0;
        for (SmeltingStrategy.OreRequirement requirement : bar.ingredients) {
            count += context.inventory().getCount(requirement.itemName);
        }
        return count;
    }

    private void resetActionGuards() {
        walkGuard.reset();
        furnaceGuard.reset();
        failedWidgetOpens = 0;
        failedSmeltAttempts = 0;
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
    public TaskStopReason getReplanStopReason(AccountContext context) {
        if (context.getRealLevel(Skill.SMITHING) < bar.requiredLevel) {
            return TaskStopReason.LEVEL_TOO_LOW;
        }
        return TaskStopReason.TASK_REQUESTED_REPLAN;
    }

    @Override
    public TaskStopReason getLastStopReason() {
        return lastStopReason;
    }

    private TaskStatus stop(TaskStatus status, TaskStopReason reason) {
        lastStopReason = reason != null ? reason : TaskStopReason.UNKNOWN;
        return status;
    }

    @Override
    public String describe() {
        return "Smelting (" + bar.name() + ") - " + phase;
    }
}
