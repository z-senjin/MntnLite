package net.runelite.client.plugins.microbot.mntn.builder.core.planner;

import net.runelite.client.plugins.microbot.mntn.builder.activities.Activity;
import net.runelite.client.plugins.microbot.mntn.builder.activities.ActivityType;
import net.runelite.client.plugins.microbot.mntn.builder.activities.Strategy;
import net.runelite.client.plugins.microbot.mntn.builder.core.AccountContext;
import net.runelite.client.plugins.microbot.mntn.builder.core.goals.Goal;
import net.runelite.client.plugins.microbot.mntn.builder.core.requirements.ActivityRequest;
import net.runelite.client.plugins.microbot.mntn.builder.core.requirements.Requirement;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.Task;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class StartupPlanCacheTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Before
    public void useTemporaryCacheDirectory() throws Exception {
        StartupPlanCache.setCacheDirectoryForTests(temporaryFolder.newFolder("startup-cache").toPath());
    }

    @After
    public void clearCache() {
        StartupPlanCache.resetCacheDirectoryForTests();
    }

    @Test
    public void persistsMatchingGoalDirection() {
        TestRequirement requirement = new TestRequirement();
        TestStrategy strategy = new TestStrategy();
        Plan plan = new Plan(new TestGoal(), requirement, new TestActivity(), strategy, 10);

        StartupPlanCache.remember("42", "same", plan);

        StartupPlanCache.Direction direction = StartupPlanCache.takeIfUsable("42", "same");
        assertEquals("Test goal", direction.goalName());
    }

    @Test
    public void rejectsChangedConfigurationOrProfile() {
        TestRequirement requirement = new TestRequirement();
        TestStrategy strategy = new TestStrategy();
        Plan plan = new Plan(new TestGoal(), requirement, new TestActivity(), strategy, 10);
        StartupPlanCache.remember("42", "first", plan);

        assertNull(StartupPlanCache.takeIfUsable("42", "changed"));
        assertNull(StartupPlanCache.takeIfUsable("99", "first"));
    }

    private static final class TestGoal implements Goal {
        @Override
        public String name() {
            return "Test goal";
        }

        @Override
        public boolean isComplete(AccountContext context) {
            return false;
        }

        @Override
        public List<Requirement> requirements(AccountContext context) {
            return Collections.emptyList();
        }

        @Override
        public double priority(AccountContext context) {
            return 0;
        }
    }

    private static final class TestRequirement implements Requirement {
        @Override
        public boolean isSatisfied(AccountContext context) {
            return false;
        }

        @Override
        public List<ActivityRequest> getWaysToSatisfy(AccountContext context) {
            return Collections.emptyList();
        }

        @Override
        public double urgency(AccountContext context) {
            return 0;
        }

        @Override
        public String description() {
            return "Test requirement";
        }
    }

    private static final class TestActivity implements Activity {
        @Override
        public ActivityType type() {
            return ActivityType.FISHING;
        }

        @Override
        public boolean canProvide(ActivityRequest request, AccountContext context) {
            return false;
        }

        @Override
        public List<Strategy> getStrategies(AccountContext context, ActivityRequest request) {
            return Collections.emptyList();
        }
    }

    private static final class TestStrategy implements Strategy {
        private boolean executable = true;

        @Override
        public String name() {
            return "Test strategy";
        }

        @Override
        public boolean canExecute(AccountContext context) {
            return executable;
        }

        @Override
        public double score(AccountContext context) {
            return 0;
        }

        @Override
        public Task createTask(AccountContext context) {
            return null;
        }

        @Override
        public Duration commitmentDuration(AccountContext context) {
            return Duration.ofMinutes(1);
        }
    }
}
