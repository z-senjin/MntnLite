package net.runelite.client.plugins.microbot.mntn.builder.activities.smithing;

import net.runelite.client.plugins.microbot.mntn.builder.activities.Activity;
import net.runelite.client.plugins.microbot.mntn.builder.activities.ActivityType;
import net.runelite.client.plugins.microbot.mntn.builder.activities.Strategy;
import net.runelite.client.plugins.microbot.mntn.builder.core.AccountContext;
import net.runelite.client.plugins.microbot.mntn.builder.core.requirements.ActivityRequest;

import java.util.ArrayList;
import java.util.List;

public class SmithingActivity implements Activity {

    @Override
    public ActivityType type() {
        return ActivityType.SMITHING;
    }

    @Override
    public boolean canProvide(ActivityRequest request, AccountContext context) {
        return request.type() == ActivityType.SMITHING;
    }

    @Override
    public List<Strategy> getStrategies(AccountContext context, ActivityRequest request) {
        List<Strategy> strategies = new ArrayList<>();
        for (SmeltingStrategy.Bar bar : SmeltingStrategy.Bar.values()) {
            strategies.add(new SmeltingStrategy(bar));
        }
        for (ForgingStrategy.BarType barType : ForgingStrategy.BarType.values()) {
            strategies.add(new ForgingStrategy(barType));
        }
        return strategies;
    }
}
