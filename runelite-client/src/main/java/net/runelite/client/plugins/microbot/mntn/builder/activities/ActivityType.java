package net.runelite.client.plugins.microbot.mntn.builder.activities;

import net.runelite.api.Skill;

public enum ActivityType {
    FISHING,
    COOKING,
    FIREMAKING,
    WOODCUTTING,
    COMBAT,
    BANKING,
    SUPPLY,
    MINING,
    CRAFTING,
    SMITHING,
    QUESTING;

    /** Maps a Skill to the ActivityType that trains it. Extend as you add more activities. */
    public static ActivityType forSkill(Skill skill) {
        switch (skill) {
            case FISHING:
                return FISHING;
            case COOKING:
                return COOKING;
            case FIREMAKING:
                return FIREMAKING;
            case WOODCUTTING:
                return WOODCUTTING;
            case MINING:
                return MINING;
            case CRAFTING:
                return CRAFTING;
            case SMITHING:
                return SMITHING;
            case ATTACK:
            case STRENGTH:
            case DEFENCE:
            case PRAYER:
                return COMBAT;
            default:
                return null;
        }
    }
}
