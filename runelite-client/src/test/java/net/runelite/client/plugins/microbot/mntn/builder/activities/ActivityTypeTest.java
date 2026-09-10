package net.runelite.client.plugins.microbot.mntn.builder.activities;

import net.runelite.api.Skill;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ActivityTypeTest {

    @Test
    public void mapsFiremakingSkillToFiremakingActivity() {
        assertEquals(ActivityType.FIREMAKING, ActivityType.forSkill(Skill.FIREMAKING));
    }
}
