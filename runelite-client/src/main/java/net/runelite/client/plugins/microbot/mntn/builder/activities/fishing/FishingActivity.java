package net.runelite.client.plugins.microbot.mntn.builder.activities.fishing;

import net.runelite.client.plugins.microbot.mntn.builder.activities.Activity;
import net.runelite.client.plugins.microbot.mntn.builder.activities.ActivityType;
import net.runelite.client.plugins.microbot.mntn.builder.activities.Strategy;
import net.runelite.client.plugins.microbot.mntn.builder.core.AccountContext;
import net.runelite.client.plugins.microbot.mntn.builder.core.requirements.ActivityRequest;

import java.util.ArrayList;
import java.util.List;

public class FishingActivity implements Activity {

    @Override
    public ActivityType type() {
        return ActivityType.FISHING;
    }

    @Override
    public boolean canProvide(ActivityRequest request, AccountContext context) {
        return request.type() == ActivityType.FISHING;
    }

    @Override
    public List<Strategy> getStrategies(AccountContext context, ActivityRequest request) {
        List<Strategy> strategies = new ArrayList<>();
        for (FishingStrategy.Method method : FishingStrategy.Method.values()) {
            strategies.add(new FishingStrategy(method));
        }
        return strategies;
    }
}
