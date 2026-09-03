package net.runelite.client.plugins.microbot.mntn.builder;

import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.mntn.builder.activities.Activity;
import net.runelite.client.plugins.microbot.mntn.builder.activities.ActivityType;
import net.runelite.api.Quest;
import net.runelite.client.plugins.microbot.mntn.builder.activities.combat.CombatActivity;
import net.runelite.client.plugins.microbot.mntn.builder.activities.cooking.CookingActivity;
import net.runelite.client.plugins.microbot.mntn.builder.activities.fishing.FishingActivity;
import net.runelite.client.plugins.microbot.mntn.builder.activities.mining.MiningActivity;
import net.runelite.client.plugins.microbot.mntn.builder.activities.questing.QuestingActivity;
import net.runelite.client.plugins.microbot.mntn.builder.activities.smithing.SmithingActivity;
import net.runelite.client.plugins.microbot.mntn.builder.activities.woodcutting.WoodcuttingActivity;
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
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;

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
    public java.time.Instant debugTaskStartTime = null;
    public double debugScore = 0;

    public boolean run(MntnBuilderConfig config) {
        // AccountProfile is now the single "what does this account want to become" source -
        // doc section 4. Config values feed the target levels; priority for each is
        // randomized by AccountProfile itself (Rs2Random) so this account's goal-weighting
        // doesn't play out identically to every other account run off the same config.
        AccountProfile.Builder profileBuilder = AccountProfile.builder()
                .skill(Skill.FISHING, config.fishingTarget())
                .skill(Skill.COOKING, config.cookingTarget())
                .skill(Skill.WOODCUTTING, config.woodcuttingTarget())
                .skill(Skill.MINING, config.miningTarget())
                .skill(Skill.SMITHING, config.smithingTarget())
                .skill(Skill.ATTACK, config.attackTarget())
                .skill(Skill.STRENGTH, config.strengthTarget())
                .skill(Skill.DEFENCE, config.defenceTarget());

        if (config.enableCooksAssistant()) {
            profileBuilder.quest(Quest.COOKS_ASSISTANT);
        }

        if (config.enableDoricsQuest()) {
            profileBuilder.quest(Quest.DORICS_QUEST);
        }

        AccountProfile profile = profileBuilder.build();

        List<Goal> goals = profile.toGoals();
        List<Activity> activities = Arrays.asList(
                new FishingActivity(),
                new CookingActivity(),
                new WoodcuttingActivity(),
                new MiningActivity(),
                new SmithingActivity(),
                new QuestingActivity(),
                new CombatActivity(config)
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
                } else if (isCommitmentExpired() && !Rs2Dialogue.isInDialogue()) {
                    replanOnTimeout();
                }

                TaskStatus status = taskManager.tick(context);
                if (status == TaskStatus.REPLAN || status == TaskStatus.FAILED
                        || status == TaskStatus.COMPLETE) {
                    currentPlan = null;
                    taskManager.setTask(null);
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
            // DEPOSIT_ALL with depositEquipment=true - this is a startup reset that
            // clears both inventory and worn equipment into the bank so the planner
            // starts from a clean slate and the bank cache is fully populated.
            taskManager.setTask(new BankingTask(BankingTask.Mode.DEPOSIT_ALL, true));
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

             case WOODCUTTING:
                 Rs2Antiban.setActivity(
                     net.runelite.client.plugins.microbot.util.antiban.enums.Activity.GENERAL_WOODCUTTING
                 );
                 break;

             case MINING:
                 Rs2Antiban.setActivity(
                     net.runelite.client.plugins.microbot.util.antiban.enums.Activity.GENERAL_MINING
                 );
                 break;

             case SMITHING:
                 Rs2Antiban.setActivity(
                     net.runelite.client.plugins.microbot.util.antiban.enums.Activity.GENERAL_SMITHING
                 );
                 break;

             case QUESTING:
                 Rs2Antiban.setActivity(
                     net.runelite.client.plugins.microbot.util.antiban.enums.Activity.GENERAL_COLLECTING
                 );
                 break;

             case COMBAT:
                 Rs2Antiban.setActivity(
                     net.runelite.client.plugins.microbot.util.antiban.enums.Activity.GENERAL_COMBAT
                 );
                 break;

            default:
                break;
        }
    }

    public boolean isCommitmentExpired() {
        if (debugTime == null || debugTaskStartTime == null) {
            return false;
        }
        return Duration.between(debugTaskStartTime, java.time.Instant.now()).compareTo(debugTime) >= 0;
    }

    private void applyPlan(Plan plan) {
        currentPlan = plan;
        updateAntibanActivity(
                plan.activity().type()
        );
        Task task = plan.strategy().createTask(context);
        taskManager.setTask(task);

        debugGoal = plan.goal().name();
        debugRequirement = plan.requirement().description();
        debugActivity = plan.activity().type().name();
        debugStrategy = plan.strategy().name();
        debugTime = plan.strategy().commitmentDuration(context);
        debugTaskStartTime = java.time.Instant.now();
        debugScore = plan.score();

        System.out.println("[MntnPlanner] Selected: " + debugActivity + " / " + debugStrategy
                + " (score=" + debugScore + ") for " + debugRequirement);
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
            debugTime = null;
            debugTaskStartTime = null;
            debugScore = 0;
            return;
        }

        boolean shouldSwitch = !taskManager.hasTask()
                || currentPlan == null
                || candidate.score() > currentPlan.score() + COMMITMENT_MARGIN;
        if (!shouldSwitch) {
            return;
        }

        applyPlan(candidate);
    }

    private void replanOnTimeout() {
        List<Plan> candidates = planner.planAll(context);
        if (candidates.isEmpty()) {
            currentPlan = null;
            taskManager.setTask(null);
            debugGoal = "All goals complete";
            debugRequirement = "-";
            debugActivity = "-";
            debugStrategy = "-";
            debugTime = null;
            debugTaskStartTime = null;
            debugScore = 0;
            return;
        }

        // Look for the best candidate with a different strategy or goal
        Plan nextPlan = null;
        if (currentPlan != null) {
            for (Plan p : candidates) {
                if (!p.strategy().name().equals(currentPlan.strategy().name())) {
                    nextPlan = p;
                    break;
                }
            }
        }

        // If no other strategy is available, refresh the top candidate
        if (nextPlan == null) {
            nextPlan = candidates.get(0);
        }

        System.out.println("[MntnPlanner] Commitment expired for " + (currentPlan != null ? currentPlan.strategy().name() : "previous task")
                + ". Moving to next: " + nextPlan.strategy().name());
        applyPlan(nextPlan);
    }

    @Override
    public void shutdown() {
        super.shutdown();
    }
}
