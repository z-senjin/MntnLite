package net.runelite.client.plugins.microbot.mntn.builder.tasks.skilling;

import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.mntn.builder.activities.woodcutting.WoodcuttingStrategy;
import net.runelite.client.plugins.microbot.mntn.builder.core.AccountContext;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.Task;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.TaskActionGuard;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.TaskStatus;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.TaskStopReason;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.banking.BankingTask;

import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;

/**
 * Generic Woodcutting Task, parameterized by WoodcuttingStrategy.Method - same pattern as
 * FishingTask/CookingTask. Trees are GAME OBJECTS in OSRS (not NPCs like fishing spots), so
 * this uses Rs2TileObjectCache - same API your CookingTask already uses for the range/fire.
 */
public class WoodcuttingTask implements Task {

    private enum Phase {
        WALK_TO_TREE, CHOPPING, BANKING
    }

    private final WoodcuttingStrategy.Method method;
    private final WoodcuttingStrategy.Location location;
    private Phase phase = Phase.WALK_TO_TREE;
    private BankingTask bankingTask;
    private TaskStopReason lastStopReason = TaskStopReason.NONE;
    private final TaskActionGuard walkGuard = new TaskActionGuard(10, 30_000, 800);
    private final TaskActionGuard chopGuard = new TaskActionGuard(5, 12_000, 900);
    private final TaskActionGuard treeGuard = new TaskActionGuard(8, 12_000, 900);

    public WoodcuttingTask(WoodcuttingStrategy.Method method) {
        this(method, WoodcuttingStrategy.Location.defaultFor(method));
    }

    public WoodcuttingTask(WoodcuttingStrategy.Method method, WoodcuttingStrategy.Location location) {
        if (location.method != method) {
            throw new IllegalArgumentException("Woodcutting location does not support " + method);
        }
        this.method = method;
        this.location = location;
    }

    private void debugLog(AccountContext context, String message) {
        if (context.isDebugLogging()) {
            Microbot.log("[MntnBuilder][WoodcuttingTask][DEBUG] " + message);
        }
    }

    @Override
    public TaskStatus tick(AccountContext context) {
        debugLog(context, "tick: phase=" + phase + ", method=" + method.name());

        if (!context.isLoggedIn()) {
            debugLog(context, "Not logged in, returning BLOCKED");
            return stop(TaskStatus.BLOCKED, TaskStopReason.NOT_LOGGED_IN);
        }

        switch (phase) {
            case WALK_TO_TREE:
                return handleWalk(context);
            case CHOPPING:
                return handleChop(context);
            case BANKING:
                return handleBank(context);
            default:
                debugLog(context, "Unknown phase, returning RUNNING");
                return TaskStatus.RUNNING;
        }
    }

    private Rs2TileObjectModel findNearestTree() {
        return Microbot.getRs2TileObjectCache().query()
                .withIds(method.treeObjectIds)
                .within(20)
                .nearest();
    }

    private TaskStatus handleWalk(AccountContext context) {
        debugLog(context, "handleWalk: checking axe");
        String axe = WoodcuttingStrategy.findBestAxe(context, true);
        if (axe == null) {
            debugLog(context, "No axe available, switching to BANKING");
            phase = Phase.BANKING;
            return TaskStatus.RUNNING;
        }

        tryEquipAxe(context, axe);

        if (context.isNear(location.point, 10)) {
            walkGuard.reset();
            debugLog(context, "Near woodcutting location, switching to CHOPPING");
            phase = Phase.CHOPPING;
            return TaskStatus.RUNNING;
        }

        TaskActionGuard.Result walkResult = walkGuard.evaluate("walk to trees " + method.name(), false);
        if (walkResult == TaskActionGuard.Result.EXHAUSTED) {
            return stop(TaskStatus.REPLAN, TaskStopReason.TRAVEL_FAILED);
        }
        if (walkResult == TaskActionGuard.Result.READY) {
            debugLog(context, "Walking to woodcutting location: " + location.point);
            Rs2Walker.walkTo(location.point);
            walkGuard.recordAttempt();
        }
        return TaskStatus.RUNNING;
    }

    private TaskStatus handleChop(AccountContext context) {
        debugLog(context, "handleChop: inventoryFull=" + context.inventory().isFull());

        if (context.inventory().isFull()) {
            debugLog(context, "Inventory full, switching to BANKING");
            phase = Phase.BANKING;
            return TaskStatus.RUNNING;
        }

        String axe = WoodcuttingStrategy.findBestAxe(context, true);
        if (axe == null) {
            debugLog(context, "No axe available, switching to BANKING");
            phase = Phase.BANKING;
            return TaskStatus.RUNNING;
        }

        tryEquipAxe(context, axe);

        Rs2TileObjectModel tree = findNearestTree();
        if (tree == null) {
            TaskActionGuard.Result treeResult = treeGuard.evaluate("find tree " + location.name(), false);
            if (treeResult == TaskActionGuard.Result.EXHAUSTED) {
                return stop(TaskStatus.REPLAN, TaskStopReason.RESOURCE_NOT_FOUND);
            }
            if (treeResult == TaskActionGuard.Result.READY) {
                treeGuard.recordAttempt();
            }
            debugLog(context, "No tree found nearby, switching to WALK_TO_TREE");
            phase = Phase.WALK_TO_TREE;
            return TaskStatus.RUNNING;
        }

        treeGuard.reset();

        boolean isMovingOrAnimating = Rs2Player.isAnimating() || Rs2Player.isMoving();
        if (isMovingOrAnimating) {
            chopGuard.reset();
            debugLog(context, "Already animating/moving, waiting");
            return TaskStatus.RUNNING;
        }
        TaskActionGuard.Result chopResult = chopGuard.evaluate("chop " + method.name(), false);
        if (chopResult == TaskActionGuard.Result.EXHAUSTED) {
            return stop(TaskStatus.REPLAN, TaskStopReason.ACTION_FAILED);
        }
        if (chopResult == TaskActionGuard.Result.READY) {
            debugLog(context, "Clicking tree: " + tree.getWorldLocation() + " with action: " + method.action);
            tree.click(method.action);
            chopGuard.recordAttempt();
        }
        return TaskStatus.RUNNING;
    }

    private TaskStatus handleBank(AccountContext context) {
        debugLog(context, "handleBank: bankingTask=" + (bankingTask != null ? bankingTask.describe() : "null"));

        if (bankingTask == null) {

            String ownedAxe = WoodcuttingStrategy.findBestAxe(context, true);

            if (ownedAxe != null) {
                if (context.equipment().hasItem(ownedAxe)) {
                    // Axe is equipped - deposit all items in inventory
                    debugLog(context, "Axe equipped, creating DEPOSIT_ALL banking task");
                    bankingTask = new BankingTask(BankingTask.Mode.DEPOSIT_ALL);
                } else if (WoodcuttingStrategy.canWield(context, ownedAxe)) {
                    debugLog(context, "Axe in inventory and can wield, wielding then DEPOSIT_ALL");
                    Rs2Inventory.wield(ownedAxe);
                    bankingTask = new BankingTask(BankingTask.Mode.DEPOSIT_ALL);
                } else {
                    // Have an axe in inventory but cannot wield - deposit everything else, keep the axe.
                    debugLog(context, "Axe in inventory but cannot wield, DEPOSIT_ALL_EXCEPT " + ownedAxe);
                    bankingTask = new BankingTask(BankingTask.Mode.DEPOSIT_ALL_EXCEPT, ownedAxe);
                }
            } else {
                String bankAxe = WoodcuttingStrategy.findBestAxe(context, false);
                if (bankAxe != null) {
                    debugLog(context, "Axe in bank, creating DEPOSIT_ALL_AND_WITHDRAW for " + bankAxe);
                    bankingTask = new BankingTask(BankingTask.Mode.DEPOSIT_ALL_AND_WITHDRAW, null, bankAxe, 1);
                } else {
                    // Shouldn't happen - canExecute() already required an axe to exist
                    // somewhere - but if it's gone (e.g. dropped/sold mid-session), reroll.
                    debugLog(context, "No axe available anywhere, returning REPLAN");
                    return stop(TaskStatus.REPLAN, TaskStopReason.MISSING_TOOL);
                }
            }
        }

        TaskStatus bankStatus = bankingTask.tick(context);

        if (bankStatus == TaskStatus.COMPLETE) {

            bankingTask = null;

            String axe = WoodcuttingStrategy.findBestAxe(context, true);
            if (axe == null) {
                if (WoodcuttingStrategy.findBestAxe(context, false) == null) {
                    debugLog(context, "No axe available after banking, returning REPLAN");
                    return stop(TaskStatus.REPLAN, TaskStopReason.MISSING_TOOL);
                }
                debugLog(context, "Axe not in inventory/equipped but in bank, staying in BANKING");
                phase = Phase.BANKING;
                return TaskStatus.RUNNING;
            }

            tryEquipAxe(context, axe);
            debugLog(context, "Switching to WALK_TO_TREE");
            phase = Phase.WALK_TO_TREE;
            return TaskStatus.RUNNING;
        }

        if (bankStatus.isUnsuccessfulStop()) {
            debugLog(context, "Banking failed/replan: " + bankStatus);
            bankingTask = null;
            return stop(bankStatus, TaskStopReason.BANK_FAILED);
        }

        return TaskStatus.RUNNING;
    }

    private void tryEquipAxe(AccountContext context, String axeName) {
        if (axeName == null) return;
        if (!context.equipment().hasItem(axeName)
                && context.inventory().hasItem(axeName)
                && WoodcuttingStrategy.canWield(context, axeName)) {
            Rs2Inventory.wield(axeName);
        }
    }

    @Override
    public boolean needsReplan(AccountContext context) {
        boolean levelCheck = context.getRealLevel(Skill.WOODCUTTING) < method.requiredLevel;
        boolean axeCheck = WoodcuttingStrategy.findBestAxe(context, false) == null;
        if (levelCheck || axeCheck) {
            debugLog(context, "needsReplan: levelCheck=" + levelCheck + " (current=" + context.getRealLevel(Skill.WOODCUTTING) + ", required=" + method.requiredLevel + "), axeCheck=" + axeCheck);
        }
        return levelCheck || axeCheck;
    }

    @Override
    public TaskStopReason getReplanStopReason(AccountContext context) {
        if (context.getRealLevel(Skill.WOODCUTTING) < method.requiredLevel) {
            return TaskStopReason.LEVEL_TOO_LOW;
        }
        if (WoodcuttingStrategy.findBestAxe(context, false) == null) {
            return TaskStopReason.MISSING_TOOL;
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
        return "Woodcutting (" + method.name() + " at " + location.name() + ") - " + phase;
    }
}
