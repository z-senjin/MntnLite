package net.runelite.client.plugins.microbot.mntn.builder.activities.woodcutting;

import net.runelite.client.plugins.microbot.mntn.builder.activities.Activity;
import net.runelite.client.plugins.microbot.mntn.builder.activities.ActivityType;
import net.runelite.client.plugins.microbot.mntn.builder.activities.Strategy;
import net.runelite.client.plugins.microbot.mntn.builder.core.AccountContext;
import net.runelite.client.plugins.microbot.mntn.builder.core.requirements.ActivityRequest;

import java.util.ArrayList;
import java.util.List;

public class WoodcuttingActivity implements Activity {

    @Override
    public ActivityType type() {
        return ActivityType.WOODCUTTING;
    }

    @Override
    public boolean canProvide(ActivityRequest request, AccountContext context) {
        return request.type() == ActivityType.WOODCUTTING;
    }

    @Override
    public List<Strategy> getStrategies(AccountContext context, ActivityRequest request) {
        List<Strategy> strategies = new ArrayList<>();
        for (WoodcuttingStrategy.Method method : WoodcuttingStrategy.Method.values()) {
            strategies.add(new WoodcuttingStrategy(method));
        }
        return strategies;
    }
}
