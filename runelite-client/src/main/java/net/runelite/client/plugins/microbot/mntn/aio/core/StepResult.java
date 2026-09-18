/*
 * Role:
 * Describes the result of one AccountStrategy tick.
 *
 * Purpose:
 * The main AccountBuilderScript uses this result to decide whether it
 * should keep running the current strategy, request another plan,
 * mark the work complete, or stop the plugin.
 */
package net.runelite.client.plugins.microbot.mntn.aio.core;

public enum StepResult
{
    /*
     * The strategy is still working. Keep the current Plan and call
     * tick() again during the next scheduled loop.
     */
    RUNNING,

    /*
     * The strategy has completed the goal or its assigned work.
     * Reset the strategy and ask the Planner for another Plan.
     */
    COMPLETE,

    /*
     * The account's state changed enough that a different strategy
     * might now be preferable.
     */
    REPLAN,

    /*
     * Stop the account-builder plugin.
     */
    STOP
}