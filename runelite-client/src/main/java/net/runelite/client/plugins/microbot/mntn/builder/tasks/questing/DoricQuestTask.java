package net.runelite.client.plugins.microbot.mntn.builder.tasks.questing;

import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.mntn.builder.activities.mining.MiningStrategy;
import net.runelite.client.plugins.microbot.mntn.builder.core.AccountContext;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.Task;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.TaskStatus;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.banking.BankingTask;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.ArrayList;
import java.util.List;

import static net.runelite.client.plugins.microbot.util.Global.sleep;

/**
 * Doric's Quest task.
 *
 * Items needed: 6 Clay, 4 Copper ore, 2 Iron ore.
 * NPC: Doric, north of Falador near the Taverley gate.
 *
 * Uses hybrid item sourcing (bank first, mine what's missing)
 * and MiningStrategy.findBestPickaxe() for pickaxe management.
 */
public class DoricQuestTask implements Task {

    private static final String CLAY = "Clay";
    private static final String COPPER_ORE = "Copper ore";
    private static final String IRON_ORE = "Iron ore";

    private static final int CLAY_NEEDED = 6;
    private static final int COPPER_NEEDED = 4;
    private static final int IRON_NEEDED = 2;

    /*
     * Clay rocks - Varrock West Mine area.
     */
    private static final WorldPoint CLAY_MINE = new WorldPoint(3180, 3372, 0);
    private static final int[] CLAY_ROCK_IDS = {11362, 11363};

    /*
     * Copper and Iron - Varrock East Mine (same coordinates as MiningStrategy.Method).
     */
    private static final WorldPoint COPPER_MINE = new WorldPoint(3288, 3363, 0);
    private static final int[] COPPER_ROCK_IDS = {10943, 11161};

    private static final WorldPoint IRON_MINE = new WorldPoint(3286, 3369, 0);
    private static final int[] IRON_ROCK_IDS = {11364, 11365};

    /*
     * Doric's hut, north of Falador.
     */
    private static final WorldPoint DORIC_LOCATION = new WorldPoint(2952, 3451, 0);

    public enum Phase {
        CHECK_STATUS,
        BANKING,
        ENSURE_PICKAXE,
        GATHER_CLAY,
        GATHER_COPPER,
        GATHER_IRON,
        TALK_TO_DORIC
    }

    private Phase phase = Phase.CHECK_STATUS;
    private boolean hasBanked = false;
    private BankingTask bankingTask;

    private void debugLog(AccountContext context, String message) {
        if (context.isDebugLogging()) {
            Microbot.log("[MntnBuilder][DoricQuestTask][DEBUG] " + message);
        }
    }

    @Override
    public TaskStatus tick(AccountContext context) {
        debugLog(context, "tick: phase=" + phase + ", questState=" + context.getQuestState(Quest.DORICS_QUEST));

        if (!context.isLoggedIn()) {
            debugLog(context, "Not logged in, returning BLOCKED");
            return TaskStatus.BLOCKED;
        }

        if (context.getQuestState(Quest.DORICS_QUEST) == QuestState.FINISHED) {
            debugLog(context, "Quest finished, returning COMPLETE");
            return TaskStatus.COMPLETE;
        }

        switch (phase) {
            case CHECK_STATUS:
                return handleCheckStatus(context);
            case BANKING:
                return handleBanking(context);
            case ENSURE_PICKAXE:
                return handleEnsurePickaxe(context);
            case GATHER_CLAY:
                return handleGatherClay(context);
            case GATHER_COPPER:
                return handleGatherCopper(context);
            case GATHER_IRON:
                return handleGatherIron(context);
            case TALK_TO_DORIC:
                return handleTalkToDoric(context);
            default:
                debugLog(context, "Unknown phase, returning RUNNING");
                return TaskStatus.RUNNING;
        }
    }

    // ── Phase handlers ──────────────────────────────────────────────────

    private TaskStatus handleCheckStatus(AccountContext context) {
        debugLog(context, "handleCheckStatus: clay=" + context.inventory().getCount(CLAY) + "/" + CLAY_NEEDED
                + ", copper=" + context.inventory().getCount(COPPER_ORE) + "/" + COPPER_NEEDED
                + ", iron=" + context.inventory().getCount(IRON_ORE) + "/" + IRON_NEEDED
                + ", hasBanked=" + hasBanked + ", hasPickaxe=" + hasPickaxe(context));

        if (hasAllQuestItems(context)) {
            debugLog(context, "All quest items collected, switching to TALK_TO_DORIC");
            phase = Phase.TALK_TO_DORIC;
            return TaskStatus.RUNNING;
        }

        // Hybrid sourcing: check bank first if we haven't banked yet
        if (!hasBanked) {
            boolean bankHasAnything = context.bank().hasItem(CLAY)
                    || context.bank().hasItem(COPPER_ORE)
                    || context.bank().hasItem(IRON_ORE);

            if (bankHasAnything) {
                debugLog(context, "Bank has quest items, switching to BANKING");
                phase = Phase.BANKING;
                return TaskStatus.RUNNING;
            }
            hasBanked = true;
            debugLog(context, "Bank has no quest items, hasBanked=true");
        }

        // Make sure we have a pickaxe before mining
        if (!hasPickaxe(context)) {
            debugLog(context, "No pickaxe, switching to ENSURE_PICKAXE");
            phase = Phase.ENSURE_PICKAXE;
            return TaskStatus.RUNNING;
        }

        // Mine what's missing in order: clay → copper → iron
        if (context.inventory().getCount(CLAY) < CLAY_NEEDED) {
            debugLog(context, "Missing clay, switching to GATHER_CLAY");
            phase = Phase.GATHER_CLAY;
            return TaskStatus.RUNNING;
        }

        if (context.inventory().getCount(COPPER_ORE) < COPPER_NEEDED) {
            debugLog(context, "Missing copper, switching to GATHER_COPPER");
            phase = Phase.GATHER_COPPER;
            return TaskStatus.RUNNING;
        }

        if (context.inventory().getCount(IRON_ORE) < IRON_NEEDED) {
            debugLog(context, "Missing iron, switching to GATHER_IRON");
            phase = Phase.GATHER_IRON;
            return TaskStatus.RUNNING;
        }

        debugLog(context, "All items collected, switching to TALK_TO_DORIC");
        phase = Phase.TALK_TO_DORIC;
        return TaskStatus.RUNNING;
    }

    private TaskStatus handleBanking(AccountContext context) {
        debugLog(context, "handleBanking: bankingTask=" + (bankingTask != null ? bankingTask.describe() : "null"));

        if (bankingTask == null) {
            List<BankingTask.ItemWithdrawal> withdrawals = new ArrayList<>();

            int clayNeeded = CLAY_NEEDED - context.inventory().getCount(CLAY);
            if (clayNeeded > 0 && context.bank().hasItem(CLAY)) {
                int withdrawAmount = Math.min(clayNeeded, context.bank().getCount(CLAY));
                debugLog(context, "Adding clay withdrawal: " + withdrawAmount);
                withdrawals.add(new BankingTask.ItemWithdrawal(
                        CLAY, withdrawAmount));
            }

            int copperNeeded = COPPER_NEEDED - context.inventory().getCount(COPPER_ORE);
            if (copperNeeded > 0 && context.bank().hasItem(COPPER_ORE)) {
                int withdrawAmount = Math.min(copperNeeded, context.bank().getCount(COPPER_ORE));
                debugLog(context, "Adding copper withdrawal: " + withdrawAmount);
                withdrawals.add(new BankingTask.ItemWithdrawal(
                        COPPER_ORE, withdrawAmount));
            }

            int ironNeeded = IRON_NEEDED - context.inventory().getCount(IRON_ORE);
            if (ironNeeded > 0 && context.bank().hasItem(IRON_ORE)) {
                int withdrawAmount = Math.min(ironNeeded, context.bank().getCount(IRON_ORE));
                debugLog(context, "Adding iron withdrawal: " + withdrawAmount);
                withdrawals.add(new BankingTask.ItemWithdrawal(
                        IRON_ORE, withdrawAmount));
            }

            debugLog(context, "Creating DEPOSIT_ALL_AND_WITHDRAW banking task with " + withdrawals.size() + " withdrawals");
            bankingTask = new BankingTask(
                    BankingTask.Mode.DEPOSIT_ALL_AND_WITHDRAW,
                    new String[]{CLAY, COPPER_ORE, IRON_ORE},
                    withdrawals.toArray(new BankingTask.ItemWithdrawal[0])
            );
        }

        TaskStatus status = bankingTask.tick(context);
        if (status == TaskStatus.COMPLETE) {
            debugLog(context, "Banking complete");
            bankingTask = null;
            hasBanked = true;
            phase = Phase.CHECK_STATUS;
            return TaskStatus.RUNNING;
        }
        if (status == TaskStatus.FAILED || status == TaskStatus.REPLAN) {
            debugLog(context, "Banking failed/replan: " + status);
            bankingTask = null;
            hasBanked = true;
            phase = Phase.CHECK_STATUS;
            return TaskStatus.RUNNING;
        }

        return TaskStatus.RUNNING;
    }

    private TaskStatus handleEnsurePickaxe(AccountContext context) {
        debugLog(context, "handleEnsurePickaxe: hasPickaxe=" + hasPickaxe(context));
        if (hasPickaxe(context)) {
            tryEquipPickaxe(context);
            debugLog(context, "Pickaxe found, switching to CHECK_STATUS");
            phase = Phase.CHECK_STATUS;
            return TaskStatus.RUNNING;
        }

        // Check if there's one in the bank
        MiningStrategy.Pickaxe bankPickaxe = MiningStrategy.findBestPickaxe(context, false);
        if (bankPickaxe == null) {
            // No pickaxe anywhere - can't mine, replan
            debugLog(context, "No pickaxe anywhere, returning REPLAN");
            return TaskStatus.REPLAN;
        }

        if (bankingTask == null) {
            debugLog(context, "Creating DEPOSIT_ALL_AND_WITHDRAW banking task for pickaxe: " + bankPickaxe.itemName);
            bankingTask = new BankingTask(
                    BankingTask.Mode.DEPOSIT_ALL_AND_WITHDRAW,
                    new String[]{CLAY, COPPER_ORE, IRON_ORE},
                    new BankingTask.ItemWithdrawal(bankPickaxe.itemName, 1)
            );
        }

        TaskStatus status = bankingTask.tick(context);
        if (status == TaskStatus.COMPLETE) {
            debugLog(context, "Pickaxe banking complete");
            bankingTask = null;
            tryEquipPickaxe(context);
            phase = Phase.CHECK_STATUS;
            return TaskStatus.RUNNING;
        }
        if (status == TaskStatus.FAILED || status == TaskStatus.REPLAN) {
            debugLog(context, "Pickaxe banking failed/replan: " + status);
            bankingTask = null;
            return TaskStatus.REPLAN;
        }

        return TaskStatus.RUNNING;
    }

    private TaskStatus handleGatherClay(AccountContext context) {
        debugLog(context, "handleGatherClay: count=" + context.inventory().getCount(CLAY) + "/" + CLAY_NEEDED);
        if (context.inventory().getCount(CLAY) >= CLAY_NEEDED) {
            debugLog(context, "Enough clay, switching to CHECK_STATUS");
            phase = Phase.CHECK_STATUS;
            return TaskStatus.RUNNING;
        }
        return mineOre(context, CLAY_MINE, CLAY_ROCK_IDS);
    }

    private TaskStatus handleGatherCopper(AccountContext context) {
        debugLog(context, "handleGatherCopper: count=" + context.inventory().getCount(COPPER_ORE) + "/" + COPPER_NEEDED);
        if (context.inventory().getCount(COPPER_ORE) >= COPPER_NEEDED) {
            debugLog(context, "Enough copper, switching to CHECK_STATUS");
            phase = Phase.CHECK_STATUS;
            return TaskStatus.RUNNING;
        }
        return mineOre(context, COPPER_MINE, COPPER_ROCK_IDS);
    }

    private TaskStatus handleGatherIron(AccountContext context) {
        debugLog(context, "handleGatherIron: count=" + context.inventory().getCount(IRON_ORE) + "/" + IRON_NEEDED);
        if (context.inventory().getCount(IRON_ORE) >= IRON_NEEDED) {
            debugLog(context, "Enough iron, switching to CHECK_STATUS");
            phase = Phase.CHECK_STATUS;
            return TaskStatus.RUNNING;
        }
        return mineOre(context, IRON_MINE, IRON_ROCK_IDS);
    }

    /**
     * Generic mining loop. Walks to the mine and mines rocks until the gather
     * phase's completion check triggers a phase change back to CHECK_STATUS.
     */
    private TaskStatus mineOre(AccountContext context, WorldPoint mineLocation, int[] rockIds) {
        debugLog(context, "mineOre: nearMine=" + context.isNear(mineLocation, 12) + ", animating=" + Rs2Player.isAnimating() + ", moving=" + Rs2Player.isMoving());

        if (!context.isNear(mineLocation, 12)) {
            debugLog(context, "Walking to mine: " + mineLocation);
            Rs2Walker.walkTo(mineLocation);
            return TaskStatus.RUNNING;
        }

        if (Rs2Player.isAnimating() || Rs2Player.isMoving()) {
            debugLog(context, "Already animating/moving, waiting");
            return TaskStatus.RUNNING;
        }

        Rs2TileObjectModel rock = Microbot.getRs2TileObjectCache().query()
                .withIds(rockIds)
                .within(20)
                .nearest();

        if (rock != null) {
            debugLog(context, "Clicking rock: " + rock.getWorldLocation());
            rock.click("Mine");
            sleep(300, 600);
        } else {
            debugLog(context, "No rock found nearby");
        }

        return TaskStatus.RUNNING;
    }

    private TaskStatus handleTalkToDoric(AccountContext context) {
        debugLog(context, "handleTalkToDoric: nearDoric=" + context.isNear(DORIC_LOCATION, 8) + ", inDialogue=" + Rs2Dialogue.isInDialogue());

        if (!context.isNear(DORIC_LOCATION, 8)) {
            debugLog(context, "Walking to Doric: " + DORIC_LOCATION);
            Rs2Walker.walkTo(DORIC_LOCATION);
            return TaskStatus.RUNNING;
        }

        if (Rs2Dialogue.isInDialogue()) {
            if (Rs2Dialogue.hasSelectAnOption()) {
                debugLog(context, "Selecting dialogue option");
                Rs2Dialogue.clickOption(
                        "I wanted to use your anvils.",
                        "Sure, I can do that.",
                        "Yes."
                );
            }
            if (Rs2Dialogue.hasContinue()) {
                debugLog(context, "Clicking continue");
                Rs2Dialogue.clickContinue();
            }
            sleep(400, 800);

            if (context.getQuestState(Quest.DORICS_QUEST) == QuestState.FINISHED) {
                debugLog(context, "Quest finished!");
                return TaskStatus.COMPLETE;
            }
            return TaskStatus.RUNNING;
        }

        if (context.getQuestState(Quest.DORICS_QUEST) == QuestState.FINISHED) {
            debugLog(context, "Quest finished!");
            return TaskStatus.COMPLETE;
        }

        Rs2NpcModel doric = Microbot.getRs2NpcCache().query()
                .withNames("Doric")
                .within(10)
                .nearest();

        if (doric != null) {
            debugLog(context, "Talking to Doric");
            doric.click("Talk-to");
            sleep(600, 1200);
        } else {
            debugLog(context, "Doric not found nearby");
        }

        return TaskStatus.RUNNING;
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private boolean hasAllQuestItems(AccountContext context) {
        return context.inventory().getCount(CLAY) >= CLAY_NEEDED
                && context.inventory().getCount(COPPER_ORE) >= COPPER_NEEDED
                && context.inventory().getCount(IRON_ORE) >= IRON_NEEDED;
    }

    private boolean hasPickaxe(AccountContext context) {
        return MiningStrategy.findBestPickaxe(context, true) != null;
    }

    private void tryEquipPickaxe(AccountContext context) {
        MiningStrategy.Pickaxe pickaxe = MiningStrategy.findBestPickaxe(context, true);
        if (pickaxe != null
                && !context.equipment().hasItem(pickaxe.itemName)
                && context.inventory().hasItem(pickaxe.itemName)
                && MiningStrategy.canWield(context, pickaxe)) {
            Rs2Inventory.wield(pickaxe.itemName);
        }
    }

    @Override
    public boolean needsReplan(AccountContext context) {
        return context.getQuestState(Quest.DORICS_QUEST) == QuestState.FINISHED;
    }

    @Override
    public String describe() {
        return "Doric's Quest - " + phase;
    }
}
