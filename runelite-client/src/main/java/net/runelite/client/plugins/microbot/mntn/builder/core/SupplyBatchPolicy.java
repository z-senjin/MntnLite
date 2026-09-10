package net.runelite.client.plugins.microbot.mntn.builder.core;

/**
 * Shared batch sizing for consumed inputs bought through the Grand Exchange.
 * Strategies opt in only for inputs they actually consume; tools remain one-off supplies.
 */
public final class SupplyBatchPolicy {

    public static final int MIN_CONSUMABLE_BATCH = 100;
    public static final int MAX_CONSUMABLE_BATCH = 500;
    private static final int DEFAULT_UNIT_PRICE = 100;
    private static final double GE_PRICE_MULTIPLIER = 1.10;

    private SupplyBatchPolicy() {
    }

    /**
     * Returns an affordable batch between 100 and 500. Returning 100 when funds are lower
     * deliberately leaves the existing supply route unrunnable instead of degrading into
     * repeated one-item purchases.
     */
    public static int consumableBatchSize(AccountContext context, String itemName) {
        int availableCoins = availableCoins(context);
        int affordableQuantity = availableCoins / estimatedGeUnitPrice(itemName);
        if (affordableQuantity < MIN_CONSUMABLE_BATCH) {
            return MIN_CONSUMABLE_BATCH;
        }
        return Math.min(MAX_CONSUMABLE_BATCH, affordableQuantity);
    }

    private static int availableCoins(AccountContext context) {
        if (context == null) {
            return 0;
        }
        return Math.max(0, context.inventory().getCount("Coins"))
                + Math.max(0, context.bank().getCount("Coins"));
    }

    private static int estimatedGeUnitPrice(String itemName) {
        return Math.max(1, (int) Math.ceil(
                BuilderItemPrices.estimate(itemName, DEFAULT_UNIT_PRICE) * GE_PRICE_MULTIPLIER));
    }
}
