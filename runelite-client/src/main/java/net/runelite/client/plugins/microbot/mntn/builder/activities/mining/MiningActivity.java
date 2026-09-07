package net.runelite.client.plugins.microbot.mntn.builder.activities.mining;

import net.runelite.client.plugins.microbot.mntn.builder.activities.Activity;
import net.runelite.client.plugins.microbot.mntn.builder.activities.ActivityType;
import net.runelite.client.plugins.microbot.mntn.builder.activities.Strategy;
import net.runelite.client.plugins.microbot.mntn.builder.core.AccountContext;
import net.runelite.client.plugins.microbot.mntn.builder.core.requirements.ActivityRequest;
import net.runelite.client.plugins.microbot.mntn.builder.core.requirements.ItemRequirement;

import java.util.ArrayList;
import java.util.List;

public class MiningActivity implements Activity {

    @Override
    public ActivityType type() {
        return ActivityType.MINING;
    }

    @Override
    public boolean canProvide(ActivityRequest request, AccountContext context) {
        if (request.type() != ActivityType.MINING) {
            return false;
        }
        if (!(request.payload() instanceof ItemRequirement)) {
            return true;
        }
        ItemRequirement requirement = (ItemRequirement) request.payload();
        return produces(requirement.getItemName());
    }

    @Override
    public List<Strategy> getStrategies(AccountContext context, ActivityRequest request) {
        List<Strategy> strategies = new ArrayList<>();
        for (MiningStrategy.Method method : MiningStrategy.Method.values()) {
            if (request.payload() instanceof ItemRequirement
                    && !method.oreItemName.equalsIgnoreCase(((ItemRequirement) request.payload()).getItemName())) {
                continue;
            }
            strategies.add(new MiningStrategy(method));
        }
        return strategies;
    }

    private boolean produces(String itemName) {
        for (MiningStrategy.Method method : MiningStrategy.Method.values()) {
            if (method.oreItemName.equalsIgnoreCase(itemName)) {
                return true;
            }
        }
        return false;
    }
}
