package net.runelite.client.plugins.microbot.mntn.builder;

import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.mntn.builder.activities.Activity;
import net.runelite.client.plugins.microbot.mntn.builder.activities.cooking.CookingActivity;
import net.runelite.client.plugins.microbot.mntn.builder.activities.fishing.FishingActivity;
import net.runelite.client.plugins.microbot.mntn.builder.core.AccountContext;
import net.runelite.client.plugins.microbot.mntn.builder.core.goals.Goal;
import net.runelite.client.plugins.microbot.mntn.builder.core.goals.SkillGoal;
import net.runelite.client.plugins.microbot.mntn.builder.core.planner.AccountPlanner;
import net.runelite.client.plugins.microbot.mntn.builder.core.planner.Plan;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.Task;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.TaskManager;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.TaskStatus;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Top-level driver, structurally the same shape as GemCrabKillerScript.run(): one
 * scheduleWithFixedDelay loop wrapped in try/catch with the same isLoggedIn()/super.run()
 * guards. The difference is what's INSIDE the loop - instead of a switch on a hand-written
 * state enum, it asks AccountPlanner what to do and hands the result to TaskManager.
 */
public class MntnBuilderScript extends Script {

    // Don't switch away from the current plan unless a new candidate beats it by this much.
    // Prevents thrashing between two similarly-scored strategies every tick (doc section 8).
    private static final double COMMITMENT_MARGIN = 10.0;

    private final AccountContext context = new AccountContext();
    private final TaskManager taskManager = new TaskManager();
    private AccountPlanner planner;
    private Plan currentPlan;

    // Read by mntn.builderOverlay - same pattern as GemCrabKillerOverlay reading
    // plugin.gemCrabKillerScript.gemCrabKillerState directly.
    public String debugGoal = "-";
    public String debugRequirement = "-";
    public String debugActivity = "-";
    public String debugStrategy = "-";
    public double debugScore = 0;

    public boolean run(MntnBuilderConfig config) {
        List<Goal> goals = Arrays.asList(
                new SkillGoal(Skill.FISHING, config.fishingTarget(), 50),
                new SkillGoal(Skill.COOKING, config.cookingTarget(), 50)
                // TODO: once Fishing -> Bank works end-to-end, add:
                //   new SkillGoal(Skill.COOKING, ..., priority)
                //   new SkillGoal(Skill.ATTACK/STRENGTH/DEFENCE, ..., priority)
        );
        List<Activity> activities = Arrays.asList(
                new FishingActivity(),
                new CookingActivity()
                // TODO: new CookingActivity(), new CombatActivity()
        );
        planner = new AccountPlanner(goals, activities);

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;

                if (!taskManager.hasTask()) {
                    replan();
                }

                TaskStatus status = taskManager.tick(context);
                if (status == TaskStatus.REPLAN || status == TaskStatus.FAILED
                        || status == TaskStatus.COMPLETE) {
                    replan();
                }
            } catch (Exception ex) {
                System.out.println(ex.getMessage());
                Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    private void replan() {
        Plan candidate = planner.plan(context);

        if (candidate == null) {
            // Nothing left to do - all goals complete or all requirements blocked.
            currentPlan = null;
            taskManager.setTask(null);
            debugGoal = "All goals complete";
            debugRequirement = "-";
            debugActivity = "-";
            debugStrategy = "-";
            debugScore = 0;
            return;
        }

        boolean shouldSwitch = currentPlan == null
                || candidate.score() > currentPlan.score() + COMMITMENT_MARGIN;
        if (!shouldSwitch) {
            return;
        }

        currentPlan = candidate;
        Task task = candidate.strategy().createTask(context);
        taskManager.setTask(task);

        debugGoal = candidate.goal().name();
        debugRequirement = candidate.requirement().description();
        debugActivity = candidate.activity().type().name();
        debugStrategy = candidate.strategy().name();
        debugScore = candidate.score();

        System.out.println("[MntnPlanner] Selected: " + debugActivity + " / " + debugStrategy
                + " (score=" + debugScore + ") for " + debugRequirement);
    }

    @Override
    public void shutdown() {
        super.shutdown();
    }
}
