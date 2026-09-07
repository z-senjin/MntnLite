package net.runelite.client.plugins.microbot.mntn.builder.core.goals;

import net.runelite.api.ItemID;
import net.runelite.client.plugins.microbot.mntn.builder.core.AccountContext;
import net.runelite.client.plugins.microbot.mntn.builder.core.requirements.MoneyRequirement;
import net.runelite.client.plugins.microbot.mntn.builder.core.requirements.Requirement;

import java.util.Collections;
import java.util.List;

public class MoneyGoal implements Goal {

    private final int amount;
    private final double priority;

    public MoneyGoal(int amount, double priority) {
        this.amount = Math.max(1, amount);
        this.priority = priority;
    }

    @Override
    public String name() {
        return "Save " + amount + " coins";
    }

    @Override
    public boolean isComplete(AccountContext context) {
        return context.inventory().getCount(ItemID.COINS_995)
                + context.bank().getCount(ItemID.COINS_995) >= amount;
    }

    @Override
    public List<Requirement> requirements(AccountContext context) {
        return Collections.singletonList(new MoneyRequirement(amount));
    }

    @Override
    public double priority(AccountContext context) {
        return priority;
    }
}
