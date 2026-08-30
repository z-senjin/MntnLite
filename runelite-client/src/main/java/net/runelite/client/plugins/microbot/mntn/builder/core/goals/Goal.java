package net.runelite.client.plugins.microbot.mntn.builder.core.goals;

import net.runelite.client.plugins.microbot.mntn.builder.core.AccountContext;
import net.runelite.client.plugins.microbot.mntn.builder.core.requirements.Requirement;

import java.util.List;

public interface Goal {
    String name();

    boolean isComplete(AccountContext context);

    List<Requirement> requirements(AccountContext context);

    double priority(AccountContext context);
}
