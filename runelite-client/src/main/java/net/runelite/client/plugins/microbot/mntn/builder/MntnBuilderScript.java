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
import net.runelite.client.plugins.microbot.mntn.builder.activities.moneymaking.MoneyMakingActivity;
import net.runelite.client.plugins.microbot.mntn.builder.activities.mining.MiningActivity;
import net.runelite.client.plugins.microbot.mntn.builder.activities.questing.QuestingActivity;
import net.runelite.client.plugins.microbot.mntn.builder.activities.smithing.SmithingActivity;
import net.runelite.client.plugins.microbot.mntn.builder.activities.supply.SupplyActivity;
import net.runelite.client.plugins.microbot.mntn.builder.activities.supply.SupplyRoutePolicy;
import net.runelite.client.plugins.microbot.mntn.builder.activities.woodcutting.WoodcuttingActivity;
import net.runelite.client.plugins.microbot.mntn.builder.core.AccountContext;
import net.runelite.client.plugins.microbot.mntn.builder.core.AllowedContent;
import net.runelite.client.plugins.microbot.mntn.builder.core.goals.Goal;
import net.runelite.client.plugins.microbot.mntn.builder.core.goals.MoneyGoal;
import net.runelite.client.plugins.microbot.mntn.builder.core.goals.QuestGoal;
import net.runelite.client.plugins.microbot.mntn.builder.core.goals.SkillGoal;
import net.runelite.client.plugins.microbot.mntn.builder.core.planner.AccountMemory;
import net.runelite.client.plugins.microbot.mntn.builder.core.planner.AccountPlanner;
import net.runelite.client.plugins.microbot.mntn.builder.core.planner.Plan;
import net.runelite.client.plugins.microbot.mntn.builder.core.planner.SessionFlavor;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.Task;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.TaskManager;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.TaskStatus;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.TaskStopReason;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.banking.BankingTask;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.enums.ActivityIntensity;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
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
    private final AccountMemory memory = new AccountMemory();
    private AccountPlanner planner;
    private Plan currentPlan;
    private MntnBuilderConfig config;

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
    private int lastFishingWeight;
    private int lastCookingWeight;
    private int lastWoodcuttingWeight;
    private int lastMiningWeight;
    private int lastSmithingWeight;
    private int lastAttackWeight;
    private int lastStrengthWeight;
    private int lastDefenceWeight;
    private int lastPrayerWeight;
    private int lastMoneyTarget;
    private boolean lastCooksAssistant;
    private boolean lastDoricsQuest;
    private ActivityIntensity lastAntibanIntensity;
    private AllowedContent lastAllowedContent;
    private SessionFlavor lastSessionFlavor;
    private boolean lastShowOverlay;
    private boolean lastDetailedOverlay;
    private boolean lastAllowGrandExchange;
    private boolean lastAllowShops;
    private boolean lastAllowGroundPickups;

    private boolean initialBankDone = false;
    private boolean postStartupReplanPending = false;
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

        addSkillGoal(goals, Skill.FISHING, cfg.fishingTarget(), cfg.fishingWeight());
        addSkillGoal(goals, Skill.COOKING, cfg.cookingTarget(), cfg.cookingWeight());
        addSkillGoal(goals, Skill.WOODCUTTING, cfg.woodcuttingTarget(), cfg.woodcuttingWeight());
        addSkillGoal(goals, Skill.MINING, cfg.miningTarget(), cfg.miningWeight());
        addSkillGoal(goals, Skill.SMITHING, cfg.smithingTarget(), cfg.smithingWeight());
        addSkillGoal(goals, Skill.ATTACK, cfg.attackTarget(), cfg.attackWeight());
        addSkillGoal(goals, Skill.STRENGTH, cfg.strengthTarget(), cfg.strengthWeight());
        addSkillGoal(goals, Skill.DEFENCE, cfg.defenceTarget(), cfg.defenceWeight());
        addSkillGoal(goals, Skill.PRAYER, cfg.prayerTarget(), cfg.prayerWeight());

        if (cfg.moneyTarget() > 0) {
            goals.add(new MoneyGoal(cfg.moneyTarget(), 45));
        }

        if (cfg.enableCooksAssistant()) {
            addQuestGoal(goals, Quest.COOKS_ASSISTANT);
        }

        if (cfg.enableDoricsQuest()) {
            addQuestGoal(goals, Quest.DORICS_QUEST);
        }

        return goals;
    }

    private void addSkillGoal(List<Goal> goals, Skill skill, int targetLevel, int weight) {
        if (targetLevel <= 0) {
            return;
        }
        goals.add(new SkillGoal(skill, targetLevel, priorityFromWeight(weight)));
    }

    private double priorityFromWeight(int weight) {
        int clamped = Math.max(1, Math.min(9, weight));
        return clamped * 10.0;
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
        lastFishingWeight = cfg.fishingWeight();
        lastCookingWeight = cfg.cookingWeight();
        lastWoodcuttingWeight = cfg.woodcuttingWeight();
        lastMiningWeight = cfg.miningWeight();
        lastSmithingWeight = cfg.smithingWeight();
        lastAttackWeight = cfg.attackWeight();
        lastStrengthWeight = cfg.strengthWeight();
        lastDefenceWeight = cfg.defenceWeight();
        lastPrayerWeight = cfg.prayerWeight();
        lastMoneyTarget = cfg.moneyTarget();
        lastCooksAssistant = cfg.enableCooksAssistant();
        lastDoricsQuest = cfg.enableDoricsQuest();
        lastAntibanIntensity = cfg.antibanIntensity();
        lastAllowedContent = cfg.allowedContent();
        lastSessionFlavor = cfg.sessionFlavor();
        lastShowOverlay = cfg.showOverlay();
        lastDetailedOverlay = cfg.detailedOverlay();
        lastAllowGrandExchange = cfg.allowGrandExchange();
        lastAllowShops = cfg.allowShops();
        lastAllowGroundPickups = cfg.allowGroundPickups();
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
                || cfg.fishingWeight() != lastFishingWeight
                || cfg.cookingWeight() != lastCookingWeight
                || cfg.woodcuttingWeight() != lastWoodcuttingWeight
                || cfg.miningWeight() != lastMiningWeight
                || cfg.smithingWeight() != lastSmithingWeight
                || cfg.attackWeight() != lastAttackWeight
                || cfg.strengthWeight() != lastStrengthWeight
                || cfg.defenceWeight() != lastDefenceWeight
                || cfg.prayerWeight() != lastPrayerWeight
                || cfg.moneyTarget() != lastMoneyTarget
                || cfg.enableCooksAssistant() != lastCooksAssistant
                || cfg.enableDoricsQuest() != lastDoricsQuest
                || cfg.antibanIntensity() != lastAntibanIntensity
                || cfg.allowedContent() != lastAllowedContent
                || cfg.sessionFlavor() != lastSessionFlavor
                || cfg.showOverlay() != lastShowOverlay
                || cfg.detailedOverlay() != lastDetailedOverlay
                || cfg.allowGrandExchange() != lastAllowGrandExchange
                || cfg.allowShops() != lastAllowShops
                || cfg.allowGroundPickups() != lastAllowGroundPickups;
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
            planner = new AccountPlanner(updatedGoals, createActivities(newConfig));
            planner.setDebugLogging(debugLogging);
            planner.setAllowedContent(newConfig.allowedContent());
            planner.setMemory(memory);
            planner.setSessionFlavor(newConfig.sessionFlavor());
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
        if (mainScheduledFuture != null && !mainScheduledFuture.isDone()) {
            mainScheduledFuture.cancel(true);
        }
        currentPlan = null;
        taskManager.setTask(null);
        initialBankDone = false;
        postStartupReplanPending = false;
        showWaitingState("Starting", "Preparing planner");

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
                    + ", fishingWeight=" + config.fishingWeight() + ", cookingWeight=" + config.cookingWeight()
                    + ", woodcuttingWeight=" + config.woodcuttingWeight() + ", miningWeight=" + config.miningWeight()
                    + ", smithingWeight=" + config.smithingWeight() + ", attackWeight=" + config.attackWeight()
                    + ", strengthWeight=" + config.strengthWeight() + ", defenceWeight=" + config.defenceWeight()
                    + ", prayerWeight=" + config.prayerWeight()
                    + ", moneyTarget=" + config.moneyTarget()
                    + ", cooksAssistant=" + config.enableCooksAssistant() + ", doricsQuest=" + config.enableDoricsQuest()
                    + ", antibanIntensity=" + config.antibanIntensity()
                    + ", sessionFlavor=" + config.sessionFlavor()
                    + ", showOverlay=" + config.showOverlay()
                    + ", detailedOverlay=" + config.detailedOverlay()
                    + ", allowGrandExchange=" + config.allowGrandExchange()
                    + ", allowShops=" + config.allowShops()
                    + ", allowGroundPickups=" + config.allowGroundPickups());
        }

        List<Goal> goals = buildGoals(config);
        List<Activity> activities = createActivities(config);
        Microbot.log("[MntnBuilder] Starting account builder with goals=" + goals.size()
                + ", activities=" + activities.size()
                + ", content=" + config.allowedContent()
                + ", flavor=" + config.sessionFlavor());
        planner = new AccountPlanner(goals, activities);
        planner.setDebugLogging(debugLogging);
        planner.setAllowedContent(config.allowedContent());
        planner.setMemory(memory);
        planner.setSessionFlavor(config.sessionFlavor());

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
                    showWaitingState("Login", "Waiting for account login");
                    return;
                }

                if (needsPlannerOnlyRecovery()) {
                    debugLog("Running planner-only recovery before script guard");
                    postStartupReplanPending = false;
                    showPlanningState("Selecting next task");
                    replan();
                    return;
                }

                if (!super.run()) {
                    showWaitingState("Paused", "Waiting for Microbot script guard");
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

                if (postStartupReplanPending) {
                    debugLog("Running first normal replan after startup banking");
                    postStartupReplanPending = false;
                    showPlanningState("Selecting first task after bank cache");
                    replan();
                    return;
                }

                // Check if current goal is complete
                if (currentPlan != null && currentPlan.goal().isComplete(context)) {
                    debugLog("Goal reached: " + currentPlan.goal().name() + "! Replanning...");
                    Microbot.log("[MntnBuilder] Goal reached: " + currentPlan.goal().name() + "! Replanning...");
                    finishCurrentPlan(TaskStatus.COMPLETE, TaskStopReason.GOAL_COMPLETE);
                    replan();
                    return;
                }

                if (currentPlan != null && currentPlan.requirement().isSatisfied(context)) {
                    debugLog("Requirement satisfied: " + currentPlan.requirement().description() + ". Replanning...");
                    finishCurrentPlan(TaskStatus.COMPLETE, TaskStopReason.REQUIREMENT_SATISFIED);
                    replan();
                    return;
                }

                if (!taskManager.hasTask()) {
                    debugLog("No active task, replanning");
                    replan();
                    if (!taskManager.hasTask()) {
                        debugLog("Replan did not produce a runnable task this tick");
                        return;
                    }
                } else if (isCommitmentExpired() && !Rs2Dialogue.isInDialogue()) {
                    debugLog("Commitment expired, replanning on timeout");
                    replanOnTimeout();
                    if (!taskManager.hasTask()) {
                        debugLog("Timeout replan did not produce a runnable task this tick");
                        return;
                    }
                } else {
                    debugLog("Continuing current task: " + (taskManager.getCurrentTask() != null ? taskManager.getCurrentTask().describe() : "null"));
                }

                TaskStatus status = taskManager.tick(context);
                debugLog("Task tick returned: " + status);
                if (status.needsPlannerDecision()) {
                    debugLog("Task status requires replan: " + status
                            + " (reason=" + taskManager.getLastStopReason() + ")");
                    finishCurrentPlan(status, terminalStopReason(status));
                    replan();
                    return;
                }

                debugLog("--- Tick end ---");
            } catch (Throwable ex) {
                showScriptError(ex);
                System.out.println(ex.getMessage());
                if (ex instanceof Exception) {
                    Microbot.logStackTrace(this.getClass().getSimpleName(), (Exception) ex);
                } else {
                    Microbot.log("[MntnBuilder] " + ex.getClass().getSimpleName() + ": " + safeErrorMessage(ex));
                }
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    private void finishCurrentPlan(TaskStatus status, TaskStopReason reason) {
        if (currentPlan != null) {
            memory.recordOutcome(currentPlan, status, reason);
        }
        currentPlan = null;
        taskManager.setTask(null);
    }

    private TaskStopReason terminalStopReason(TaskStatus status) {
        TaskStopReason reportedReason = taskManager.getLastStopReason();
        if (status == TaskStatus.COMPLETE && currentPlan != null) {
            if (currentPlan.goal().isComplete(context)) {
                return TaskStopReason.GOAL_COMPLETE;
            }
            if (currentPlan.requirement().isSatisfied(context)) {
                return TaskStopReason.REQUIREMENT_SATISFIED;
            }
            return reportedReason != TaskStopReason.NONE
                    ? reportedReason
                    : TaskStopReason.TASK_REQUESTED_REPLAN;
        }
        return reportedReason != TaskStopReason.NONE
                ? reportedReason
                : TaskStopReason.UNKNOWN;
    }

    private List<Activity> createActivities(MntnBuilderConfig config) {
        return Arrays.asList(
                new FishingActivity(),
                new CookingActivity(),
                new WoodcuttingActivity(),
                new MiningActivity(),
                new SmithingActivity(),
                new QuestingActivity(),
                new CombatActivity(config),
                new MoneyMakingActivity(),
                new SupplyActivity(SupplyRoutePolicy.fromConfig(config))
        );
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

        if (isInitialBankCacheReady()) {
            completeInitialBanking("bank cache ready before startup tick");
            return;
        }

        TaskStatus status = taskManager.tick(context);
        debugLog("Initial banking tick status: " + status);
        if (status == TaskStatus.COMPLETE || isInitialBankCacheReady()) {
            completeInitialBanking(status == TaskStatus.COMPLETE
                    ? "banking task completed"
                    : "bank cache ready after deposit");
        } else if (status.isUnsuccessfulStop()) {
            debugLog("Initial banking failed/replan, clearing task for retry");
            // Startup banking itself failed somehow - clear it so the next tick retries
            // rather than getting stuck forever on a dead task.
            taskManager.setTask(null);
        }
    }

    private boolean isInitialBankCacheReady() {
        if (!Rs2Bank.isOpen()) {
            return false;
        }
        context.bank().refresh();
        return context.inventory().isEmpty() && Rs2Equipment.items().isEmpty();
    }

    private void completeInitialBanking(String reason) {
        debugLog("Initial banking complete (" + reason + ")");
        Microbot.log("[MntnBuilder] Startup banking complete: " + reason);
        initialBankDone = true;
        postStartupReplanPending = true;
        taskManager.setTask(null);
        showPlanningState("Selecting first task after bank cache");
    }

    private boolean needsPlannerOnlyRecovery() {
        return initialBankDone
                && currentPlan == null
                && !taskManager.hasTask()
                && (postStartupReplanPending || "Planning".equals(debugGoal));
    }

    private void showPlanningState(String requirement) {
        debugGoal = "Planning";
        debugRequirement = requirement;
        debugActivity = "-";
        debugStrategy = "-";
        debugTime = null;
        debugTaskStartTime = null;
        debugScore = 0;
    }

    private void showWaitingState(String goal, String requirement) {
        debugGoal = goal;
        debugRequirement = requirement;
        debugActivity = "-";
        debugStrategy = "-";
        debugTime = null;
        debugTaskStartTime = null;
        debugScore = 0;
    }

    private void showScriptError(Throwable ex) {
        debugGoal = "Script error";
        debugRequirement = ex.getClass().getSimpleName() + ": " + safeErrorMessage(ex);
        debugActivity = "-";
        debugStrategy = "-";
        debugTime = null;
        debugTaskStartTime = null;
        debugScore = 0;
    }

    private String safeErrorMessage(Throwable ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return "Check Microbot log";
        }
        return message.length() > 160 ? message.substring(0, 160) : message;
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

    public MntnBuilderOverlayState getOverlayState() {
        Task task = taskManager.getCurrentTask();
        MntnBuilderConfig activeConfig = config;
        String runnerState = isRunning()
                ? (initialBankDone ? "Running" : "Startup")
                : "Stopped";
        return new MntnBuilderOverlayState(
                activeConfig == null || activeConfig.showOverlay(),
                activeConfig == null || activeConfig.detailedOverlay(),
                runnerState,
                debugGoal,
                debugRequirement,
                debugActivity,
                debugStrategy,
                task != null ? task.describe() : "-",
                taskManager.getLastStatus().name(),
                taskManager.getLastStopReason().name(),
                activeConfig != null ? activeConfig.allowedContent().name() : "-",
                activeConfig != null ? activeConfig.sessionFlavor().name() : "-",
                debugTime,
                debugTaskStartTime,
                debugScore
        );
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
        debugTime = adjustedCommitment(plan);
        debugTaskStartTime = java.time.Instant.now();
        debugScore = plan.score();
        memory.recordSelected(plan);

        debugLog("Applied new plan: goal=" + debugGoal + ", requirement=" + debugRequirement
                + ", activity=" + debugActivity + ", strategy=" + debugStrategy
                + ", score=" + debugScore + ", commitment=" + debugTime);
        String selectedMessage = "[MntnPlanner] Selected: " + debugActivity + " / " + debugStrategy
                + " (score=" + debugScore + ") for " + debugRequirement;
        System.out.println(selectedMessage);
        Microbot.log(selectedMessage);
    }

    private Duration adjustedCommitment(Plan plan) {
        Duration base = plan.strategy().commitmentDuration(context);
        SessionFlavor flavor = config != null ? config.sessionFlavor() : SessionFlavor.BALANCED;
        double multiplier = flavor.commitmentMultiplier(plan.activity().type());
        long millis = Math.max(Duration.ofMinutes(1).toMillis(), Math.round(base.toMillis() * multiplier));
        return Duration.ofMillis(millis);
    }

    public void forceReplan() {
        debugLog("Force replan requested via overlay button");
        currentPlan = null;
        taskManager.setTask(null);
        replan();
    }

    private void replan() {
        debugLog("Replanning...");
        Microbot.log("[MntnBuilder] Replanning from goal=" + debugGoal
                + ", need=" + debugRequirement
                + ", hasTask=" + taskManager.hasTask()
                + ", hasPlan=" + (currentPlan != null));
        Plan candidate = planner.plan(context);

        if (candidate == null) {
            String diagnostic = planner.diagnoseNoPlan(context);
            debugLog("Replan: " + diagnostic);
            Microbot.log("[MntnBuilder] " + diagnostic);
            // Nothing left to do - all goals complete or all requirements blocked.
            currentPlan = null;
            taskManager.setTask(null);
            debugGoal = diagnostic.startsWith("All goals complete") ? "All goals complete" : "No runnable plan";
            debugRequirement = diagnostic;
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
            String diagnostic = planner.diagnoseNoPlan(context);
            debugLog("Replan on timeout: " + diagnostic);
            currentPlan = null;
            taskManager.setTask(null);
            debugGoal = diagnostic.startsWith("All goals complete") ? "All goals complete" : "No runnable plan";
            debugRequirement = diagnostic;
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
        questPriorities.clear();
        memory.clear();
        currentPlan = null;
        taskManager.setTask(null);
        initialBankDone = false;
        postStartupReplanPending = false;
        antibanInitialized = false;
    }
}
