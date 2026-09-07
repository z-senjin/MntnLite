package net.runelite.client.plugins.microbot.mntn.builder.core.goals;

import net.runelite.client.plugins.microbot.mntn.builder.core.AccountContext;
import net.runelite.client.plugins.microbot.mntn.builder.core.requirements.ItemRequirement;
import net.runelite.client.plugins.microbot.mntn.builder.core.requirements.Requirement;

import java.util.Collections;
import java.util.List;

public class ItemGoal implements Goal {

    private final String itemName;
    private final int quantity;
    private final double priority;

    public ItemGoal(String itemName, int quantity, double priority) {
        this.itemName = itemName;
        this.quantity = quantity;
        this.priority = priority;
    }

    @Override
    public String name() {
        return "Obtain " + quantity + " x " + itemName;
    }

    @Override
    public boolean isComplete(AccountContext context) {
        return context.inventory().getCount(itemName) >= quantity
                || context.bank().getCount(itemName) >= quantity;
    }

    @Override
    public List<Requirement> requirements(AccountContext context) {
        return Collections.singletonList(new ItemRequirement(itemName, quantity));
    }

    @Override
    public double priority(AccountContext context) {
        return priority;
    }
}
