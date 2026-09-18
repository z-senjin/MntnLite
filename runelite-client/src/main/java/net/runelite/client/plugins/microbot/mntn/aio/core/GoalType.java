/*
 * Role:
 * Defines the different types of account goals understood by the planner.
 *
 * Purpose:
 * Goal uses this value to determine how completion should be checked.
 * For example, a skill goal checks a level while a quest goal checks
 * the quest's completion state.
 */
package net.runelite.client.plugins.microbot.mntn.aio.core;

public enum GoalType
{
    SKILL_LEVEL,
    CASH,
    QUEST
}