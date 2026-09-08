package net.runelite.client.plugins.microbot.mntn.builder.core.planner;

import net.runelite.client.plugins.microbot.mntn.builder.activities.Strategy;
import net.runelite.client.plugins.microbot.mntn.builder.activities.ActivityType;
import net.runelite.client.plugins.microbot.mntn.builder.core.AccountContext;
import net.runelite.client.plugins.microbot.mntn.builder.core.goals.Goal;
import net.runelite.client.plugins.microbot.mntn.builder.core.requirements.Requirement;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.TaskStopReason;
import net.runelite.api.coords.WorldPoint;

import java.time.Duration;

/**
 * Central scoring policy for planner candidates.
 *
 * It currently preserves the existing simple formula. As account behavior grows,
 * add travel cost, bank readiness, supply cost, XP/profit estimates, safety,
 * unlock value, and recent repetition penalties here instead of spreading them
 * across AccountPlanner.
 */
public class PlannerScorer {

    private static final Duration RECENT_SELECTION_WINDOW = Duration.ofHours(2);

    public double score(
            Goal goal,
            Requirement requirement,
            ActivityType activityType,
            Strategy strategy,
            AccountContext context,
            AccountMemory memory,
            SessionFlavor sessionFlavor
    ) {
        double score = strategy.score(context)
                + requirement.urgency(context)
                + goal.priority(context);

        if (sessionFlavor != null) {
            score += sessionFlavor.scoreBonus(activityType);
        }
        score += readinessScore(context, strategy);
        score += Math.min(20, strategy.estimatedXpPerHour(context) / 1000.0);
        score += Math.min(30, strategy.estimatedProfitPerHour(context) / 1000.0);
        score += Math.min(30, strategy.unlockValue(context));
        score += strategy.safetyScore(context);
        score -= Math.min(30, strategy.estimatedSupplyCost(context) / 1000.0);

        if (memory == null) {
            return score;
        }

        score -= cooldownPenalty(memory.getRecentStopReason(strategy.name()));

        int recentSelections = memory.recentSelectionCount(strategy.name(), RECENT_SELECTION_WINDOW);
        score -= Math.min(25, recentSelections * 5);

        return score;
    }

    private double readinessScore(AccountContext context, Strategy strategy) {
        double score = context.bank().isCachePopulated() ? 3 : -3;
        WorldPoint current = context.getLocation();
        WorldPoint target = strategy.preferredLocation(context);
        if (current != null && target != null) {
            score -= Math.min(35, current.distanceTo2D(target) / 20.0);
        }
        return score;
    }

    private double cooldownPenalty(TaskStopReason reason) {
        switch (reason) {
            case NONE:
                return 0;
            case MISSING_COINS:
                return 90;
            case MANUAL_SKIP:
                return 80;
            case SHOP_OUT_OF_STOCK:
            case SHOP_UNAVAILABLE:
            case SHOP_ZERO_VALUE:
            case GE_OFFER_FAILED:
                return 75;
            case MISSING_BANK_ITEM:
            case EQUIPMENT_MISSING:
            case MISSING_TOOL:
            case MISSING_SUPPLIES:
                return 65;
            case LEVEL_TOO_LOW:
                return 55;
            case GROUND_ITEM_NOT_FOUND:
            case GROUND_PICKUP_FAILED:
            case RESOURCE_NOT_FOUND:
                return 45;
            case INVENTORY_FULL:
            case BANK_FAILED:
            case GE_COLLECT_FAILED:
            case TASK_REQUESTED_REPLAN:
            case TASK_CREATION_FAILED:
            case PRODUCTION_WIDGET_FAILED:
            case ACTION_FAILED:
            case QUEST_STEP_FAILED:
                return 35;
            case UNSUPPORTED_ROUTE:
            case SHOP_ROUTE_INCOMPLETE:
            case GROUND_ROUTE_INCOMPLETE:
                return 100;
            case NOT_LOGGED_IN:
            case UNKNOWN:
            case REQUIREMENT_SATISFIED:
            case GOAL_COMPLETE:
            default:
                return 60;
        }
    }
}
