package net.runelite.client.plugins.microbot.mntn.builder.activities.cooking;

import net.runelite.client.plugins.microbot.mntn.builder.activities.Activity;
import net.runelite.client.plugins.microbot.mntn.builder.activities.ActivityType;
import net.runelite.client.plugins.microbot.mntn.builder.activities.Strategy;
import net.runelite.client.plugins.microbot.mntn.builder.core.AccountContext;
import net.runelite.client.plugins.microbot.mntn.builder.core.requirements.ActivityRequest;

import java.util.ArrayList;
import java.util.List;

public class CookingActivity implements Activity {

    @Override
    public ActivityType type() {
        return ActivityType.COOKING;
    }

    @Override
    public boolean canProvide(
            ActivityRequest request,
            AccountContext context
    ) {
        return request.type() == ActivityType.COOKING;
    }

    @Override
    public List<Strategy> getStrategies(
            AccountContext context,
            ActivityRequest request
    ) {

        List<Strategy> strategies = new ArrayList<>();

        for (CookingStrategy.Method method :
                CookingStrategy.Method.values()) {

            strategies.add(
                    new CookingStrategy(method)
            );
        }

        return strategies;
    }
}