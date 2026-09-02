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

    @Override
    public TaskStatus tick(AccountContext context) {
        if (!context.isLoggedIn()) {
            return TaskStatus.BLOCKED;
        }

        if (context.getQuestState(Quest.COOKS_ASSISTANT) == QuestState.FINISHED) {
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
                return TaskStatus.RUNNING;
        }
    }

    private TaskStatus handleCheckStatus(AccountContext context) {
        if (hasAllQuestItems(context)) {
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
                phase = Phase.BANKING;
                return TaskStatus.RUNNING;
            }
            hasBanked = true;
        }

        if (!context.inventory().hasItem(EGG)) {
            phase = Phase.GATHER_EGG;
            return TaskStatus.RUNNING;
        }

        if (!context.inventory().hasItem(MILK)) {
            if (!context.inventory().hasItem(BUCKET)) {
                phase = Phase.GATHER_BUCKET;
            } else {
                phase = Phase.GATHER_MILK;
            }
            return TaskStatus.RUNNING;
        }

        if (!context.inventory().hasItem(FLOUR)) {
            if (!context.inventory().hasItem(POT)) {
                phase = Phase.GATHER_POT;
            } else if (!context.inventory().hasItem(GRAIN)) {
                phase = Phase.GATHER_WHEAT;
            } else {
                phase = Phase.MILL_FLOUR;
            }
            return TaskStatus.RUNNING;
        }

        phase = Phase.TALK_TO_COOK;
        return TaskStatus.RUNNING;
    }

    private TaskStatus handleBanking(AccountContext context) {
        if (bankingTask == null) {
            List<BankingTask.ItemWithdrawal> withdrawals = new ArrayList<>();
            if (!context.inventory().hasItem(EGG) && context.bank().hasItem(EGG)) {
                withdrawals.add(new BankingTask.ItemWithdrawal(EGG, 1));
            }
            if (!context.inventory().hasItem(MILK) && context.bank().hasItem(MILK)) {
                withdrawals.add(new BankingTask.ItemWithdrawal(MILK, 1));
            }
            if (!context.inventory().hasItem(FLOUR) && context.bank().hasItem(FLOUR)) {
                withdrawals.add(new BankingTask.ItemWithdrawal(FLOUR, 1));
            }
            if (!context.inventory().hasItem(MILK) && !context.inventory().hasItem(BUCKET) && context.bank().hasItem(BUCKET)) {
                withdrawals.add(new BankingTask.ItemWithdrawal(BUCKET, 1));
            }
            if (!context.inventory().hasItem(FLOUR) && !context.inventory().hasItem(POT) && context.bank().hasItem(POT)) {
                withdrawals.add(new BankingTask.ItemWithdrawal(POT, 1));
            }

            bankingTask = new BankingTask(
                    BankingTask.Mode.DEPOSIT_ALL_AND_WITHDRAW,
                    new String[]{EGG, MILK, FLOUR, BUCKET, POT, GRAIN},
                    withdrawals.toArray(new BankingTask.ItemWithdrawal[0])
            );
        }

        TaskStatus status = bankingTask.tick(context);
        if (status == TaskStatus.COMPLETE) {
            bankingTask = null;
            hasBanked = true;
            phase = Phase.CHECK_STATUS;
            return TaskStatus.RUNNING;
        }
        if (status == TaskStatus.FAILED || status == TaskStatus.REPLAN) {
            bankingTask = null;
            hasBanked = true;
            phase = Phase.CHECK_STATUS;
            return TaskStatus.RUNNING;
        }

        return TaskStatus.RUNNING;
    }

    private TaskStatus handleGatherEgg(AccountContext context) {
        if (context.inventory().hasItem(EGG)) {
            phase = Phase.CHECK_STATUS;
            return TaskStatus.RUNNING;
        }

        if (!context.isNear(CHICKEN_COOP, 8)) {
            Rs2Walker.walkTo(CHICKEN_COOP);
            return TaskStatus.RUNNING;
        }

        Rs2TileItemModel egg = Microbot.getRs2TileItemCache().query()
                .withName(EGG)
                .within(15)
                .nearest();
        if (egg != null) {
            egg.pickup();
        }
        sleepUntilTrue(() -> context.inventory().hasItem(EGG), 300, 4000);
        if (context.inventory().hasItem(EGG)) {
            phase = Phase.CHECK_STATUS;
        }
        return TaskStatus.RUNNING;
    }

    private TaskStatus handleGatherBucket(AccountContext context) {
        if (context.inventory().hasItem(BUCKET) || context.inventory().hasItem(MILK)) {
            phase = Phase.CHECK_STATUS;
            return TaskStatus.RUNNING;
        }

        if (!context.isNear(LUMBRIDGE_CELLAR, 10)) {
            Rs2Walker.walkTo(LUMBRIDGE_CELLAR);
            return TaskStatus.RUNNING;
        }

        Rs2TileItemModel bucket = Microbot.getRs2TileItemCache().query()
                .withName(BUCKET)
                .within(15)
                .nearest();
        if (bucket != null) {
            bucket.pickup();
        }
        sleepUntilTrue(() -> context.inventory().hasItem(BUCKET), 300, 4000);
        if (context.inventory().hasItem(BUCKET)) {
            phase = Phase.CHECK_STATUS;
        }
        return TaskStatus.RUNNING;
    }

    private TaskStatus handleGatherMilk(AccountContext context) {
        if (context.inventory().hasItem(MILK)) {
            phase = Phase.CHECK_STATUS;
            return TaskStatus.RUNNING;
        }

        if (!context.inventory().hasItem(BUCKET)) {
            phase = Phase.GATHER_BUCKET;
            return TaskStatus.RUNNING;
        }

        if (!context.isNear(DAIRY_COW, 10)) {
            Rs2Walker.walkTo(DAIRY_COW);
            return TaskStatus.RUNNING;
        }

        if (Rs2Player.isAnimating() || Rs2Player.isMoving()) {
            return TaskStatus.RUNNING;
        }

        Rs2TileObjectModel cow = Microbot.getRs2TileObjectCache().query()
                .withNames("Dairy cow")
                .within(10)
                .nearest();

        if (cow != null) {
            cow.click("Milk");
            sleepUntilTrue(() -> context.inventory().hasItem(MILK), 300, 6000);
            if (context.inventory().hasItem(MILK)) {
                phase = Phase.CHECK_STATUS;
            }
        }
        return TaskStatus.RUNNING;
    }

    private TaskStatus handleGatherPot(AccountContext context) {
        if (context.inventory().hasItem(POT) || context.inventory().hasItem(FLOUR)) {
            phase = Phase.CHECK_STATUS;
            return TaskStatus.RUNNING;
        }

        if (!context.isNear(LUMBRIDGE_KITCHEN, 6)) {
            Rs2Walker.walkTo(LUMBRIDGE_KITCHEN);
            return TaskStatus.RUNNING;
        }

        Rs2TileItemModel pot = Microbot.getRs2TileItemCache().query()
                .withName(POT)
                .within(10)
                .nearest();
        if (pot != null) {
            pot.pickup();
        }
        sleepUntilTrue(() -> context.inventory().hasItem(POT), 300, 4000);
        if (context.inventory().hasItem(POT)) {
            phase = Phase.CHECK_STATUS;
        }
        return TaskStatus.RUNNING;
    }

    private TaskStatus handleGatherWheat(AccountContext context) {
        if (context.inventory().hasItem(GRAIN) || context.inventory().hasItem(FLOUR)) {
            phase = Phase.CHECK_STATUS;
            return TaskStatus.RUNNING;
        }

        if (!context.isNear(WHEAT_FIELD, 10)) {
            Rs2Walker.walkTo(WHEAT_FIELD);
            return TaskStatus.RUNNING;
        }

        if (Rs2Player.isAnimating() || Rs2Player.isMoving()) {
            return TaskStatus.RUNNING;
        }

        Rs2TileObjectModel wheat = Microbot.getRs2TileObjectCache().query()
                .withNames("Wheat")
                .within(10)
                .nearest();

        if (wheat != null) {
            wheat.click("Pick");
            sleepUntilTrue(() -> context.inventory().hasItem(GRAIN), 300, 5000);
            if (context.inventory().hasItem(GRAIN)) {
                phase = Phase.CHECK_STATUS;
            }
        }
        return TaskStatus.RUNNING;
    }

    private TaskStatus handleMillFlour(AccountContext context) {
        if (context.inventory().hasItem(FLOUR)) {
            phase = Phase.CHECK_STATUS;
            return TaskStatus.RUNNING;
        }

        // If we have Grain, head to the top floor to put grain in the hopper
        if (context.inventory().hasItem(GRAIN)) {
            int plane = Microbot.getClient().getPlane();
            if (plane != 2) {
                Rs2Walker.walkTo(MILL_TOP);
                return TaskStatus.RUNNING;
            }

            Rs2TileObjectModel hopper = Microbot.getRs2TileObjectCache().query()
                    .withNames("Hopper")
                    .within(10)
                    .nearest();

            if (hopper != null && context.inventory().hasItem(GRAIN)) {
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
