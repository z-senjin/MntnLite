package net.runelite.client.plugins.microbot.mntn.builder.activities.crafting;

import net.runelite.client.plugins.microbot.mntn.builder.activities.Activity;
import net.runelite.client.plugins.microbot.mntn.builder.activities.ActivityType;
import net.runelite.client.plugins.microbot.mntn.builder.activities.Strategy;
import net.runelite.client.plugins.microbot.mntn.builder.core.AccountContext;
import net.runelite.client.plugins.microbot.mntn.builder.core.requirements.ActivityRequest;
import net.runelite.client.plugins.microbot.mntn.builder.core.requirements.ItemRequirement;

import java.util.ArrayList;
import java.util.List;

/** F2P Crafting methods that use a single explicit input/loadout and action flow. */
public class CraftingActivity implements Activity {

    @Override
    public ActivityType type() {
        return ActivityType.CRAFTING;
    }

    @Override
    public boolean canProvide(ActivityRequest request, AccountContext context) {
        if (request.type() != ActivityType.CRAFTING) {
            return false;
        }
        if (!(request.payload() instanceof ItemRequirement)) {
            return true;
        }
        return produces(((ItemRequirement) request.payload()).getItemName());
    }

    @Override
    public List<Strategy> getStrategies(AccountContext context, ActivityRequest request) {
        List<Strategy> strategies = new ArrayList<>();
        for (CraftingStrategy.Method method : CraftingStrategy.Method.values()) {
            if (request.payload() instanceof ItemRequirement
                    && !method.productName.equalsIgnoreCase(((ItemRequirement) request.payload()).getItemName())) {
                continue;
            }
            strategies.add(new CraftingStrategy(method));
        }
        return strategies;
    }

    private boolean produces(String itemName) {
        for (CraftingStrategy.Method method : CraftingStrategy.Method.values()) {
            if (method.productName.equalsIgnoreCase(itemName)) {
                return true;
            }
        }
        return false;
    }
}
