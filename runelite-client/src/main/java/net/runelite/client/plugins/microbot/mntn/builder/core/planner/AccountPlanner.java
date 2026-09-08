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
import java.util.List;
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
        List<Plan> candidates = new ArrayList<>();
        AccountSnapshot snapshot = contentSnapshot(context);
        int incompleteGoals = 0;
        int unmetRequirements = 0;
        int providerMatches = 0;
        int strategiesConsidered = 0;

        for (Goal goal : goals) {
            boolean goalComplete = goal.isComplete(context);
            if (goalComplete) {
                continue;
            }
            incompleteGoals++;
            for (Requirement requirement : goal.requirements(context)) {
                boolean requirementSatisfied = requirement.isSatisfied(context);
                if (requirementSatisfied) {
                    continue;
                }
                unmetRequirements++;
                for (ActivityRequest request : requirement.getWaysToSatisfy(context)) {
                    for (Activity activity : activities) {
                        boolean canProvide = activity.canProvide(request, context);
                        if (!canProvide) {
                            continue;
                        }
                        providerMatches++;
                        for (Strategy strategy : activity.getStrategies(context, request)) {
                            strategiesConsidered++;
                            addStrategyCandidate(
                                    candidates,
                                    goal,
                                    requirement,
                                    activity,
                                    strategy,
                                    context,
                                    snapshot,
                                    0,
                                    requirement,
                                    activity,
                                    strategy
                            );
                        }
                    }
                }
            }
        }

        candidates.sort(Comparator.comparingDouble(Plan::score).reversed());
        if (debugLogging) {
            debugLog("pass: goals=" + goals.size()
                    + ", incomplete=" + incompleteGoals
                    + ", unmet=" + unmetRequirements
                    + ", providers=" + providerMatches
                    + ", strategies=" + strategiesConsidered
                    + ", candidates=" + candidates.size()
                    + ", top="
                    + (candidates.isEmpty() ? "none" : candidates.get(0).strategy().name()
                    + " score=" + candidates.get(0).score()));
        }
        return candidates;
    }

    public Plan plan(AccountContext context) {
        List<Plan> candidates = planAll(context);
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    /**
     * Advances a prerequisite chain without letting an unrelated strategy replace the
     * original objective between individual supply steps.
     */
    public Plan continuePlan(Plan completedPlan, AccountContext context) {
        return withPlanningSnapshot(context, "objective continuation",
                () -> continuePlanInSnapshot(completedPlan, context));
    }

    private Plan continuePlanInSnapshot(Plan completedPlan, AccountContext context) {
        if (completedPlan == null || !completedPlan.hasPendingObjective()
                || completedPlan.goal().isComplete(context)) {
            return null;
        }

        return planObjective(
                completedPlan.goal(),
                completedPlan.objectiveRequirement(),
                completedPlan.objectiveActivity(),
                completedPlan.objectiveStrategy(),
                context
        );
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

    private Plan planObjective(
            Goal goal,
            Requirement objectiveRequirement,
            Activity objectiveActivity,
            Strategy objectiveStrategy,
            AccountContext context
    ) {
        AccountSnapshot snapshot = contentSnapshot(context);
        if (!allowedContent.allows(objectiveStrategy.contentAccess())
                || !objectiveStrategy.contentAccess().isCurrentlyReachable(snapshot)) {
            return null;
        }

        Optional<Requirement> missingRequirement = objectiveStrategy.requirements(context)
                .stream()
                .filter(requirement -> !requirement.isSatisfied(context))
                .findFirst();
        if (missingRequirement.isPresent()) {
            List<Plan> prerequisites = new ArrayList<>();
            addRequirementCandidates(
                    prerequisites,
                    goal,
                    missingRequirement.get(),
                    context,
                    snapshot,
                    0,
                    objectiveRequirement,
                    objectiveActivity,
                    objectiveStrategy
            );
            prerequisites.sort(Comparator.comparingDouble(Plan::score).reversed());
            return prerequisites.isEmpty() ? null : prerequisites.get(0);
        }

        if (!objectiveStrategy.canExecute(context)) {
            return null;
        }

        double score = scorer.score(
                goal,
                objectiveRequirement,
                objectiveActivity.type(),
                objectiveStrategy,
                context,
                memory,
                sessionFlavor
        );
        return new Plan(
                goal,
                objectiveRequirement,
                objectiveActivity,
                objectiveStrategy,
                score,
                objectiveRequirement,
                objectiveActivity,
                objectiveStrategy
        );
    }

    private boolean addRequirementCandidates(
            List<Plan> candidates,
            Goal goal,
            Requirement missingRequirement,
            AccountContext context,
            AccountSnapshot snapshot,
            int depth,
            Requirement objectiveRequirement,
            Activity objectiveActivity,
            Strategy objectiveStrategy
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
                                objectiveRequirement,
                                objectiveActivity,
                                objectiveStrategy
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

    private boolean addStrategyCandidate(
            List<Plan> candidates,
            Goal goal,
            Requirement requirement,
            Activity activity,
            Strategy strategy,
            AccountContext context,
            AccountSnapshot snapshot,
            int depth,
            Requirement objectiveRequirement,
            Activity objectiveActivity,
            Strategy objectiveStrategy
    ) {
        if (!allowedContent.allows(strategy.contentAccess())) {
            return false;
        }
        if (!strategy.contentAccess().isCurrentlyReachable(snapshot)) {
            return false;
        }

        Optional<Requirement> missingStrategyRequirement = strategy.requirements(context)
                .stream()
                .filter(strategyRequirement -> !strategyRequirement.isSatisfied(context))
                .findFirst();
        if (missingStrategyRequirement.isPresent()) {
            if (addRequirementCandidates(
                    candidates,
                    goal,
                    missingStrategyRequirement.get(),
                    context,
                    snapshot,
                    depth + 1,
                    objectiveRequirement,
                    objectiveActivity,
                    objectiveStrategy
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
        candidates.add(new Plan(
                goal,
                requirement,
                activity,
                strategy,
                score,
                objectiveRequirement,
                objectiveActivity,
                objectiveStrategy
        ));
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
