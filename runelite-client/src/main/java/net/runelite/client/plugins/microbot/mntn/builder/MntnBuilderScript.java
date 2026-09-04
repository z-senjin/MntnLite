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
    private boolean debugLogging = false;

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
        boolean wasDebug = this.debugLogging;
        this.config = newConfig;
        this.debugLogging = newConfig.debugLogging();
        updateConfigSnapshot(newConfig);

        if (debugLogging) {
            debugLog("Config changed: debugLogging=" + debugLogging + " (was " + wasDebug + ")");
        }
        Microbot.log("[MntnBuilder] Config changed: updating goals and targets");

        List<Goal> updatedGoals = buildGoals(newConfig);
        if (planner != null) {
            planner.setGoals(updatedGoals);
            planner.setDebugLogging(debugLogging);
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
        this.debugLogging = config.debugLogging();
        updateConfigSnapshot(config);

        if (debugLogging) {
            debugLog("=== MntnBuilderScript started ===");
            debugLog("Config: fishingTarget=" + config.fishingTarget() + ", cookingTarget=" + config.cookingTarget()
                    + ", woodcuttingTarget=" + config.woodcuttingTarget() + ", miningTarget=" + config.miningTarget()
                    + ", smithingTarget=" + config.smithingTarget() + ", attackTarget=" + config.attackTarget()
                    + ", strengthTarget=" + config.strengthTarget() + ", defenceTarget=" + config.defenceTarget()
                    + ", prayerTarget=" + config.prayerTarget()
                    + ", cooksAssistant=" + config.enableCooksAssistant() + ", doricsQuest=" + config.enableDoricsQuest()
                    + ", antibanIntensity=" + config.antibanIntensity());
        }

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
        planner.setDebugLogging(debugLogging);

        if (debugLogging) {
            debugLog("Initialized with " + goals.size() + " goals and " + activities.size() + " activities");
            for (Goal g : goals) {
                debugLog("  Goal: " + g.name() + " (priority=" + g.priority(context) + ")");
            }
        }

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                context.setDebugLogging(debugLogging);

                if (!Microbot.isLoggedIn()) {
                    debugLog("Not logged in, skipping tick");
                    return;
                }
                if (!super.run()) {
                    debugLog("super.run() returned false, skipping tick");
                    return;
                }

                debugLog("--- Tick start ---");

                // Check for dynamic config updates
                if (this.config != null && isConfigChanged(this.config)) {
                    debugLog("Config changed detected, updating...");
                    onConfigChanged(this.config);
                }

                setupAntiban(this.config);

                if (!initialBankDone) {
                    debugLog("Initial banking not done, running initial banking");
                    runInitialBanking();
                    return;
                }

                // Check if current goal is complete
                if (currentPlan != null && currentPlan.goal().isComplete(context)) {
                    debugLog("Goal reached: " + currentPlan.goal().name() + "! Replanning...");
                    Microbot.log("[MntnBuilder] Goal reached: " + currentPlan.goal().name() + "! Replanning...");
                    currentPlan = null;
                    taskManager.setTask(null);
                    replan();
                }

                if (!taskManager.hasTask()) {
                    debugLog("No active task, replanning");
                    replan();
                } else if (isCommitmentExpired() && !Rs2Dialogue.isInDialogue()) {
                    debugLog("Commitment expired, replanning on timeout");
                    replanOnTimeout();
                } else {
                    debugLog("Continuing current task: " + (taskManager.getCurrentTask() != null ? taskManager.getCurrentTask().describe() : "null"));
                }

                TaskStatus status = taskManager.tick(context);
                debugLog("Task tick returned: " + status);
                if (status == TaskStatus.REPLAN || status == TaskStatus.FAILED
                        || status == TaskStatus.COMPLETE) {
                    debugLog("Task status requires replan: " + status);
                    currentPlan = null;
                    taskManager.setTask(null);
                    replan();
                }

                debugLog("--- Tick end ---");
            } catch (Exception ex) {
                System.out.println(ex.getMessage());
                Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    private void debugLog(String message) {
        if (debugLogging) {
            Microbot.log("[MntnBuilder][DEBUG] " + message);
        }
    }

    private void setupAntiban(MntnBuilderConfig config) {
        if (antibanInitialized) {
            debugLog("Antiban already initialized, skipping");
            return;
        }

        debugLog("Initializing antiban with intensity: " + config.antibanIntensity());

        Rs2Antiban.setActivityIntensity(
                config.antibanIntensity()
        );
        Rs2Antiban.setActivityIntensity(ActivityIntensity.MODERATE);

        antibanInitialized = true;
        debugLog("Antiban initialized");
    }

    private void runInitialBanking() {
        if (!taskManager.hasTask()) {
            debugLog("Starting initial banking (DEPOSIT_ALL with equipment)");
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
        debugLog("Initial banking tick status: " + status);
        if (status == TaskStatus.COMPLETE) {
            debugLog("Initial banking complete");
            initialBankDone = true;
        } else if (status == TaskStatus.FAILED || status == TaskStatus.REPLAN) {
            debugLog("Initial banking failed/replan, clearing task for retry");
            // Startup banking itself failed somehow - clear it so the next tick retries
            // rather than getting stuck forever on a dead task.
            taskManager.setTask(null);
        }
    }

    private void updateAntibanActivity(ActivityType type) {
        debugLog("Updating antiban activity to: " + type);
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
                debugLog("Unknown activity type for antiban: " + type);
                break;
        }
    }

    public boolean isCommitmentExpired() {
        if (debugTime == null || debugTaskStartTime == null) {
            return false;
        }
        boolean expired = Duration.between(debugTaskStartTime, java.time.Instant.now()).compareTo(debugTime) >= 0;
        if (expired) {
            debugLog("Commitment expired (elapsed >= " + debugTime + ")");
        }
        return expired;
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

        debugLog("Applied new plan: goal=" + debugGoal + ", requirement=" + debugRequirement
                + ", activity=" + debugActivity + ", strategy=" + debugStrategy
                + ", score=" + debugScore + ", commitment=" + debugTime);
        System.out.println("[MntnPlanner] Selected: " + debugActivity + " / " + debugStrategy
                + " (score=" + debugScore + ") for " + debugRequirement);
    }

    public void forceReplan() {
        debugLog("Force replan requested via overlay button");
        currentPlan = null;
        taskManager.setTask(null);
        replan();
    }

    private void replan() {
        debugLog("Replanning...");
        Plan candidate = planner.plan(context);

        if (candidate == null) {
            debugLog("Replan: no candidates (all goals complete or blocked)");
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

        debugLog("Replan: top candidate: goal=" + candidate.goal().name()
                + ", activity=" + candidate.activity().type()
                + ", strategy=" + candidate.strategy().name()
                + ", score=" + candidate.score());

        boolean shouldSwitch = !taskManager.hasTask()
                || currentPlan == null
                || candidate.score() > currentPlan.score() + COMMITMENT_MARGIN;
        if (!shouldSwitch) {
            debugLog("Replan: keeping current plan (current score=" + (currentPlan != null ? currentPlan.score() : "none")
                    + ", candidate score=" + candidate.score() + ", margin=" + COMMITMENT_MARGIN + ")");
            return;
        }

        debugLog("Replan: switching to new plan");
        applyPlan(candidate);
    }

    private void replanOnTimeout() {
        debugLog("Replanning on timeout...");
        List<Plan> candidates = planner.planAll(context);
        if (candidates.isEmpty()) {
            debugLog("Replan on timeout: no candidates");
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

        debugLog("Commitment expired for " + (currentPlan != null ? currentPlan.strategy().name() : "previous task")
                + ". Moving to next: " + nextPlan.strategy().name());
        System.out.println("[MntnPlanner] Commitment expired for " + (currentPlan != null ? currentPlan.strategy().name() : "previous task")
                + ". Moving to next: " + nextPlan.strategy().name());
        applyPlan(nextPlan);
    }

    @Override
    public void shutdown() {
        debugLog("Shutting down MntnBuilderScript");
        super.shutdown();
        skillPriorities.clear();
        questPriorities.clear();
        currentPlan = null;
        taskManager.setTask(null);
        initialBankDone = false;
        antibanInitialized = false;
    }
}
