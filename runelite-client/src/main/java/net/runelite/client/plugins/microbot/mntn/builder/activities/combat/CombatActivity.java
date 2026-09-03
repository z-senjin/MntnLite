package net.runelite.client.plugins.microbot.mntn.builder.activities.combat;

import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.mntn.builder.MntnBuilderConfig;
import net.runelite.client.plugins.microbot.mntn.builder.activities.Activity;
import net.runelite.client.plugins.microbot.mntn.builder.activities.ActivityType;
import net.runelite.client.plugins.microbot.mntn.builder.activities.Strategy;
import net.runelite.client.plugins.microbot.mntn.builder.core.AccountContext;
import net.runelite.client.plugins.microbot.mntn.builder.core.requirements.ActivityRequest;

import java.util.ArrayList;
import java.util.List;

public class CombatActivity implements Activity {

    private final MntnBuilderConfig config;

    public CombatActivity(MntnBuilderConfig config) {
        this.config = config;
    }

    @Override
    public ActivityType type() {
        return ActivityType.COMBAT;
    }

    @Override
    public boolean canProvide(ActivityRequest request, AccountContext context) {
        return request.type() == ActivityType.COMBAT;
    }

    @Override
    public List<Strategy> getStrategies(AccountContext context, ActivityRequest request) {
        Skill targetSkill = Skill.STRENGTH;
        if (request.payload() instanceof Skill) {
            Skill s = (Skill) request.payload();
            if (s == Skill.ATTACK || s == Skill.DEFENCE || s == Skill.STRENGTH) {
                targetSkill = s;
            }
        }

        int targetLevel;
        switch (targetSkill) {
            case ATTACK:
                targetLevel = config.attackTarget();
                break;
            case DEFENCE:
                targetLevel = config.defenceTarget();
                break;
            case STRENGTH:
            default:
                targetLevel = config.strengthTarget();
                break;
        }

        int prayerTarget = config.prayerTarget();

        List<Strategy> strategies = new ArrayList<>();
        for (CombatStrategy.Monster monster : CombatStrategy.Monster.values()) {
            strategies.add(new CombatStrategy(monster, targetSkill, targetLevel, prayerTarget));
        }

        return strategies;
    }
}
