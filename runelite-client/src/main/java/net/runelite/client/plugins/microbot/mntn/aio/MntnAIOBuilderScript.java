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
import net.runelite.client.plugins.microbot.mntn.aio.strategies.skilling.mining.CopperTinMiningStrategy;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
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
        return Arrays.asList(
                /*
                 * Reach Mining level 15 with priority 1.
                 */
                Goal.skill(
                        Skill.MINING,
                        15,
                        1
                )

                /*
                 * Additional examples:
                 *
                 * Goal.skill(Skill.FISHING, 40, 2),
                 * Goal.cash(100_000, 3),
                 * Goal.quest(Quest.COOKS_ASSISTANT, 4)
                 */
        );
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
                new CopperTinMiningStrategy()

                /*
                 * Add additional strategies later:
                 *
                 * new IronMiningStrategy(),
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
            if (goal != null &&
                    !goal.isComplete(accountContext))
            {
                return false;
            }
        }

        return true;
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
     * Useful for an overlay.
     */
    public boolean isFinished()
    {
        return finished;
    }
}