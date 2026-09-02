package net.runelite.client.plugins.microbot.mntn.builder.activities.mining;

import net.runelite.client.plugins.microbot.mntn.builder.activities.Activity;
import net.runelite.client.plugins.microbot.mntn.builder.activities.ActivityType;
import net.runelite.client.plugins.microbot.mntn.builder.activities.Strategy;
import net.runelite.client.plugins.microbot.mntn.builder.core.AccountContext;
import net.runelite.client.plugins.microbot.mntn.builder.core.requirements.ActivityRequest;

import java.util.ArrayList;
import java.util.List;

public class MiningActivity implements Activity {

    @Override
    public ActivityType type() {
        return ActivityType.MINING;
    }

    @Override
    public boolean canProvide(ActivityRequest request, AccountContext context) {
        return request.type() == ActivityType.MINING;
    }

    @Override
    public List<Strategy> getStrategies(AccountContext context, ActivityRequest request) {
        List<Strategy> strategies = new ArrayList<>();
        for (MiningStrategy.Method method : MiningStrategy.Method.values()) {
            strategies.add(new MiningStrategy(method));
        }
        return strategies;
    }
}
