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
import net.runelite.client.plugins.microbot.mntn.builder.activities.crafting.CraftingActivity;
import net.runelite.client.plugins.microbot.mntn.builder.activities.firemaking.FiremakingActivity;
import net.runelite.client.plugins.microbot.mntn.builder.activities.fishing.FishingActivity;
import net.runelite.client.plugins.microbot.mntn.builder.activities.mining.MiningActivity;
import net.runelite.client.plugins.microbot.mntn.builder.activities.questing.QuestingActivity;
import net.runelite.client.plugins.microbot.mntn.builder.activities.smithing.SmithingActivity;
import net.runelite.client.plugins.microbot.mntn.builder.activities.supply.SupplyActivity;
import net.runelite.client.plugins.microbot.mntn.builder.activities.supply.SupplyRoutePolicy;
import net.runelite.client.plugins.microbot.mntn.builder.activities.woodcutting.WoodcuttingActivity;
import net.runelite.client.plugins.microbot.mntn.builder.core.AccountContext;
import net.runelite.client.plugins.microbot.mntn.builder.core.AllowedContent;
import net.runelite.client.plugins.microbot.mntn.builder.core.goals.Goal;
import net.runelite.client.plugins.microbot.mntn.builder.core.goals.QuestGoal;
import net.runelite.client.plugins.microbot.mntn.builder.core.goals.SkillGoal;
import net.runelite.client.plugins.microbot.mntn.builder.core.planner.AccountMemory;
import net.runelite.client.plugins.microbot.mntn.builder.core.planner.AccountPlanner;
import net.runelite.client.plugins.microbot.mntn.builder.core.planner.Plan;
import net.runelite.client.plugins.microbot.mntn.builder.core.planner.SessionFlavor;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.Task;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.TaskActionGuard;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.TaskInventoryPreparationTask;
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
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

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
    private static final long NO_PLAN_RETRY_DELAY_MS = 5_000L;
    private static final long OVERLAY_SKILL_SNAPSHOT_INTERVAL_MS = 1_000L;
    private static final int LOGIN_READY_TICKS_REQUIRED = 2;
    private static final double PROFILE_GOAL_PRIORITY_BASE = 50.0;
    private static final int PROFILE_GOAL_PRIORITY_VARIATION = 5;

    private final AccountContext context = new AccountContext();
    private final TaskManager taskManager = new TaskManager();
    private final AccountMemory memory = new AccountMemory();
    private AccountPlanner planner;
    private Plan currentPlan;
    private MntnBuilderConfig config;

    // Cached config snapshot to detect modifications even without event triggers
    private int lastFishingTarget;
    private int lastCookingTarget;
    private int lastFiremakingTarget;
    private int lastWoodcuttingTarget;
    private int lastMiningTarget;
    private int lastSmithingTarget;
    private int lastCraftingTarget;
    private int lastAttackTarget;
    private int lastStrengthTarget;
    private int lastDefenceTarget;
    private int lastPrayerTarget;
    private boolean lastCooksAssistant;
    private boolean lastDoricsQuest;
    private boolean lastSheepShearer;
    private ActivityIntensity lastAntibanIntensity;
    private AllowedContent lastAllowedContent;
    private SessionFlavor lastSessionFlavor;
    private boolean lastShowOverlay;
    private boolean lastDetailedOverlay;
    private boolean lastAllowGrandExchange;
    private boolean lastAllowShops;
    private boolean lastAllowGroundPickups;
    private MntnBuilderTestOverride lastTestOverride;

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
    private final AtomicInteger activityTimeAdjustmentMinutes = new AtomicInteger();
    private final AtomicReference<MntnBuilderOverlayFocus> overlayFocusRequested = new AtomicReference<>();
    private volatile Map<Skill, Integer> overlaySkillLevels = Collections.emptyMap();
    private volatile boolean overlayQuestFocusAvailable;
    private long lastOverlaySkillSnapshotAtMs;
    private boolean testOverrideFinished;
    private long nextPlannerAttemptAtMs;
    private Plan transientRetryPlan;

    public String debugGoal = "-";
    public String debugRequirement = "-";
    public String debugActivity = "-";
    public String debugStrategy = "-";
    public Duration debugTime = null;
    public java.time.Instant debugTaskStartTime = null;
    public double debugScore = 0;

    public List<Goal> buildGoals(MntnBuilderConfig cfg) {
        return buildGoalsForProfile(cfg, startupCacheProfileId());
    }

    List<Goal> buildGoalsForProfile(MntnBuilderConfig cfg, String profileId) {
        List<Goal> goals = new ArrayList<>();

        addSkillGoal(goals, Skill.FISHING, cfg.fishingTarget(), profileId);
        addSkillGoal(goals, Skill.COOKING, cfg.cookingTarget(), profileId);
        addSkillGoal(goals, Skill.FIREMAKING, cfg.firemakingTarget(), profileId);
        addSkillGoal(goals, Skill.WOODCUTTING, cfg.woodcuttingTarget(), profileId);
        addSkillGoal(goals, Skill.MINING, cfg.miningTarget(), profileId);
        addSkillGoal(goals, Skill.SMITHING, cfg.smithingTarget(), profileId);
        addSkillGoal(goals, Skill.CRAFTING, cfg.craftingTarget(), profileId);
        addSkillGoal(goals, Skill.ATTACK, cfg.attackTarget(), profileId);
        addSkillGoal(goals, Skill.STRENGTH, cfg.strengthTarget(), profileId);
        addSkillGoal(goals, Skill.DEFENCE, cfg.defenceTarget(), profileId);
        addSkillGoal(goals, Skill.PRAYER, cfg.prayerTarget(), profileId);

        if (cfg.enableCooksAssistant()) {
            addQuestGoal(goals, Quest.COOKS_ASSISTANT, profileId);
        }

        if (cfg.enableDoricsQuest()) {
            addQuestGoal(goals, Quest.DORICS_QUEST, profileId);
        }

        if (cfg.enableSheepShearer()) {
            addQuestGoal(goals, Quest.SHEEP_SHEARER, profileId);
        }

        return goals;
    }

    private void addSkillGoal(List<Goal> goals, Skill skill, int targetLevel, String profileId) {
        if (targetLevel <= 0) {
            return;
        }
        goals.add(new SkillGoal(skill, targetLevel, profileGoalPriority(profileId, skill.name())));
    }

    static double profileGoalPriority(String profileId, String goalKey) {
        String stableProfileId = profileId == null || profileId.isBlank() ? "default" : profileId;
        int range = PROFILE_GOAL_PRIORITY_VARIATION * 2 + 1;
        int variation = Math.floorMod((stableProfileId + ":" + goalKey).hashCode(), range)
                - PROFILE_GOAL_PRIORITY_VARIATION;
        return PROFILE_GOAL_PRIORITY_BASE + variation;
    }

    private void addQuestGoal(List<Goal> goals, Quest quest, String profileId) {
        goals.add(new QuestGoal(quest, profileGoalPriority(profileId, quest.name())));
    }

    private void updateConfigSnapshot(MntnBuilderConfig cfg) {
        lastFishingTarget = cfg.fishingTarget();
        lastCookingTarget = cfg.cookingTarget();
        lastFiremakingTarget = cfg.firemakingTarget();
        lastWoodcuttingTarget = cfg.woodcuttingTarget();
        lastMiningTarget = cfg.miningTarget();
        lastSmithingTarget = cfg.smithingTarget();
        lastCraftingTarget = cfg.craftingTarget();
        lastAttackTarget = cfg.attackTarget();
        lastStrengthTarget = cfg.strengthTarget();
        lastDefenceTarget = cfg.defenceTarget();
        lastPrayerTarget = cfg.prayerTarget();
        lastCooksAssistant = cfg.enableCooksAssistant();
        lastDoricsQuest = cfg.enableDoricsQuest();
        lastSheepShearer = cfg.enableSheepShearer();
        lastAntibanIntensity = cfg.antibanIntensity();
        lastAllowedContent = cfg.allowedContent();
        lastSessionFlavor = cfg.sessionFlavor();
        lastShowOverlay = cfg.showOverlay();
        lastDetailedOverlay = cfg.detailedOverlay();
        lastAllowGrandExchange = cfg.allowGrandExchange();
        lastAllowShops = cfg.allowShops();
        lastAllowGroundPickups = cfg.allowGroundPickups();
        lastTestOverride = cfg.testOverride();
    }

    private boolean isConfigChanged(MntnBuilderConfig cfg) {
        return cfg.fishingTarget() != lastFishingTarget
                || cfg.cookingTarget() != lastCookingTarget
                || cfg.firemakingTarget() != lastFiremakingTarget
                || cfg.woodcuttingTarget() != lastWoodcuttingTarget
                || cfg.miningTarget() != lastMiningTarget
                || cfg.smithingTarget() != lastSmithingTarget
                || cfg.craftingTarget() != lastCraftingTarget
                || cfg.attackTarget() != lastAttackTarget
                || cfg.strengthTarget() != lastStrengthTarget
                || cfg.defenceTarget() != lastDefenceTarget
                || cfg.prayerTarget() != lastPrayerTarget
                || cfg.enableCooksAssistant() != lastCooksAssistant
                || cfg.enableDoricsQuest() != lastDoricsQuest
                || cfg.enableSheepShearer() != lastSheepShearer
                || cfg.antibanIntensity() != lastAntibanIntensity
                || cfg.allowedContent() != lastAllowedContent
                || cfg.sessionFlavor() != lastSessionFlavor
                || cfg.showOverlay() != lastShowOverlay
                || cfg.detailedOverlay() != lastDetailedOverlay
                || cfg.allowGrandExchange() != lastAllowGrandExchange
                || cfg.allowShops() != lastAllowShops
                || cfg.allowGroundPickups() != lastAllowGroundPickups
                || cfg.testOverride() != lastTestOverride;
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
            nextPlannerAttemptAtMs = 0;
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
        nextPlannerAttemptAtMs = 0;
        transientRetryPlan = null;
        forceReplanRequested.set(false);
        activityTimeAdjustmentMinutes.set(0);
        overlayFocusRequested.set(null);
        overlaySkillLevels = Collections.emptyMap();
        overlayQuestFocusAvailable = false;
        lastOverlaySkillSnapshotAtMs = 0;
        testOverrideFinished = false;
        showWaitingState("Starting", "Preparing planner");

        this.config = config;
        this.debugLogging = config.debugLogging();
        updateConfigSnapshot(config);
        publishRuntimeStatus();

        if (debugLogging) {
            debugLog("=== MntnBuilderScript started ===");
            debugLog("Config: fishingTarget=" + config.fishingTarget() + ", cookingTarget=" + config.cookingTarget()
                    + ", firemakingTarget=" + config.firemakingTarget()
                    + ", woodcuttingTarget=" + config.woodcuttingTarget() + ", miningTarget=" + config.miningTarget()
                    + ", smithingTarget=" + config.smithingTarget() + ", craftingTarget=" + config.craftingTarget()
                    + ", attackTarget=" + config.attackTarget()
                    + ", strengthTarget=" + config.strengthTarget() + ", defenceTarget=" + config.defenceTarget()
                    + ", prayerTarget=" + config.prayerTarget()
                    + ", cooksAssistant=" + config.enableCooksAssistant() + ", doricsQuest=" + config.enableDoricsQuest()
                    + ", sheepShearer=" + config.enableSheepShearer()
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
                    debugLog("super.run() returned false, skipping tick");
                    return;
                }
                resumeFromScriptGuard();

                processActivityTimeAdjustmentRequest();

                if (processOverlayFocusRequest()) {
                    return;
                }

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
                    if (isPlannerRetryDelayed()) {
                        return;
                    }
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
                    if (retryTransientTaskFailure(status)) {
                        return;
                    }
                    TaskStatus outcomeStatus = plannerOutcomeStatus(
                            status,
                            currentPlan != null && currentPlan.goal().isComplete(context),
                            currentPlan != null && currentPlan.requirement().isSatisfied(context)
                    );
                    TaskStopReason outcomeReason = outcomeStatus == status
                            ? terminalStopReason(status)
                            : TaskStopReason.TASK_REQUESTED_REPLAN;
                    if (outcomeStatus != status) {
                        Microbot.log("[MntnBuilder] Task completed without satisfying its plan; cooling down "
                                + (currentPlan != null ? currentPlan.strategy().name() : "unknown strategy"));
                    }
                    if (taskManager.getLastStopReason() == TaskStopReason.TRAVEL_FAILED) {
                        Rs2Walker.clearWalkingRoute("MntnBuilder task travel failed");
                    }
                    debugLog("Task status requires replan: " + status
                            + " (reason=" + taskManager.getLastStopReason() + ")");
                    finishCurrentPlan(outcomeStatus, outcomeReason);
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
        if (!context.isGameplayReady()) {
            awaitingLoginStabilization = true;
            readyLoginTicks = 0;
            pauseForScriptGuard();
            showWaitingState("Login", Microbot.isLoggedIn()
                    ? "Waiting for game world to load"
                    : "Waiting for account login");
            return true;
        }

        if (!awaitingLoginStabilization) {
            return false;
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

    /**
     * A task has already exhausted its local action guard before reporting one of these reasons.
     * Give it one clean task instance before abandoning its activity for an unrelated goal.
     */
    private boolean retryTransientTaskFailure(TaskStatus status) {
        TaskStopReason reason = taskManager.getLastStopReason();
        if (!isTransientRetryable(status, reason)
                || currentPlan == null
                || transientRetryPlan == currentPlan) {
            return false;
        }

        Task retryTask;
        try {
            retryTask = prepareTask(currentPlan.strategy().createTask(context));
        } catch (RuntimeException ex) {
            debugLog("Could not create transient retry task: " + ex.getClass().getSimpleName());
            return false;
        }
        if (retryTask == null) {
            debugLog("Could not create transient retry task: returned null");
            return false;
        }

        transientRetryPlan = currentPlan;
        Rs2Walker.clearWalkingRoute("MntnBuilder transient task retry");
        taskManager.setTask(retryTask);
        Microbot.log("[MntnBuilder] Retrying " + currentPlan.strategy().name()
                + " once after " + reason);
        return true;
    }

    static boolean isTransientRetryable(TaskStatus status, TaskStopReason reason) {
        if (status != TaskStatus.REPLAN && status != TaskStatus.FAILED) {
            return false;
        }
        return reason == TaskStopReason.TRAVEL_FAILED
                || reason == TaskStopReason.RESOURCE_NOT_FOUND
                || reason == TaskStopReason.ACTION_FAILED
                || reason == TaskStopReason.PRODUCTION_WIDGET_FAILED;
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
            Task task = testOverride.createTask(context);
            if (task == null) {
                testOverrideFinished = true;
                showWaitingState("Test complete", testOverride.displayName());
                return;
            }

            currentPlan = null;
            taskManager.setTask(prepareTask(task));
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
                new FiremakingActivity(),
                new WoodcuttingActivity(),
                new MiningActivity(),
                new SmithingActivity(),
                new CraftingActivity(),
                new QuestingActivity(),
                new CombatActivity(config),
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
                && !postStartupReplanPending
                && "Planning".equals(debugGoal);
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
        // A break/login wait is only a temporary runner state. Keep the active plan's
        // deadline intact so it resumes with the same remaining commitment time.
        if (currentPlan != null && taskManager.hasTask()) {
            return;
        }
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
            case FIREMAKING:
                return net.runelite.client.plugins.microbot.util.antiban.enums.Activity.GENERAL_FIREMAKING;
            case SMITHING:
                if (strategy.contains("BRONZE")) {
                    return net.runelite.client.plugins.microbot.util.antiban.enums.Activity.SMELTING_BRONZE_BARS;
                }
                if (strategy.contains("IRON")) {
                    return net.runelite.client.plugins.microbot.util.antiban.enums.Activity.SMELTING_IRON_BARS;
                }
                return net.runelite.client.plugins.microbot.util.antiban.enums.Activity.GENERAL_SMITHING;
            case CRAFTING:
                return net.runelite.client.plugins.microbot.util.antiban.enums.Activity.GENERAL_CRAFTING;
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
        boolean expired = isCommitmentExpired(debugTime, debugTaskStartTime, java.time.Instant.now());
        if (expired) {
            debugLog("Commitment expired (elapsed >= " + debugTime + ")");
        }
        return expired;
    }

    static boolean isCommitmentExpired(Duration duration, java.time.Instant startedAt, java.time.Instant now) {
        if (duration == null || startedAt == null || now == null) {
            return false;
        }
        Duration elapsed = Duration.between(startedAt, now);
        return !elapsed.isNegative() && elapsed.compareTo(duration) >= 0;
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
                debugScore,
                overlaySkillLevels,
                overlayQuestFocusAvailable
        );
    }

    private void publishRuntimeStatus() {
        long now = System.currentTimeMillis();
        if (now - lastOverlaySkillSnapshotAtMs >= OVERLAY_SKILL_SNAPSHOT_INTERVAL_MS) {
            overlaySkillLevels = context.getRealSkillLevels();
            lastOverlaySkillSnapshotAtMs = now;
        }
        overlayQuestFocusAvailable = config != null
                && (config.enableCooksAssistant() || config.enableDoricsQuest() || config.enableSheepShearer());
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
            task = prepareTask(task);
        } catch (RuntimeException ex) {
            return recoverFromTaskCreationFailure(plan, ex.getClass().getSimpleName());
        }
        if (task == null) {
            return recoverFromTaskCreationFailure(plan, "returned null");
        }

        // A completed, skipped, or failed task must not leave its route active for the next one.
        Rs2Walker.clearWalkingRoute("MntnBuilder plan transition");
        currentPlan = plan;
        transientRetryPlan = null;
        nextPlannerAttemptAtMs = 0;
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

    static TaskStatus plannerOutcomeStatus(
            TaskStatus taskStatus,
            boolean goalComplete,
            boolean requirementSatisfied
    ) {
        if (taskStatus == TaskStatus.COMPLETE && !goalComplete && !requirementSatisfied) {
            return TaskStatus.REPLAN;
        }
        return taskStatus;
    }

    private Task prepareTask(Task task) {
        return task == null ? null : new TaskInventoryPreparationTask(task);
    }

    private String startupCacheProfileId() {
        if (Microbot.getConfigManager() == null || Microbot.getConfigManager().getProfile() == null) {
            return null;
        }
        return String.valueOf(Microbot.getConfigManager().getProfile().getId());
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

    /** Queues a small commitment adjustment; the script worker owns the actual clock update. */
    public void adjustActivityTime(int minutes) {
        if (minutes != 0) {
            activityTimeAdjustmentMinutes.getAndAdd(minutes);
        }
    }

    /** Queues a one-shot focus task without altering the account's configured goals. */
    public void focusActivity(MntnBuilderOverlayFocus focus) {
        if (focus != null) {
            overlayFocusRequested.set(focus);
        }
    }

    boolean isForceReplanRequested() {
        return forceReplanRequested.get();
    }

    private void processActivityTimeAdjustmentRequest() {
        int adjustmentMinutes = activityTimeAdjustmentMinutes.getAndSet(0);
        if (adjustmentMinutes == 0 || currentPlan == null || debugTime == null || debugTaskStartTime == null) {
            return;
        }

        Duration adjusted = debugTime.plusMinutes(adjustmentMinutes);
        debugTime = adjusted.isNegative() ? Duration.ZERO : adjusted;
        Microbot.log("[MntnBuilder] Activity time adjusted by " + adjustmentMinutes
                + " minutes for " + currentPlan.strategy().name());
    }

    private boolean processOverlayFocusRequest() {
        MntnBuilderOverlayFocus focus = overlayFocusRequested.get();
        if (focus == null) {
            return false;
        }
        if (!initialBankDone || planner == null) {
            return false;
        }
        overlayFocusRequested.compareAndSet(focus, null);

        if (isTestOverrideActive()) {
            Microbot.log("[MntnBuilder] Overlay focus ignored while a test override is active");
            return true;
        }

        if (currentPlan != null) {
            memory.recordOutcome(currentPlan, TaskStatus.REPLAN, TaskStopReason.MANUAL_SKIP);
        }
        currentPlan = null;
        taskManager.setTask(null);

        Plan focusedPlan = focus.isQuestFocus()
                ? planner.planForGoals(context, incompleteQuestGoals())
                : focusedSkillPlan(focus);
        if (focusedPlan != null) {
            Microbot.log("[MntnBuilder] Overlay focus selected " + focusedPlan.strategy().name()
                    + " for " + focus.displayName());
            applyPlan(focusedPlan);
            return true;
        }

        showPlanningState("No runnable " + focus.displayName() + " activity; returning to normal planner");
        return true;
    }

    private Plan focusedSkillPlan(MntnBuilderOverlayFocus focus) {
        Skill skill = focus.skill();
        if (skill == null) {
            return null;
        }
        int targetLevel = Math.min(99, context.getRealLevel(skill) + 1);
        if (context.getRealLevel(skill) >= targetLevel) {
            return null;
        }
        return planner.planForGoal(context,
                new SkillGoal(skill, targetLevel, PROFILE_GOAL_PRIORITY_BASE + 1000));
    }

    private List<Goal> incompleteQuestGoals() {
        List<Goal> questGoals = new ArrayList<>();
        for (Goal goal : planner.getGoals()) {
            if (goal instanceof QuestGoal && !goal.isComplete(context)) {
                questGoals.add(goal);
            }
        }
        return questGoals;
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
            nextPlannerAttemptAtMs = System.currentTimeMillis() + NO_PLAN_RETRY_DELAY_MS;
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

    private boolean isPlannerRetryDelayed() {
        return System.currentTimeMillis() < nextPlannerAttemptAtMs;
    }

    private void replanOnTimeout() {
        debugLog("Replanning on timeout...");
        Plan expiredPlan = currentPlan;
        List<Plan> candidates = planner.planAll(context);
        if (candidates.isEmpty()) {
            // Do not leave an expired task installed when the live-state pass happens
            // to have no candidates. The normal planner has the bounded no-plan retry
            // and fresh-account combat fallback needed to recover cleanly.
            currentPlan = null;
            taskManager.setTask(null);
            showPlanningState("Selecting next task after activity timeout");
            replan();
            return;
        }

        Plan nextPlan = selectTimeoutSuccessor(candidates, expiredPlan);
        currentPlan = null;
        taskManager.setTask(null);

        debugLog("Commitment expired for " + (expiredPlan != null ? expiredPlan.strategy().name() : "previous task")
                + ". Moving to next: " + nextPlan.strategy().name());
        System.out.println("[MntnPlanner] Commitment expired for " + (expiredPlan != null ? expiredPlan.strategy().name() : "previous task")
                + ". Moving to next: " + nextPlan.strategy().name());
        applyPlan(nextPlan);
    }

    /**
     * Expiry is a deliberate activity boundary, so favor a different activity first.
     * When only one activity can progress the configured goals, use a different method;
     * only repeat the old method when it is genuinely the sole runnable option.
     */
    static Plan selectTimeoutSuccessor(List<Plan> candidates, Plan expiredPlan) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        if (expiredPlan == null) {
            return candidates.get(0);
        }

        for (Plan candidate : candidates) {
            if (candidate.activity().type() != expiredPlan.activity().type()) {
                return candidate;
            }
        }
        for (Plan candidate : candidates) {
            if (!candidate.strategy().name().equals(expiredPlan.strategy().name())) {
                return candidate;
            }
        }
        return candidates.get(0);
    }

    @Override
    public void shutdown() {
        debugLog("Shutting down MntnBuilderScript");
        super.shutdown();
        memory.clear();
        currentPlan = null;
        taskManager.setTask(null);
        initialBankDone = false;
        postStartupReplanPending = false;
        waitingForScriptGuard = false;
        scriptGuardPausedAt = null;
        awaitingLoginStabilization = false;
        readyLoginTicks = 0;
        transientRetryPlan = null;
        forceReplanRequested.set(false);
        activityTimeAdjustmentMinutes.set(0);
        overlayFocusRequested.set(null);
        overlaySkillLevels = Collections.emptyMap();
        overlayQuestFocusAvailable = false;
        lastOverlaySkillSnapshotAtMs = 0;
        antibanInitialized = false;
        MntnBuilderRuntimeStatus.clear();
    }
}
