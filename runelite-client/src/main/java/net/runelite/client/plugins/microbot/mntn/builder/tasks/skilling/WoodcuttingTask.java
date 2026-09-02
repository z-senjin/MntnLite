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

    @Override
    public TaskStatus tick(AccountContext context) {
        if (!context.isLoggedIn()) {
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
        String axe = WoodcuttingStrategy.findBestAxe(context, true);
        if (axe == null) {
            phase = Phase.BANKING;
            return TaskStatus.RUNNING;
        }

        tryEquipAxe(context, axe);

        if (context.isNear(method.location, 10)) {
            phase = Phase.CHOPPING;
            return TaskStatus.RUNNING;
        }

        Rs2Walker.walkTo(method.location);
        return TaskStatus.RUNNING;
    }

    private TaskStatus handleChop(AccountContext context) {
        if (context.inventory().isFull()) {
            phase = Phase.BANKING;
            return TaskStatus.RUNNING;
        }

        String axe = WoodcuttingStrategy.findBestAxe(context, true);
        if (axe == null) {
            phase = Phase.BANKING;
            return TaskStatus.RUNNING;
        }

        tryEquipAxe(context, axe);

        Rs2TileObjectModel tree = findNearestTree();
        if (tree == null) {
            // Tree could be depleted/despawned - go find another of the same type.
            phase = Phase.WALK_TO_TREE;
            return TaskStatus.RUNNING;
        }

        if (Microbot.getClient().getLocalPlayer() != null) {

            boolean isMovingOrAnimating = Rs2Player.isAnimating() || Rs2Player.isMoving();

            sleep(300, 1000);

            boolean isMovingOrAnimatingAgain = Rs2Player.isAnimating() || Rs2Player.isMoving();


            if (isMovingOrAnimating || isMovingOrAnimatingAgain) {
                return TaskStatus.RUNNING;
            } else {
                tree.click(method.action);
            }
        }
        return TaskStatus.RUNNING;
    }

    private TaskStatus handleBank(AccountContext context) {

        if (bankingTask == null) {

            String ownedAxe = WoodcuttingStrategy.findBestAxe(context, true);

            if (ownedAxe != null) {
                if (context.equipment().hasItem(ownedAxe)) {
                    // Axe is equipped - deposit all items in inventory
                    bankingTask = new BankingTask(BankingTask.Mode.DEPOSIT_ALL);
                } else if (WoodcuttingStrategy.canWield(context, ownedAxe)) {
                    Rs2Inventory.wield(ownedAxe);
                    bankingTask = new BankingTask(BankingTask.Mode.DEPOSIT_ALL);
                } else {
                    // Have an axe in inventory but cannot wield - deposit everything else, keep the axe.
                    bankingTask = new BankingTask(BankingTask.Mode.DEPOSIT_ALL_EXCEPT, ownedAxe);
                }
            } else {
                String bankAxe = WoodcuttingStrategy.findBestAxe(context, false);
                if (bankAxe != null) {
                    bankingTask = new BankingTask(BankingTask.Mode.DEPOSIT_ALL_AND_WITHDRAW, null, bankAxe, 1);
                } else {
                    // Shouldn't happen - canExecute() already required an axe to exist
                    // somewhere - but if it's gone (e.g. dropped/sold mid-session), reroll.
                    return TaskStatus.REPLAN;
                }
            }
        }

        TaskStatus bankStatus = bankingTask.tick(context);

        if (bankStatus == TaskStatus.COMPLETE) {

            bankingTask = null;

            String axe = WoodcuttingStrategy.findBestAxe(context, true);
            if (axe == null) {
                phase = Phase.BANKING;
                return TaskStatus.RUNNING;
            }

            tryEquipAxe(context, axe);
            phase = Phase.WALK_TO_TREE;
            return TaskStatus.RUNNING;
        }

        if (bankStatus == TaskStatus.FAILED || bankStatus == TaskStatus.REPLAN) {
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
        return context.getRealLevel(Skill.WOODCUTTING) < method.requiredLevel;
    }

    @Override
    public String describe() {
        return "Woodcutting (" + method.name() + ") - " + phase;
    }
}
