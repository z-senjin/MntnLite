package net.runelite.client.plugins.microbot.mntn.builder.core.planner;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.mntn.builder.activities.Activity;
import net.runelite.client.plugins.microbot.mntn.builder.activities.Strategy;
import net.runelite.client.plugins.microbot.mntn.builder.activities.ActivityType;
import net.runelite.client.plugins.microbot.mntn.builder.activities.combat.CombatStrategy;
import net.runelite.client.plugins.microbot.mntn.builder.activities.supply.SupplyRouteType;
import net.runelite.client.plugins.microbot.mntn.builder.activities.supply.SupplyStrategy;
import net.runelite.client.plugins.microbot.mntn.builder.core.AccountContext;
import net.runelite.client.plugins.microbot.mntn.builder.core.AccountSnapshot;
import net.runelite.client.plugins.microbot.mntn.builder.core.AllowedContent;
import net.runelite.client.plugins.microbot.mntn.builder.core.goals.Goal;
import net.runelite.client.plugins.microbot.mntn.builder.core.requirements.ActivityRequest;
import net.runelite.client.plugins.microbot.mntn.builder.core.requirements.ItemRequirement;
import net.runelite.client.plugins.microbot.mntn.builder.core.requirements.Requirement;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Straight implementation of the doc's planner outline (section 14). No caching, no
 * commitment threshold here - that's handled by the caller (MntnBuilderScript) comparing
 * this plan's score against the currently-running plan before switching.
 */
public class AccountPlanner {

    private List<Goal> goals;
    private final List<Activity> activities;
    private boolean debugLogging = false;
    private AllowedContent allowedContent = AllowedContent.F2P_ONLY;
    private final PlannerScorer scorer = new PlannerScorer();
    private AccountMemory memory;
    private SessionFlavor sessionFlavor = SessionFlavor.BALANCED;
    private static final int MAX_REQUIREMENT_DEPTH = 4;

    public AccountPlanner(List<Goal> goals, List<Activity> activities) {
        this.goals = goals;
        this.activities = activities;
    }

    public void setDebugLogging(boolean debugLogging) {
        this.debugLogging = debugLogging;
    }

    public void setAllowedContent(AllowedContent allowedContent) {
        this.allowedContent = allowedContent != null ? allowedContent : AllowedContent.F2P_ONLY;
    }

    public void setMemory(AccountMemory memory) {
        this.memory = memory;
    }

    public void setSessionFlavor(SessionFlavor sessionFlavor) {
        this.sessionFlavor = sessionFlavor != null ? sessionFlavor : SessionFlavor.BALANCED;
    }

    private void debugLog(String message) {
        if (debugLogging) {
            Microbot.log("[MntnBuilder][Planner][DEBUG] " + message);
        }
    }

    public void setGoals(List<Goal> goals) {
        this.goals = goals;
    }

    public List<Goal> getGoals() {
        return goals;
    }

    public List<Plan> planAll(AccountContext context) {
        return withPlanningSnapshot(context, "candidate evaluation", () -> planAllInSnapshot(context));
    }

    private List<Plan> planAllInSnapshot(AccountContext context) {
        return planGoalsInSnapshot(context, goals, "candidate evaluation");
    }

    /** Plans only the supplied temporary goal without mutating the configured account goals. */
    public Plan planForGoal(AccountContext context, Goal goal) {
        if (goal == null) {
            return null;
        }
        List<Plan> candidates = withPlanningSnapshot(context, "overlay focus", () ->
                planGoalsInSnapshot(context, java.util.Collections.singletonList(goal), "overlay focus"));
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    /** Plans only the supplied configured goals, used for the overlay's quest focus. */
    public Plan planForGoals(AccountContext context, List<Goal> requestedGoals) {
        if (requestedGoals == null || requestedGoals.isEmpty()) {
            return null;
        }
        List<Plan> candidates = withPlanningSnapshot(context, "overlay focus", () ->
                planGoalsInSnapshot(context, requestedGoals, "overlay focus"));
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    private List<Plan> planGoalsInSnapshot(AccountContext context, List<Goal> candidateGoals, String operation) {
        AccountSnapshot snapshot = contentSnapshot(context);
        List<Plan> rankedCandidates = rankCandidates(collectCandidates(context, snapshot, false, candidateGoals));
        String selectionStage = "ready";
        if (rankedCandidates.isEmpty()) {
            rankedCandidates = rankCandidates(collectCandidates(context, snapshot, true, candidateGoals));
            selectionStage = "prerequisite";
        }
        if (debugLogging) {
            debugLog(operation + ": stage=" + selectionStage
                    + ", goals=" + candidateGoals.size()
                    + ", candidates=" + rankedCandidates.size()
                    + ", top="
                    + (rankedCandidates.isEmpty() ? "none" : rankedCandidates.get(0).strategy().name()
                    + " score=" + rankedCandidates.get(0).score()));
        }
        return rankedCandidates;
    }

    /**
     * Prefer work that can begin now. Supply is only considered after no productive
     * activity is ready, so the planner acquires one missing input instead of stocking
     * every material and equipment prerequisite across the account.
     */
    private List<Plan> collectCandidates(
            AccountContext context,
            AccountSnapshot snapshot,
            boolean resolvePrerequisites,
            List<Goal> candidateGoals
    ) {
        List<Plan> candidates = new ArrayList<>();
        for (Goal goal : candidateGoals) {
            if (goal.isComplete(context)) {
                continue;
            }
            for (Requirement requirement : goal.requirements(context)) {
                if (requirement.isSatisfied(context)) {
                    continue;
                }
                for (ActivityRequest request : requirement.getWaysToSatisfy(context)) {
                    for (Activity activity : activities) {
                        if (!activity.canProvide(request, context)
                                || (!resolvePrerequisites && activity.type() == ActivityType.SUPPLY)) {
                            continue;
                        }
                        for (Strategy strategy : activity.getStrategies(context, request)) {
                            addStrategyCandidate(
                                    candidates,
                                    goal,
                                    requirement,
                                    activity,
                                    strategy,
                                    context,
                                    snapshot,
                                    0,
                                    resolvePrerequisites
                            );
                        }
                    }
                }
            }
        }
        return candidates;
    }

    public Plan plan(AccountContext context) {
        List<Plan> candidates = planAll(context);
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    /**
     * Returns the safest zero-supply combat task when normal prerequisite planning cannot
     * produce a candidate. This gives a fresh F2P account a recovery route even when the
     * bank cache or a supply route is temporarily unavailable.
     */
    public Plan planCombatBootstrap(AccountContext context) {
        return withPlanningSnapshot(context, "combat bootstrap", () -> planCombatBootstrapInSnapshot(context));
    }

    private Plan planCombatBootstrapInSnapshot(AccountContext context) {
        for (Goal goal : goals) {
            if (goal.isComplete(context)) {
                continue;
            }

            for (Requirement requirement : goal.requirements(context)) {
                if (requirement.isSatisfied(context)) {
                    continue;
                }

                for (ActivityRequest request : requirement.getWaysToSatisfy(context)) {
                    if (request.type() != ActivityType.COMBAT) {
                        continue;
                    }

                    for (Activity activity : activities) {
                        if (activity.type() != ActivityType.COMBAT || !activity.canProvide(request, context)) {
                            continue;
                        }

                        for (Strategy strategy : activity.getStrategies(context, request)) {
                            if (!(strategy instanceof CombatStrategy)
                                    || ((CombatStrategy) strategy).getMonster() != CombatStrategy.Monster.CHICKENS
                                    || !allowedContent.allows(strategy.contentAccess())
                                    || !strategy.canExecute(context)) {
                                continue;
                            }

                            double score = scorer.score(
                                    goal,
                                    requirement,
                                    activity.type(),
                                    strategy,
                                    context,
                                    memory,
                                    sessionFlavor
                            );
                            debugLog("Combat bootstrap selected " + strategy.name()
                                    + " for " + goal.name());
                            return new Plan(goal, requirement, activity, strategy, score);
                        }
                    }
                }
            }
        }
        return null;
    }

    public String diagnoseNoPlan(AccountContext context) {
        return withPlanningSnapshot(context, "no-plan diagnosis", () -> diagnoseNoPlanInSnapshot(context));
    }

    private String diagnoseNoPlanInSnapshot(AccountContext context) {
        AccountSnapshot snapshot = contentSnapshot(context);
        int incompleteGoals = 0;
        String firstBlockedRequirement = null;

        for (Goal goal : goals) {
            if (goal.isComplete(context)) {
                continue;
            }
            incompleteGoals++;

            for (Requirement requirement : goal.requirements(context)) {
                if (requirement.isSatisfied(context)) {
                    continue;
                }
                RequirementDiagnostics diagnostics = diagnoseRequirement(requirement, context, snapshot);
                if (firstBlockedRequirement == null) {
                    firstBlockedRequirement = goal.name() + " / " + requirement.description()
                            + " providers=" + diagnostics.providers
                            + " strategies=" + diagnostics.strategies
                            + " executable=" + diagnostics.executable
                            + " unmet=" + diagnostics.unmetRequirements
                            + " filtered=" + diagnostics.filtered;
                }
            }
        }

        if (incompleteGoals == 0) {
            return "All goals complete";
        }
        if (firstBlockedRequirement == null) {
            return "Incomplete goals found, but all requirements look satisfied";
        }
        return "No runnable plan. Incomplete goals=" + incompleteGoals + ". First blocked: " + firstBlockedRequirement;
    }

    /**
     * F2P strategies are reachable without account-snapshot data. Snapshot capture
     * reads every skill and quest state, so only do it when members strategies may
     * be considered and need the members-world check.
     */
    private AccountSnapshot contentSnapshot(AccountContext context) {
        return allowedContent == AllowedContent.ALL ? context.snapshot() : null;
    }

    private <T> T withPlanningSnapshot(AccountContext context, String operation, Supplier<T> action) {
        long snapshotStartedAt = System.nanoTime();
        context.beginPlanningSnapshot();
        long evaluationStartedAt = System.nanoTime();
        try {
            return action.get();
        } finally {
            logSlowStage("snapshot capture", evaluationStartedAt - snapshotStartedAt);
            logSlowStage(operation, System.nanoTime() - evaluationStartedAt);
            context.endPlanningSnapshot();
        }
    }

    private void logSlowStage(String stage, long elapsedNanos) {
        long elapsedMs = elapsedNanos / 1_000_000L;
        if (elapsedMs >= 250L) {
            Microbot.log("[MntnBuilder] Planner " + stage + " took " + elapsedMs + "ms");
        }
    }

    /**
     * Recursive prerequisite resolution can discover the same actionable route through more
     * than one parent candidate. Retain one best copy so commitment comparison and debug
     * output reflect actual choices rather than traversal duplicates.
     */
    private List<Plan> rankCandidates(List<Plan> candidates) {
        Map<String, Plan> uniqueCandidates = new LinkedHashMap<>();
        for (Plan candidate : candidates) {
            String key = candidate.goal().name() + "|"
                    + candidate.requirement().description() + "|"
                    + candidate.activity().type() + "|"
                    + candidate.strategy().name();
            Plan existing = uniqueCandidates.get(key);
            if (existing == null || candidate.score() > existing.score()) {
                uniqueCandidates.put(key, candidate);
            }
        }

        List<Plan> rankedCandidates = new ArrayList<>(uniqueCandidates.values());
        rankedCandidates.sort(Comparator.comparingDouble(Plan::score).reversed());
        return rankedCandidates;
    }

    private boolean addRequirementCandidates(
            List<Plan> candidates,
            Goal goal,
            Requirement missingRequirement,
            AccountContext context,
            AccountSnapshot snapshot,
            int depth
    ) {
        if (depth > MAX_REQUIREMENT_DEPTH) {
            return false;
        }

        for (int rank = 0; rank <= 3; rank++) {
            List<Plan> rankedCandidates = new ArrayList<>();
            for (ActivityRequest supplyRequest : missingRequirement.getWaysToSatisfy(context)) {
                for (Activity supplyActivity : activities) {
                    if (!supplyActivity.canProvide(supplyRequest, context)) {
                        continue;
                    }
                    for (Strategy supplyStrategy : supplyActivity.getStrategies(context, supplyRequest)) {
                        if (acquisitionRank(missingRequirement, supplyActivity, supplyStrategy) != rank) {
                            continue;
                        }
                        addStrategyCandidate(
                                rankedCandidates,
                                goal,
                                missingRequirement,
                                supplyActivity,
                                supplyStrategy,
                                context,
                                snapshot,
                                depth,
                                true
                        );
                    }
                }
            }
            if (!rankedCandidates.isEmpty()) {
                candidates.addAll(rankedCandidates);
                return true;
            }
        }
        return false;
    }

    /**
     * Prefer resources the account already owns or can gather before spending coins.
     * This keeps early accounts from funding a GE purchase when a usable skill route
     * (for example, mining coal) is already available.
     */
    private int acquisitionRank(Requirement requirement, Activity activity, Strategy strategy) {
        if (!(requirement instanceof ItemRequirement)) {
            return 0;
        }

        if (strategy instanceof SupplyStrategy) {
            SupplyRouteType routeType = ((SupplyStrategy) strategy).routeType();
            switch (routeType) {
                case BANK:
                    return 0;
                case GROUND_ITEM:
                    return 1;
                case SHOP:
                    return 2;
                case GRAND_EXCHANGE:
                    return 3;
                default:
                    return 3;
            }
        }

        switch (activity.type()) {
            case MINING:
            case WOODCUTTING:
            case FISHING:
                return 1;
            default:
                return 2;
        }
    }

    private boolean usesGrandExchange(Strategy strategy) {
        return strategy instanceof SupplyStrategy
                && ((SupplyStrategy) strategy).routeType() == SupplyRouteType.GRAND_EXCHANGE;
    }

    private boolean addStrategyCandidate(
            List<Plan> candidates,
            Goal goal,
            Requirement requirement,
            Activity activity,
            Strategy strategy,
            AccountContext context,
            AccountSnapshot snapshot,
            int depth,
            boolean resolvePrerequisites
    ) {
        if (!allowedContent.allows(strategy.contentAccess())) {
            return false;
        }
        if (!strategy.contentAccess().isCurrentlyReachable(snapshot)) {
            return false;
        }
        if (memory != null && memory.isCoolingDown(strategy.name())) {
            return false;
        }
        if (memory != null && memory.isGrandExchangeUnavailable() && usesGrandExchange(strategy)) {
            return false;
        }

        Optional<Requirement> missingStrategyRequirement = strategy.requirements(context)
                .stream()
                .filter(strategyRequirement -> !strategyRequirement.isSatisfied(context))
                .findFirst();
        if (missingStrategyRequirement.isPresent()) {
            if (!resolvePrerequisites) {
                return false;
            }
            if (addRequirementCandidates(
                    candidates,
                    goal,
                    missingStrategyRequirement.get(),
                    context,
                    snapshot,
                    depth + 1
            )) {
                return true;
            }
            return false;
        }

        boolean canExecute = strategy.canExecute(context);
        if (!canExecute) {
            return false;
        }

        double score = scorer.score(goal, requirement, activity.type(), strategy, context, memory, sessionFlavor);
        candidates.add(new Plan(goal, requirement, activity, strategy, score));
        return true;
    }

    private RequirementDiagnostics diagnoseRequirement(
            Requirement requirement,
            AccountContext context,
            AccountSnapshot snapshot
    ) {
        RequirementDiagnostics diagnostics = new RequirementDiagnostics();
        for (ActivityRequest request : requirement.getWaysToSatisfy(context)) {
            for (Activity activity : activities) {
                if (!activity.canProvide(request, context)) {
                    continue;
                }
                diagnostics.providers++;
                for (Strategy strategy : activity.getStrategies(context, request)) {
                    diagnostics.strategies++;
                    if (!allowedContent.allows(strategy.contentAccess())
                            || !strategy.contentAccess().isCurrentlyReachable(snapshot)) {
                        diagnostics.filtered++;
                        continue;
                    }
                    Optional<Requirement> missing = strategy.requirements(context)
                            .stream()
                            .filter(strategyRequirement -> !strategyRequirement.isSatisfied(context))
                            .findFirst();
                    if (missing.isPresent()) {
                        diagnostics.unmetRequirements++;
                        continue;
                    }
                    if (strategy.canExecute(context)) {
                        diagnostics.executable++;
                    }
                }
            }
        }
        return diagnostics;
    }

    private static class RequirementDiagnostics {
        private int providers;
        private int strategies;
        private int executable;
        private int unmetRequirements;
        private int filtered;
    }
}
