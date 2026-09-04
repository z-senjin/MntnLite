package net.runelite.client.plugins.microbot.mntn.builder.tasks.skilling;

import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.mntn.builder.activities.woodcutting.WoodcuttingStrategy;
import net.runelite.client.plugins.microbot.mntn.builder.core.AccountContext;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.Task;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.TaskStatus;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.banking.BankingTask;

import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;

import static net.runelite.client.plugins.microbot.util.Global.sleep;

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
    private Phase phase = Phase.WALK_TO_TREE;
    private BankingTask bankingTask;

    public WoodcuttingTask(WoodcuttingStrategy.Method method) {
        this.method = method;
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
            return TaskStatus.BLOCKED;
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

    /**
     * The same visual tree type has several distinct game object ids (different rotations/
     * graphical variants around the map) - this tries each id in method.treeObjectIds in
     * turn and returns the first one that's actually nearby, rather than requiring a single
     * id to match.
     *
     * NOTE: this returns the first MATCHING id's nearest instance, not necessarily the
     * closest instance across ALL ids - e.g. if id[0] has a match 20 tiles away and id[1]
     * has one 3 tiles away, this still returns id[0]'s. Good enough to get chopping working;
     * if you want true nearest-across-all-variants later, this is the method to extend with
     * a distance comparison between candidates.
     */
    private Rs2TileObjectModel findNearestTree() {
        for (int objectId : method.treeObjectIds) {
            Rs2TileObjectModel tree = Microbot.getRs2TileObjectCache().query().withId(objectId).nearest();
            if (tree != null) {
                return tree;
            }
        }
        return null;
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

        if (context.isNear(method.location, 10)) {
            debugLog(context, "Near woodcutting location, switching to CHOPPING");
            phase = Phase.CHOPPING;
            return TaskStatus.RUNNING;
        }

        debugLog(context, "Walking to woodcutting location: " + method.location);
        Rs2Walker.walkTo(method.location);
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
            // Tree could be depleted/despawned - go find another of the same type.
            debugLog(context, "No tree found nearby, switching to WALK_TO_TREE");
            phase = Phase.WALK_TO_TREE;
            return TaskStatus.RUNNING;
        }

        if (Microbot.getClient().getLocalPlayer() != null) {

            boolean isMovingOrAnimating = Rs2Player.isAnimating() || Rs2Player.isMoving();

            sleep(300, 1000);

            boolean isMovingOrAnimatingAgain = Rs2Player.isAnimating() || Rs2Player.isMoving();


            if (isMovingOrAnimating || isMovingOrAnimatingAgain) {
                debugLog(context, "Already animating/moving, waiting");
                return TaskStatus.RUNNING;
            } else {
                debugLog(context, "Clicking tree: " + tree.getWorldLocation() + " with action: " + method.action);
                tree.click(method.action);
            }
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
                    return TaskStatus.REPLAN;
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
                    return TaskStatus.REPLAN;
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

        if (bankStatus == TaskStatus.FAILED || bankStatus == TaskStatus.REPLAN) {
            debugLog(context, "Banking failed/replan: " + bankStatus);
            bankingTask = null;
            return bankStatus;
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
    public String describe() {
        return "Woodcutting (" + method.name() + ") - " + phase;
    }
}
