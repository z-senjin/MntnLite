package net.runelite.client.plugins.microbot.mntn.builder;

import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerPlugin;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerScript;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerState;
import net.runelite.client.plugins.microbot.breakhandler.breakhandlerv2.BreakHandlerV2Plugin;
import net.runelite.client.plugins.microbot.breakhandler.breakhandlerv2.BreakHandlerV2State;
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
import net.runelite.client.plugins.microbot.mntn.builder.tasks.TaskActionGuard;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.TaskManager;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.TaskStatus;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.TaskStopReason;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.banking.BankingTask;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
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
import java.util.concurrent.atomic.AtomicBoolean;

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
    private static final long CONFIG_CHANGE_DEBOUNCE_MS = 750L;
    private static final long SLOW_PLANNER_PASS_MS = 250L;
    private static final int LOGIN_READY_TICKS_REQUIRED = 2;

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
    private MntnBuilderTestOverride lastTestOverride;
    private int lastTestCoinTarget;

    private boolean initialBankDone = false;
    private boolean postStartupReplanPending = false;
    private boolean waitingForScriptGuard = false;
    private java.time.Instant scriptGuardPausedAt;
    private boolean awaitingLoginStabilization;
    private int readyLoginTicks;
    private boolean antibanInitialized = false;
    private boolean debugLogging = false;
    private volatile boolean configRefreshPending;
    private volatile long configRefreshRequestedAtMs;
    private final AtomicBoolean forceReplanRequested = new AtomicBoolean(false);
    private boolean testOverrideFinished;

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
        lastTestOverride = cfg.testOverride();
        lastTestCoinTarget = cfg.testCoinTarget();
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
                || cfg.allowGroundPickups() != lastAllowGroundPickups
                || cfg.testOverride() != lastTestOverride
                || cfg.testCoinTarget() != lastTestCoinTarget;
    }

    public void onConfigChanged(MntnBuilderConfig newConfig) {
        // ConfigChanged is delivered on RuneLite's UI event thread. Only queue the
        // update here; the scheduled script loop owns cache and planner operations.
        this.config = newConfig;
        configRefreshRequestedAtMs = System.currentTimeMillis();
        configRefreshPending = true;
    }

    private boolean isConfigRefreshReady() {
        return configRefreshPending
                && System.currentTimeMillis() - configRefreshRequestedAtMs >= CONFIG_CHANGE_DEBOUNCE_MS;
    }

    private void applyConfigChange() {
        MntnBuilderConfig newConfig = this.config;
        if (newConfig == null) {
            return;
        }

        boolean wasDebug = this.debugLogging;
        boolean plannerConfigurationChanged = isConfigChanged(newConfig);
        configRefreshPending = false;
        this.debugLogging = newConfig.debugLogging();
        updateConfigSnapshot(newConfig);

        if (debugLogging) {
            debugLog("Config changed: debugLogging=" + debugLogging + " (was " + wasDebug + ")");
        }

        if (plannerConfigurationChanged && planner != null) {
            Microbot.log("[MntnBuilder] Applying queued planner configuration update");
            List<Goal> updatedGoals = buildGoals(newConfig);
            planner = new AccountPlanner(updatedGoals, createActivities(newConfig));
            planner.setDebugLogging(debugLogging);
            planner.setAllowedContent(newConfig.allowedContent());
            planner.setMemory(memory);
            planner.setSessionFlavor(newConfig.sessionFlavor());
            currentPlan = null;
            taskManager.setTask(null);
            testOverrideFinished = false;
            showPlanningState(newConfig.testOverride().isActive()
                    ? "Starting selected test"
                    : "Configuration updated");
        }

        Rs2Antiban.setActivityIntensity(newConfig.antibanIntensity());
    }

    public boolean run(MntnBuilderConfig config) {
        if (mainScheduledFuture != null && !mainScheduledFuture.isDone()) {
            mainScheduledFuture.cancel(true);
        }
        currentPlan = null;
        taskManager.setTask(null);
        initialBankDone = false;
        postStartupReplanPending = false;
        waitingForScriptGuard = false;
        scriptGuardPausedAt = null;
        awaitingLoginStabilization = false;
        readyLoginTicks = 0;
        configRefreshPending = false;
        configRefreshRequestedAtMs = 0;
        forceReplanRequested.set(false);
        testOverrideFinished = false;
        showWaitingState("Starting", "Preparing planner");

        this.config = config;
        this.debugLogging = config.debugLogging();
        updateConfigSnapshot(config);
        publishRuntimeStatus();

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

                if (waitForBreakHandlerOwnership() || waitForLoginRecovery()) {
                    return;
                }

                if (!super.run()) {
                    pauseForScriptGuard();
                    debugLog("super.run() returned false, skipping tick");
                    return;
                }
                resumeFromScriptGuard();

                if (processForcedReplanRequest()) {
                    return;
                }

                if (needsPlannerOnlyRecovery()) {
                    debugLog("Running planner-only recovery after script guard");
                    postStartupReplanPending = false;
                    showPlanningState("Selecting next task");
                    replan();
                    return;
                }

                debugLog("--- Tick start ---");

                // Dynamic config changes are applied on this script thread after a
                // short debounce, never in the config panel's UI event handler.
                if (!configRefreshPending && this.config != null && isConfigChanged(this.config)) {
                    configRefreshRequestedAtMs = System.currentTimeMillis();
                    configRefreshPending = true;
                }
                if (isConfigRefreshReady()) {
                    applyConfigChange();
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

                if (isTestOverrideActive()) {
                    runTestOverride();
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
                    if (status == TaskStatus.COMPLETE && continueSatisfiedRequirement()) {
                        return;
                    }
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
            } finally {
                publishRuntimeStatus();
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    /**
     * A task owns its cleanup phases. In particular, a bank withdrawal must be allowed
     * to close the bank before a satisfied prerequisite starts the next task.
     */
    private boolean continueSatisfiedRequirement() {
        if (currentPlan == null || currentPlan.goal().isComplete(context)
                || !currentPlan.requirement().isSatisfied(context)) {
            return false;
        }

        Plan continuation = planner.continuePlan(currentPlan, context);
        if (continuation == null) {
            return false;
        }

        debugLog("Requirement satisfied: " + currentPlan.requirement().description()
                + ". Continuing " + continuation.objectiveStrategy().name() + ".");
        memory.recordOutcome(currentPlan, TaskStatus.COMPLETE, TaskStopReason.REQUIREMENT_SATISFIED);
        return applyPlan(continuation);
    }

    /**
     * A Break Handler owns the entire break window, not merely the logout/login clicks. This
     * lets its safety check observe an idle player instead of competing with an active Builder
     * task, and preserves the Builder task for a stable resume after the break finishes.
     */
    private boolean waitForBreakHandlerOwnership() {
        BreakHandlerV2State v2State = BreakHandlerV2State.getCurrentState();
        if (Microbot.isPluginEnabled(BreakHandlerV2Plugin.class)
                && isBreakHandlerOwnershipState(v2State)) {
            awaitingLoginStabilization = true;
            readyLoginTicks = 0;
            pauseForScriptGuard();
            showWaitingState("Break", "Break Handler V2: " + v2State.getDescription());
            return true;
        }

        BreakHandlerState state = BreakHandlerScript.getCurrentState();
        if (Microbot.isPluginEnabled(BreakHandlerPlugin.class)
                && isBreakHandlerOwnershipState(state)) {
            awaitingLoginStabilization = true;
            readyLoginTicks = 0;
            pauseForScriptGuard();
            showWaitingState("Break", "Break Handler: " + state);
            return true;
        }

        return false;
    }

    /**
     * Require a stable game-ready state before resuming a task interrupted by a logout or a
     * completed Break Handler break.
     */
    private boolean waitForLoginRecovery() {
        if (!Microbot.isLoggedIn()) {
            awaitingLoginStabilization = true;
            readyLoginTicks = 0;
            pauseForScriptGuard();
            showWaitingState("Login", "Waiting for account login");
            return true;
        }

        if (!awaitingLoginStabilization) {
            return false;
        }

        if (!context.isGameplayReady()) {
            readyLoginTicks = 0;
            pauseForScriptGuard();
            showWaitingState("Login", "Waiting for game world to load");
            return true;
        }

        readyLoginTicks++;
        if (readyLoginTicks < LOGIN_READY_TICKS_REQUIRED) {
            pauseForScriptGuard();
            showWaitingState("Login", "Confirming game is ready");
            return true;
        }

        awaitingLoginStabilization = false;
        readyLoginTicks = 0;
        return false;
    }

    static boolean isBreakHandlerOwnershipState(BreakHandlerState state) {
        return state != null && state != BreakHandlerState.WAITING_FOR_BREAK;
    }

    static boolean isBreakHandlerOwnershipState(BreakHandlerV2State state) {
        return state != null && state != BreakHandlerV2State.WAITING_FOR_BREAK;
    }

    private void finishCurrentPlan(TaskStatus status, TaskStopReason reason) {
        if (currentPlan != null) {
            memory.recordOutcome(currentPlan, status, reason);
            if (status == TaskStatus.COMPLETE) {
                applyPlanBoundaryAntiban();
            }
        }
        currentPlan = null;
        taskManager.setTask(null);
    }

    private boolean isTestOverrideActive() {
        return config != null && config.testOverride().isActive();
    }

    private void runTestOverride() {
        MntnBuilderTestOverride testOverride = config.testOverride();
        if (testOverrideFinished) {
            return;
        }

        if (!taskManager.hasTask()) {
            Task task = testOverride.createTask(context, config.testCoinTarget());
            if (task == null) {
                testOverrideFinished = true;
                showWaitingState("Test complete", testOverride.displayName());
                return;
            }

            currentPlan = null;
            taskManager.setTask(task);
            debugGoal = "Test override";
            debugRequirement = testOverride.displayName();
            debugActivity = testOverride.activityType().name();
            debugStrategy = testOverride.name();
            debugTime = null;
            debugTaskStartTime = java.time.Instant.now();
            debugScore = 0;
            Rs2Antiban.setActivity(antibanActivityFor(testOverride.activityType(), testOverride.name()));
            Rs2Antiban.setActivityIntensity(config.antibanIntensity());
            Microbot.log("[MntnBuilder] Starting test override: " + testOverride.displayName());
        }

        TaskStatus status = taskManager.tick(context);
        if (status.clearsTask()) {
            testOverrideFinished = true;
            debugGoal = status == TaskStatus.COMPLETE ? "Test complete" : "Test stopped";
            debugRequirement = testOverride.displayName();
            debugTime = null;
            debugTaskStartTime = null;
            Microbot.log("[MntnBuilder] Test override ended: " + testOverride.displayName()
                    + " (" + status + ", " + taskManager.getLastStopReason() + ")");
        }
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
                new MoneyMakingActivity(SupplyRoutePolicy.fromConfig(config)),
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

    private void updateAntibanActivity(Plan plan) {
        net.runelite.client.plugins.microbot.util.antiban.enums.Activity activity = antibanActivityFor(
                plan.activity().type(), plan.strategy().name());
        Rs2Antiban.setActivity(activity);

        // setActivity applies the activity's default intensity. Restore the builder's
        // configured intensity so the user's builder setting is honored per plan.
        Rs2Antiban.setActivityIntensity(config != null ? config.antibanIntensity() : ActivityIntensity.MODERATE);
        debugLog("Antiban activity=" + activity + ", intensity=" + Rs2Antiban.getActivityIntensity());
    }

    static net.runelite.client.plugins.microbot.util.antiban.enums.Activity antibanActivityFor(
            ActivityType type, String strategyName) {
        String strategy = strategyName != null ? strategyName : "";
        switch (type) {
            case COMBAT:
                if (strategy.startsWith("Chickens_")) {
                    return net.runelite.client.plugins.microbot.util.antiban.enums.Activity.KILLING_CHICKENS;
                }
                if (strategy.startsWith("Cows_")) {
                    return net.runelite.client.plugins.microbot.util.antiban.enums.Activity.KILLING_COWS_AND_TANNING_COWHIDE;
                }
                return net.runelite.client.plugins.microbot.util.antiban.enums.Activity.GENERAL_COMBAT;
            case FISHING:
                return "NET_SHRIMP".equals(strategy)
                        ? net.runelite.client.plugins.microbot.util.antiban.enums.Activity.CATCHING_SHRIMP_AND_ANCHOVIES
                        : net.runelite.client.plugins.microbot.util.antiban.enums.Activity.GENERAL_FISHING;
            case WOODCUTTING:
                if ("OAK_TREE".equals(strategy)) {
                    return net.runelite.client.plugins.microbot.util.antiban.enums.Activity.CUTTING_OAK_LOGS;
                }
                if ("WILLOW_TREE".equals(strategy)) {
                    return net.runelite.client.plugins.microbot.util.antiban.enums.Activity.CUTTING_WILLOW_LOGS;
                }
                return net.runelite.client.plugins.microbot.util.antiban.enums.Activity.GENERAL_WOODCUTTING;
            case MINING:
                if ("IRON_ORE".equals(strategy)) {
                    return net.runelite.client.plugins.microbot.util.antiban.enums.Activity.MINING_IRON_ORE_FREE_TO_PLAY;
                }
                if ("COAL_ORE".equals(strategy)) {
                    return net.runelite.client.plugins.microbot.util.antiban.enums.Activity.MINING_COAL_FREE_TO_PLAY;
                }
                return net.runelite.client.plugins.microbot.util.antiban.enums.Activity.GENERAL_MINING;
            case COOKING:
                return net.runelite.client.plugins.microbot.util.antiban.enums.Activity.GENERAL_COOKING;
            case SMITHING:
                if (strategy.contains("BRONZE")) {
                    return net.runelite.client.plugins.microbot.util.antiban.enums.Activity.SMELTING_BRONZE_BARS;
                }
                if (strategy.contains("IRON")) {
                    return net.runelite.client.plugins.microbot.util.antiban.enums.Activity.SMELTING_IRON_BARS;
                }
                return net.runelite.client.plugins.microbot.util.antiban.enums.Activity.GENERAL_SMITHING;
            case MONEY_MAKING:
            case SUPPLY:
            case QUESTING:
            default:
                return net.runelite.client.plugins.microbot.util.antiban.enums.Activity.GENERAL_COLLECTING;
        }
    }

    private void applyPlanBoundaryAntiban() {
        if (!Rs2AntibanSettings.antibanEnabled) {
            return;
        }

        // The global antiban panel owns these preferences. The builder only asks for
        // an already-enabled behavior at a safe boundary between completed plans.
        if (Rs2AntibanSettings.usePlayStyle && !Rs2AntibanSettings.actionCooldownActive) {
            Rs2Antiban.actionCooldown();
        }
        if (Rs2AntibanSettings.takeMicroBreaks
                && !Rs2AntibanSettings.microBreakActive
                && Microbot.isPluginEnabled(BreakHandlerPlugin.class)) {
            Rs2Antiban.takeMicroBreakByChance();
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
        java.time.Instant overlayTaskStartTime = taskStartTimeForOverlay();
        String runnerState = !isRunning()
                ? "Stopped"
                : awaitingLoginStabilization
                ? "Waiting for login"
                : waitingForScriptGuard
                ? scriptGuardState()
                : initialBankDone ? "Running" : "Startup";
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
                overlayTaskStartTime,
                debugScore
        );
    }

    private void publishRuntimeStatus() {
        MntnBuilderRuntimeStatus.publish(getOverlayState());
    }

    private void pauseForScriptGuard() {
        if (!waitingForScriptGuard) {
            waitingForScriptGuard = true;
            scriptGuardPausedAt = java.time.Instant.now();
        }
    }

    private void resumeFromScriptGuard() {
        if (scriptGuardPausedAt != null) {
            Duration pausedDuration = Duration.between(scriptGuardPausedAt, java.time.Instant.now());
            if (!pausedDuration.isNegative()) {
                if (debugTaskStartTime != null) {
                    debugTaskStartTime = debugTaskStartTime.plus(pausedDuration);
                }
                TaskActionGuard.suspendTimeouts(pausedDuration);
            }
        }
        waitingForScriptGuard = false;
        scriptGuardPausedAt = null;
    }

    private java.time.Instant taskStartTimeForOverlay() {
        if (!waitingForScriptGuard || scriptGuardPausedAt == null || debugTaskStartTime == null) {
            return debugTaskStartTime;
        }
        return debugTaskStartTime.plus(Duration.between(scriptGuardPausedAt, java.time.Instant.now()));
    }

    static String scriptGuardState() {
        if (Rs2AntibanSettings.microBreakActive) {
            return "Paused: Antiban break";
        }
        if (Rs2AntibanSettings.actionCooldownActive) {
            return "Paused: Antiban cooldown";
        }
        return "Paused";
    }

    private boolean applyPlan(Plan plan) {
        Task task;
        try {
            task = plan.strategy().createTask(context);
        } catch (RuntimeException ex) {
            return recoverFromTaskCreationFailure(plan, ex.getClass().getSimpleName());
        }
        if (task == null) {
            return recoverFromTaskCreationFailure(plan, "returned null");
        }

        currentPlan = plan;
        updateAntibanActivity(plan);
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
        return true;
    }

    private boolean recoverFromTaskCreationFailure(Plan plan, String detail) {
        memory.recordOutcome(plan, TaskStatus.FAILED, TaskStopReason.TASK_CREATION_FAILED);
        currentPlan = null;
        taskManager.setTask(null);
        showPlanningState("Recovering from task creation failure");
        Microbot.log("[MntnBuilder] Could not create task for " + plan.strategy().name()
                + " (" + detail + "); replanning");
        return false;
    }

    private Duration adjustedCommitment(Plan plan) {
        Duration base = plan.strategy().commitmentDuration(context);
        SessionFlavor flavor = config != null ? config.sessionFlavor() : SessionFlavor.BALANCED;
        double multiplier = flavor.commitmentMultiplier(plan.activity().type());
        long millis = Math.max(Duration.ofMinutes(1).toMillis(), Math.round(base.toMillis() * multiplier));
        return Duration.ofMillis(millis);
    }

    public void forceReplan() {
        // Overlay mouse callbacks run outside the script worker. Queue the request so
        // planner and task state remain owned by the scheduled script loop.
        forceReplanRequested.set(true);
    }

    boolean isForceReplanRequested() {
        return forceReplanRequested.get();
    }

    private boolean processForcedReplanRequest() {
        if (!forceReplanRequested.getAndSet(false)) {
            return false;
        }
        if (!initialBankDone || planner == null) {
            debugLog("Ignoring manual skip while startup banking is still running");
            return false;
        }
        if (isTestOverrideActive()) {
            currentPlan = null;
            taskManager.setTask(null);
            testOverrideFinished = true;
            showWaitingState("Test skipped", config.testOverride().displayName());
            return true;
        }

        if (currentPlan != null) {
            memory.recordOutcome(currentPlan, TaskStatus.REPLAN, TaskStopReason.MANUAL_SKIP);
        }
        currentPlan = null;
        taskManager.setTask(null);
        showPlanningState("Selecting new task after manual skip");
        replan();
        return true;
    }

    private void replan() {
        long startedAtNanos = System.nanoTime();
        debugLog("Replanning...");
        Microbot.log("[MntnBuilder] Replanning from goal=" + debugGoal
                + ", need=" + debugRequirement
                + ", hasTask=" + taskManager.hasTask()
                + ", hasPlan=" + (currentPlan != null));
        Plan candidate = null;
        RuntimeException plannerFailure = null;
        try {
            candidate = planner.plan(context);
        } catch (RuntimeException ex) {
            plannerFailure = ex;
            Microbot.log("[MntnBuilder] Normal planner pass failed: "
                    + ex.getClass().getSimpleName() + "; attempting combat bootstrap");
        }

        if (candidate == null) {
            candidate = planner.planCombatBootstrap(context);
            if (candidate != null) {
                Microbot.log("[MntnBuilder] Normal planner produced no task"
                        + (plannerFailure != null ? " after an error" : "")
                        + "; using fresh-F2P combat bootstrap");
            }
        }

        if (candidate == null) {
            String diagnostic = plannerFailure != null
                    ? "Planner failed: " + plannerFailure.getClass().getSimpleName()
                    : planner.diagnoseNoPlan(context);
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
            logSlowPlannerPass(startedAtNanos);
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
            logSlowPlannerPass(startedAtNanos);
            return;
        }

        debugLog("Replan: switching to new plan");
        applyPlan(candidate);
        logSlowPlannerPass(startedAtNanos);
    }

    private void logSlowPlannerPass(long startedAtNanos) {
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos);
        if (elapsedMs >= SLOW_PLANNER_PASS_MS) {
            Microbot.log("[MntnBuilder] Planner pass took " + elapsedMs + "ms");
        }
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
        waitingForScriptGuard = false;
        scriptGuardPausedAt = null;
        awaitingLoginStabilization = false;
        readyLoginTicks = 0;
        forceReplanRequested.set(false);
        antibanInitialized = false;
        MntnBuilderRuntimeStatus.clear();
    }
}
