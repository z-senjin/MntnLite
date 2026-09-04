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
import net.runelite.client.plugins.microbot.mntn.builder.core.goals.QuestGoal;
import net.runelite.client.plugins.microbot.mntn.builder.core.goals.SkillGoal;
import net.runelite.client.plugins.microbot.mntn.builder.core.planner.AccountPlanner;
import net.runelite.client.plugins.microbot.mntn.builder.core.planner.Plan;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.Task;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.TaskManager;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.TaskStatus;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.banking.BankingTask;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.enums.ActivityIntensity;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private MntnBuilderConfig config;

    private final Map<Skill, Double> skillPriorities = new HashMap<>();
    private final Map<Quest, Integer> questPriorities = new HashMap<>();

    // Cached config snapshot to detect modifications even without event triggers
    private int lastFishingTarget;
    private int lastCookingTarget;
    private int lastWoodcuttingTarget;
    private int lastMiningTarget;
    private int lastSmithingTarget;
    private int lastAttackTarget;
    private int lastStrengthTarget;
    private int lastDefenceTarget;
    private int lastPrayerTarget;
    private boolean lastCooksAssistant;
    private boolean lastDoricsQuest;
    private ActivityIntensity lastAntibanIntensity;

    private boolean initialBankDone = false;
    private boolean antibanInitialized = false;

    public String debugGoal = "-";
    public String debugRequirement = "-";
    public String debugActivity = "-";
    public String debugStrategy = "-";
    public Duration debugTime = null;
    public java.time.Instant debugTaskStartTime = null;
    public double debugScore = 0;

    public List<Goal> buildGoals(MntnBuilderConfig cfg) {
        List<Goal> goals = new ArrayList<>();

        addSkillGoal(goals, Skill.FISHING, cfg.fishingTarget());
        addSkillGoal(goals, Skill.COOKING, cfg.cookingTarget());
        addSkillGoal(goals, Skill.WOODCUTTING, cfg.woodcuttingTarget());
        addSkillGoal(goals, Skill.MINING, cfg.miningTarget());
        addSkillGoal(goals, Skill.SMITHING, cfg.smithingTarget());
        addSkillGoal(goals, Skill.ATTACK, cfg.attackTarget());
        addSkillGoal(goals, Skill.STRENGTH, cfg.strengthTarget());
        addSkillGoal(goals, Skill.DEFENCE, cfg.defenceTarget());

        if (cfg.enableCooksAssistant()) {
            addQuestGoal(goals, Quest.COOKS_ASSISTANT);
        }

        if (cfg.enableDoricsQuest()) {
            addQuestGoal(goals, Quest.DORICS_QUEST);
        }

        return goals;
    }

    private void addSkillGoal(List<Goal> goals, Skill skill, int targetLevel) {
        double priority = skillPriorities.computeIfAbsent(skill, s -> (double) Rs2Random.between(40, 60));
        goals.add(new SkillGoal(skill, targetLevel, priority));
    }

    private void addQuestGoal(List<Goal> goals, Quest quest) {
        int priority = questPriorities.computeIfAbsent(quest, q -> Rs2Random.between(40, 60));
        goals.add(new QuestGoal(quest, priority));
    }

    private void updateConfigSnapshot(MntnBuilderConfig cfg) {
        lastFishingTarget = cfg.fishingTarget();
        lastCookingTarget = cfg.cookingTarget();
        lastWoodcuttingTarget = cfg.woodcuttingTarget();
        lastMiningTarget = cfg.miningTarget();
        lastSmithingTarget = cfg.smithingTarget();
        lastAttackTarget = cfg.attackTarget();
        lastStrengthTarget = cfg.strengthTarget();
        lastDefenceTarget = cfg.defenceTarget();
        lastPrayerTarget = cfg.prayerTarget();
        lastCooksAssistant = cfg.enableCooksAssistant();
        lastDoricsQuest = cfg.enableDoricsQuest();
        lastAntibanIntensity = cfg.antibanIntensity();
    }

    private boolean isConfigChanged(MntnBuilderConfig cfg) {
        return cfg.fishingTarget() != lastFishingTarget
                || cfg.cookingTarget() != lastCookingTarget
                || cfg.woodcuttingTarget() != lastWoodcuttingTarget
                || cfg.miningTarget() != lastMiningTarget
                || cfg.smithingTarget() != lastSmithingTarget
                || cfg.attackTarget() != lastAttackTarget
                || cfg.strengthTarget() != lastStrengthTarget
                || cfg.defenceTarget() != lastDefenceTarget
                || cfg.prayerTarget() != lastPrayerTarget
                || cfg.enableCooksAssistant() != lastCooksAssistant
                || cfg.enableDoricsQuest() != lastDoricsQuest
                || cfg.antibanIntensity() != lastAntibanIntensity;
    }

    public void onConfigChanged(MntnBuilderConfig newConfig) {
        this.config = newConfig;
        updateConfigSnapshot(newConfig);

        Microbot.log("[MntnBuilder] Config changed: updating goals and targets");

        List<Goal> updatedGoals = buildGoals(newConfig);
        if (planner != null) {
            planner.setGoals(updatedGoals);
        }

        Rs2Antiban.setActivityIntensity(newConfig.antibanIntensity());

        // If the currently active goal was completed by the config change, replan immediately
        if (currentPlan != null && currentPlan.goal().isComplete(context)) {
            Microbot.log("[MntnBuilder] Current goal completed by config update: " + currentPlan.goal().name() + " -> replanning");
            currentPlan = null;
            taskManager.setTask(null);
            replan();
        } else {
            replan();
        }
    }

    public boolean run(MntnBuilderConfig config) {
        this.config = config;
        updateConfigSnapshot(config);

        List<Goal> goals = buildGoals(config);
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

                // Check for dynamic config updates
                if (this.config != null && isConfigChanged(this.config)) {
                    onConfigChanged(this.config);
                }

                setupAntiban(this.config);

                if (!initialBankDone) {
                    runInitialBanking();
                    return;
                }

                // Check if current goal is complete
                if (currentPlan != null && currentPlan.goal().isComplete(context)) {
                    Microbot.log("[MntnBuilder] Goal reached: " + currentPlan.goal().name() + "! Replanning...");
                    currentPlan = null;
                    taskManager.setTask(null);
                    replan();
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

    public void forceReplan() {
        currentPlan = null;
        taskManager.setTask(null);
        replan();
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
        skillPriorities.clear();
        questPriorities.clear();
        currentPlan = null;
        taskManager.setTask(null);
        initialBankDone = false;
        antibanInitialized = false;
    }
}
