package net.runelite.client.plugins.microbot.mntn.builder.tasks.skilling;

import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.mntn.builder.activities.firemaking.FiremakingStrategy;
import net.runelite.client.plugins.microbot.mntn.builder.core.AccountContext;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.Task;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.TaskActionGuard;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.TaskStatus;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.TaskStopReason;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.banking.BankingTask;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.awt.event.KeyEvent;

/**
 * Creates or joins one Forester's Campfire, then tends it through the normal
 * production interface. The task deliberately never line-burns a full inventory.
 */
public class FiremakingTask implements Task {

    private static final int MAX_LOGS_PER_TRIP = 27;

    private enum Phase {
        BANKING, WALK_TO_CAMPFIRE, FIND_OR_START_CAMPFIRE, CREATE_CAMPFIRE, TENDING
    }

    private final FiremakingStrategy.Method method;
    private final WorldPoint campfireLocation;
    private Phase phase = Phase.BANKING;
    private BankingTask bankingTask;
    private TaskStopReason lastStopReason = TaskStopReason.NONE;
    private final TaskActionGuard walkGuard = new TaskActionGuard(10, 30_000, 800);
    private final TaskActionGuard starterFireGuard = new TaskActionGuard(5, 15_000, 1_000);
    private final TaskActionGuard campfireGuard = new TaskActionGuard(5, 15_000, 800);
    private final TaskActionGuard tendGuard = new TaskActionGuard(4, 15_000, 900);
    private boolean starterFirePending;
    private int logsBeforeTend;

    public FiremakingTask(FiremakingStrategy.Method method) {
        this.method = method;
        this.campfireLocation = FiremakingStrategy.randomCampfireLocation();
    }

    @Override
    public TaskStatus tick(AccountContext context) {
        if (!context.isLoggedIn()) {
            return stop(TaskStatus.BLOCKED, TaskStopReason.NOT_LOGGED_IN);
        }

        switch (phase) {
            case BANKING:
                return handleBank(context);
            case WALK_TO_CAMPFIRE:
                return handleWalk(context);
            case FIND_OR_START_CAMPFIRE:
                return handleFindOrStartCampfire(context);
            case CREATE_CAMPFIRE:
                return handleCreateCampfire(context);
            case TENDING:
                return handleTending(context);
            default:
                return TaskStatus.RUNNING;
        }
    }

    private TaskStatus handleBank(AccountContext context) {
        if (hasInputs(context)) {
            resetActionGuards();
            phase = Phase.WALK_TO_CAMPFIRE;
            return TaskStatus.RUNNING;
        }

        if (!hasAccountItem(context, FiremakingStrategy.TINDERBOX)
                || !hasAccountItem(context, method.logItemName)) {
            return stop(TaskStatus.REPLAN, missingInputReason(context));
        }

        if (bankingTask == null) {
            bankingTask = new BankingTask(
                    BankingTask.Mode.DEPOSIT_ALL_AND_WITHDRAW,
                    null,
                    new BankingTask.ItemWithdrawal(FiremakingStrategy.TINDERBOX, 1),
                    new BankingTask.ItemWithdrawal(method.logItemName,
                            logsForTrip(availableLogCount(context)))
            );
        }

        TaskStatus bankStatus = bankingTask.tick(context);
        if (bankStatus == TaskStatus.COMPLETE) {
            bankingTask = null;
            if (!hasInputs(context)) {
                return stop(TaskStatus.REPLAN, missingInputReason(context));
            }
            resetActionGuards();
            phase = Phase.WALK_TO_CAMPFIRE;
            return TaskStatus.RUNNING;
        }
        if (bankStatus.isUnsuccessfulStop()) {
            bankingTask = null;
            return stop(bankStatus, TaskStopReason.BANK_FAILED);
        }
        return TaskStatus.RUNNING;
    }

    private TaskStatus handleWalk(AccountContext context) {
        if (!hasInputs(context)) {
            phase = Phase.BANKING;
            return TaskStatus.RUNNING;
        }
        if (context.isNear(campfireLocation, 6)) {
            walkGuard.reset();
            phase = Phase.FIND_OR_START_CAMPFIRE;
            return TaskStatus.RUNNING;
        }

        TaskActionGuard.Result result = walkGuard.evaluate("walk to firemaking area", false);
        if (result == TaskActionGuard.Result.EXHAUSTED) {
            return stop(TaskStatus.REPLAN, TaskStopReason.TRAVEL_FAILED);
        }
        if (result == TaskActionGuard.Result.READY) {
            Rs2Walker.walkTo(campfireLocation);
            walkGuard.recordAttempt();
        }
        return TaskStatus.RUNNING;
    }

    private TaskStatus handleFindOrStartCampfire(AccountContext context) {
        if (!hasInputs(context)) {
            resetActionGuards();
            phase = Phase.BANKING;
            return TaskStatus.RUNNING;
        }
        if (!context.isNear(campfireLocation, 8)) {
            resetActionGuards();
            phase = Phase.WALK_TO_CAMPFIRE;
            return TaskStatus.RUNNING;
        }

        if (nearbyCampfire() != null) {
            starterFireGuard.reset();
            starterFirePending = false;
            phase = Phase.TENDING;
            return TaskStatus.RUNNING;
        }

        if (nearbyFire() != null) {
            starterFireGuard.reset();
            starterFirePending = false;
            phase = Phase.CREATE_CAMPFIRE;
            return TaskStatus.RUNNING;
        }

        if (Rs2Player.isAnimating() || Rs2Player.isMoving()) {
            return TaskStatus.RUNNING;
        }

        if (starterFirePending) {
            TaskActionGuard.Result result = starterFireGuard.evaluate(
                    "light starter fire " + method.logItemName, false);
            if (result == TaskActionGuard.Result.EXHAUSTED) {
                return stop(TaskStatus.REPLAN, TaskStopReason.ACTION_FAILED);
            }
            if (result == TaskActionGuard.Result.READY) {
                starterFirePending = false;
            }
            return TaskStatus.RUNNING;
        }

        if (!Rs2Inventory.isItemSelected()) {
            TaskActionGuard.Result result = starterFireGuard.evaluate(
                    "select tinderbox for starter fire", false);
            if (result == TaskActionGuard.Result.EXHAUSTED) {
                return stop(TaskStatus.REPLAN, TaskStopReason.ACTION_FAILED);
            }
            if (result == TaskActionGuard.Result.READY) {
                Rs2Inventory.use(FiremakingStrategy.TINDERBOX);
                starterFireGuard.recordAttempt();
            }
            return TaskStatus.RUNNING;
        }

        if (!FiremakingStrategy.TINDERBOX.equalsIgnoreCase(Rs2Inventory.getSelectedItemName())) {
            Rs2Inventory.deselect();
            starterFireGuard.reset();
            return TaskStatus.RUNNING;
        }

        TaskActionGuard.Result result = starterFireGuard.evaluate(
                "light starter fire " + method.logItemName, false);
        if (result == TaskActionGuard.Result.EXHAUSTED) {
            return stop(TaskStatus.REPLAN, TaskStopReason.ACTION_FAILED);
        }
        if (result == TaskActionGuard.Result.READY) {
            Rs2Inventory.use(method.logItemName);
            starterFireGuard.recordAttempt();
            starterFirePending = true;
        }
        return TaskStatus.RUNNING;
    }

    private TaskStatus handleCreateCampfire(AccountContext context) {
        if (!context.inventory().hasItem(method.logItemName)) {
            resetActionGuards();
            phase = Phase.BANKING;
            return TaskStatus.RUNNING;
        }

        // Creating a campfire immediately starts the automatic tending animation.
        // Do not issue another inventory or object interaction until it genuinely ends.
        if (Rs2Player.isAnimating() || Rs2Player.isMoving()) {
            return TaskStatus.RUNNING;
        }

        Rs2TileObjectModel campfire = nearbyCampfire();
        if (campfire != null) {
            campfireGuard.reset();
            phase = Phase.TENDING;
            return TaskStatus.RUNNING;
        }

        Rs2TileObjectModel fire = nearbyFire();
        if (fire == null) {
            if (Rs2Inventory.isItemSelected()) {
                Rs2Inventory.deselect();
            }
            campfireGuard.reset();
            phase = Phase.FIND_OR_START_CAMPFIRE;
            return TaskStatus.RUNNING;
        }

        if (!Rs2Inventory.isItemSelected()) {
            TaskActionGuard.Result result = campfireGuard.evaluate(
                    "select log for campfire " + method.logItemName, false);
            if (result == TaskActionGuard.Result.EXHAUSTED) {
                return stop(TaskStatus.REPLAN, TaskStopReason.ACTION_FAILED);
            }
            if (result == TaskActionGuard.Result.READY) {
                Rs2Inventory.use(method.logItemName);
                campfireGuard.recordAttempt();
            }
            return TaskStatus.RUNNING;
        }

        TaskActionGuard.Result result = campfireGuard.evaluate("create Forester's Campfire", false);
        if (result == TaskActionGuard.Result.EXHAUSTED) {
            return stop(TaskStatus.REPLAN, TaskStopReason.ACTION_FAILED);
        }
        if (result == TaskActionGuard.Result.READY) {
            fire.click();
            campfireGuard.recordAttempt();
        }
        return TaskStatus.RUNNING;
    }

    private TaskStatus handleTending(AccountContext context) {
        if (!context.inventory().hasItem(method.logItemName)) {
            resetActionGuards();
            phase = Phase.BANKING;
            return TaskStatus.RUNNING;
        }

        if (Rs2Player.isAnimating() || Rs2Player.isMoving()) {
            tendGuard.reset();
            return TaskStatus.RUNNING;
        }

        Rs2TileObjectModel campfire = nearbyCampfire();
        if (campfire == null) {
            resetActionGuards();
            phase = Phase.FIND_OR_START_CAMPFIRE;
            return TaskStatus.RUNNING;
        }

        if (!Rs2Widget.isProductionWidgetOpen()) {
            TaskActionGuard.Result result = tendGuard.evaluate("open campfire tending", false);
            if (result == TaskActionGuard.Result.EXHAUSTED) {
                return stop(TaskStatus.REPLAN, TaskStopReason.PRODUCTION_WIDGET_FAILED);
            }
            if (result == TaskActionGuard.Result.READY) {
                campfire.click("Tend-to");
                tendGuard.recordAttempt();
            }
            return TaskStatus.RUNNING;
        }

        int logCount = context.inventory().getCount(method.logItemName);
        boolean logConsumed = logsBeforeTend > logCount;
        TaskActionGuard.Result result = tendGuard.evaluate("tend campfire " + method.logItemName, logConsumed);
        if (result == TaskActionGuard.Result.EXHAUSTED) {
            return stop(TaskStatus.REPLAN, TaskStopReason.PRODUCTION_WIDGET_FAILED);
        }
        if (result == TaskActionGuard.Result.READY) {
            logsBeforeTend = logCount;
            Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
            tendGuard.recordAttempt();
            Microbot.status = "Tending Forester's Campfire with " + method.logItemName;
        }
        return TaskStatus.RUNNING;
    }

    private Rs2TileObjectModel nearbyCampfire() {
        return Microbot.getClientThread().invoke(() -> Microbot.getRs2TileObjectCache().query()
                .withIds(FiremakingStrategy.FORESTERS_CAMPFIRE_IDS)
                .nearest(10));
    }

    private Rs2TileObjectModel nearbyFire() {
        return Microbot.getClientThread().invoke(() -> Microbot.getRs2TileObjectCache().query()
                .withIds(FiremakingStrategy.STARTER_FIRE_IDS)
                .nearest(10));
    }

    private boolean hasInputs(AccountContext context) {
        return context.inventory().hasItem(FiremakingStrategy.TINDERBOX)
                && context.inventory().hasItem(method.logItemName);
    }

    private boolean hasAccountItem(AccountContext context, String itemName) {
        return context.inventory().hasItem(itemName) || context.bank().hasItem(itemName);
    }

    private int availableLogCount(AccountContext context) {
        return context.inventory().getCount(method.logItemName)
                + context.bank().getCount(method.logItemName);
    }

    public static int logsForTrip(int availableLogCount) {
        return Math.max(1, Math.min(MAX_LOGS_PER_TRIP, availableLogCount));
    }

    private TaskStopReason missingInputReason(AccountContext context) {
        if (!context.inventory().hasItem(FiremakingStrategy.TINDERBOX)
                && !context.bank().hasItem(FiremakingStrategy.TINDERBOX)) {
            return TaskStopReason.MISSING_TOOL;
        }
        return TaskStopReason.MISSING_SUPPLIES;
    }

    private void resetActionGuards() {
        walkGuard.reset();
        starterFireGuard.reset();
        campfireGuard.reset();
        tendGuard.reset();
        starterFirePending = false;
        logsBeforeTend = 0;
    }

    @Override
    public boolean needsReplan(AccountContext context) {
        return context.getRealLevel(Skill.FIREMAKING) < method.requiredLevel;
    }

    @Override
    public TaskStopReason getReplanStopReason(AccountContext context) {
        return context.getRealLevel(Skill.FIREMAKING) < method.requiredLevel
                ? TaskStopReason.LEVEL_TOO_LOW
                : TaskStopReason.TASK_REQUESTED_REPLAN;
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
        return "Firemaking (" + method.name() + ") - " + phase;
    }
}
