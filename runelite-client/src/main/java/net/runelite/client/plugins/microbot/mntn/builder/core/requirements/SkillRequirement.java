package net.runelite.client.plugins.microbot.mntn.builder.core.requirements;

import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.mntn.builder.activities.ActivityType;
import net.runelite.client.plugins.microbot.mntn.builder.core.AccountContext;

import java.util.Collections;
import java.util.List;

public class SkillRequirement implements Requirement {

    private final Skill skill;
    private final int targetLevel;

    public SkillRequirement(Skill skill, int targetLevel) {
        this.skill = skill;
        this.targetLevel = targetLevel;
    }

    @Override
    public boolean isSatisfied(AccountContext context) {
        return context.getRealLevel(skill) >= targetLevel;
    }

    @Override
    public List<ActivityRequest> getWaysToSatisfy(AccountContext context) {
        ActivityType type = ActivityType.forSkill(skill);
        if (type == null) {
            return Collections.emptyList();
        }
        return Collections.singletonList(new ActivityRequest(type, skill));
    }

    @Override
    public double urgency(AccountContext context) {
        int remaining = Math.max(0, targetLevel - context.getRealLevel(skill));
        return Math.min(25, remaining * 2.0);
    }

    @Override
    public String description() {
        return "Reach " + skill.getName() + " " + targetLevel;
    }
}
