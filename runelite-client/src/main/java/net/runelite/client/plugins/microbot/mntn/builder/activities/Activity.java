package net.runelite.client.plugins.microbot.mntn.builder.activities;

import net.runelite.client.plugins.microbot.mntn.builder.core.AccountContext;
import net.runelite.client.plugins.microbot.mntn.builder.core.requirements.ActivityRequest;

import java.util.List;

public interface Activity {
    ActivityType type();

    boolean canProvide(ActivityRequest request, AccountContext context);

    List<Strategy> getStrategies(AccountContext context, ActivityRequest request);
}
