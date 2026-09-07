package net.runelite.client.plugins.microbot.mntn.builder.core.requirements;

import net.runelite.client.plugins.microbot.mntn.builder.activities.ActivityType;
import net.runelite.client.plugins.microbot.mntn.builder.core.AccountContext;

import java.util.Collections;
import java.util.List;

public class EquipmentRequirement implements Requirement {

    private final String itemName;

    public EquipmentRequirement(String itemName) {
        this.itemName = itemName;
    }

    @Override
    public boolean isSatisfied(AccountContext context) {
        return context.equipment().hasItem(itemName);
    }

    @Override
    public List<ActivityRequest> getWaysToSatisfy(AccountContext context) {
        return Collections.singletonList(new ActivityRequest(ActivityType.SUPPLY, this));
    }

    @Override
    public double urgency(AccountContext context) {
        if (context.inventory().hasItem(itemName)) {
            return 20;
        }
        if (context.bank().hasItem(itemName)) {
            return 10;
        }
        return 30;
    }

    @Override
    public String description() {
        return "Equip " + itemName;
    }

    public String getItemName() {
        return itemName;
    }

    public boolean isAvailable(AccountContext context) {
        return context.equipment().hasItem(itemName)
                || context.inventory().hasItem(itemName)
                || context.bank().hasItem(itemName);
    }
}
