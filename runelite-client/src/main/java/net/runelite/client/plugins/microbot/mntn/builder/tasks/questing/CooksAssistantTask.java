package net.runelite.client.plugins.microbot.mntn.builder.tasks.questing;

import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.mntn.builder.core.AccountContext;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.Task;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.TaskStatus;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.banking.BankingTask;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.api.tileitem.models.Rs2TileItemModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.ArrayList;
import java.util.List;

import static net.runelite.client.plugins.microbot.util.Global.sleep;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntilTrue;

public class CooksAssistantTask implements Task {

    private static final String EGG = "Egg";
    private static final String MILK = "Bucket of milk";
    private static final String FLOUR = "Pot of flour";
    private static final String BUCKET = "Bucket";
    private static final String POT = "Pot";
    private static final String GRAIN = "Grain";

    private static final WorldPoint LUMBRIDGE_KITCHEN = new WorldPoint(3208, 3213, 0);
    private static final WorldPoint CHICKEN_COOP = new WorldPoint(3230, 3297, 0);
    private static final WorldPoint DAIRY_COW = new WorldPoint(3254, 3271, 0);
    private static final WorldPoint LUMBRIDGE_CELLAR = new WorldPoint(3216, 9625, 0);
    private static final WorldPoint WHEAT_FIELD = new WorldPoint(3162, 3295, 0);
    private static final WorldPoint MILL_GROUND = new WorldPoint(3166, 3307, 0);
    private static final WorldPoint MILL_TOP = new WorldPoint(3166, 3307, 2);

    public enum Phase {
        CHECK_STATUS,
        BANKING,
        GATHER_EGG,
        GATHER_BUCKET,
        GATHER_MILK,
        GATHER_POT,
        GATHER_WHEAT,
        MILL_FLOUR,
        TALK_TO_COOK
    }

    private Phase phase = Phase.CHECK_STATUS;
    private boolean hasBanked = false;
    private BankingTask bankingTask;

    private void debugLog(AccountContext context, String message) {
        if (context.isDebugLogging()) {
            Microbot.log("[MntnBuilder][CooksAssistantTask][DEBUG] " + message);
        }
    }

    @Override
    public TaskStatus tick(AccountContext context) {
        debugLog(context, "tick: phase=" + phase + ", questState=" + context.getQuestState(Quest.COOKS_ASSISTANT));

        if (!context.isLoggedIn()) {
            debugLog(context, "Not logged in, returning BLOCKED");
            return TaskStatus.BLOCKED;
        }

        if (context.getQuestState(Quest.COOKS_ASSISTANT) == QuestState.FINISHED) {
            debugLog(context, "Quest finished, returning COMPLETE");
            return TaskStatus.COMPLETE;
        }

        switch (phase) {
            case CHECK_STATUS:
                return handleCheckStatus(context);
            case BANKING:
                return handleBanking(context);
            case GATHER_EGG:
                return handleGatherEgg(context);
            case GATHER_BUCKET:
                return handleGatherBucket(context);
            case GATHER_MILK:
                return handleGatherMilk(context);
            case GATHER_POT:
                return handleGatherPot(context);
            case GATHER_WHEAT:
                return handleGatherWheat(context);
            case MILL_FLOUR:
                return handleMillFlour(context);
            case TALK_TO_COOK:
                return handleTalkToCook(context);
            default:
                debugLog(context, "Unknown phase, returning RUNNING");
                return TaskStatus.RUNNING;
        }
    }

    private TaskStatus handleCheckStatus(AccountContext context) {
        debugLog(context, "handleCheckStatus: egg=" + context.inventory().hasItem(EGG)
                + ", milk=" + context.inventory().hasItem(MILK)
                + ", flour=" + context.inventory().hasItem(FLOUR)
                + ", bucket=" + context.inventory().hasItem(BUCKET)
                + ", pot=" + context.inventory().hasItem(POT)
                + ", grain=" + context.inventory().hasItem(GRAIN)
                + ", hasBanked=" + hasBanked);

        if (hasAllQuestItems(context)) {
            debugLog(context, "All quest items collected, switching to TALK_TO_COOK");
            phase = Phase.TALK_TO_COOK;
            return TaskStatus.RUNNING;
        }

        // Check if bank has useful items if we haven't banked yet
        if (!hasBanked) {
            boolean bankHasAnything = context.bank().hasItem(EGG)
                    || context.bank().hasItem(MILK)
                    || context.bank().hasItem(FLOUR)
                    || context.bank().hasItem(BUCKET)
                    || context.bank().hasItem(POT);

            if (bankHasAnything) {
                debugLog(context, "Bank has quest items, switching to BANKING");
                phase = Phase.BANKING;
                return TaskStatus.RUNNING;
            }
            hasBanked = true;
            debugLog(context, "Bank has no quest items, hasBanked=true");
        }

        if (!context.inventory().hasItem(EGG)) {
            debugLog(context, "Missing egg, switching to GATHER_EGG");
            phase = Phase.GATHER_EGG;
            return TaskStatus.RUNNING;
        }

        if (!context.inventory().hasItem(MILK)) {
            if (!context.inventory().hasItem(BUCKET)) {
                debugLog(context, "Missing milk and bucket, switching to GATHER_BUCKET");
                phase = Phase.GATHER_BUCKET;
            } else {
                debugLog(context, "Missing milk, has bucket, switching to GATHER_MILK");
                phase = Phase.GATHER_MILK;
            }
            return TaskStatus.RUNNING;
        }

        if (!context.inventory().hasItem(FLOUR)) {
            if (!context.inventory().hasItem(POT)) {
                debugLog(context, "Missing flour and pot, switching to GATHER_POT");
                phase = Phase.GATHER_POT;
            } else if (!context.inventory().hasItem(GRAIN)) {
                debugLog(context, "Missing flour, has pot, missing grain, switching to GATHER_WHEAT");
                phase = Phase.GATHER_WHEAT;
            } else {
                debugLog(context, "Missing flour, has pot and grain, switching to MILL_FLOUR");
                phase = Phase.MILL_FLOUR;
            }
            return TaskStatus.RUNNING;
        }

        debugLog(context, "All items collected, switching to TALK_TO_COOK");
        phase = Phase.TALK_TO_COOK;
        return TaskStatus.RUNNING;
    }

    private TaskStatus handleBanking(AccountContext context) {
        debugLog(context, "handleBanking: bankingTask=" + (bankingTask != null ? bankingTask.describe() : "null"));

        if (bankingTask == null) {
            List<BankingTask.ItemWithdrawal> withdrawals = new ArrayList<>();
            if (!context.inventory().hasItem(EGG) && context.bank().hasItem(EGG)) {
                debugLog(context, "Adding egg withdrawal");
                withdrawals.add(new BankingTask.ItemWithdrawal(EGG, 1));
            }
            if (!context.inventory().hasItem(MILK) && context.bank().hasItem(MILK)) {
                debugLog(context, "Adding milk withdrawal");
                withdrawals.add(new BankingTask.ItemWithdrawal(MILK, 1));
            }
            if (!context.inventory().hasItem(FLOUR) && context.bank().hasItem(FLOUR)) {
                debugLog(context, "Adding flour withdrawal");
                withdrawals.add(new BankingTask.ItemWithdrawal(FLOUR, 1));
            }
            if (!context.inventory().hasItem(MILK) && !context.inventory().hasItem(BUCKET) && context.bank().hasItem(BUCKET)) {
                debugLog(context, "Adding bucket withdrawal");
                withdrawals.add(new BankingTask.ItemWithdrawal(BUCKET, 1));
            }
            if (!context.inventory().hasItem(FLOUR) && !context.inventory().hasItem(POT) && context.bank().hasItem(POT)) {
                debugLog(context, "Adding pot withdrawal");
                withdrawals.add(new BankingTask.ItemWithdrawal(POT, 1));
            }

            debugLog(context, "Creating DEPOSIT_ALL_AND_WITHDRAW banking task with " + withdrawals.size() + " withdrawals");
            bankingTask = new BankingTask(
                    BankingTask.Mode.DEPOSIT_ALL_AND_WITHDRAW,
                    new String[]{EGG, MILK, FLOUR, BUCKET, POT, GRAIN},
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

    private TaskStatus handleGatherEgg(AccountContext context) {
        debugLog(context, "handleGatherEgg: hasEgg=" + context.inventory().hasItem(EGG) + ", nearCoop=" + context.isNear(CHICKEN_COOP, 8));
        if (context.inventory().hasItem(EGG)) {
            debugLog(context, "Has egg, switching to CHECK_STATUS");
            phase = Phase.CHECK_STATUS;
            return TaskStatus.RUNNING;
        }

        if (!context.isNear(CHICKEN_COOP, 8)) {
            debugLog(context, "Walking to chicken coop: " + CHICKEN_COOP);
            Rs2Walker.walkTo(CHICKEN_COOP);
            return TaskStatus.RUNNING;
        }

        Rs2TileItemModel egg = Microbot.getRs2TileItemCache().query()
                .withName(EGG)
                .within(15)
                .nearest();
        if (egg != null) {
            debugLog(context, "Picking up egg at: " + egg.getWorldLocation());
            egg.pickup();
        }
        sleepUntilTrue(() -> context.inventory().hasItem(EGG), 300, 4000);
        if (context.inventory().hasItem(EGG)) {
            debugLog(context, "Got egg, switching to CHECK_STATUS");
            phase = Phase.CHECK_STATUS;
        }
        return TaskStatus.RUNNING;
    }

    private TaskStatus handleGatherBucket(AccountContext context) {
        debugLog(context, "handleGatherBucket: hasBucket=" + context.inventory().hasItem(BUCKET) + ", hasMilk=" + context.inventory().hasItem(MILK) + ", nearCellar=" + context.isNear(LUMBRIDGE_CELLAR, 10));
        if (context.inventory().hasItem(BUCKET) || context.inventory().hasItem(MILK)) {
            debugLog(context, "Has bucket or milk, switching to CHECK_STATUS");
            phase = Phase.CHECK_STATUS;
            return TaskStatus.RUNNING;
        }

        if (!context.isNear(LUMBRIDGE_CELLAR, 10)) {
            debugLog(context, "Walking to Lumbridge cellar: " + LUMBRIDGE_CELLAR);
            Rs2Walker.walkTo(LUMBRIDGE_CELLAR);
            return TaskStatus.RUNNING;
        }

        Rs2TileItemModel bucket = Microbot.getRs2TileItemCache().query()
                .withName(BUCKET)
                .within(15)
                .nearest();
        if (bucket != null) {
            debugLog(context, "Picking up bucket at: " + bucket.getWorldLocation());
            bucket.pickup();
        }
        sleepUntilTrue(() -> context.inventory().hasItem(BUCKET), 300, 4000);
        if (context.inventory().hasItem(BUCKET)) {
            debugLog(context, "Got bucket, switching to CHECK_STATUS");
            phase = Phase.CHECK_STATUS;
        }
        return TaskStatus.RUNNING;
    }

    private TaskStatus handleGatherMilk(AccountContext context) {
        debugLog(context, "handleGatherMilk: hasMilk=" + context.inventory().hasItem(MILK) + ", hasBucket=" + context.inventory().hasItem(BUCKET) + ", nearCow=" + context.isNear(DAIRY_COW, 10));
        if (context.inventory().hasItem(MILK)) {
            debugLog(context, "Has milk, switching to CHECK_STATUS");
            phase = Phase.CHECK_STATUS;
            return TaskStatus.RUNNING;
        }

        if (!context.inventory().hasItem(BUCKET)) {
            debugLog(context, "No bucket, switching to GATHER_BUCKET");
            phase = Phase.GATHER_BUCKET;
            return TaskStatus.RUNNING;
        }

        if (!context.isNear(DAIRY_COW, 10)) {
            debugLog(context, "Walking to dairy cow: " + DAIRY_COW);
            Rs2Walker.walkTo(DAIRY_COW);
            return TaskStatus.RUNNING;
        }

        if (Rs2Player.isAnimating() || Rs2Player.isMoving()) {
            debugLog(context, "Already animating/moving, waiting");
            return TaskStatus.RUNNING;
        }

        Rs2TileObjectModel cow = Microbot.getRs2TileObjectCache().query()
                .withNames("Dairy cow")
                .within(10)
                .nearest();

        if (cow != null) {
            debugLog(context, "Milking cow at: " + cow.getWorldLocation());
            cow.click("Milk");
            sleepUntilTrue(() -> context.inventory().hasItem(MILK), 300, 6000);
            if (context.inventory().hasItem(MILK)) {
                debugLog(context, "Got milk, switching to CHECK_STATUS");
                phase = Phase.CHECK_STATUS;
            }
        }
        return TaskStatus.RUNNING;
    }

    private TaskStatus handleGatherPot(AccountContext context) {
        debugLog(context, "handleGatherPot: hasPot=" + context.inventory().hasItem(POT) + ", hasFlour=" + context.inventory().hasItem(FLOUR) + ", nearKitchen=" + context.isNear(LUMBRIDGE_KITCHEN, 6));
        if (context.inventory().hasItem(POT) || context.inventory().hasItem(FLOUR)) {
            debugLog(context, "Has pot or flour, switching to CHECK_STATUS");
            phase = Phase.CHECK_STATUS;
            return TaskStatus.RUNNING;
        }

        if (!context.isNear(LUMBRIDGE_KITCHEN, 6)) {
            debugLog(context, "Walking to Lumbridge kitchen: " + LUMBRIDGE_KITCHEN);
            Rs2Walker.walkTo(LUMBRIDGE_KITCHEN);
            return TaskStatus.RUNNING;
        }

        Rs2TileItemModel pot = Microbot.getRs2TileItemCache().query()
                .withName(POT)
                .within(10)
                .nearest();
        if (pot != null) {
            debugLog(context, "Picking up pot at: " + pot.getWorldLocation());
            pot.pickup();
        }
        sleepUntilTrue(() -> context.inventory().hasItem(POT), 300, 4000);
        if (context.inventory().hasItem(POT)) {
            debugLog(context, "Got pot, switching to CHECK_STATUS");
            phase = Phase.CHECK_STATUS;
        }
        return TaskStatus.RUNNING;
    }

    private TaskStatus handleGatherWheat(AccountContext context) {
        debugLog(context, "handleGatherWheat: hasGrain=" + context.inventory().hasItem(GRAIN) + ", hasFlour=" + context.inventory().hasItem(FLOUR) + ", nearField=" + context.isNear(WHEAT_FIELD, 10));
        if (context.inventory().hasItem(GRAIN) || context.inventory().hasItem(FLOUR)) {
            debugLog(context, "Has grain or flour, switching to CHECK_STATUS");
            phase = Phase.CHECK_STATUS;
            return TaskStatus.RUNNING;
        }

        if (!context.isNear(WHEAT_FIELD, 10)) {
            debugLog(context, "Walking to wheat field: " + WHEAT_FIELD);
            Rs2Walker.walkTo(WHEAT_FIELD);
            return TaskStatus.RUNNING;
        }

        if (Rs2Player.isAnimating() || Rs2Player.isMoving()) {
            debugLog(context, "Already animating/moving, waiting");
            return TaskStatus.RUNNING;
        }

        Rs2TileObjectModel wheat = Microbot.getRs2TileObjectCache().query()
                .withNames("Wheat")
                .within(10)
                .nearest();

        if (wheat != null) {
            debugLog(context, "Picking wheat at: " + wheat.getWorldLocation());
            wheat.click("Pick");
            sleepUntilTrue(() -> context.inventory().hasItem(GRAIN), 300, 5000);
            if (context.inventory().hasItem(GRAIN)) {
                debugLog(context, "Got grain, switching to CHECK_STATUS");
                phase = Phase.CHECK_STATUS;
            }
        }
        return TaskStatus.RUNNING;
    }

    private TaskStatus handleMillFlour(AccountContext context) {
        debugLog(context, "handleMillFlour: hasFlour=" + context.inventory().hasItem(FLOUR) + ", hasGrain=" + context.inventory().hasItem(GRAIN) + ", plane=" + Microbot.getClient().getPlane());
        if (context.inventory().hasItem(FLOUR)) {
            debugLog(context, "Has flour, switching to CHECK_STATUS");
            phase = Phase.CHECK_STATUS;
            return TaskStatus.RUNNING;
        }

        // If we have Grain, head to the top floor to put grain in the hopper
        if (context.inventory().hasItem(GRAIN)) {
            int plane = Microbot.getClient().getPlane();
            if (plane != 2) {
                debugLog(context, "Walking to mill top floor: " + MILL_TOP);
                Rs2Walker.walkTo(MILL_TOP);
                return TaskStatus.RUNNING;
            }

            Rs2TileObjectModel hopper = Microbot.getRs2TileObjectCache().query()
                    .withNames("Hopper")
                    .within(10)
                    .nearest();

            if (hopper != null && context.inventory().hasItem(GRAIN)) {
                debugLog(context, "Filling hopper with grain");
                hopper.click("Fill");
                sleepUntilTrue(() -> !context.inventory().hasItem(GRAIN), 300, 4000);
            }

            Rs2TileObjectModel controls = Microbot.getRs2TileObjectCache().query()
                    .withNames("Hopper controls")
                    .within(10)
                    .nearest();

            if (controls != null) {
                controls.click("Operate");
                sleep(1200, 1800);
            }

            // Descend back down to ground floor
            Rs2Walker.walkTo(MILL_GROUND);
            return TaskStatus.RUNNING;
        }

        // Ground floor: collect flour into pot
        int plane = Microbot.getClient().getPlane();
        if (plane != 0) {
            Rs2Walker.walkTo(MILL_GROUND);
            return TaskStatus.RUNNING;
        }

        Rs2TileObjectModel bin = Microbot.getRs2TileObjectCache().query()
                .withNames("Flour bin")
                .within(10)
                .nearest();

        if (bin != null) {
            bin.click("Empty");
            sleepUntilTrue(() -> context.inventory().hasItem(FLOUR), 300, 5000);
            if (context.inventory().hasItem(FLOUR)) {
                phase = Phase.CHECK_STATUS;
            }
        }

        return TaskStatus.RUNNING;
    }

    private TaskStatus handleTalkToCook(AccountContext context) {
        if (!context.isNear(LUMBRIDGE_KITCHEN, 6)) {
            Rs2Walker.walkTo(LUMBRIDGE_KITCHEN);
            return TaskStatus.RUNNING;
        }

        if (Rs2Dialogue.isInDialogue()) {
            if (Rs2Dialogue.hasSelectAnOption()) {
                Rs2Dialogue.clickOption("What's wrong?", "I'm always happy to help a cook in need.", "Yes.");
            }
            if (Rs2Dialogue.hasContinue()) {
                Rs2Dialogue.clickContinue();
            }
            sleep(400, 800);

            if (context.getQuestState(Quest.COOKS_ASSISTANT) == QuestState.FINISHED) {
                return TaskStatus.COMPLETE;
            }
            return TaskStatus.RUNNING;
        }

        if (context.getQuestState(Quest.COOKS_ASSISTANT) == QuestState.FINISHED) {
            return TaskStatus.COMPLETE;
        }

        Rs2NpcModel cook = Microbot.getRs2NpcCache().query()
                .withNames("Cook")
                .within(10)
                .nearest();

        if (cook != null) {
            cook.click("Talk-to");
            sleep(600, 1200);
        }

        return TaskStatus.RUNNING;
    }

    private boolean hasAllQuestItems(AccountContext context) {
        return context.inventory().hasItem(EGG)
                && context.inventory().hasItem(MILK)
                && context.inventory().hasItem(FLOUR);
    }

    @Override
    public boolean needsReplan(AccountContext context) {
        return context.getQuestState(Quest.COOKS_ASSISTANT) == QuestState.FINISHED;
    }

    @Override
    public String describe() {
        return "Cook's Assistant - " + phase;
    }
}
