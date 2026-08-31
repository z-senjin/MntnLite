package net.runelite.client.plugins.microbot.mntn.builder;

import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.mntn.builder.activities.Activity;
import net.runelite.client.plugins.microbot.mntn.builder.activities.ActivityType;
import net.runelite.client.plugins.microbot.mntn.builder.activities.cooking.CookingActivity;
import net.runelite.client.plugins.microbot.mntn.builder.activities.fishing.FishingActivity;
import net.runelite.client.plugins.microbot.mntn.builder.core.AccountContext;
import net.runelite.client.plugins.microbot.mntn.builder.core.AccountProfile;
import net.runelite.client.plugins.microbot.mntn.builder.core.goals.Goal;
import net.runelite.client.plugins.microbot.mntn.builder.core.planner.AccountPlanner;
import net.runelite.client.plugins.microbot.mntn.builder.core.planner.Plan;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.Task;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.TaskManager;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.TaskStatus;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.banking.BankingTask;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.enums.ActivityIntensity;

import java.time.Duration;
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


    private boolean initialBankDone = false;
    private boolean antibanInitialized = false;

    public String debugGoal = "-";
    public String debugRequirement = "-";
    public String debugActivity = "-";
    public String debugStrategy = "-";
    public Duration debugTime = null;
    public double debugScore = 0;

    public boolean run(MntnBuilderConfig config) {
        // AccountProfile is now the single "what does this account want to become" source -
        // doc section 4. Config values feed the target levels; priority for each is
        // randomized by AccountProfile itself (Rs2Random) so this account's goal-weighting
        // doesn't play out identically to every other account run off the same config.
        AccountProfile profile = AccountProfile.builder()
                .skill(Skill.FISHING, config.fishingTarget())
                .skill(Skill.COOKING, config.cookingTarget())
                // TODO: once Combat/Woodcutting activities exist, add them here the same way:
                //   .skill(Skill.ATTACK, config.attackTarget())
                //   .skill(Skill.STRENGTH, config.strengthTarget())
                //   .skill(Skill.DEFENCE, config.defenceTarget())
                .build();

        List<Goal> goals = profile.toGoals();
        List<Activity> activities = Arrays.asList(
                new FishingActivity(),
                new CookingActivity()
                // TODO: new CombatActivity(), new WoodcuttingActivity()
        );
        planner = new AccountPlanner(goals, activities);

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;

                setupAntiban(config);

                if (!initialBankDone) {
                    runInitialBanking();
                    return;
                }

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

    private void setupAntiban(MntnBuilderConfig config) {
        if (antibanInitialized) {
            return;
        }

        Rs2Antiban.setActivityIntensity(
                config.antibanIntensity()
        );
        Rs2Antiban.setActivityIntensity(ActivityIntensity.MODERATE);

        antibanInitialized = true;
    }

    private void runInitialBanking() {
        if (!taskManager.hasTask()) {
            // DEPOSIT_ALL with no keep-item - this is a startup reset, not mid-activity
            // banking, so there's nothing that needs protecting from being deposited yet.
            // If you want to keep something equipped/held from before the script even
            // started (e.g. a starter axe), swap this for DEPOSIT_ALL_EXCEPT with that name.
            taskManager.setTask(new BankingTask(BankingTask.Mode.DEPOSIT_ALL, null));
            debugGoal = "Startup";
            debugRequirement = "Warm bank cache";
            debugActivity = "-";
            debugStrategy = "Initial banking";
            debugScore = 0;
        }

        TaskStatus status = taskManager.tick(context);
        if (status == TaskStatus.COMPLETE) {
            initialBankDone = true;
        } else if (status == TaskStatus.FAILED || status == TaskStatus.REPLAN) {
            // Startup banking itself failed somehow - clear it so the next tick retries
            // rather than getting stuck forever on a dead task.
            taskManager.setTask(null);
        }
    }

    private void updateAntibanActivity(ActivityType type) {
        switch (type) {

            case FISHING:
                Rs2Antiban.setActivity(
                        net.runelite.client.plugins.microbot.util.antiban.enums.Activity.GENERAL_FISHING
                );
                break;

            case COOKING:
                Rs2Antiban.setActivity(
                        net.runelite.client.plugins.microbot.util.antiban.enums.Activity.GENERAL_COOKING
                );
                break;

            // Later:
            // case WOODCUTTING:
            //     Rs2Antiban.setActivity(
            //         Activity.GENERAL_WOODCUTTING
            //     );
            //     break;

            default:
                break;
        }
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

        boolean shouldSwitch = !taskManager.hasTask()
                || currentPlan == null
                || candidate.score() > currentPlan.score() + COMMITMENT_MARGIN;
        if (!shouldSwitch) {
            return;
        }

        currentPlan = candidate;
        updateAntibanActivity(
                candidate.activity().type()
        );
        Task task = candidate.strategy().createTask(context);
        taskManager.setTask(task);

        debugGoal = candidate.goal().name();
        debugRequirement = candidate.requirement().description();
        debugActivity = candidate.activity().type().name();
        debugStrategy = candidate.strategy().name();
        debugTime = candidate.strategy().commitmentDuration(context);
        debugScore = candidate.score();

        System.out.println("[MntnPlanner] Selected: " + debugActivity + " / " + debugStrategy
                + " (score=" + debugScore + ") for " + debugRequirement);
    }

    @Override
    public void shutdown() {
        super.shutdown();
    }
}
