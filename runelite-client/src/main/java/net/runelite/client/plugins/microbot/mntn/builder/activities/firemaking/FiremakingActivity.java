package net.runelite.client.plugins.microbot.mntn.builder.activities.firemaking;

import net.runelite.client.plugins.microbot.mntn.builder.activities.Activity;
import net.runelite.client.plugins.microbot.mntn.builder.activities.ActivityType;
import net.runelite.client.plugins.microbot.mntn.builder.activities.Strategy;
import net.runelite.client.plugins.microbot.mntn.builder.core.AccountContext;
import net.runelite.client.plugins.microbot.mntn.builder.core.requirements.ActivityRequest;

import java.util.ArrayList;
import java.util.List;

/** Provides Firemaking training strategies for Firemaking skill goals. */
public class FiremakingActivity implements Activity {

    @Override
    public ActivityType type() {
        return ActivityType.FIREMAKING;
    }

    @Override
    public boolean canProvide(ActivityRequest request, AccountContext context) {
        return request.type() == ActivityType.FIREMAKING;
    }

    @Override
    public List<Strategy> getStrategies(AccountContext context, ActivityRequest request) {
        List<Strategy> strategies = new ArrayList<>();
        for (FiremakingStrategy.Method method : FiremakingStrategy.Method.values()) {
            strategies.add(new FiremakingStrategy(method));
        }
        return strategies;
    }
}
