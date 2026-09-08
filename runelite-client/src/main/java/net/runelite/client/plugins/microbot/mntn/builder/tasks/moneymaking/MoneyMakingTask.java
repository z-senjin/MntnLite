package net.runelite.client.plugins.microbot.mntn.builder.tasks.moneymaking;

import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.mntn.builder.activities.combat.CombatStrategy;
import net.runelite.client.plugins.microbot.mntn.builder.activities.fishing.FishingStrategy;
import net.runelite.client.plugins.microbot.mntn.builder.activities.moneymaking.MoneyMakingStrategy;
import net.runelite.client.plugins.microbot.mntn.builder.activities.mining.MiningStrategy;
import net.runelite.client.plugins.microbot.mntn.builder.activities.smithing.SmeltingStrategy;
import net.runelite.client.plugins.microbot.mntn.builder.activities.woodcutting.WoodcuttingStrategy;
import net.runelite.client.plugins.microbot.mntn.builder.core.AccountContext;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.Task;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.TaskActionGuard;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.TaskStatus;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.TaskStopReason;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.banking.BankingTask;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.combat.CombatTask;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.skilling.FishingTask;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.skilling.MiningTask;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.skilling.SmeltingTask;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.skilling.WoodcuttingTask;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.shop.Rs2Shop;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

public class MoneyMakingTask implements Task {

    private static final int MAX_SELL_ATTEMPTS = 3;
    private static final int MAX_GE_COLLECT_ATTEMPTS = 20;
    private static final String LUMBRIDGE_GENERAL_STORE_NPC = "Shop keeper";
    private static final WorldPoint LUMBRIDGE_GENERAL_STORE = new WorldPoint(3212, 3246, 0);
    private static final int GENERAL_STORE_RANGE = 10;

    private enum Phase {
        CHECK,
        GATHER,
        WITHDRAW_SELLABLES,
        SHOP_WALK,
        SHOP_SELL,
        SELL,
        COLLECT,
        DONE
    }

    private final MoneyMakingStrategy.Method method;
    private final int targetCoins;
    private final MoneyMakingStrategy.SaleRoute saleRoute;
    private Phase phase = Phase.CHECK;
    private Task gatherTask;
    private BankingTask bankingTask;
    private TaskStopReason lastStopReason = TaskStopReason.NONE;
    private int sellAttempts;
    private int collectAttempts;
    private int coinsBeforeSell;
    private int coinsBeforeShopSell;
    private int beforeShopSellQuantity;
    private int lastBonesCount = -1;
    private int inventoryCleanupAttempts;
    private boolean shopSellPending;
    private boolean withdrawConfirmationPending;
    private final TaskActionGuard shopWalkGuard = new TaskActionGuard(4, 20000, 2500);
    private final TaskActionGuard shopOpenGuard = new TaskActionGuard(3, 12000, 2000);
    private final TaskActionGuard shopSellGuard = new TaskActionGuard(5, 18000, 1200);
    private final TaskActionGuard withdrawConfirmGuard = new TaskActionGuard(1, 6000, 0);
    private final TaskActionGuard geCollectGuard = new TaskActionGuard(3, 15000, 2000);

    public MoneyMakingTask(MoneyMakingStrategy.Method method, int targetCoins) {
        this(method, targetCoins, MoneyMakingStrategy.SaleRoute.GRAND_EXCHANGE);
    }

    public MoneyMakingTask(MoneyMakingStrategy.Method method, int targetCoins,
                           MoneyMakingStrategy.SaleRoute saleRoute) {
        this.method = method;
        this.targetCoins = targetCoins;
        this.saleRoute = saleRoute;
    }

    @Override
    public TaskStatus tick(AccountContext context) {
        if (!context.isLoggedIn()) {
            return stop(TaskStatus.BLOCKED, TaskStopReason.NOT_LOGGED_IN);
        }

        if (context.inventory().getCount("Coins") >= targetCoins) {
            phase = Phase.DONE;
        }

        if (Rs2Inventory.isFull() && !context.inventory().hasItem(method.itemName)) {
            return clearNonSellableInventory(context);
        }

        switch (phase) {
            case CHECK:
                return handleCheck(context);
            case GATHER:
                return handleGather(context);
            case WITHDRAW_SELLABLES:
                return handleWithdrawSellables(context);
            case SHOP_WALK:
                return handleShopWalk(context);
            case SHOP_SELL:
                return handleShopSell(context);
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
            clearWithdrawConfirmation();
            phase = saleRoute == MoneyMakingStrategy.SaleRoute.GENERAL_STORE
                    ? Phase.SHOP_WALK
                    : Phase.SELL;
            return TaskStatus.RUNNING;
        }
        if (context.bank().hasItem(method.itemName)) {
            clearWithdrawConfirmation();
            phase = Phase.WITHDRAW_SELLABLES;
            return TaskStatus.RUNNING;
        }

        phase = Phase.GATHER;
        return TaskStatus.RUNNING;
    }

    private TaskStatus clearNonSellableInventory(AccountContext context) {
        int bones = Rs2Inventory.count("Bones");
        if (bones <= 0) {
            return stop(TaskStatus.BLOCKED, TaskStopReason.INVENTORY_FULL);
        }

        if (bones < lastBonesCount) {
            inventoryCleanupAttempts = 0;
        }
        if (inventoryCleanupAttempts >= MAX_SELL_ATTEMPTS) {
            return stop(TaskStatus.BLOCKED, TaskStopReason.INVENTORY_FULL);
        }

        lastBonesCount = bones;
        inventoryCleanupAttempts++;
        if (!Rs2Inventory.interact("Bones", "Bury")) {
            debugLog(context, "Could not bury bones while clearing full money-making inventory");
        }
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
                CombatStrategy.Monster monster = method == MoneyMakingStrategy.Method.CHICKEN_FEATHERS
                        ? CombatStrategy.Monster.CHICKENS
                        : CombatStrategy.Monster.COWS;
                return new CombatTask(monster, Skill.STRENGTH, currentStrength + 1, 0, method.itemName);
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
            clearWithdrawConfirmation();
            phase = salePhase();
            return TaskStatus.RUNNING;
        }
        if (withdrawConfirmationPending) {
            TaskActionGuard.Result confirmation = withdrawConfirmGuard.evaluate(
                    "confirm withdrawal of " + method.itemName,
                    false
            );
            if (confirmation == TaskActionGuard.Result.EXHAUSTED) {
                clearWithdrawConfirmation();
                return stop(TaskStatus.BLOCKED, TaskStopReason.BANK_FAILED);
            }
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
            withdrawConfirmationPending = true;
            debugLog(context, "Waiting to confirm bank withdrawal of " + method.itemName);
            return TaskStatus.RUNNING;
        }
        if (status.isUnsuccessfulStop()) {
            bankingTask = null;
            clearWithdrawConfirmation();
            return stop(TaskStatus.BLOCKED, TaskStopReason.BANK_FAILED);
        }
        return TaskStatus.RUNNING;
    }

    private TaskStatus handleShopWalk(AccountContext context) {
        if (context.isNear(LUMBRIDGE_GENERAL_STORE, GENERAL_STORE_RANGE)) {
            resetShopSession();
            phase = Phase.SHOP_SELL;
            return TaskStatus.RUNNING;
        }

        TaskActionGuard.Result walkResult = shopWalkGuard.evaluate("walk to Lumbridge General Store", false);
        if (walkResult == TaskActionGuard.Result.EXHAUSTED) {
            return stop(TaskStatus.REPLAN, TaskStopReason.TRAVEL_FAILED);
        }
        if (walkResult == TaskActionGuard.Result.READY) {
            Rs2Walker.walkTo(LUMBRIDGE_GENERAL_STORE, GENERAL_STORE_RANGE);
            shopWalkGuard.recordAttempt();
        }
        return TaskStatus.RUNNING;
    }

    private TaskStatus handleShopSell(AccountContext context) {
        if (!method.canSellAtGeneralStore()) {
            closeShopIfOpen();
            return stop(TaskStatus.BLOCKED, TaskStopReason.SHOP_ZERO_VALUE);
        }

        int quantity = context.inventory().getCount(method.itemName);
        if (quantity <= 0) {
            closeShopIfOpen();
            phase = context.inventory().getCount("Coins") >= targetCoins ? Phase.DONE : Phase.CHECK;
            return TaskStatus.RUNNING;
        }

        if (!Rs2Shop.isOpen()) {
            shopSellPending = false;
            shopSellGuard.reset();
            TaskActionGuard.Result openResult = shopOpenGuard.evaluate(
                    "open Lumbridge General Store", false
            );
            if (openResult == TaskActionGuard.Result.EXHAUSTED) {
                return stop(TaskStatus.BLOCKED, TaskStopReason.SHOP_UNAVAILABLE);
            }
            if (openResult == TaskActionGuard.Result.READY) {
                boolean opened = Rs2Shop.openShop(LUMBRIDGE_GENERAL_STORE_NPC, false);
                debugLog(context, "Opening Lumbridge General Store accepted=" + opened
                        + " attempt=" + (shopOpenGuard.attempts() + 1));
                shopOpenGuard.recordAttempt();
            }
            return TaskStatus.RUNNING;
        }
        shopOpenGuard.reset();

        boolean itemWasSold = shopSellPending && quantity < beforeShopSellQuantity;
        if (itemWasSold && context.inventory().getCount("Coins") <= coinsBeforeShopSell) {
            shopSellPending = false;
            closeShopIfOpen();
            debugLog(context, "Stopped shop sale of " + method.itemName
                    + " because it did not increase coins");
            return stop(TaskStatus.BLOCKED, TaskStopReason.SHOP_ZERO_VALUE);
        }
        TaskActionGuard.Result sellResult = shopSellGuard.evaluate(
                "sell " + method.itemName + " to Lumbridge General Store", itemWasSold
        );
        if (sellResult == TaskActionGuard.Result.CONFIRMED) {
            shopSellPending = false;
            debugLog(context, "Confirmed sale of " + method.itemName);
            return TaskStatus.RUNNING;
        }
        if (sellResult == TaskActionGuard.Result.EXHAUSTED) {
            shopSellPending = false;
            closeShopIfOpen();
            return stop(TaskStatus.BLOCKED, TaskStopReason.SHOP_UNAVAILABLE);
        }
        if (sellResult == TaskActionGuard.Result.READY) {
            int unitPrice = currentGeneralStoreSellPrice();
            if (unitPrice <= 0) {
                closeShopIfOpen();
                debugLog(context, "Stopped shop sale of " + method.itemName
                        + " because the current shop payout is 0 gp");
                return stop(TaskStatus.BLOCKED, TaskStopReason.SHOP_ZERO_VALUE);
            }
            beforeShopSellQuantity = quantity;
            coinsBeforeShopSell = context.inventory().getCount("Coins");
            shopSellPending = true;
            String sellQuantity = "1";
            if (!Rs2Inventory.sellItem(method.itemName, sellQuantity)) {
                shopSellPending = false;
                closeShopIfOpen();
                return stop(TaskStatus.BLOCKED, TaskStopReason.ACTION_FAILED);
            }
            debugLog(context, "Selling " + sellQuantity + " x " + method.itemName
                    + " for " + unitPrice + " gp from inventory quantity=" + quantity);
            shopSellGuard.recordAttempt();
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
        int currentCoins = context.inventory().getCount("Coins");
        if (currentCoins >= targetCoins) {
            geCollectGuard.reset();
            phase = Phase.DONE;
            return TaskStatus.RUNNING;
        }
        if (currentCoins > coinsBeforeSell) {
            geCollectGuard.reset();
            sellAttempts = 0;
            phase = Phase.CHECK;
            return TaskStatus.RUNNING;
        }

        if (!Rs2GrandExchange.hasSoldOffer()) {
            collectAttempts++;
            if (collectAttempts < MAX_GE_COLLECT_ATTEMPTS) {
                return TaskStatus.RUNNING;
            }
            sellAttempts++;
            phase = Phase.CHECK;
            return sellAttempts >= MAX_SELL_ATTEMPTS
                    ? stop(TaskStatus.BLOCKED, TaskStopReason.GE_COLLECT_FAILED)
                    : TaskStatus.RUNNING;
        }

        TaskActionGuard.Result collectResult = geCollectGuard.evaluate(
                "collect sold " + method.itemName,
                false
        );
        if (collectResult == TaskActionGuard.Result.EXHAUSTED) {
            return stop(TaskStatus.BLOCKED, TaskStopReason.GE_COLLECT_FAILED);
        }
        if (collectResult == TaskActionGuard.Result.READY) {
            Rs2GrandExchange.collectAllToInventory();
            geCollectGuard.recordAttempt();
        }
        return TaskStatus.RUNNING;
    }

    private int estimatedSellPrice() {
        double multiplier = Rs2Random.between(90, 98) / 100.0;
        return Math.max(1, (int) Math.floor(method.fallbackUnitPrice * multiplier));
    }

    private Phase salePhase() {
        return saleRoute == MoneyMakingStrategy.SaleRoute.GENERAL_STORE
                ? Phase.SHOP_WALK
                : Phase.SELL;
    }

    /**
     * General stores pay a percentage of an item's in-game value that falls with
     * current stock, to a 10% floor. A failed lookup is treated as zero so a sale
     * is never made on an unverified price.
     */
    private int currentGeneralStoreSellPrice() {
        Rs2ItemModel inventoryItem = Rs2Inventory.get(method.itemName, true);
        if (inventoryItem == null) {
            return 0;
        }

        try {
            int storeValue = Microbot.getRs2ItemManager().getPrice(inventoryItem.getId());
            if (storeValue <= 0) {
                return 0;
            }

            int stock = Rs2Shop.shopItems.stream()
                    .filter(item -> item.getId() == inventoryItem.getId())
                    .mapToInt(Rs2ItemModel::getQuantity)
                    .findFirst()
                    .orElse(0);
            int payoutPercent = Math.max(10, 40 - (3 * Math.max(0, stock)));
            return (storeValue * payoutPercent) / 100;
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    private void closeShopIfOpen() {
        if (Rs2Shop.isOpen()) {
            Rs2Shop.closeShop();
        }
    }

    private void resetShopSession() {
        shopWalkGuard.reset();
        shopOpenGuard.reset();
        shopSellGuard.reset();
        shopSellPending = false;
        beforeShopSellQuantity = 0;
        coinsBeforeShopSell = 0;
    }

    private void clearWithdrawConfirmation() {
        withdrawConfirmationPending = false;
        withdrawConfirmGuard.reset();
    }

    private void debugLog(AccountContext context, String message) {
        if (context.isDebugLogging()) {
            Microbot.log("[MntnBuilder][MoneyMakingTask][DEBUG] " + message);
        }
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
