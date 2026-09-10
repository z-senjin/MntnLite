package net.runelite.client.plugins.microbot.mntn.builder;

import net.runelite.api.Skill;

/** Actions the overlay can request without changing the configured account goals. */
enum MntnBuilderOverlayFocus {
    FISHING(Skill.FISHING),
    COOKING(Skill.COOKING),
    FIREMAKING(Skill.FIREMAKING),
    WOODCUTTING(Skill.WOODCUTTING),
    MINING(Skill.MINING),
    SMITHING(Skill.SMITHING),
    CRAFTING(Skill.CRAFTING),
    ATTACK(Skill.ATTACK),
    STRENGTH(Skill.STRENGTH),
    DEFENCE(Skill.DEFENCE),
    PRAYER(Skill.PRAYER),
    QUESTS(null);

    private final Skill skill;

    MntnBuilderOverlayFocus(Skill skill) {
        this.skill = skill;
    }

    Skill skill() {
        return skill;
    }

    boolean isQuestFocus() {
        return this == QUESTS;
    }

    String displayName() {
        return isQuestFocus() ? "Quests" : skill.getName();
    }

    static MntnBuilderOverlayFocus forSkill(Skill skill) {
        if (skill == null) {
            return null;
        }
        for (MntnBuilderOverlayFocus focus : values()) {
            if (focus.skill == skill) {
                return focus;
            }
        }
        return null;
    }
}
