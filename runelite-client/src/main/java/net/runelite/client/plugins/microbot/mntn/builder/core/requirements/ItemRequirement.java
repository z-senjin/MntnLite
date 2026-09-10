package net.runelite.client.plugins.microbot.mntn.builder.core.requirements;

import net.runelite.client.plugins.microbot.mntn.builder.activities.ActivityType;
import net.runelite.client.plugins.microbot.mntn.builder.core.AccountContext;

import java.util.Arrays;
import java.util.List;

public class ItemRequirement implements Requirement {

    private final String itemName;
    private final int quantity;

    public ItemRequirement(String itemName, int quantity) {
        this.itemName = itemName;
        this.quantity = Math.max(1, quantity);
    }

    @Override
    public boolean isSatisfied(AccountContext context) {
        return context.inventory().getCount(itemName) >= quantity;
    }

    @Override
    public List<ActivityRequest> getWaysToSatisfy(AccountContext context) {
        return Arrays.asList(
                new ActivityRequest(ActivityType.SUPPLY, this),
                new ActivityRequest(ActivityType.MINING, this),
                new ActivityRequest(ActivityType.WOODCUTTING, this),
                new ActivityRequest(ActivityType.FISHING, this),
                new ActivityRequest(ActivityType.CRAFTING, this)
        );
    }

    @Override
    public double urgency(AccountContext context) {
        int missing = getMissingInventoryQuantity(context);
        return Math.min(30, missing * 3.0);
    }

    @Override
    public String description() {
        return "Have " + quantity + " x " + itemName + " in inventory";
    }

    public String getItemName() {
        return itemName;
    }

    public int getQuantity() {
        return quantity;
    }

    public int getMissingInventoryQuantity(AccountContext context) {
        return Math.max(0, quantity - context.inventory().getCount(itemName));
    }

    /** Quantity still absent after counting the task's inventory and its bank. */
    public int getMissingAccountQuantity(AccountContext context) {
        return Math.max(0, quantity - context.inventory().getCount(itemName) - context.bank().getCount(itemName));
    }

    public boolean isAvailableInBank(AccountContext context) {
        return getMissingAccountQuantity(context) == 0;
    }
}
