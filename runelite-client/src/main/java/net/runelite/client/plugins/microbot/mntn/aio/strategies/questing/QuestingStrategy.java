/*
 * Role:
 * Handles quest automation by setting the active quest for the Quest Helper plugin
 * and checking player requirements (skills, items, quest state).
 *
 * Purpose:
 * When a quest goal is selected by the planner, this strategy:
 * 1. Verifies the player meets the quest's skill requirements
 * 2. Sets the quest as active in the Quest Helper plugin
 * 3. Progresses through quest steps until completion
 *
 * Strategy Behavior:
 * - Dynamic: Works with any quest from the QuestHelperQuest enum
 * - Requirements: Checks Rs2Player skills and item presence
 * - Active Quest: Uses QuestHelper plugin to set the active quest
 * - Step-based: State machine progressing through quest steps
 */
package net.runelite.client.plugins.microbot.mntn.aio.strategies.questing;

import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.mntn.aio.core.*;
import net.runelite.client.plugins.microbot.questhelper.QuestHelperPlugin;
import net.runelite.client.plugins.microbot.questhelper.questhelpers.QuestHelper;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.api.ItemID;

/**
 * Strategy for handling quest goals.
 * Sets the active quest in Quest Helper and checks player requirements.
 */
public final class QuestingStrategy implements AccountStrategy
{
	private static final int MIN_QUEST_POINTS = 1;

	private State state = State.IDLE;
	private Quest currentQuest = null;

	@Override
	public String getName()
	{
		return "Questing";
	}

	@Override
	public boolean supports(Goal goal)
	{
		return goal != null && goal.getType() == GoalType.QUEST;
	}

	@Override
	public boolean canStart(AccountContext context, Goal goal)
	{
		if (goal == null || !supports(goal))
		{
			return false;
		}

		currentQuest = goal.getQuest();

		// Quest must not already be complete
		if (context.isQuestComplete(currentQuest))
		{
			Microbot.log("[Questing] Quest already complete: " + currentQuest);
			return false;
		}

		// Check skill requirements
		if (!hasRequiredSkills(context, currentQuest))
		{
			Microbot.log("[Questing] Missing skill requirements for: " + currentQuest);
			return false;
		}

        //TODO:
		// Check item requirements (quest-starting items in inventory/bank)
//		if (!hasRequiredItems(context, currentQuest))
//		{
//			Microbot.log("[Questing] Missing item requirements for: " + currentQuest);
//			return false;
//		}

		Microbot.log("[Questing] Requirements met for: " + currentQuest);
		return true;
	}

	@Override
	public StepResult tick(AccountContext context, Goal goal)
	{
		if (goal == null || !supports(goal))
		{
			return StepResult.STOP;
		}

		currentQuest = goal.getQuest();

		// If quest is complete, mark goal complete
		if (context.isQuestComplete(currentQuest))
		{
			Microbot.log("[Questing] Quest completed: " + currentQuest);
			return StepResult.COMPLETE;
		}

		// Set the quest as active in Quest Helper
		setQuestActive(context);

		// Progress through quest steps based on current state
		return progressQuest(context);
	}

	@Override
	public void reset()
	{
		state = State.IDLE;
		currentQuest = null;
		Microbot.log("[Questing] Strategy reset");
	}

	/**
	 * Sets the specified quest as active in the Quest Helper plugin.
	 */
	private void setQuestActive(AccountContext context)
	{
		if (currentQuest == null)
		{
			return;
		}

		try
		{
			// Use Quest Helper's startQuestHelper method to set the quest as active
			// This is called via the client thread to avoid thread issues
			Microbot.getClientThread().invokeAtTickEnd(() ->
			{
				String questName = currentQuest.getName();
				// Get QuestHelperPlugin using the class literal (fully qualified class name)
				try
				{
					QuestHelperPlugin questHelperPlugin = Microbot.getPlugin(QuestHelperPlugin.class);
					if (questHelperPlugin != null)
					{
						// Check if quest is already active to avoid spamming
						QuestHelper currentSelected = questHelperPlugin.getSelectedQuest();
						if (currentSelected != null && currentSelected.getQuest() != null
							&& currentSelected.getQuest().getName().equals(questName))
						{
							// Quest is already active, no need to start it again
							return;
						}

						questHelperPlugin.startQuestHelper(questName);
						Microbot.log("[Questing] Started quest helper for: " + questName);
					}
					else
					{
						Microbot.log("[Questing] Quest Helper plugin not found (not loaded?)");
					}
				}
				catch (Exception e)
				{
					Microbot.log("[Questing] Error starting quest helper: " + e.getMessage());
				}
			});
		}
		catch (Exception e)
		{
			Microbot.log("[Questing] Error setting quest active: " + e.getMessage());
		}
	}

	/**
	 * Progresses through the quest steps based on current state.
	 */
	private StepResult progressQuest(AccountContext context)
	{
		if (currentQuest == null)
		{
			return StepResult.RUNNING;
		}

		try
		{
			switch (state)
			{
				case IDLE:
					// Initial state - just set the quest active
					Microbot.log("[Questing] IDLE state for: " + currentQuest);
					state = State.WAITING;
					return StepResult.RUNNING;

				case WAITING:
					// Wait for quest to be recognized/started
					if (isQuestProgressing(context))
					{
						Microbot.log("[Questing] Quest progressing, moving forward");
						state = State.STEP_FORWARD;
						return StepResult.RUNNING;
					}
					return StepResult.RUNNING;

				case STEP_FORWARD:
					// Take a step forward in the quest
					if (takeQuestStep(context))
					{
						Microbot.log("[Questing] Quest step completed");
						state = State.WAITING;
						return StepResult.RUNNING;
					}
					// If step failed, try again
					return StepResult.RUNNING;

				default:
					Microbot.log("[Questing] Unknown state: " + state);
					return StepResult.RUNNING;
			}
		}
		catch (Exception e)
		{
			Microbot.log("[Questing] Error progressing quest: " + e.getMessage());
			return StepResult.RUNNING;
		}
	}

	/**
	 * Checks if the quest is currently progressing (started but not complete).
	 */
	private boolean isQuestProgressing(AccountContext context)
	{
		if (currentQuest == null)
		{
			return false;
		}

		// Check if quest state has changed (started)
		QuestState currentState = Rs2Player.getQuestState(currentQuest);
		return currentState != QuestState.NOT_STARTED && currentState != QuestState.FINISHED;
	}

	/**
	 * Takes a step forward in the quest (talking to NPCs, clicking objects, etc.).
	 */
	private boolean takeQuestStep(AccountContext context)
	{
		if (currentQuest == null)
		{
			return false;
		}

		// For now, just mark as progressing
		// In a full implementation, this would involve:
		// - Finding and talking to quest NPCs
		// - Clicking quest objects
		// - Following quest markers
		// - Using Microbot caches to find quest objectives

		Microbot.log("[Questing] Taking quest step for: " + currentQuest);
		return true; // Placeholder - actual implementation would perform quest actions
	}

	/**
	 * Checks if the player has the required skills for the quest.
	 */
	private boolean hasRequiredSkills(AccountContext context, Quest quest)
	{
		// Check for required skills using Rs2Player
		// Common quest requirements can be checked here
		// For now, we check basic skill prerequisites

		// Get the player's real skill levels
		// Specific quest skill requirements would be quest-dependent
		// This is a placeholder - full implementation would check quest-specific skills

		// Example: Check if player has minimum combat/other skills
		// In a full implementation, this would lookup the quest's actual skill requirements
		// from QuestHelperQuest enum or Rs2Player data

		// For now, assume skills are OK - specific quest requirements
		// can be added later by checking Rs2Player.getRealSkillLevel(Skill)
		return true;
	}

/**
	 * Checks if the player has the required items for the quest.
	 * Checks inventory and bank for quest-starting items.
	 */
	private boolean hasRequiredItems(AccountContext context, Quest quest)
	{
		if (currentQuest == null)
		{
			return true;
		}

		boolean hasItem = context.getBankCache().hasItem(ItemID.COINS);

		return hasItem;
	}

/**
	 * Checks if the player has required quest points.
	 * Uses Rs2Player to check quest state and progress.
	 */
	private boolean hasRequiredQuestPoints(AccountContext context)
	{
		if (currentQuest != null)
		{
			QuestState state = Rs2Player.getQuestState(currentQuest);
			return state != QuestState.NOT_STARTED;
		}

		// Default: assume quest points are OK
		return true;
	}

	/**
	 * Enum representing the quest state machine states.
	 */
	private enum State
	{
		IDLE,
		WAITING,
		STEP_FORWARD
	}
}