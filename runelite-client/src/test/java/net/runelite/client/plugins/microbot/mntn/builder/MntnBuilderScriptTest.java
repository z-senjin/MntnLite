package net.runelite.client.plugins.microbot.mntn.builder;

import net.runelite.client.plugins.microbot.mntn.builder.core.AccountContext;
import net.runelite.client.plugins.microbot.mntn.builder.core.goals.Goal;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MntnBuilderScriptTest {

    @Test
    public void targetZeroDisablesSkillGoal() {
        TestConfig config = new TestConfig();
        config.fishingTarget = 0;
        config.cookingTarget = 0;
        config.woodcuttingTarget = 0;
        config.miningTarget = 0;
        config.smithingTarget = 0;
        config.attackTarget = 0;
        config.strengthTarget = 0;
        config.defenceTarget = 0;
        config.prayerTarget = 0;

        List<Goal> goals = new MntnBuilderScript().buildGoals(config);

        assertTrue(goals.isEmpty());
    }

    @Test
    public void skillWeightsBecomeGoalPriority() {
        TestConfig config = new TestConfig();
        config.fishingTarget = 20;
        config.cookingTarget = 0;
        config.woodcuttingTarget = 0;
        config.miningTarget = 0;
        config.smithingTarget = 0;
        config.attackTarget = 0;
        config.strengthTarget = 0;
        config.defenceTarget = 0;
        config.prayerTarget = 0;
        config.fishingWeight = 8;

        List<Goal> goals = new MntnBuilderScript().buildGoals(config);

        assertEquals(1, goals.size());
        assertEquals("Fishing 20", goals.get(0).name());
        assertEquals(80.0, goals.get(0).priority(new AccountContext()), 0.01);
    }

    @Test
    public void skillWeightsAreClampedBeforePriority() {
        TestConfig config = new TestConfig();
        config.fishingTarget = 20;
        config.cookingTarget = 0;
        config.woodcuttingTarget = 0;
        config.miningTarget = 0;
        config.smithingTarget = 0;
        config.attackTarget = 0;
        config.strengthTarget = 0;
        config.defenceTarget = 0;
        config.prayerTarget = 0;
        config.fishingWeight = 99;

        List<Goal> goals = new MntnBuilderScript().buildGoals(config);

        assertFalse(goals.isEmpty());
        assertEquals(90.0, goals.get(0).priority(new AccountContext()), 0.01);
    }

    private static class TestConfig implements MntnBuilderConfig {
        private int fishingTarget = 20;
        private int cookingTarget = 20;
        private int woodcuttingTarget = 20;
        private int miningTarget = 20;
        private int smithingTarget = 20;
        private int attackTarget = 20;
        private int strengthTarget = 20;
        private int defenceTarget = 20;
        private int prayerTarget = 20;
        private int fishingWeight = 5;

        @Override
        public int fishingTarget() {
            return fishingTarget;
        }

        @Override
        public int cookingTarget() {
            return cookingTarget;
        }

        @Override
        public int woodcuttingTarget() {
            return woodcuttingTarget;
        }

        @Override
        public int miningTarget() {
            return miningTarget;
        }

        @Override
        public int smithingTarget() {
            return smithingTarget;
        }

        @Override
        public int attackTarget() {
            return attackTarget;
        }

        @Override
        public int strengthTarget() {
            return strengthTarget;
        }

        @Override
        public int defenceTarget() {
            return defenceTarget;
        }

        @Override
        public int prayerTarget() {
            return prayerTarget;
        }

        @Override
        public int fishingWeight() {
            return fishingWeight;
        }

        @Override
        public boolean enableCooksAssistant() {
            return false;
        }

        @Override
        public boolean enableDoricsQuest() {
            return false;
        }
    }
}
