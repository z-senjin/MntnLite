package net.runelite.client.plugins.microbot.mntn.builder.activities;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.mntn.builder.core.AccountContext;
import net.runelite.client.plugins.microbot.mntn.builder.core.ContentAccess;
import net.runelite.client.plugins.microbot.mntn.builder.core.requirements.Requirement;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.Task;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

public interface Strategy {
    String name();

    default ContentAccess contentAccess() {
        return ContentAccess.FREE_TO_PLAY;
    }

    default List<Requirement> requirements(AccountContext context) {
        return Collections.emptyList();
    }

    default WorldPoint preferredLocation(AccountContext context) {
        return null;
    }

    default int estimatedXpPerHour(AccountContext context) {
        return 0;
    }

    default int estimatedProfitPerHour(AccountContext context) {
        return 0;
    }

    default int estimatedSupplyCost(AccountContext context) {
        return 0;
    }

    default double safetyScore(AccountContext context) {
        return 0;
    }

    default double unlockValue(AccountContext context) {
        return 0;
    }

    boolean canExecute(AccountContext context);

    double score(AccountContext context);

    Task createTask(AccountContext context);

    /**
     * How long a single commitment to this strategy should last before the planner forces a
     * fresh re-evaluation - even if nothing else has changed and this same strategy would
     * still win. Called once, at the moment this strategy is selected.
     *
     * Implementations should return a randomized value (e.g. via Rs2Random) so session
     * lengths vary run to run instead of every Fishing session lasting an identical amount
     * of time. This lives on Strategy (not Task, not Activity) so any future strategy -
     * including a future QuestStrategy - gets time-budgeting for free just by implementing
     * this interface, with no quest-specific plumbing needed anywhere else.
     */
    Duration commitmentDuration(AccountContext context);
}
