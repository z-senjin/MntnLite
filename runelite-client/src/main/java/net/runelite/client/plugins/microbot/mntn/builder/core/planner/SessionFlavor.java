package net.runelite.client.plugins.microbot.mntn.builder.core.planner;

import net.runelite.client.plugins.microbot.mntn.builder.activities.ActivityType;

public enum SessionFlavor {
    BALANCED,
    QUEST_FOCUSED,
    GATHERER,
    COMBAT_HEAVY,
    EFFICIENT;

    public double scoreBonus(ActivityType activityType) {
        switch (this) {
            case QUEST_FOCUSED:
                return activityType == ActivityType.QUESTING ? 25 : activityType == ActivityType.SUPPLY ? 8 : 0;
            case GATHERER:
                return isGathering(activityType) ? 18 : activityType == ActivityType.SUPPLY ? 6 : -4;
            case COMBAT_HEAVY:
                return activityType == ActivityType.COMBAT ? 22 : activityType == ActivityType.MONEY_MAKING ? 8 : -3;
            case EFFICIENT:
                return activityType == ActivityType.MONEY_MAKING || activityType == ActivityType.QUESTING ? 12 : 4;
            case BALANCED:
            default:
                return 0;
        }
    }

    public double commitmentMultiplier(ActivityType activityType) {
        switch (this) {
            case QUEST_FOCUSED:
                return activityType == ActivityType.QUESTING ? 1.3 : 0.9;
            case GATHERER:
                return isGathering(activityType) ? 1.25 : 0.9;
            case COMBAT_HEAVY:
                return activityType == ActivityType.COMBAT ? 1.25 : 0.9;
            case EFFICIENT:
                return 0.75;
            case BALANCED:
            default:
                return 1.0;
        }
    }

    private static boolean isGathering(ActivityType activityType) {
        return activityType == ActivityType.FISHING
                || activityType == ActivityType.MINING
                || activityType == ActivityType.WOODCUTTING;
    }

    @Override
    public String toString() {
        switch (this) {
            case QUEST_FOCUSED:
                return "Quest focused";
            case GATHERER:
                return "Gatherer";
            case COMBAT_HEAVY:
                return "Combat heavy";
            case EFFICIENT:
                return "Efficient";
            case BALANCED:
            default:
                return "Balanced";
        }
    }
}
