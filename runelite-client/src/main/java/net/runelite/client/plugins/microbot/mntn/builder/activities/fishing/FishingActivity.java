package net.runelite.client.plugins.microbot.mntn.builder.activities.fishing;

import net.runelite.client.plugins.microbot.mntn.builder.activities.Activity;
import net.runelite.client.plugins.microbot.mntn.builder.activities.ActivityType;
import net.runelite.client.plugins.microbot.mntn.builder.activities.Strategy;
import net.runelite.client.plugins.microbot.mntn.builder.core.AccountContext;
import net.runelite.client.plugins.microbot.mntn.builder.core.requirements.ActivityRequest;
import net.runelite.client.plugins.microbot.mntn.builder.core.requirements.ItemRequirement;

import java.util.ArrayList;
import java.util.List;

public class FishingActivity implements Activity {

    @Override
    public ActivityType type() {
        return ActivityType.FISHING;
    }

    @Override
    public boolean canProvide(ActivityRequest request, AccountContext context) {
        if (request.type() != ActivityType.FISHING) {
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
        for (FishingStrategy.Method method : FishingStrategy.Method.values()) {
            if (request.payload() instanceof ItemRequirement
                    && !method.fishItemName.equalsIgnoreCase(((ItemRequirement) request.payload()).getItemName())) {
                continue;
            }
            for (FishingStrategy.Location location : FishingStrategy.Location.values()) {
                if (location.method == method) {
                    strategies.add(new FishingStrategy(method, location));
                }
            }
        }
        return strategies;
    }

    private boolean produces(String itemName) {
        for (FishingStrategy.Method method : FishingStrategy.Method.values()) {
            if (method.fishItemName.equalsIgnoreCase(itemName)) {
                return true;
            }
        }
        return false;
    }
}
