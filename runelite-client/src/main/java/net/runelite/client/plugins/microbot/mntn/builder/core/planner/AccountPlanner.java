package net.runelite.client.plugins.microbot.mntn.builder.core.planner;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.mntn.builder.activities.Activity;
import net.runelite.client.plugins.microbot.mntn.builder.activities.Strategy;
import net.runelite.client.plugins.microbot.mntn.builder.core.AccountContext;
import net.runelite.client.plugins.microbot.mntn.builder.core.AccountSnapshot;
import net.runelite.client.plugins.microbot.mntn.builder.core.AllowedContent;
import net.runelite.client.plugins.microbot.mntn.builder.core.goals.Goal;
import net.runelite.client.plugins.microbot.mntn.builder.core.requirements.ActivityRequest;
import net.runelite.client.plugins.microbot.mntn.builder.core.requirements.Requirement;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

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
        List<Plan> candidates = new ArrayList<>();
        AccountSnapshot snapshot = context.snapshot();

        debugLog("planAll: evaluating " + goals.size() + " goals");

        for (Goal goal : goals) {
            debugLog("  Goal: " + goal.name() + " (complete=" + goal.isComplete(context) + ")");
            if (goal.isComplete(context)) {
                debugLog("    -> skipping (complete)");
                continue;
            }
            for (Requirement requirement : goal.requirements(context)) {

                debugLog("    Requirement: " + requirement.description() + " (satisfied=" + requirement.isSatisfied(context) + ")");
                if (requirement.isSatisfied(context)) {
                    debugLog("      -> skipping (satisfied)");
                    continue;
                }
                for (ActivityRequest request : requirement.getWaysToSatisfy(context)) {
                    debugLog("      ActivityRequest: " + request.type().name());
                    for (Activity activity : activities) {
                        debugLog("        Activity: " + activity.type().name() + " (canProvide=" + activity.canProvide(request, context) + ")");
                        if (!activity.canProvide(request, context)) {
                            continue;
                        }
                        for (Strategy strategy : activity.getStrategies(context, request)) {
                            addStrategyCandidate(candidates, goal, requirement, activity, strategy, context, snapshot, 0);
                        }
                    }
                }
            }
        }

        candidates.sort(Comparator.comparingDouble(Plan::score).reversed());
        debugLog("planAll: " + candidates.size() + " candidates, top=" + (candidates.isEmpty() ? "none" : candidates.get(0).strategy().name() + " score=" + candidates.get(0).score()));
        return candidates;
    }

    public Plan plan(AccountContext context) {
        List<Plan> candidates = planAll(context);
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    public String diagnoseNoPlan(AccountContext context) {
        AccountSnapshot snapshot = context.snapshot();
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

    private boolean addRequirementCandidates(
            List<Plan> candidates,
            Goal goal,
            Requirement missingRequirement,
            AccountContext context,
            AccountSnapshot snapshot,
            int depth
    ) {
        if (depth > MAX_REQUIREMENT_DEPTH) {
            debugLog("          Requirement depth exceeded for " + missingRequirement.description());
            return false;
        }

        boolean added = false;
        for (ActivityRequest supplyRequest : missingRequirement.getWaysToSatisfy(context)) {
            for (Activity supplyActivity : activities) {
                if (!supplyActivity.canProvide(supplyRequest, context)) {
                    continue;
                }
                for (Strategy supplyStrategy : supplyActivity.getStrategies(context, supplyRequest)) {
                    added |= addStrategyCandidate(
                            candidates,
                            goal,
                            missingRequirement,
                            supplyActivity,
                            supplyStrategy,
                            context,
                            snapshot,
                            depth
                    );
                }
            }
        }
        return added;
    }

    private boolean addStrategyCandidate(
            List<Plan> candidates,
            Goal goal,
            Requirement requirement,
            Activity activity,
            Strategy strategy,
            AccountContext context,
            AccountSnapshot snapshot,
            int depth
    ) {
        if (!allowedContent.allows(strategy.contentAccess())) {
            debugLog("          Strategy: " + strategy.name() + " skipped (content access="
                    + strategy.contentAccess() + ", allowed=" + allowedContent + ")");
            return false;
        }
        if (!strategy.contentAccess().isCurrentlyReachable(snapshot)) {
            debugLog("          Strategy: " + strategy.name() + " skipped (members world required)");
            return false;
        }

        Optional<Requirement> missingStrategyRequirement = strategy.requirements(context)
                .stream()
                .filter(strategyRequirement -> !strategyRequirement.isSatisfied(context))
                .findFirst();
        if (missingStrategyRequirement.isPresent()) {
            if (addRequirementCandidates(candidates, goal, missingStrategyRequirement.get(), context, snapshot, depth + 1)) {
                return true;
            }
            debugLog("          Strategy: " + strategy.name()
                    + " has unmet requirement but no prerequisite candidate; evaluating task fallback");
        }

        debugLog("          Strategy: " + strategy.name() + " (canExecute=" + strategy.canExecute(context) + ")");
        if (!strategy.canExecute(context)) {
            return false;
        }

        double score = scorer.score(goal, requirement, activity.type(), strategy, context, memory, sessionFlavor);

        debugLog("            -> Score: " + score + " (strategy=" + strategy.score(context)
                + " + urgency=" + requirement.urgency(context) + " + priority=" + goal.priority(context) + ")");
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
