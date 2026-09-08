package net.runelite.client.plugins.microbot.mntn.builder.core.planner;

import net.runelite.client.plugins.microbot.mntn.builder.core.AccountContext;

import java.time.Duration;

/** Keeps one verified startup candidate while the client remains open. */
public final class StartupPlanCache {

    private static final Duration MAX_AGE = Duration.ofMinutes(15);
    private static CachedPlan cachedPlan;

    private StartupPlanCache() {
    }

    public static synchronized void remember(String configurationFingerprint, Plan plan) {
        if (configurationFingerprint != null && plan != null) {
            cachedPlan = new CachedPlan(configurationFingerprint, plan, System.currentTimeMillis());
        }
    }

    public static synchronized Plan takeIfUsable(String configurationFingerprint, AccountContext context) {
        if (cachedPlan == null
                || !cachedPlan.configurationFingerprint.equals(configurationFingerprint)
                || System.currentTimeMillis() - cachedPlan.createdAtMs > MAX_AGE.toMillis()) {
            return null;
        }

        Plan plan = cachedPlan.plan;
        if (plan.goal().isComplete(context)
                || plan.requirement().isSatisfied(context)
                || !plan.strategy().canExecute(context)) {
            cachedPlan = null;
            return null;
        }
        return plan;
    }

    static synchronized void clear() {
        cachedPlan = null;
    }

    private static final class CachedPlan {
        private final String configurationFingerprint;
        private final Plan plan;
        private final long createdAtMs;

        private CachedPlan(String configurationFingerprint, Plan plan, long createdAtMs) {
            this.configurationFingerprint = configurationFingerprint;
            this.plan = plan;
            this.createdAtMs = createdAtMs;
        }
    }
}
