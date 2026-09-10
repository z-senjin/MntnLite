package net.runelite.client.plugins.microbot.mntn.builder.core.planner;

import net.runelite.client.plugins.microbot.mntn.builder.activities.Activity;
import net.runelite.client.plugins.microbot.mntn.builder.activities.Strategy;
import net.runelite.client.plugins.microbot.mntn.builder.core.goals.Goal;
import net.runelite.client.plugins.microbot.mntn.builder.core.requirements.Requirement;

/**
 * One immediately executable action selected from the current account state.
 */
public class Plan {

    private final Goal goal;
    private final Requirement requirement;
    private final Activity activity;
    private final Strategy strategy;
    private final double score;
    public Plan(Goal goal, Requirement requirement, Activity activity, Strategy strategy, double score) {
        this.goal = goal;
        this.requirement = requirement;
        this.activity = activity;
        this.strategy = strategy;
        this.score = score;
    }

    public Goal goal() {
        return goal;
    }

    public Requirement requirement() {
        return requirement;
    }

    public Activity activity() {
        return activity;
    }

    public Strategy strategy() {
        return strategy;
    }

    public double score() {
        return score;
    }

}
