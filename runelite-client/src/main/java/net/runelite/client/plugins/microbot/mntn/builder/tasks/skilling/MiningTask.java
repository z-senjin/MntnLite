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

    private void debugLog(AccountContext context, String message) {
        if (context.isDebugLogging()) {
            Microbot.log("[MntnBuilder][MiningTask][DEBUG] " + message);
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
            case WALK_TO_MINE:
                return handleWalk(context);
            case MINING:
                return handleMine(context);
            case BANKING:
                return handleBank(context);
            default:
                debugLog(context, "Unknown phase, returning RUNNING");
                return TaskStatus.RUNNING;
        }
    }

    private TaskStatus handleWalk(AccountContext context) {
        debugLog(context, "handleWalk: checking pickaxe");
        MiningStrategy.Pickaxe pickaxe = MiningStrategy.findBestPickaxe(context, true);
        if (pickaxe == null) {
            debugLog(context, "No pickaxe available, switching to BANKING");
            phase = Phase.BANKING;
            return TaskStatus.RUNNING;
        }

        tryEquipPickaxe(context, pickaxe);

        if (context.isNear(method.location, 12)) {
            debugLog(context, "Near mining location, switching to MINING");
            phase = Phase.MINING;
            return TaskStatus.RUNNING;
        }

        debugLog(context, "Walking to mining location: " + method.location);
        Rs2Walker.walkTo(method.location);
        return TaskStatus.RUNNING;
    }

    private TaskStatus handleMine(AccountContext context) {
        debugLog(context, "handleMine: inventoryFull=" + context.inventory().isFull());

        if (context.inventory().isFull()) {
            debugLog(context, "Inventory full, switching to BANKING");
            phase = Phase.BANKING;
            return TaskStatus.RUNNING;
        }

        MiningStrategy.Pickaxe pickaxe = MiningStrategy.findBestPickaxe(context, true);
        if (pickaxe == null) {
            debugLog(context, "No pickaxe available, switching to BANKING");
            phase = Phase.BANKING;
            return TaskStatus.RUNNING;
        }

        tryEquipPickaxe(context, pickaxe);

        if (Microbot.getClient().getLocalPlayer() != null) {
            boolean isMovingOrAnimating = Rs2Player.isAnimating() || Rs2Player.isMoving();
            if (isMovingOrAnimating) {
                debugLog(context, "Already animating/moving, waiting");
                return TaskStatus.RUNNING;
            }

            Rs2TileObjectModel rock = findNearestRock();
            if (rock == null) {
                debugLog(context, "No rock found nearby");
                if (!context.isNear(method.location, 15)) {
                    debugLog(context, "Not near mining location, switching to WALK_TO_MINE");
                    phase = Phase.WALK_TO_MINE;
                }
                return TaskStatus.RUNNING;
            }

            debugLog(context, "Clicking rock: " + rock.getWorldLocation() + " with action: " + method.action);
            rock.click(method.action);
            sleep(300, 600);
        }

        return TaskStatus.RUNNING;
    }

    private TaskStatus handleBank(AccountContext context) {
        debugLog(context, "handleBank: bankingTask=" + (bankingTask != null ? bankingTask.describe() : "null"));

        if (bankingTask == null) {
            MiningStrategy.Pickaxe equippedPickaxe = getEquippedUsablePickaxe(context);
            if (equippedPickaxe != null) {
                // Pickaxe already equipped - deposit everything in inventory (ores, gems)
                debugLog(context, "Pickaxe already equipped, creating DEPOSIT_ALL banking task");
                bankingTask = new BankingTask(BankingTask.Mode.DEPOSIT_ALL);
            } else {
                MiningStrategy.Pickaxe invPickaxe = getInventoryUsablePickaxe(context);
                if (invPickaxe != null) {
                    if (MiningStrategy.canWield(context, invPickaxe)) {
                        debugLog(context, "Pickaxe in inventory and can wield, wielding then DEPOSIT_ALL");
                        Rs2Inventory.wield(invPickaxe.itemName);
                        bankingTask = new BankingTask(BankingTask.Mode.DEPOSIT_ALL);
                    } else {
                        debugLog(context, "Pickaxe in inventory but cannot wield, DEPOSIT_ALL_EXCEPT " + invPickaxe.itemName);
                        bankingTask = new BankingTask(BankingTask.Mode.DEPOSIT_ALL_EXCEPT, invPickaxe.itemName);
                    }
                } else {
                    MiningStrategy.Pickaxe bankPickaxe = MiningStrategy.findBestPickaxe(context, false);
                    if (bankPickaxe != null) {
                        debugLog(context, "Pickaxe in bank, creating DEPOSIT_ALL_AND_WITHDRAW for " + bankPickaxe.itemName);
                        bankingTask = new BankingTask(
                                BankingTask.Mode.DEPOSIT_ALL_AND_WITHDRAW,
                                null,
                                bankPickaxe.itemName,
                                1
                        );
                    } else {
                        debugLog(context, "No pickaxe available anywhere, returning REPLAN");
                        return TaskStatus.REPLAN;
                    }
                }
            }
        }

        TaskStatus bankStatus = bankingTask.tick(context);

        if (bankStatus == TaskStatus.COMPLETE) {
            debugLog(context, "Banking complete");
            bankingTask = null;

            MiningStrategy.Pickaxe pickaxe = MiningStrategy.findBestPickaxe(context, true);
            if (pickaxe == null) {
                if (MiningStrategy.findBestPickaxe(context, false) == null) {
                    debugLog(context, "No pickaxe available after banking, returning REPLAN");
                    return TaskStatus.REPLAN;
                }
                debugLog(context, "Pickaxe not in inventory/equipped but in bank, staying in BANKING");
                phase = Phase.BANKING;
                return TaskStatus.RUNNING;
            }

            tryEquipPickaxe(context, pickaxe);
            debugLog(context, "Switching to WALK_TO_MINE");
            phase = Phase.WALK_TO_MINE;
            return TaskStatus.RUNNING;
        }

        if (bankStatus == TaskStatus.FAILED || bankStatus == TaskStatus.REPLAN) {
            debugLog(context, "Banking failed/replan: " + bankStatus);
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
        boolean levelCheck = context.getRealLevel(Skill.MINING) < method.requiredLevel;
        boolean pickaxeCheck = MiningStrategy.findBestPickaxe(context, false) == null;
        if (levelCheck || pickaxeCheck) {
            debugLog(context, "needsReplan: levelCheck=" + levelCheck + " (current=" + context.getRealLevel(Skill.MINING) + ", required=" + method.requiredLevel + "), pickaxeCheck=" + pickaxeCheck);
        }
        return levelCheck || pickaxeCheck;
    }

    @Override
    public String describe() {
        return "Mining (" + method.name() + ") - " + phase;
    }
}
