package net.runelite.client.plugins.microbot.mntn.builder.core.requirements;

import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.mntn.builder.activities.ActivityType;
import net.runelite.client.plugins.microbot.mntn.builder.core.AccountContext;

import java.util.Collections;
import java.util.List;

public class SkillRequirement implements Requirement {

    private final Skill skill;
    private final int targetLevel;
    private final double urgency;

    public SkillRequirement(Skill skill, int targetLevel, double urgency) {
        this.skill = skill;
        this.targetLevel = targetLevel;
        this.urgency = urgency;
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
        return urgency;
    }

    @Override
    public String description() {
        return "Reach " + skill.getName() + " " + targetLevel;
    }
}
