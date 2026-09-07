package net.runelite.client.plugins.microbot.mntn.builder.tasks.moneymaking;

import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.mntn.builder.activities.combat.CombatStrategy;
import net.runelite.client.plugins.microbot.mntn.builder.activities.fishing.FishingStrategy;
import net.runelite.client.plugins.microbot.mntn.builder.activities.moneymaking.MoneyMakingStrategy;
import net.runelite.client.plugins.microbot.mntn.builder.activities.mining.MiningStrategy;
import net.runelite.client.plugins.microbot.mntn.builder.activities.smithing.SmeltingStrategy;
import net.runelite.client.plugins.microbot.mntn.builder.activities.woodcutting.WoodcuttingStrategy;
import net.runelite.client.plugins.microbot.mntn.builder.core.AccountContext;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.Task;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.TaskStatus;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.TaskStopReason;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.banking.BankingTask;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.combat.CombatTask;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.skilling.FishingTask;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.skilling.MiningTask;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.skilling.SmeltingTask;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.skilling.WoodcuttingTask;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.item.Rs2ItemManager;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntilTrue;

public class MoneyMakingTask implements Task {

    private static final int MAX_SELL_ATTEMPTS = 3;
    private static final int MAX_GE_COLLECT_ATTEMPTS = 20;

    private enum Phase {
        CHECK,
        GATHER,
        WITHDRAW_SELLABLES,
        SELL,
        COLLECT,
        DONE
    }

    private final MoneyMakingStrategy.Method method;
    private final int targetCoins;
    private Phase phase = Phase.CHECK;
    private Task gatherTask;
    private BankingTask bankingTask;
    private TaskStopReason lastStopReason = TaskStopReason.NONE;
    private int sellAttempts;
    private int collectAttempts;
    private int coinsBeforeSell;

    public MoneyMakingTask(MoneyMakingStrategy.Method method, int targetCoins) {
        this.method = method;
        this.targetCoins = targetCoins;
    }

    @Override
    public TaskStatus tick(AccountContext context) {
        if (!context.isLoggedIn()) {
            return stop(TaskStatus.BLOCKED, TaskStopReason.NOT_LOGGED_IN);
        }

        if (context.inventory().getCount("Coins") >= targetCoins) {
            phase = Phase.DONE;
        }

        switch (phase) {
            case CHECK:
                return handleCheck(context);
            case GATHER:
                return handleGather(context);
            case WITHDRAW_SELLABLES:
                return handleWithdrawSellables(context);
            case SELL:
                return handleSell(context);
            case COLLECT:
                return handleCollect(context);
            case DONE:
            default:
                lastStopReason = TaskStopReason.NONE;
                return TaskStatus.COMPLETE;
        }
    }

    private TaskStatus handleCheck(AccountContext context) {
        if (context.inventory().hasItem(method.itemName)) {
            phase = Phase.SELL;
            return TaskStatus.RUNNING;
        }
        if (context.bank().hasItem(method.itemName)) {
            phase = Phase.WITHDRAW_SELLABLES;
            return TaskStatus.RUNNING;
        }

        phase = Phase.GATHER;
        return TaskStatus.RUNNING;
    }

    private TaskStatus handleGather(AccountContext context) {
        int gathered = context.inventory().getCount(method.itemName) + context.bank().getCount(method.itemName);
        if (gathered >= method.minimumSellQuantity || context.inventory().isFull()) {
            gatherTask = null;
            phase = Phase.CHECK;
            return TaskStatus.RUNNING;
        }

        if (gatherTask == null) {
            gatherTask = createGatherTask(context);
            if (gatherTask == null) {
                return stop(TaskStatus.BLOCKED, TaskStopReason.TASK_REQUESTED_REPLAN);
            }
        }

        TaskStatus status = gatherTask.tick(context);
        if (status.isUnsuccessfulStop()) {
            TaskStopReason reason = gatherTask.getLastStopReason();
            gatherTask = null;
            phase = Phase.CHECK;
            return stop(TaskStatus.BLOCKED, reason != TaskStopReason.NONE ? reason : TaskStopReason.TASK_REQUESTED_REPLAN);
        }
        if (status.clearsTask()) {
            gatherTask = null;
            phase = Phase.CHECK;
        }
        return TaskStatus.RUNNING;
    }

    private Task createGatherTask(AccountContext context) {
        switch (method.sourceType) {
            case COMBAT:
                int currentStrength = Math.max(1, context.getRealLevel(Skill.STRENGTH));
                return new CombatTask(CombatStrategy.Monster.COWS, Skill.STRENGTH, currentStrength + 1, 0);
            case FISHING:
                return new FishingTask(FishingStrategy.Method.NET_SHRIMP);
            case MINING:
                return new MiningTask(method == MoneyMakingStrategy.Method.IRON_ORE
                        ? MiningStrategy.Method.IRON_ORE
                        : MiningStrategy.Method.COPPER_ORE);
            case SMELTING:
                return new SmeltingTask(method == MoneyMakingStrategy.Method.IRON_BAR
                        ? SmeltingStrategy.Bar.IRON_BAR
                        : SmeltingStrategy.Bar.BRONZE_BAR);
            case WOODCUTTING:
                return new WoodcuttingTask(WoodcuttingStrategy.Method.NORMAL_TREE);
            default:
                return null;
        }
    }

    private TaskStatus handleWithdrawSellables(AccountContext context) {
        if (context.inventory().hasItem(method.itemName)) {
            phase = Phase.SELL;
            return TaskStatus.RUNNING;
        }
        if (!context.bank().hasItem(method.itemName)) {
            phase = Phase.GATHER;
            return TaskStatus.RUNNING;
        }

        if (bankingTask == null) {
            bankingTask = new BankingTask(
                    BankingTask.Mode.WITHDRAW,
                    null,
                    method.itemName,
                    -1
            );
        }

        TaskStatus status = bankingTask.tick(context);
        if (status == TaskStatus.COMPLETE) {
            bankingTask = null;
            phase = context.inventory().hasItem(method.itemName) ? Phase.SELL : Phase.GATHER;
            return TaskStatus.RUNNING;
        }
        if (status.isUnsuccessfulStop()) {
            bankingTask = null;
            return stop(TaskStatus.BLOCKED, TaskStopReason.BANK_FAILED);
        }
        return TaskStatus.RUNNING;
    }

    private TaskStatus handleSell(AccountContext context) {
        int quantity = context.inventory().getCount(method.itemName);
        if (quantity <= 0) {
            phase = Phase.CHECK;
            return TaskStatus.RUNNING;
        }

        coinsBeforeSell = context.inventory().getCount("Coins");
        boolean placed = Rs2GrandExchange.sellItem(method.itemName, quantity, estimatedSellPrice());
        if (!placed) {
            sellAttempts++;
            if (sellAttempts >= MAX_SELL_ATTEMPTS) {
                return stop(TaskStatus.BLOCKED, TaskStopReason.GE_OFFER_FAILED);
            }
            return TaskStatus.RUNNING;
        }

        collectAttempts = 0;
        phase = Phase.COLLECT;
        return TaskStatus.RUNNING;
    }

    private TaskStatus handleCollect(AccountContext context) {
        collectAttempts++;
        if (!Rs2GrandExchange.hasSoldOffer() && collectAttempts < MAX_GE_COLLECT_ATTEMPTS) {
            return TaskStatus.RUNNING;
        }
        if (!Rs2GrandExchange.hasSoldOffer() && collectAttempts >= MAX_GE_COLLECT_ATTEMPTS) {
            sellAttempts++;
            phase = Phase.CHECK;
            return sellAttempts >= MAX_SELL_ATTEMPTS
                    ? stop(TaskStatus.BLOCKED, TaskStopReason.GE_COLLECT_FAILED)
                    : TaskStatus.RUNNING;
        }

        Rs2GrandExchange.collectAllToInventory();
        sleepUntilTrue(
                () -> context.inventory().getCount("Coins") > coinsBeforeSell
                        || context.inventory().getCount("Coins") >= targetCoins,
                100,
                8000
        );

        if (context.inventory().getCount("Coins") >= targetCoins) {
            phase = Phase.DONE;
            return TaskStatus.RUNNING;
        }
        if (context.inventory().getCount("Coins") > coinsBeforeSell) {
            sellAttempts = 0;
            phase = Phase.CHECK;
            return TaskStatus.RUNNING;
        }

        sellAttempts++;
        phase = sellAttempts >= MAX_SELL_ATTEMPTS ? Phase.CHECK : Phase.COLLECT;
        return sellAttempts >= MAX_SELL_ATTEMPTS
                ? stop(TaskStatus.BLOCKED, TaskStopReason.GE_COLLECT_FAILED)
                : TaskStatus.RUNNING;
    }

    private int estimatedSellPrice() {
        int itemId = Rs2ItemManager.getItemIdByName(method.itemName, false);
        if (itemId <= 0) {
            return method.fallbackUnitPrice;
        }

        int price = Rs2GrandExchange.getOfferPrice(itemId);
        if (price <= 0) {
            return method.fallbackUnitPrice;
        }

        double multiplier = Rs2Random.between(90, 98) / 100.0;
        return Math.max(1, (int) Math.floor(price * multiplier));
    }

    private TaskStatus stop(TaskStatus status, TaskStopReason reason) {
        lastStopReason = reason;
        return status;
    }

    @Override
    public boolean needsReplan(AccountContext context) {
        return context.inventory().getCount("Coins") >= targetCoins;
    }

    @Override
    public TaskStopReason getReplanStopReason(AccountContext context) {
        if (context.inventory().getCount("Coins") >= targetCoins) {
            return TaskStopReason.REQUIREMENT_SATISFIED;
        }
        return TaskStopReason.TASK_REQUESTED_REPLAN;
    }

    @Override
    public TaskStopReason getLastStopReason() {
        return lastStopReason;
    }

    @Override
    public String describe() {
        return "Money making (" + method.name() + ") - " + phase;
    }
}
