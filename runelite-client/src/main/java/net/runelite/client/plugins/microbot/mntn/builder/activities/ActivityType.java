package net.runelite.client.plugins.microbot.mntn.builder.activities;

import net.runelite.api.Skill;

public enum ActivityType {
    FISHING,
    COOKING,
    WOODCUTTING,
    COMBAT,
    BANKING;

    /** Maps a Skill to the ActivityType that trains it. Extend as you add more activities. */
    public static ActivityType forSkill(Skill skill) {
        switch (skill) {
            case FISHING:
                return FISHING;
            case COOKING:
                return COOKING;
            case WOODCUTTING:
                return WOODCUTTING;
            case ATTACK:
            case STRENGTH:
            case DEFENCE:
                return COMBAT;
            default:
                return null;
        }
    }
}
