package net.runelite.client.plugins.microbot.mntn.builder.core.requirements;

import net.runelite.client.plugins.microbot.mntn.builder.core.AccountContext;

import java.util.List;

public interface Requirement {
    boolean isSatisfied(AccountContext context);

    List<ActivityRequest> getWaysToSatisfy(AccountContext context);

    double urgency(AccountContext context);

    /** Human-readable, for the debug overlay/logs. */
    String description();
}
