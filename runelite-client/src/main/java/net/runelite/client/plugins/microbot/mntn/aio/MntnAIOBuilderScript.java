/*
 * Role:
 * Owns the main scheduled loop for the Mntn AIO Account Builder.
 *
 * Purpose:
 * Initializes the configured goals and strategies, asks Planner to select
 * work, retains the selected Plan between ticks, and executes one small
 * strategy step every 600 milliseconds.
 *
 * Important:
 * This class does not contain the details of mining, questing, banking,
 * or other activities. Those behaviors belong inside classes that
 * implement AccountStrategy.
 */
package net.runelite.client.plugins.microbot.mntn.aio;

import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.mntn.aio.core.*;
import net.runelite.client.plugins.microbot.mntn.aio.strategies.moneymaking.TinderboxLootingStrategy;
import net.runelite.client.plugins.microbot.mntn.aio.strategies.skilling.mining.CopperTinMiningStrategy;
import net.runelite.client.plugins.microbot.mntn.aio.strategies.skilling.mining.IronMiningStrategy;
import net.runelite.client.plugins.microbot.mntn.aio.strategies.skilling.woodcutting.NormalTreeWoodcuttingStrategy;
import net.runelite.client.plugins.microbot.mntn.aio.strategies.skilling.woodcutting.OakTreeWoodcuttingStrategy;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.util.*;
import java.util.concurrent.TimeUnit;

public class MntnAIOBuilderScript extends Script
{
    /*
     * Planner selects which goal and strategy should run.
     */
    private final Planner planner = new Planner();

    /*
     * AccountContext provides current levels, quests, coins, and items.
     */
    private final AccountContext accountContext = new AccountContext();

    /*
     * Goals the account builder should complete.
     */
    private List<Goal> goals = Collections.emptyList();

    /*
     * Strategies available to work on those goals.
     */
    private List<AccountStrategy> strategies = Collections.emptyList();

    /*
     * The currently selected goal and strategy.
     *
     * This must remain assigned between scheduled ticks so that the
     * strategy's internal state machine can make progress.
     */
    private Plan activePlan;

    private MntnAIOBuilderConfig config;

    /*
     * Each plan runs for a random time between
     * COMMITMENT_MINUTES_MIN and COMMITMENT_MINUTES_MAX, after which the
     * planner picks the next goal or strategy.
     */
    private static final int COMMITMENT_MINUTES_MIN = 60;
    private static final int COMMITMENT_MINUTES_MAX = 180;

    /*
     * When the active plan should stop and the planner should choose
     * again. Reset whenever a new plan is selected.
     */
    private long activePlanDeadlineMs = 0;

    private boolean hasInitialized = false;
    private boolean debugLogging = true;
    private boolean finished;
    private String lastStatus;

    /**
     * Starts the account builder and its scheduled execution loop.
     */
    public boolean run(MntnAIOBuilderConfig config)
    {
        /*
         * Cancel an existing scheduled loop if run() is called again.
         */
        if (mainScheduledFuture != null &&
                !mainScheduledFuture.isDone())
        {
            mainScheduledFuture.cancel(true);
        }

        /*
         * Reset all state because the same script instance may be used
         * again after the plugin is stopped and restarted.
         */
        clearActivePlan();

        this.config = Objects.requireNonNull(config, "config");
        this.finished = false;
        this.lastStatus = null;

        /*
         * Create goals and strategies once at startup.
         *
         * Do not recreate these inside the scheduled loop.
         */
        this.goals = loadGoals(config);
        this.strategies = createStrategies(config);

        debugLog(
                "Starting with " + goals.size() +
                        " goals and " + strategies.size() +
                        " strategies"
        );

        setStatus("Starting account builder");

        mainScheduledFuture =
                scheduledExecutorService.scheduleWithFixedDelay(
                        () -> {
                            try
                            {
                                /*
                                 * Wait until the account is logged in.
                                 */
                                if (!Microbot.isLoggedIn())
                                {
                                    setStatus("Waiting for login");
                                    return;
                                }

                                /*
                                 * Handles the normal Microbot script
                                 * pause and shutdown checks.
                                 */
                                if (!super.run())
                                {
                                    return;
                                }
                                if(!hasInitialized){
                                    cacheBank();
                                    return;
                                }

                                runPlannerTick();
                            }
                            catch (Exception ex)
                            {
                                setStatus(
                                        "Error: " +
                                                ex.getClass().getSimpleName()
                                );

                                Microbot.log(
                                        "[MntnBuilder] Tick failed: " +
                                                ex.getMessage()
                                );

                                Microbot.logStackTrace(
                                        getClass().getSimpleName(),
                                        ex
                                );
                            }
                        },
                        0,
                        600,
                        TimeUnit.MILLISECONDS
                );

        return true;
    }

    private void cacheBank(){
        if(Rs2Player.isAnimating() || Rs2Player.isMoving()){
            return;
        }
        if(!Rs2Bank.isOpen()){
            sleepUntil(Rs2Bank::walkToBankAndUseBank, Rs2Random.between(800, 3000));
        } else {
            sleepUntil(Rs2Bank::depositAll, Rs2Random.between(800, 2000));
            sleepUntil(Rs2Bank::depositEquipment,Rs2Random.between(800, 2000));
            accountContext.getBankCache().refresh();
            hasInitialized = accountContext.getBankCache().isPopulated();
        }
    }

    /**
     * Executes one account-builder tick.
     *
     * This method either selects a Plan or executes one small step from
     * the strategy contained in the active Plan.
     */
    private void runPlannerTick()
    {
        if (finished)
        {
            return;
        }

        /*
         * The active goal might have completed since the previous tick.
         */
        if (activePlan != null &&
                activePlan.getGoal().isComplete(accountContext))
        {
            debugLog(
                    "Goal completed: " +
                            activePlan.getGoal()
            );

            clearActivePlan();
        }

        /*
         * The plan's random time limit has expired, so pick a new
         * goal or strategy even if the current goal is still
         * incomplete.
         */
        if (activePlan != null &&
                isCommitmentExpired(System.currentTimeMillis()))
        {
            debugLog(
                    "Plan time limit reached, replanning"
            );

            clearActivePlan();
        }

        /*
         * Only ask the Planner for work when no Plan is currently active.
         */
        if (activePlan == null)
        {
            activePlan = planner.choose(
                    goals,
                    strategies,
                    accountContext
            );

            /*
             * A null Plan means either every goal is finished or no
             * strategy can currently start.
             */
            if (activePlan == null)
            {
                if (allGoalsComplete())
                {
                    finished = true;
                    setStatus("All goals complete");
                }
                else
                {
                    setStatus("No available strategy");
                }

                return;
            }

            activePlanDeadlineMs =
                    System.currentTimeMillis() +
                            randomCommitmentMs();

            debugLog(
                    "Selected plan: " +
                            activePlan
            );

            setStatus(
                    activePlan.getStrategy().getName() +
                            " - " +
                            activePlan.getGoal()
            );
        }

        executeActivePlan();
    }

    /**
     * Runs one small step from the active strategy.
     */
    private void executeActivePlan()
    {
        Goal goal = activePlan.getGoal();
        AccountStrategy strategy =
                activePlan.getStrategy();

        StepResult result = strategy.tick(
                accountContext,
                goal
        );

        if (result == null)
        {
            throw new IllegalStateException(
                    strategy.getName() +
                            " returned a null StepResult"
            );
        }

        switch (result)
        {
            case RUNNING:
                /*
                 * Keep activePlan. The same strategy will receive another
                 * tick after 600 milliseconds.
                 */
                break;

            case COMPLETE:
                debugLog(
                        strategy.getName() +
                                " returned COMPLETE"
                );

                clearActivePlan();
                break;

            case REPLAN:
                debugLog(
                        strategy.getName() +
                                " requested replanning"
                );

                clearActivePlan();
                break;

            case STOP:
                debugLog(
                        strategy.getName() +
                                " requested a stop"
                );

                setStatus(
                        "Stopped by " +
                                strategy.getName()
                );

                clearActivePlan();
                finished = true;
                break;

            default:
                throw new IllegalStateException(
                        "Unknown StepResult: " + result
                );
        }
    }

    /**
     * Defines the account goals.
     *
     * This starts with one hardcoded test goal. Later, this method can
     * read the selected skills, target levels, quests, and priorities
     * from MntnAIOBuilderConfig.
     */
    private List<Goal> loadGoals(
            MntnAIOBuilderConfig config)
    {
        ArrayList<Goal> goals = new ArrayList<Goal>();
        if(config.miningTarget() > 0) {
            goals.add(Goal.skill(Skill.MINING, config.miningTarget(), Rs2Random.between(1, 10)));
        }

        if(config.woodcuttingTarget() > 0) {
            goals.add(Goal.skill(Skill.WOODCUTTING, config.woodcuttingTarget(), Rs2Random.between(1, 10)));
        }

        goals.add(Goal.cash(20000, Rs2Random.between(1, 10)));
        //TODO
        return goals;
    }

    /**
     * Registers the strategies available to the Planner.
     *
     * Strategies are checked from top to bottom, so preferred methods
     * should appear before fallback methods.
     */
    private List<AccountStrategy> createStrategies(
            MntnAIOBuilderConfig config)
    {
        return Arrays.asList(
                // TODO
                // Place higher lvl strategies first so its always doing the best strategy that can be handled until we can make it more randomized
                new IronMiningStrategy(),
                new CopperTinMiningStrategy(),
                new TinderboxLootingStrategy(),
                new OakTreeWoodcuttingStrategy(),
                new NormalTreeWoodcuttingStrategy()

                /*
                 * Add additional strategies later:
                 *
                 *
                 * new BasicMoneyStrategy(),
                 * new CooksAssistantStrategy()
                 */
        );
    }

    /**
     * Returns true only when every configured goal is complete.
     */
    private boolean allGoalsComplete()
    {
        if (goals.isEmpty())
        {
            return true;
        }

        for (Goal goal : goals)
        {
            Microbot.log("Goal: " + goal.toString());
            Microbot.log("Goal is complete? " + goal.isComplete(accountContext));
            if (goal != null &&
                    !goal.isComplete(accountContext))
            {
                return false;
            }
        }

        return true;
    }

    /**
     * Returns true when the active plan has run long enough that it
     * should hand control back to the Planner.
     */
    public boolean isCommitmentExpired(long nowMs)
    {
        return activePlan != null && nowMs >= activePlanDeadlineMs;
    }

    /**
     * A random commitment length between 60 and 180 minutes.
     */
    private static long randomCommitmentMs()
    {
        int minutes = Rs2Random.betweenInclusive(
                COMMITMENT_MINUTES_MIN,
                COMMITMENT_MINUTES_MAX
        );

        return TimeUnit.MINUTES.toMillis(minutes);
    }

    /**
     * Resets the active strategy and removes the current Plan.
     */
    private void clearActivePlan()
    {
        if (activePlan != null)
        {
            try
            {
                activePlan.getStrategy().reset();
            }
            catch (Exception ex)
            {
                Microbot.log(
                        "[MntnBuilder] Failed to reset " +
                                activePlan.getStrategy().getName() +
                                ": " +
                                ex.getMessage()
                );
            }

            activePlan = null;
            activePlanDeadlineMs = 0;
        }
    }

    /**
     * Updates Microbot.status and only logs when the status changes.
     */
    private void setStatus(String status)
    {
        Microbot.status = status;

        if (!Objects.equals(lastStatus, status))
        {
            debugLog("Status: " + status);
            lastStatus = status;
        }
    }

    private void debugLog(String message)
    {
        if (debugLogging)
        {
            Microbot.log(
                    "[MntnBuilder][DEBUG] " +
                            message
            );
        }
    }

    /**
     * Stops the scheduler and resets the active strategy.
     */
    @Override
    public void shutdown()
    {
        debugLog("Shutting down MntnAIOBuilderScript");

        clearActivePlan();

        finished = false;
        goals = Collections.emptyList();
        strategies = Collections.emptyList();
        config = null;
        lastStatus = null;

        super.shutdown();
    }

    /**
     * Useful for an overlay.
     */
    public Plan getActivePlan()
    {
        return activePlan;
    }

    /**
     * Useful for an overlay. Read-only view of the account's live state.
     */
    public AccountContext getAccountContext()
    {
        return accountContext;
    }

    /**
     * Useful for an overlay. Milliseconds left before the active plan
     * replans, or 0 when no plan is active.
     */
    public long getActivePlanRemainingMs()
    {
        if (activePlan == null)
        {
            return 0;
        }

        return Math.max(0, activePlanDeadlineMs - System.currentTimeMillis());
    }

    /**
     * Useful for an overlay.
     */
    public boolean isFinished()
    {
        return finished;
    }
}