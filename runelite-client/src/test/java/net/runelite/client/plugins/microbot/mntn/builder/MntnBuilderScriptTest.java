package net.runelite.client.plugins.microbot.mntn.builder;

import net.runelite.client.plugins.microbot.mntn.builder.activities.ActivityType;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerState;
import net.runelite.client.plugins.microbot.breakhandler.breakhandlerv2.BreakHandlerV2State;
import net.runelite.client.plugins.microbot.mntn.builder.core.AccountContext;
import net.runelite.client.plugins.microbot.mntn.builder.core.goals.Goal;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;
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
    public void profileGoalPrioritiesAreStableAndBounded() {
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
        MntnBuilderScript script = new MntnBuilderScript();
        List<Goal> goals = script.buildGoalsForProfile(config, "profile-a");

        assertEquals(1, goals.size());
        assertEquals("Fishing 20", goals.get(0).name());
        assertEquals(MntnBuilderScript.profileGoalPriority("profile-a", "FISHING"),
                goals.get(0).priority(new AccountContext()), 0.01);
        assertTrue(goals.get(0).priority(new AccountContext()) >= 45.0);
        assertTrue(goals.get(0).priority(new AccountContext()) <= 55.0);
    }

    @Test
    public void profileGoalPrioritiesSurviveScriptRecreation() {
        TestConfig config = new TestConfig();
        config.cookingTarget = 0;
        config.woodcuttingTarget = 0;
        config.miningTarget = 0;
        config.smithingTarget = 0;
        config.attackTarget = 0;
        config.strengthTarget = 0;
        config.defenceTarget = 0;
        config.prayerTarget = 0;

        double first = new MntnBuilderScript().buildGoalsForProfile(config, "profile-a")
                .get(0).priority(new AccountContext());
        double second = new MntnBuilderScript().buildGoalsForProfile(config, "profile-a")
                .get(0).priority(new AccountContext());

        assertEquals(first, second, 0.01);
        assertTrue(first != MntnBuilderScript.profileGoalPriority("profile-b", "FISHING"));
    }

    @Test
    public void mapsKnownStarterMethodsToSpecificAntibanActivities() {
        assertEquals(Activity.KILLING_CHICKENS,
                MntnBuilderScript.antibanActivityFor(ActivityType.COMBAT, "Chickens_Attack"));
        assertEquals(Activity.CATCHING_SHRIMP_AND_ANCHOVIES,
                MntnBuilderScript.antibanActivityFor(ActivityType.FISHING, "NET_SHRIMP"));
        assertEquals(Activity.CUTTING_OAK_LOGS,
                MntnBuilderScript.antibanActivityFor(ActivityType.WOODCUTTING, "OAK_TREE"));
        assertEquals(Activity.GENERAL_COLLECTING,
                MntnBuilderScript.antibanActivityFor(ActivityType.SUPPLY, "SUPPLY_BANK_Equip Bronze axe"));
    }

    @Test
    public void reportsAntibanGuardReasonWithoutChangingPlanState() {
        boolean originalCooldown = Rs2AntibanSettings.actionCooldownActive;
        boolean originalBreak = Rs2AntibanSettings.microBreakActive;
        try {
            Rs2AntibanSettings.actionCooldownActive = true;
            Rs2AntibanSettings.microBreakActive = false;
            assertEquals("Paused: Antiban cooldown", MntnBuilderScript.scriptGuardState());

            Rs2AntibanSettings.microBreakActive = true;
            assertEquals("Paused: Antiban break", MntnBuilderScript.scriptGuardState());
        } finally {
            Rs2AntibanSettings.actionCooldownActive = originalCooldown;
            Rs2AntibanSettings.microBreakActive = originalBreak;
        }
    }

    @Test
    public void yieldsToEveryActiveBreakHandlerState() {
        assertFalse(MntnBuilderScript.isBreakHandlerOwnershipState(BreakHandlerState.WAITING_FOR_BREAK));
        assertTrue(MntnBuilderScript.isBreakHandlerOwnershipState(BreakHandlerState.BREAK_REQUESTED));
        assertTrue(MntnBuilderScript.isBreakHandlerOwnershipState(BreakHandlerState.INITIATING_BREAK));
        assertTrue(MntnBuilderScript.isBreakHandlerOwnershipState(BreakHandlerState.LOGGED_OUT));
        assertTrue(MntnBuilderScript.isBreakHandlerOwnershipState(BreakHandlerState.BREAK_ENDING));

        assertFalse(MntnBuilderScript.isBreakHandlerOwnershipState(BreakHandlerV2State.WAITING_FOR_BREAK));
        assertTrue(MntnBuilderScript.isBreakHandlerOwnershipState(BreakHandlerV2State.BREAK_REQUESTED));
        assertTrue(MntnBuilderScript.isBreakHandlerOwnershipState(BreakHandlerV2State.INITIATING_BREAK));
        assertTrue(MntnBuilderScript.isBreakHandlerOwnershipState(BreakHandlerV2State.LOGOUT_REQUESTED));
        assertTrue(MntnBuilderScript.isBreakHandlerOwnershipState(BreakHandlerV2State.LOGGED_OUT));
        assertTrue(MntnBuilderScript.isBreakHandlerOwnershipState(BreakHandlerV2State.PROFILE_SWITCHING));
    }

    @Test
    public void runtimeStatusPublishesImmutableOverlaySnapshot() {
        MntnBuilderOverlayState snapshot = new MntnBuilderOverlayState(
                true, true, "Running", "Attack 10", "Attack level 10", "COMBAT",
                "Chickens_Attack", "Combat (Chickens - Attack) - FIGHTING", "RUNNING",
                "NONE", "FREE_TO_PLAY", "BALANCED", null, null, 42.0);
        try {
            MntnBuilderRuntimeStatus.publish(snapshot);

            assertEquals(snapshot, MntnBuilderRuntimeStatus.getLatest());
        } finally {
            MntnBuilderRuntimeStatus.clear();
        }
        assertEquals(null, MntnBuilderRuntimeStatus.getLatest());
    }

    @Test
    public void testOverridesExposeExplicitNormalAndTaskModes() {
        assertFalse(MntnBuilderTestOverride.NORMAL_PLANNER.isActive());
        assertTrue(MntnBuilderTestOverride.COMBAT_CHICKENS_DEFENCE.isActive());
        assertEquals(ActivityType.COMBAT, MntnBuilderTestOverride.COMBAT_CHICKENS_DEFENCE.activityType());
        assertEquals(ActivityType.MONEY_MAKING, MntnBuilderTestOverride.MONEY_CHICKEN_FEATHERS.activityType());
    }

    @Test
    public void forceReplanQueuesWorkInsteadOfRunningPlannerFromCaller() {
        MntnBuilderScript script = new MntnBuilderScript();

        assertFalse(script.isForceReplanRequested());
        script.forceReplan();

        assertTrue(script.isForceReplanRequested());
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
        public boolean enableCooksAssistant() {
            return false;
        }

        @Override
        public boolean enableDoricsQuest() {
            return false;
        }
    }
}
