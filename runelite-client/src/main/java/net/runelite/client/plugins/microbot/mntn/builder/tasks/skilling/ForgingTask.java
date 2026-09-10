package net.runelite.client.plugins.microbot.mntn.builder.tasks.skilling;

import net.runelite.api.Skill;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.mntn.builder.activities.smithing.ForgingStrategy;
import net.runelite.client.plugins.microbot.mntn.builder.core.AccountContext;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.Task;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.TaskActionGuard;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.TaskStatus;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.TaskStopReason;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.banking.BankingTask;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.util.Optional;

public class ForgingTask implements Task {

    private static final int ANVIL_OBJECT_ID = 2097;

    private enum Phase {
        WALK_TO_ANVIL, FORGING, BANKING
    }

    private final ForgingStrategy.BarType barType;
    private Phase phase = Phase.WALK_TO_ANVIL;
    private BankingTask bankingTask;
    private TaskStopReason lastStopReason = TaskStopReason.NONE;
    private final TaskActionGuard walkGuard = new TaskActionGuard(10, 30_000, 800);
    private final TaskActionGuard anvilGuard = new TaskActionGuard(8, 12_000, 800);
    private final TaskActionGuard widgetGuard = new TaskActionGuard(4, 10_000, 900);
    private final TaskActionGuard forgeGuard = new TaskActionGuard(4, 12_000, 900);
    private int barsBeforeForge;

    public ForgingTask(ForgingStrategy.BarType barType) {
        this.barType = barType;
    }

    private void debugLog(AccountContext context, String message) {
        if (context.isDebugLogging()) {
            Microbot.log("[MntnBuilder][ForgingTask][DEBUG] " + message);
        }
    }

    @Override
    public TaskStatus tick(AccountContext context) {
        debugLog(context, "tick: phase=" + phase + ", barType=" + barType.name());

        if (!context.isLoggedIn()) {
            debugLog(context, "Not logged in, returning BLOCKED");
            return stop(TaskStatus.BLOCKED, TaskStopReason.NOT_LOGGED_IN);
        }

        switch (phase) {
            case WALK_TO_ANVIL:
                return handleWalk(context);
            case FORGING:
                return handleForging(context);
            case BANKING:
                return handleBank(context);
            default:
                debugLog(context, "Unknown phase, returning RUNNING");
                return TaskStatus.RUNNING;
        }
    }

    private TaskStatus handleWalk(AccountContext context) {
        debugLog(context, "handleWalk: hasHammerAndBars=" + hasHammerAndBars(context) + ", nearAnvil=" + context.isNear(ForgingStrategy.VARROCK_ANVIL, 10));

        if (!hasHammerAndBars(context)) {
            debugLog(context, "Missing hammer or bars, switching to BANKING");
            resetActionGuards();
            phase = Phase.BANKING;
            return TaskStatus.RUNNING;
        }

        if (context.isNear(ForgingStrategy.VARROCK_ANVIL, 10)) {
            debugLog(context, "Near anvil, switching to FORGING");
            walkGuard.reset();
            phase = Phase.FORGING;
            return TaskStatus.RUNNING;
        }

        TaskActionGuard.Result walkResult = walkGuard.evaluate("walk to anvil " + barType.name(), false);
        if (walkResult == TaskActionGuard.Result.EXHAUSTED) {
            return stop(TaskStatus.REPLAN, TaskStopReason.TRAVEL_FAILED);
        }
        if (walkResult == TaskActionGuard.Result.READY) {
            debugLog(context, "Walking to anvil: " + ForgingStrategy.VARROCK_ANVIL);
            Rs2Walker.walkTo(ForgingStrategy.VARROCK_ANVIL);
            walkGuard.recordAttempt();
        }
        return TaskStatus.RUNNING;
    }

    private TaskStatus handleForging(AccountContext context) {
        debugLog(context, "handleForging: hasHammerAndBars=" + hasHammerAndBars(context));

        if (!hasHammerAndBars(context)) {
            debugLog(context, "Missing hammer or bars, switching to BANKING");
            resetActionGuards();
            phase = Phase.BANKING;
            return TaskStatus.RUNNING;
        }

        if (Rs2Player.isAnimating() || Rs2Player.isMoving()) {
            debugLog(context, "Already animating/moving, waiting");
            return TaskStatus.RUNNING;
        }

        Rs2TileObjectModel anvil = Microbot.getRs2TileObjectCache().query()
                .withId(ANVIL_OBJECT_ID)
                .within(15)
                .nearest();

        if (anvil == null) {
            TaskActionGuard.Result findResult = anvilGuard.evaluate("find anvil", false);
            if (findResult == TaskActionGuard.Result.EXHAUSTED) {
                return stop(TaskStatus.REPLAN, TaskStopReason.RESOURCE_NOT_FOUND);
            }
            if (findResult == TaskActionGuard.Result.READY) {
                anvilGuard.recordAttempt();
            }
            debugLog(context, "Anvil not found; waiting for a nearby anvil");
            return TaskStatus.RUNNING;
        }
        anvilGuard.reset();

        if (!Rs2Widget.isSmithingWidgetOpen()) {
            TaskActionGuard.Result openResult = widgetGuard.evaluate("open smithing widget", false);
            if (openResult == TaskActionGuard.Result.EXHAUSTED) {
                return stop(TaskStatus.REPLAN, TaskStopReason.PRODUCTION_WIDGET_FAILED);
            }
            if (openResult == TaskActionGuard.Result.READY) {
                debugLog(context, "Clicking anvil to open smithing widget");
                anvil.click("Smith");
                widgetGuard.recordAttempt();
            }
            return TaskStatus.RUNNING;
        }
        widgetGuard.reset();

        int barCount = context.inventory().getCount(barType.barItemName);
        String targetItem = determineBestItemToForge(context, barCount);
        if (targetItem == null) {
            return stop(TaskStatus.REPLAN, TaskStopReason.LEVEL_TOO_LOW);
        }

        boolean forged = barsBeforeForge > 0 && barCount < barsBeforeForge;
        TaskActionGuard.Result forgeResult = forgeGuard.evaluate("forge " + targetItem, forged);
        if (forgeResult == TaskActionGuard.Result.CONFIRMED) {
            // Consume this one observed bar reduction. Leaving the old baseline in place
            // makes every later tick look like the same completed forge and prevents the
            // next product click.
            barsBeforeForge = 0;
            return TaskStatus.RUNNING;
        }
        if (forgeResult == TaskActionGuard.Result.EXHAUSTED) {
            return stop(TaskStatus.REPLAN, TaskStopReason.PRODUCTION_WIDGET_FAILED);
        }
        if (forgeResult == TaskActionGuard.Result.READY) {
            debugLog(context, "Clicking widget for: " + targetItem);
            barsBeforeForge = barCount;
            boolean clicked = Rs2Widget.clickWidget(targetItem, Optional.of(InterfaceID.SMITHING), 0, true);
            if (!clicked) {
                debugLog(context, "Smithing interface did not expose: " + targetItem);
            }
            forgeGuard.recordAttempt();
        }
        return TaskStatus.RUNNING;
    }

    private TaskStatus handleBank(AccountContext context) {
        debugLog(context, "handleBank: bankingTask=" + (bankingTask != null ? bankingTask.describe() : "null"));

        if (bankingTask == null) {
            boolean hasHammer = context.inventory().hasItem(ForgingStrategy.HAMMER);
            if (hasHammer) {
                debugLog(context, "Has hammer, creating DEPOSIT_ALL_AND_WITHDRAW keeping hammer, withdrawing all bars");
                bankingTask = new BankingTask(
                        BankingTask.Mode.DEPOSIT_ALL_AND_WITHDRAW,
                        new String[]{ForgingStrategy.HAMMER},
                        new BankingTask.ItemWithdrawal(barType.barItemName, -1)
                );
            } else {
                debugLog(context, "No hammer, creating DEPOSIT_ALL_AND_WITHDRAW withdrawing hammer and bars");
                bankingTask = new BankingTask(
                        BankingTask.Mode.DEPOSIT_ALL_AND_WITHDRAW,
                        null,
                        new BankingTask.ItemWithdrawal(ForgingStrategy.HAMMER, 1),
                        new BankingTask.ItemWithdrawal(barType.barItemName, -1)
                );
            }
        }

        TaskStatus bankStatus = bankingTask.tick(context);

        if (bankStatus == TaskStatus.COMPLETE) {
            debugLog(context, "Banking complete");
            bankingTask = null;
            resetActionGuards();

            if (!hasHammerAndBars(context)) {
                // Out of bars or missing hammer in bank
                debugLog(context, "Missing hammer or bars after banking, returning REPLAN");
                return stop(TaskStatus.REPLAN, TaskStopReason.MISSING_SUPPLIES);
            }

            debugLog(context, "Switching to WALK_TO_ANVIL");
            phase = Phase.WALK_TO_ANVIL;
            return TaskStatus.RUNNING;
        }

        if (bankStatus.isUnsuccessfulStop()) {
            debugLog(context, "Banking failed/replan: " + bankStatus);
            bankingTask = null;
            return stop(bankStatus, TaskStopReason.BANK_FAILED);
        }

        return TaskStatus.RUNNING;
    }

    private boolean hasHammerAndBars(AccountContext context) {
        return context.inventory().hasItem(ForgingStrategy.HAMMER)
                && context.inventory().hasItem(barType.barItemName);
    }

    /** Bar-efficient max XP using the exact unlock data declared by the selected bar type. */
    private String determineBestItemToForge(AccountContext context, int barCount) {
        int smithingLevel = context.getRealLevel(Skill.SMITHING);
        String capitalBar = barType.name().substring(0, 1).toUpperCase() + barType.name().substring(1).toLowerCase();

        if (barCount >= 5 && smithingLevel >= barType.platebodyLevel) {
            return capitalBar + " platebody";
        }
        if (barCount >= 3 && smithingLevel >= barType.platelegsLevel) {
            return capitalBar + " platelegs";
        }
        if (barCount >= 1 && smithingLevel >= barType.swordLevel) {
            return capitalBar + " sword";
        }
        if (barCount >= 1 && smithingLevel >= barType.daggerLevel) {
            return capitalBar + " dagger";
        }
        return null;
    }

    @Override
    public boolean needsReplan(AccountContext context) {
        boolean levelCheck = context.getRealLevel(Skill.SMITHING) < barType.requiredLevel;
        if (levelCheck) {
            debugLog(context, "needsReplan: levelCheck=" + levelCheck + " (current=" + context.getRealLevel(Skill.SMITHING) + ", required=" + barType.requiredLevel + ")");
        }
        return levelCheck;
    }

    private void resetActionGuards() {
        walkGuard.reset();
        anvilGuard.reset();
        widgetGuard.reset();
        forgeGuard.reset();
        barsBeforeForge = 0;
    }

    @Override
    public TaskStopReason getReplanStopReason(AccountContext context) {
        if (context.getRealLevel(Skill.SMITHING) < barType.requiredLevel) {
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
        return "Forging (" + barType.name() + ") - " + phase;
    }
}
