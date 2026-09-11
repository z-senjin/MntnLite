package net.runelite.client.plugins.microbot.mntn.builder.tasks.questing;

import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.mntn.builder.activities.questing.quests.sheepshearer.SheepShearerStrategy;
import net.runelite.client.plugins.microbot.mntn.builder.core.AccountContext;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.Task;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.TaskActionGuard;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.TaskStatus;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.TaskStopReason;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.banking.BankingTask;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

public class SheepShearerTask implements Task {

    private enum Phase {
        CHECK_STATUS,
        BANKING,
        TALK_TO_FRED
    }

    private Phase phase = Phase.CHECK_STATUS;
    private BankingTask bankingTask;
    private TaskStopReason lastStopReason = TaskStopReason.NONE;
    private final TaskActionGuard walkGuard = new TaskActionGuard(10, 30_000, 800);
    private final TaskActionGuard talkGuard = new TaskActionGuard(5, 12_000, 900);
    private final TaskActionGuard dialogueGuard = new TaskActionGuard(12, 30_000, 700);
    private final TaskActionGuard npcGuard = new TaskActionGuard(8, 15_000, 900);

    @Override
    public TaskStatus tick(AccountContext context) {
        if (!context.isLoggedIn()) {
            return stop(TaskStatus.BLOCKED, TaskStopReason.NOT_LOGGED_IN);
        }
        if (context.getQuestState(Quest.SHEEP_SHEARER) == QuestState.FINISHED) {
            return TaskStatus.COMPLETE;
        }

        switch (phase) {
            case CHECK_STATUS:
                return checkStatus(context);
            case BANKING:
                return bankBallsOfWool(context);
            case TALK_TO_FRED:
                return talkToFred(context);
            default:
                return stop(TaskStatus.REPLAN, TaskStopReason.UNKNOWN);
        }
    }

    private TaskStatus checkStatus(AccountContext context) {
        if (hasRemainingBallsOfWool(context)) {
            phase = Phase.TALK_TO_FRED;
            return TaskStatus.RUNNING;
        }
        if (context.bank().getCount(SheepShearerStrategy.BALL_OF_WOOL) >= remainingBallsRequired(context)) {
            phase = Phase.BANKING;
            return TaskStatus.RUNNING;
        }
        return stop(TaskStatus.REPLAN, TaskStopReason.MISSING_SUPPLIES);
    }

    private TaskStatus bankBallsOfWool(AccountContext context) {
        if (bankingTask == null) {
            int remainingBalls = remainingBallsRequired(context);
            bankingTask = new BankingTask(
                    BankingTask.Mode.DEPOSIT_ALL_AND_WITHDRAW,
                    null,
                    new BankingTask.ItemWithdrawal(SheepShearerStrategy.BALL_OF_WOOL,
                            remainingBalls)
            );
        }

        TaskStatus status = bankingTask.tick(context);
        if (status == TaskStatus.COMPLETE) {
            bankingTask = null;
            phase = Phase.CHECK_STATUS;
            return TaskStatus.RUNNING;
        }
        if (status.isUnsuccessfulStop()) {
            bankingTask = null;
            return stop(status, TaskStopReason.BANK_FAILED);
        }
        return TaskStatus.RUNNING;
    }

    private TaskStatus talkToFred(AccountContext context) {
        if (!hasRemainingBallsOfWool(context)) {
            phase = Phase.CHECK_STATUS;
            return TaskStatus.RUNNING;
        }

        if (!context.isNear(SheepShearerStrategy.FRED_LOCATION, 8)) {
            TaskActionGuard.Result result = walkGuard.evaluate("walk to Fred the Farmer", false);
            if (result == TaskActionGuard.Result.EXHAUSTED) {
                return stop(TaskStatus.REPLAN, TaskStopReason.TRAVEL_FAILED);
            }
            if (result == TaskActionGuard.Result.READY) {
                Rs2Walker.walkTo(SheepShearerStrategy.FRED_LOCATION);
                walkGuard.recordAttempt();
            }
            return TaskStatus.RUNNING;
        }
        walkGuard.reset();

        if (Rs2Dialogue.isInDialogue()) {
            talkGuard.reset();
            npcGuard.reset();
            TaskActionGuard.Result result = dialogueGuard.evaluate(
                    "advance Sheep Shearer dialogue",
                    context.getQuestState(Quest.SHEEP_SHEARER) == QuestState.FINISHED
            );
            if (result == TaskActionGuard.Result.EXHAUSTED) {
                return stop(TaskStatus.REPLAN, TaskStopReason.QUEST_STEP_FAILED);
            }
            if (Rs2Dialogue.hasSelectAnOption()) {
                selectDialogueOption(context);
            }
            if (Rs2Dialogue.hasContinue()) {
                Rs2Dialogue.clickContinue();
            }
            if (result == TaskActionGuard.Result.READY) {
                dialogueGuard.recordAttempt();
            }
            return context.getQuestState(Quest.SHEEP_SHEARER) == QuestState.FINISHED
                    ? TaskStatus.COMPLETE
                    : TaskStatus.RUNNING;
        }
        dialogueGuard.reset();

        Rs2NpcModel fred = Microbot.getRs2NpcCache().query()
                .withNames("Fred the Farmer")
                .within(10)
                .nearest();
        if (fred == null) {
            TaskActionGuard.Result result = npcGuard.evaluate("find Fred the Farmer", false);
            if (result == TaskActionGuard.Result.EXHAUSTED) {
                return stop(TaskStatus.REPLAN, TaskStopReason.QUEST_STEP_FAILED);
            }
            if (result == TaskActionGuard.Result.READY) {
                npcGuard.recordAttempt();
            }
            return TaskStatus.RUNNING;
        }
        npcGuard.reset();

        TaskActionGuard.Result result = talkGuard.evaluate("talk to Fred the Farmer", Rs2Dialogue.isInDialogue());
        if (result == TaskActionGuard.Result.EXHAUSTED) {
            return stop(TaskStatus.REPLAN, TaskStopReason.QUEST_STEP_FAILED);
        }
        if (result == TaskActionGuard.Result.READY) {
            fred.click("Talk-to");
            talkGuard.recordAttempt();
        }
        return TaskStatus.RUNNING;
    }

    private void selectDialogueOption(AccountContext context) {
        if (context.getQuestState(Quest.SHEEP_SHEARER) == QuestState.NOT_STARTED) {
            Rs2Dialogue.clickOption(
                    "I'm looking for a quest.",
                    "Yes, okay. I can do that.",
                    "Yes."
            );
            return;
        }

        Rs2Dialogue.clickOption("I need to talk to you about shearing these sheep!");
    }

    private int remainingBallsRequired(AccountContext context) {
        return SheepShearerStrategy.remainingBallsRequired(context);
    }

    private boolean hasRemainingBallsOfWool(AccountContext context) {
        return context.inventory().getCount(SheepShearerStrategy.BALL_OF_WOOL) >= remainingBallsRequired(context);
    }

    @Override
    public boolean needsReplan(AccountContext context) {
        return context.getQuestState(Quest.SHEEP_SHEARER) == QuestState.FINISHED;
    }

    @Override
    public TaskStopReason getReplanStopReason(AccountContext context) {
        return context.getQuestState(Quest.SHEEP_SHEARER) == QuestState.FINISHED
                ? TaskStopReason.REQUIREMENT_SATISFIED
                : TaskStopReason.TASK_REQUESTED_REPLAN;
    }

    @Override
    public TaskStopReason getLastStopReason() {
        return lastStopReason;
    }

    @Override
    public String describe() {
        return "Sheep Shearer - " + phase;
    }

    private TaskStatus stop(TaskStatus status, TaskStopReason reason) {
        lastStopReason = reason != null ? reason : TaskStopReason.UNKNOWN;
        return status;
    }
}
