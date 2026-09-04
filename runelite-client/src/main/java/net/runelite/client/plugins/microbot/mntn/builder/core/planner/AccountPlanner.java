package net.runelite.client.plugins.microbot.mntn.builder.core.planner;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.mntn.builder.activities.Activity;
import net.runelite.client.plugins.microbot.mntn.builder.activities.Strategy;
import net.runelite.client.plugins.microbot.mntn.builder.core.AccountContext;
import net.runelite.client.plugins.microbot.mntn.builder.core.goals.Goal;
import net.runelite.client.plugins.microbot.mntn.builder.core.requirements.ActivityRequest;
import net.runelite.client.plugins.microbot.mntn.builder.core.requirements.Requirement;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Straight implementation of the doc's planner outline (section 14). No caching, no
 * commitment threshold here - that's handled by the caller (MntnBuilderScript) comparing
 * this plan's score against the currently-running plan before switching.
 */
public class AccountPlanner {

    private List<Goal> goals;
    private final List<Activity> activities;
    private boolean debugLogging = false;

    public AccountPlanner(List<Goal> goals, List<Activity> activities) {
        this.goals = goals;
        this.activities = activities;
    }

    public void setDebugLogging(boolean debugLogging) {
        this.debugLogging = debugLogging;
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
                            debugLog("          Strategy: " + strategy.name() + " (canExecute=" + strategy.canExecute(context) + ")");
                            if (!strategy.canExecute(context)) {
                                continue;
                            }
                            double score = strategy.score(context)
                                    + requirement.urgency(context)
                                    + goal.priority(context);

                            debugLog("            -> Score: " + score + " (strategy=" + strategy.score(context) + " + urgency=" + requirement.urgency(context) + " + priority=" + goal.priority(context) + ")");
                            candidates.add(new Plan(goal, requirement, activity, strategy, score));
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
}
