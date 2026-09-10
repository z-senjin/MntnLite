package net.runelite.client.plugins.microbot.mntn.builder.tasks.supply;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.tileitem.models.Rs2TileItemModel;
import net.runelite.client.plugins.microbot.mntn.builder.activities.supply.SupplyRoute;
import net.runelite.client.plugins.microbot.mntn.builder.activities.supply.SupplyRouteType;
import net.runelite.client.plugins.microbot.mntn.builder.core.AccountContext;
import net.runelite.client.plugins.microbot.mntn.builder.core.BuilderItemPrices;
import net.runelite.client.plugins.microbot.mntn.builder.core.requirements.EquipmentRequirement;
import net.runelite.client.plugins.microbot.mntn.builder.core.requirements.ItemRequirement;
import net.runelite.client.plugins.microbot.mntn.builder.core.requirements.MoneyRequirement;
import net.runelite.client.plugins.microbot.mntn.builder.core.requirements.Requirement;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.Task;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.TaskActionGuard;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.TaskStatus;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.TaskStopReason;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.banking.BankingTask;
import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeAction;
import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeRequest;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.shop.Rs2Shop;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

/**
 * Route-aware supply task.
 *
 * The planner chooses the route. This task only executes that one route:
 * bank withdrawal, Grand Exchange buy, shop buy, or ground pickup.
 */
public class SupplyTask implements Task {

    private static final int MAX_GE_COLLECT_ATTEMPTS = 20;
    private static final int GRAND_EXCHANGE_BUY_PERCENT = 10;

    private enum Phase {
        CHECK,
        BANK,
        GE_BUY,
        GE_COLLECT,
        SHOP_WALK,
        SHOP_BUY,
        GROUND_WALK,
        GROUND_PICKUP,
        EQUIP,
        DONE
    }

    private final Requirement requirement;
    private final SupplyRoute route;
    private Phase phase = Phase.CHECK;
    private Phase afterBankPhase;
    private BankingTask bankingTask;
    private int beforeRouteInventoryCount;
    private TaskStopReason lastStopReason = TaskStopReason.NONE;
    private int geCollectAttempts;
    private final TaskActionGuard shopWalkGuard = new TaskActionGuard(10, 30_000, 800);
    private final TaskActionGuard groundWalkGuard = new TaskActionGuard(10, 30_000, 800);
    private final TaskActionGuard shopOpenGuard = new TaskActionGuard(4, 12_000, 900);
    private final TaskActionGuard shopBuyGuard = new TaskActionGuard(4, 12_000, 900);
    private final TaskActionGuard groundFindGuard = new TaskActionGuard(8, 12_000, 900);
    private final TaskActionGuard groundPickupGuard = new TaskActionGuard(4, 10_000, 900);
    private final TaskActionGuard geCollectGuard = new TaskActionGuard(4, 12_000, 900);
    private final TaskActionGuard equipGuard = new TaskActionGuard(4, 8_000, 700);
    private boolean shopPurchasePending;
    private boolean groundPickupPending;

    public SupplyTask(Requirement requirement) {
        this(requirement, SupplyRoute.bank(requirement.description(), 1));
    }

    public SupplyTask(Requirement requirement, SupplyRoute route) {
        this.requirement = requirement;
        this.route = route;
    }

    private void debugLog(AccountContext context, String message) {
        if (context.isDebugLogging()) {
            Microbot.log("[MntnBuilder][SupplyTask][DEBUG] " + message);
        }
    }

    @Override
    public TaskStatus tick(AccountContext context) {
        debugLog(context, "tick: phase=" + phase + ", route=" + route.describe()
                + ", requirement=" + requirement.description());

        if (!context.isLoggedIn()) {
            return stop(TaskStatus.BLOCKED, TaskStopReason.NOT_LOGGED_IN);
        }

        switch (phase) {
            case CHECK:
                return handleCheck(context);
            case BANK:
                return handleBank(context);
            case GE_BUY:
                return handleGrandExchangeBuy(context);
            case GE_COLLECT:
                return handleGrandExchangeCollect(context);
            case SHOP_WALK:
                return handleShopWalk(context);
            case SHOP_BUY:
                return handleShopBuy(context);
            case GROUND_WALK:
                return handleGroundWalk(context);
            case GROUND_PICKUP:
                return handleGroundPickup(context);
            case EQUIP:
                return handleEquip(context);
            case DONE:
            default:
                lastStopReason = TaskStopReason.NONE;
                return TaskStatus.COMPLETE;
        }
    }

    private TaskStatus stop(TaskStatus status, TaskStopReason reason) {
        lastStopReason = reason;
        return status;
    }

    private TaskStatus handleCheck(AccountContext context) {
        if (isSupplied(context)) {
            phase = Phase.DONE;
            return TaskStatus.RUNNING;
        }

        if (requirement instanceof EquipmentRequirement
                && context.inventory().hasItem(((EquipmentRequirement) requirement).getItemName())) {
            phase = Phase.EQUIP;
            return TaskStatus.RUNNING;
        }

        if (route.getType() == SupplyRouteType.BANK) {
            return prepareBankRoute(context);
        }

        if (context.inventory().isFull()) {
            return stop(TaskStatus.BLOCKED, TaskStopReason.INVENTORY_FULL);
        }

        switch (route.getType()) {
            case GRAND_EXCHANGE:
                return preparePurchaseRoute(context, Phase.GE_BUY);
            case SHOP:
                return preparePurchaseRoute(context, Phase.SHOP_WALK);
            case GROUND_ITEM:
                phase = Phase.GROUND_WALK;
                return TaskStatus.RUNNING;
            case SKILL_ACTIVITY:
            case QUEST_REWARD:
            case OTHER:
            default:
                return stop(TaskStatus.BLOCKED, TaskStopReason.UNSUPPORTED_ROUTE);
        }
    }

    private TaskStatus prepareBankRoute(AccountContext context) {
        if (requirement instanceof EquipmentRequirement) {
            EquipmentRequirement equipmentRequirement = (EquipmentRequirement) requirement;
            if (context.inventory().hasItem(equipmentRequirement.getItemName())) {
                phase = Phase.EQUIP;
                return TaskStatus.RUNNING;
            }
            if (context.bank().hasItem(equipmentRequirement.getItemName())) {
                phase = Phase.BANK;
                return TaskStatus.RUNNING;
            }
            return stop(TaskStatus.BLOCKED, TaskStopReason.EQUIPMENT_MISSING);
        }

        if (requirement instanceof ItemRequirement) {
            ItemRequirement itemRequirement = (ItemRequirement) requirement;
            if (!itemRequirement.isAvailableInBank(context)) {
                return stop(TaskStatus.BLOCKED, TaskStopReason.MISSING_BANK_ITEM);
            }
            phase = Phase.BANK;
            return TaskStatus.RUNNING;
        }

        if (requirement instanceof MoneyRequirement) {
            MoneyRequirement moneyRequirement = (MoneyRequirement) requirement;
            if (!moneyRequirement.hasEnoughTotalCoins(context)) {
                return stop(TaskStatus.BLOCKED, TaskStopReason.MISSING_COINS);
            }
            phase = Phase.BANK;
            return TaskStatus.RUNNING;
        }

        return stop(TaskStatus.BLOCKED, TaskStopReason.UNSUPPORTED_ROUTE);
    }

    private TaskStatus preparePurchaseRoute(AccountContext context, Phase purchasePhase) {
        int coinsNeeded = estimatedTotalPrice(context);
        if (!hasEnoughTotalCoins(context, coinsNeeded)) {
            return stop(TaskStatus.BLOCKED, TaskStopReason.MISSING_COINS);
        }

        int bankCoins = context.bank().getCount("Coins");
        if (bankCoins > 0) {
            // A purchase trip carries the whole available coin stack. The estimate above only
            // decides whether this route is affordable; it must not leave usable funds behind.
            bankingTask = createWithdrawalTask(context, "Coins", purchaseCoinWithdrawalAmount(bankCoins));
            afterBankPhase = purchasePhase;
            phase = Phase.BANK;
            return TaskStatus.RUNNING;
        }

        phase = purchasePhase;
        return TaskStatus.RUNNING;
    }

    private TaskStatus handleBank(AccountContext context) {
        if (bankingTask == null) {
            bankingTask = createBankingTask(context);
            if (bankingTask == null) {
                return stop(TaskStatus.BLOCKED,
                        lastStopReason == TaskStopReason.NONE ? TaskStopReason.BANK_FAILED : lastStopReason);
            }
        }

        TaskStatus status = bankingTask.tick(context);
        if (status == TaskStatus.COMPLETE) {
            bankingTask = null;
            if (afterBankPhase != null) {
                phase = afterBankPhase;
                afterBankPhase = null;
                return TaskStatus.RUNNING;
            }
            if (requirement instanceof EquipmentRequirement && !requirement.isSatisfied(context)) {
                phase = Phase.EQUIP;
                return TaskStatus.RUNNING;
            }
            phase = Phase.CHECK;
            return TaskStatus.RUNNING;
        }
        if (status.isUnsuccessfulStop()) {
            bankingTask = null;
            return stop(TaskStatus.BLOCKED, TaskStopReason.BANK_FAILED);
        }
        return TaskStatus.RUNNING;
    }

    private BankingTask createBankingTask(AccountContext context) {
        if (requirement instanceof EquipmentRequirement) {
            EquipmentRequirement equipmentRequirement = (EquipmentRequirement) requirement;
            if (!context.bank().hasItem(equipmentRequirement.getItemName())) {
                lastStopReason = TaskStopReason.EQUIPMENT_MISSING;
                return null;
            }
            return createWithdrawalTask(context, equipmentRequirement.getItemName(), 1);
        }

        if (requirement instanceof ItemRequirement) {
            ItemRequirement itemRequirement = (ItemRequirement) requirement;
            int missing = itemRequirement.getMissingInventoryQuantity(context);
            if (missing <= 0 || !itemRequirement.isAvailableInBank(context)) {
                lastStopReason = TaskStopReason.MISSING_BANK_ITEM;
                return null;
            }
            return createWithdrawalTask(context, itemRequirement.getItemName(), missing);
        }

        if (requirement instanceof MoneyRequirement) {
            MoneyRequirement moneyRequirement = (MoneyRequirement) requirement;
            int missing = moneyRequirement.getMissingInventoryCoins(context);
            if (missing <= 0 || !moneyRequirement.hasEnoughTotalCoins(context)) {
                lastStopReason = TaskStopReason.MISSING_COINS;
                return null;
            }
            return createWithdrawalTask(context, moneyRequirement.getItemName(), missing);
        }

        lastStopReason = TaskStopReason.UNSUPPORTED_ROUTE;
        return null;
    }

    private BankingTask createWithdrawalTask(AccountContext context, String itemName, int amount) {
        return new BankingTask(
                withdrawalModeFor(context.inventory().isFull()),
                null,
                itemName,
                amount
        );
    }

    static BankingTask.Mode withdrawalModeFor(boolean inventoryFull) {
        return inventoryFull
                ? BankingTask.Mode.DEPOSIT_ALL_AND_WITHDRAW
                : BankingTask.Mode.WITHDRAW;
    }

    static int purchaseCoinWithdrawalAmount(int bankCoins) {
        return bankCoins > 0 ? -1 : 0;
    }

    private TaskStatus handleGrandExchangeBuy(AccountContext context) {
        if (isSupplied(context)) {
            phase = Phase.DONE;
            return TaskStatus.RUNNING;
        }

        if (!hasOpenGrandExchangeSlot()) {
            return stop(TaskStatus.BLOCKED, TaskStopReason.GE_NO_OPEN_SLOT);
        }

        beforeRouteInventoryCount = desiredInventoryCount(context);
        boolean placed;
        try {
            placed = Rs2GrandExchange.processOffer(GrandExchangeRequest.builder()
                    .action(GrandExchangeAction.BUY)
                    .itemName(route.getItemName())
                    .exact(true)
                    .price(baseUnitPrice())
                    .percent(GRAND_EXCHANGE_BUY_PERCENT)
                    .quantity(Math.max(1, missingDesiredQuantity(context)))
                    .build());
        } catch (RuntimeException ex) {
            return stop(TaskStatus.BLOCKED, hasOpenGrandExchangeSlot()
                    ? TaskStopReason.GE_OFFER_FAILED
                    : TaskStopReason.GE_NO_OPEN_SLOT);
        }
        if (!placed) {
            return stop(TaskStatus.BLOCKED, TaskStopReason.GE_OFFER_FAILED);
        }

        geCollectAttempts = 0;
        phase = Phase.GE_COLLECT;
        return TaskStatus.RUNNING;
    }

    private boolean hasOpenGrandExchangeSlot() {
        try {
            return Rs2GrandExchange.getAvailableSlotsCount() > 0;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private TaskStatus handleGrandExchangeCollect(AccountContext context) {
        geCollectAttempts++;
        if (!Rs2GrandExchange.hasBoughtOffer() && geCollectAttempts < MAX_GE_COLLECT_ATTEMPTS) {
            return TaskStatus.RUNNING;
        }
        if (!Rs2GrandExchange.hasBoughtOffer() && geCollectAttempts >= MAX_GE_COLLECT_ATTEMPTS) {
            return stop(TaskStatus.BLOCKED, TaskStopReason.GE_COLLECT_FAILED);
        }

        boolean collected = isSupplied(context) || desiredInventoryCount(context) > beforeRouteInventoryCount;
        TaskActionGuard.Result collectResult = geCollectGuard.evaluate(
                "collect " + route.getItemName(),
                collected
        );
        if (collectResult == TaskActionGuard.Result.EXHAUSTED) {
            return stop(TaskStatus.BLOCKED, TaskStopReason.GE_COLLECT_FAILED);
        }
        if (collectResult == TaskActionGuard.Result.READY) {
            Rs2GrandExchange.collectAllToInventory();
            geCollectGuard.recordAttempt();
            return TaskStatus.RUNNING;
        }
        if (collectResult == TaskActionGuard.Result.WAITING) {
            return TaskStatus.RUNNING;
        }

        if (requirement instanceof EquipmentRequirement && !requirement.isSatisfied(context)
                && context.inventory().hasItem(route.getItemName())) {
            phase = Phase.EQUIP;
            return TaskStatus.RUNNING;
        }

        boolean supplied = isSupplied(context);
        phase = supplied ? Phase.DONE : Phase.CHECK;
        return supplied
                ? TaskStatus.RUNNING
                : TaskStatus.RUNNING;
    }

    private TaskStatus handleShopWalk(AccountContext context) {
        if (route.getLocation() == null || route.getShopNpcName() == null) {
            return stop(TaskStatus.BLOCKED, TaskStopReason.SHOP_ROUTE_INCOMPLETE);
        }
        if (context.isNear(route.getLocation(), route.getRange())) {
            shopWalkGuard.reset();
            phase = Phase.SHOP_BUY;
            return TaskStatus.RUNNING;
        }

        TaskActionGuard.Result walkResult = shopWalkGuard.evaluate("walk to shop " + route.getShopNpcName(), false);
        if (walkResult == TaskActionGuard.Result.EXHAUSTED) {
            return stop(TaskStatus.REPLAN, TaskStopReason.TRAVEL_FAILED);
        }
        if (walkResult == TaskActionGuard.Result.READY) {
            Rs2Walker.walkTo(route.getLocation(), route.getRange());
            shopWalkGuard.recordAttempt();
        }
        return TaskStatus.RUNNING;
    }

    private TaskStatus handleShopBuy(AccountContext context) {
        if (!Rs2Shop.isOpen()) {
            TaskActionGuard.Result openResult = shopOpenGuard.evaluate("open shop " + route.getShopNpcName(), false);
            if (openResult == TaskActionGuard.Result.EXHAUSTED) {
                return stop(TaskStatus.BLOCKED, TaskStopReason.SHOP_UNAVAILABLE);
            }
            if (openResult == TaskActionGuard.Result.READY) {
                Rs2Shop.openShop(route.getShopNpcName(), false);
                shopOpenGuard.recordAttempt();
            }
            return TaskStatus.RUNNING;
        }
        shopOpenGuard.reset();

        if (!Rs2Shop.hasStock(route.getItemName())) {
            Rs2Shop.closeShop();
            return stop(TaskStatus.BLOCKED, TaskStopReason.SHOP_OUT_OF_STOCK);
        }

        boolean purchaseConfirmed = shopPurchasePending
                && (isSupplied(context) || desiredInventoryCount(context) > beforeRouteInventoryCount);
        TaskActionGuard.Result buyResult = shopBuyGuard.evaluate("buy " + route.getItemName(), purchaseConfirmed);
        if (buyResult == TaskActionGuard.Result.CONFIRMED) {
            shopPurchasePending = false;
            Rs2Shop.closeShop();
        } else if (buyResult == TaskActionGuard.Result.EXHAUSTED) {
            shopPurchasePending = false;
            Rs2Shop.closeShop();
            return stop(TaskStatus.BLOCKED, TaskStopReason.SHOP_UNAVAILABLE);
        } else if (buyResult == TaskActionGuard.Result.READY) {
            beforeRouteInventoryCount = desiredInventoryCount(context);
            shopPurchasePending = true;
            Rs2Shop.buyItemOptimally(route.getItemName(), Math.max(1, missingDesiredQuantity(context)));
            shopBuyGuard.recordAttempt();
            return TaskStatus.RUNNING;
        } else {
            return TaskStatus.RUNNING;
        }

        if (requirement instanceof EquipmentRequirement && !requirement.isSatisfied(context)
                && context.inventory().hasItem(route.getItemName())) {
            phase = Phase.EQUIP;
            return TaskStatus.RUNNING;
        }

        boolean supplied = isSupplied(context);
        phase = supplied ? Phase.DONE : Phase.CHECK;
        return supplied
                ? TaskStatus.RUNNING
                : stop(TaskStatus.BLOCKED, TaskStopReason.SHOP_UNAVAILABLE);
    }

    private TaskStatus handleGroundWalk(AccountContext context) {
        if (route.getLocation() == null) {
            return stop(TaskStatus.BLOCKED, TaskStopReason.GROUND_ROUTE_INCOMPLETE);
        }
        if (context.isNear(route.getLocation(), route.getRange())) {
            groundWalkGuard.reset();
            phase = Phase.GROUND_PICKUP;
            return TaskStatus.RUNNING;
        }

        TaskActionGuard.Result walkResult = groundWalkGuard.evaluate("walk to ground item " + route.getItemName(), false);
        if (walkResult == TaskActionGuard.Result.EXHAUSTED) {
            return stop(TaskStatus.REPLAN, TaskStopReason.TRAVEL_FAILED);
        }
        if (walkResult == TaskActionGuard.Result.READY) {
            Rs2Walker.walkTo(route.getLocation(), route.getRange());
            groundWalkGuard.recordAttempt();
        }
        return TaskStatus.RUNNING;
    }

    private TaskStatus handleGroundPickup(AccountContext context) {
        Rs2TileItemModel item = findGroundSupplyItem();
        if (item == null) {
            TaskActionGuard.Result findResult = groundFindGuard.evaluate("find ground item " + route.getItemName(), false);
            if (findResult == TaskActionGuard.Result.EXHAUSTED) {
                return stop(TaskStatus.BLOCKED, TaskStopReason.GROUND_ITEM_NOT_FOUND);
            }
            if (findResult == TaskActionGuard.Result.READY) {
                groundFindGuard.recordAttempt();
            }
            return TaskStatus.RUNNING;
        }
        groundFindGuard.reset();

        boolean pickupConfirmed = groundPickupPending
                && (requirement.isSatisfied(context) || desiredInventoryCount(context) > beforeRouteInventoryCount);
        TaskActionGuard.Result pickupResult = groundPickupGuard.evaluate("pick up " + route.getItemName(), pickupConfirmed);
        if (pickupResult == TaskActionGuard.Result.CONFIRMED) {
            groundPickupPending = false;
        } else if (pickupResult == TaskActionGuard.Result.EXHAUSTED) {
            groundPickupPending = false;
            return stop(TaskStatus.BLOCKED, TaskStopReason.GROUND_PICKUP_FAILED);
        } else if (pickupResult == TaskActionGuard.Result.READY) {
            beforeRouteInventoryCount = desiredInventoryCount(context);
            groundPickupPending = true;
            item.pickup();
            groundPickupGuard.recordAttempt();
            return TaskStatus.RUNNING;
        } else {
            return TaskStatus.RUNNING;
        }

        phase = requirement.isSatisfied(context) ? Phase.DONE : Phase.CHECK;
        return requirement.isSatisfied(context)
                ? TaskStatus.RUNNING
                : stop(TaskStatus.BLOCKED, TaskStopReason.GROUND_PICKUP_FAILED);
    }

    private Rs2TileItemModel findGroundSupplyItem() {
        return Microbot.getRs2TileItemCache().query()
                .withName(route.getItemName())
                .where(Rs2TileItemModel::isLootAble)
                .where(item -> !item.isDespawned())
                .nearest(route.getRange());
    }

    private TaskStatus handleEquip(AccountContext context) {
        if (!(requirement instanceof EquipmentRequirement)) {
            phase = Phase.CHECK;
            return TaskStatus.RUNNING;
        }

        EquipmentRequirement equipmentRequirement = (EquipmentRequirement) requirement;
        if (context.equipment().hasItem(equipmentRequirement.getItemName())) {
            phase = Phase.DONE;
            return TaskStatus.RUNNING;
        }
        if (!context.inventory().hasItem(equipmentRequirement.getItemName())) {
            phase = Phase.CHECK;
            return TaskStatus.RUNNING;
        }

        TaskActionGuard.Result equipResult = equipGuard.evaluate(
                "equip " + equipmentRequirement.getItemName(),
                context.equipment().hasItem(equipmentRequirement.getItemName())
        );
        if (equipResult == TaskActionGuard.Result.CONFIRMED) {
            phase = Phase.DONE;
            return TaskStatus.RUNNING;
        }
        if (equipResult == TaskActionGuard.Result.EXHAUSTED) {
            return stop(TaskStatus.REPLAN, TaskStopReason.EQUIP_FAILED);
        }
        if (equipResult == TaskActionGuard.Result.READY) {
            Rs2Inventory.wield(equipmentRequirement.getItemName());
            equipGuard.recordAttempt();
        }
        return TaskStatus.RUNNING;
    }

    private int missingDesiredQuantity(AccountContext context) {
        if (requirement instanceof ItemRequirement) {
            return ((ItemRequirement) requirement).getMissingInventoryQuantity(context);
        }
        if (requirement instanceof EquipmentRequirement) {
            return requirement.isSatisfied(context) || context.inventory().hasItem(route.getItemName()) ? 0 : 1;
        }
        return Math.max(1, route.getQuantity());
    }

    private int desiredInventoryCount(AccountContext context) {
        return context.inventory().getCount(route.getItemName());
    }

    private boolean isSupplied(AccountContext context) {
        if (requirement instanceof EquipmentRequirement) {
            return requirement.isSatisfied(context);
        }
        return requirement.isSatisfied(context)
                || (route.getItemName() != null
                && context.inventory().getCount(route.getItemName()) >= route.getQuantity());
    }

    private int estimatedTotalPrice(AccountContext context) {
        return Math.max(1, estimatedUnitPrice()) * Math.max(1, missingDesiredQuantity(context));
    }

    private int estimatedUnitPrice() {
        return (int) Math.ceil(baseUnitPrice() * route.getPriceMultiplier());
    }

    private int baseUnitPrice() {
        int fallback = route.getEstimatedUnitPrice() > 0 ? route.getEstimatedUnitPrice() : 100;
        return BuilderItemPrices.estimate(route.getItemName(), fallback);
    }

    private boolean hasEnoughTotalCoins(AccountContext context, int coinsNeeded) {
        return context.inventory().getCount("Coins") + context.bank().getCount("Coins") >= coinsNeeded;
    }

    @Override
    public boolean needsReplan(AccountContext context) {
        return false;
    }

    @Override
    public TaskStopReason getLastStopReason() {
        return lastStopReason;
    }

    @Override
    public String describe() {
        return "Supply (" + route.describe() + ", " + requirement.description() + ") - " + phase;
    }
}
