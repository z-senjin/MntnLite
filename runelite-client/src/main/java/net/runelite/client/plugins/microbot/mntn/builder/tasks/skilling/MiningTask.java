package net.runelite.client.plugins.microbot.mntn.builder.tasks.skilling;

import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.mntn.builder.activities.mining.MiningStrategy;
import net.runelite.client.plugins.microbot.mntn.builder.core.AccountContext;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.Task;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.TaskStatus;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.banking.BankingTask;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import static net.runelite.client.plugins.microbot.util.Global.sleep;

public class MiningTask implements Task {

    private enum Phase {
        WALK_TO_MINE, MINING, BANKING
    }

    private final MiningStrategy.Method method;
    private Phase phase = Phase.WALK_TO_MINE;
    private BankingTask bankingTask;

    public MiningTask(MiningStrategy.Method method) {
        this.method = method;
    }

    @Override
    public TaskStatus tick(AccountContext context) {
        if (!context.isLoggedIn()) {
            return TaskStatus.BLOCKED;
        }

        switch (phase) {
            case WALK_TO_MINE:
                return handleWalk(context);
            case MINING:
                return handleMine(context);
            case BANKING:
                return handleBank(context);
            default:
                return TaskStatus.RUNNING;
        }
    }

    private TaskStatus handleWalk(AccountContext context) {
        MiningStrategy.Pickaxe pickaxe = MiningStrategy.findBestPickaxe(context, true);
        if (pickaxe == null) {
            phase = Phase.BANKING;
            return TaskStatus.RUNNING;
        }

        tryEquipPickaxe(context, pickaxe);

        if (context.isNear(method.location, 12)) {
            phase = Phase.MINING;
            return TaskStatus.RUNNING;
        }

        Rs2Walker.walkTo(method.location);
        return TaskStatus.RUNNING;
    }

    private TaskStatus handleMine(AccountContext context) {
        if (context.inventory().isFull()) {
            phase = Phase.BANKING;
            return TaskStatus.RUNNING;
        }

        MiningStrategy.Pickaxe pickaxe = MiningStrategy.findBestPickaxe(context, true);
        if (pickaxe == null) {
            phase = Phase.BANKING;
            return TaskStatus.RUNNING;
        }

        tryEquipPickaxe(context, pickaxe);

        if (Microbot.getClient().getLocalPlayer() != null) {
            boolean isMovingOrAnimating = Rs2Player.isAnimating() || Rs2Player.isMoving();
            if (isMovingOrAnimating) {
                return TaskStatus.RUNNING;
            }

            Rs2TileObjectModel rock = findNearestRock();
            if (rock == null) {
                if (!context.isNear(method.location, 15)) {
                    phase = Phase.WALK_TO_MINE;
                }
                return TaskStatus.RUNNING;
            }

            rock.click(method.action);
            sleep(300, 600);
        }

        return TaskStatus.RUNNING;
    }

    private TaskStatus handleBank(AccountContext context) {
        if (bankingTask == null) {
            MiningStrategy.Pickaxe equippedPickaxe = getEquippedUsablePickaxe(context);
            if (equippedPickaxe != null) {
                // Pickaxe already equipped - deposit everything in inventory (ores, gems)
                bankingTask = new BankingTask(BankingTask.Mode.DEPOSIT_ALL);
            } else {
                MiningStrategy.Pickaxe invPickaxe = getInventoryUsablePickaxe(context);
                if (invPickaxe != null) {
                    if (MiningStrategy.canWield(context, invPickaxe)) {
                        Rs2Inventory.wield(invPickaxe.itemName);
                        bankingTask = new BankingTask(BankingTask.Mode.DEPOSIT_ALL);
                    } else {
                        bankingTask = new BankingTask(BankingTask.Mode.DEPOSIT_ALL_EXCEPT, invPickaxe.itemName);
                    }
                } else {
                    MiningStrategy.Pickaxe bankPickaxe = MiningStrategy.findBestPickaxe(context, false);
                    if (bankPickaxe != null) {
                        bankingTask = new BankingTask(
                                BankingTask.Mode.DEPOSIT_ALL_AND_WITHDRAW,
                                null,
                                bankPickaxe.itemName,
                                1
                        );
                    } else {
                        return TaskStatus.REPLAN;
                    }
                }
            }
        }

        TaskStatus bankStatus = bankingTask.tick(context);

        if (bankStatus == TaskStatus.COMPLETE) {
            bankingTask = null;

            MiningStrategy.Pickaxe pickaxe = MiningStrategy.findBestPickaxe(context, true);
            if (pickaxe == null) {
                if (MiningStrategy.findBestPickaxe(context, false) == null) {
                    return TaskStatus.REPLAN;
                }
                phase = Phase.BANKING;
                return TaskStatus.RUNNING;
            }

            tryEquipPickaxe(context, pickaxe);
            phase = Phase.WALK_TO_MINE;
            return TaskStatus.RUNNING;
        }

        if (bankStatus == TaskStatus.FAILED || bankStatus == TaskStatus.REPLAN) {
            bankingTask = null;
            return bankStatus;
        }

        return TaskStatus.RUNNING;
    }

    private Rs2TileObjectModel findNearestRock() {
        return Microbot.getRs2TileObjectCache().query()
                .withIds(method.rockObjectIds)
                .within(20)
                .nearest();
    }

    private void tryEquipPickaxe(AccountContext context, MiningStrategy.Pickaxe pickaxe) {
        if (pickaxe == null) return;
        if (!context.equipment().hasItem(pickaxe.itemName)
                && context.inventory().hasItem(pickaxe.itemName)
                && MiningStrategy.canWield(context, pickaxe)) {
            Rs2Inventory.wield(pickaxe.itemName);
        }
    }

    private MiningStrategy.Pickaxe getEquippedUsablePickaxe(AccountContext context) {
        MiningStrategy.Pickaxe best = MiningStrategy.findBestPickaxe(context, true);
        if (best != null && context.equipment().hasItem(best.itemName)) {
            return best;
        }
        return null;
    }

    private MiningStrategy.Pickaxe getInventoryUsablePickaxe(AccountContext context) {
        MiningStrategy.Pickaxe best = MiningStrategy.findBestPickaxe(context, true);
        if (best != null && context.inventory().hasItem(best.itemName)) {
            return best;
        }
        return null;
    }

    @Override
    public boolean needsReplan(AccountContext context) {
        if (context.getRealLevel(Skill.MINING) < method.requiredLevel) {
            return true;
        }
        return MiningStrategy.findBestPickaxe(context, false) == null;
    }

    @Override
    public String describe() {
        return "Mining (" + method.name() + ") - " + phase;
    }
}
