package net.runelite.client.plugins.microbot.mntn.builder.core.goals;

import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.mntn.builder.core.AccountContext;
import net.runelite.client.plugins.microbot.mntn.builder.core.requirements.Requirement;
import net.runelite.client.plugins.microbot.mntn.builder.core.requirements.SkillRequirement;

import java.util.Collections;
import java.util.List;

public class SkillGoal implements Goal {

    private final Skill skill;
    private final int targetLevel;
    private final double priority;

    public SkillGoal(Skill skill, int targetLevel, double priority) {
        this.skill = skill;
        this.targetLevel = targetLevel;
        this.priority = priority;
    }

    @Override
    public String name() {
        return skill.getName() + " " + targetLevel;
    }

    @Override
    public boolean isComplete(AccountContext context) {
        return context.getRealLevel(skill) >= targetLevel;
    }

    @Override
    public List<Requirement> requirements(AccountContext context) {
        return Collections.singletonList(new SkillRequirement(skill, targetLevel, priority));
    }

    @Override
    public double priority(AccountContext context) {
        return priority;
    }
}
