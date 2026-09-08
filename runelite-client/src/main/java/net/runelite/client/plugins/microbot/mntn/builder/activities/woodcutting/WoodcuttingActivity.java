package net.runelite.client.plugins.microbot.mntn.builder.activities.woodcutting;

import net.runelite.client.plugins.microbot.mntn.builder.activities.Activity;
import net.runelite.client.plugins.microbot.mntn.builder.activities.ActivityType;
import net.runelite.client.plugins.microbot.mntn.builder.activities.Strategy;
import net.runelite.client.plugins.microbot.mntn.builder.core.AccountContext;
import net.runelite.client.plugins.microbot.mntn.builder.core.requirements.ActivityRequest;
import net.runelite.client.plugins.microbot.mntn.builder.core.requirements.ItemRequirement;

import java.util.ArrayList;
import java.util.List;

public class WoodcuttingActivity implements Activity {

    @Override
    public ActivityType type() {
        return ActivityType.WOODCUTTING;
    }

    @Override
    public boolean canProvide(ActivityRequest request, AccountContext context) {
        if (request.type() != ActivityType.WOODCUTTING) {
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
        for (WoodcuttingStrategy.Method method : WoodcuttingStrategy.Method.values()) {
            if (request.payload() instanceof ItemRequirement
                    && !method.logItemName.equalsIgnoreCase(((ItemRequirement) request.payload()).getItemName())) {
                continue;
            }
            for (WoodcuttingStrategy.Location location : WoodcuttingStrategy.Location.values()) {
                if (location.method == method) {
                    strategies.add(new WoodcuttingStrategy(method, location));
                }
            }
        }
        return strategies;
    }

    private boolean produces(String itemName) {
        for (WoodcuttingStrategy.Method method : WoodcuttingStrategy.Method.values()) {
            if (method.logItemName.equalsIgnoreCase(itemName)) {
                return true;
            }
        }
        return false;
    }
}
