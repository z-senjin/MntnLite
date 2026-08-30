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

    private final List<Goal> goals;
    private final List<Activity> activities;

    public AccountPlanner(List<Goal> goals, List<Activity> activities) {
        this.goals = goals;
        this.activities = activities;
    }

    public Plan plan(AccountContext context) {
        List<Plan> candidates = new ArrayList<>();

        for (Goal goal : goals) {
            Microbot.log("Goal: " + goal.name());
            if (goal.isComplete(context)) {
                continue;
            }
            for (Requirement requirement : goal.requirements(context)) {

                Microbot.log("Requirement: " + requirement.description());
                if (requirement.isSatisfied(context)) {
                    continue;
                }
                for (ActivityRequest request : requirement.getWaysToSatisfy(context)) {
                    Microbot.log("ActivityRequest: " + request.type().name());
                    for (Activity activity : activities) {
                        Microbot.log("Activity: " + activity.type().name());
                        if (!activity.canProvide(request, context)) {
                            continue;
                        }
                        for (Strategy strategy : activity.getStrategies(context, request)) {
                            Microbot.log("Strategy: " + strategy.name());
                            if (!strategy.canExecute(context)) {
                                continue;
                            }
                            double score = strategy.score(context)
                                    + requirement.urgency(context)
                                    + goal.priority(context);

                            Microbot.log("Score: " + score);
                            candidates.add(new Plan(goal, requirement, activity, strategy, score));
                        }
                    }
                }
            }
        }

        return candidates.stream()
                .max(Comparator.comparingDouble(Plan::score))
                .orElse(null);
    }
}
