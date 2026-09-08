package net.runelite.client.plugins.microbot.mntn.builder.core.requirements;

import net.runelite.api.ItemID;
import net.runelite.client.plugins.microbot.mntn.builder.activities.ActivityType;
import net.runelite.client.plugins.microbot.mntn.builder.core.AccountContext;

import java.util.Arrays;
import java.util.List;

public class MoneyRequirement implements Requirement {

    private static final String COINS = "Coins";

    private final int amount;

    public MoneyRequirement(int amount) {
        this.amount = Math.max(1, amount);
    }

    @Override
    public boolean isSatisfied(AccountContext context) {
        return context.inventory().getCount(ItemID.COINS_995) >= amount;
    }

    @Override
    public List<ActivityRequest> getWaysToSatisfy(AccountContext context) {
        return Arrays.asList(
                new ActivityRequest(ActivityType.SUPPLY, this),
                new ActivityRequest(ActivityType.MONEY_MAKING, this)
        );
    }

    @Override
    public double urgency(AccountContext context) {
        int missing = getMissingInventoryCoins(context);
        return Math.min(40, missing / 100.0);
    }

    @Override
    public String description() {
        return "Have " + amount + " coins in inventory";
    }

    public int getAmount() {
        return amount;
    }

    public String getItemName() {
        return COINS;
    }

    public int getMissingInventoryCoins(AccountContext context) {
        return Math.max(0, amount - context.inventory().getCount(ItemID.COINS_995));
    }

    public boolean hasEnoughTotalCoins(AccountContext context) {
        return context.inventory().getCount(ItemID.COINS_995)
                + context.bank().getCount(ItemID.COINS_995) >= amount;
    }

    public int getMissingTotalCoins(AccountContext context) {
        return Math.max(0, amount - context.inventory().getCount(ItemID.COINS_995)
                - context.bank().getCount(ItemID.COINS_995));
    }
}
