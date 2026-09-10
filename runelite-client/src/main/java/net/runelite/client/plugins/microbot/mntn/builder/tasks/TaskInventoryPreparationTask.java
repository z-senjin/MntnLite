package net.runelite.client.plugins.microbot.mntn.builder.tasks;

import net.runelite.client.plugins.microbot.mntn.builder.core.AccountContext;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.banking.BankingTask;

/**
 * Gives every selected task the same starting point: an empty inventory and no worn gear.
 * The delegate then owns its complete loadout, travel, and action flow.
 */
public class TaskInventoryPreparationTask implements Task {

    private final Task delegate;
    private BankingTask bankingTask;
    private boolean prepared;
    private TaskStopReason lastStopReason = TaskStopReason.NONE;

    public TaskInventoryPreparationTask(Task delegate) {
        this.delegate = delegate;
    }

    @Override
    public TaskStatus tick(AccountContext context) {
        if (!prepared) {
            if (!needsPreparation(context)) {
                prepared = true;
            } else {
                return prepareInventory(context);
            }
        }
        return delegate.tick(context);
    }

    boolean needsPreparation(AccountContext context) {
        return !context.inventory().isEmpty() || !context.equipment().isEmpty();
    }

    private TaskStatus prepareInventory(AccountContext context) {
        if (bankingTask == null) {
            bankingTask = new BankingTask(
                    BankingTask.Mode.DEPOSIT_ALL,
                    true
            );
        }

        TaskStatus status = bankingTask.tick(context);
        if (status == TaskStatus.COMPLETE) {
            bankingTask = null;
            prepared = true;
            return TaskStatus.RUNNING;
        }
        if (status.isUnsuccessfulStop()) {
            lastStopReason = bankingTask.getLastStopReason();
        }
        return status;
    }

    @Override
    public boolean needsReplan(AccountContext context) {
        return prepared && delegate.needsReplan(context);
    }

    @Override
    public TaskStopReason getReplanStopReason(AccountContext context) {
        return prepared ? delegate.getReplanStopReason(context) : lastStopReason;
    }

    @Override
    public TaskStopReason getLastStopReason() {
        return lastStopReason != TaskStopReason.NONE ? lastStopReason : delegate.getLastStopReason();
    }

    @Override
    public String describe() {
        return prepared ? delegate.describe() : "Banking before " + delegate.describe();
    }
}
