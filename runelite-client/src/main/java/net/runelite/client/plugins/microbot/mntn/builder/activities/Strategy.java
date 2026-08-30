package net.runelite.client.plugins.microbot.mntn.builder.activities;

import net.runelite.client.plugins.microbot.mntn.builder.core.AccountContext;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.Task;

public interface Strategy {
    String name();

    boolean canExecute(AccountContext context);

    double score(AccountContext context);

    Task createTask(AccountContext context);
}
