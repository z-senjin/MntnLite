package net.runelite.client.plugins.microbot.mntn.builder.tasks;

import net.runelite.client.plugins.microbot.mntn.builder.core.AccountContext;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.banking.BankingTask;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** Deposits inventory items unrelated to a newly selected productive task. */
public class TaskInventoryPreparationTask implements Task {

    private final Task delegate;
    private final Set<String> keepItemNames;
    private BankingTask bankingTask;
    private boolean prepared;
    private TaskStopReason lastStopReason = TaskStopReason.NONE;

    public TaskInventoryPreparationTask(Task delegate, String[] keepItemNames) {
        this.delegate = delegate;
        this.keepItemNames = keepItemNames == null
                ? Collections.emptySet()
                : Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(keepItemNames)));
    }

    @Override
    public TaskStatus tick(AccountContext context) {
        if (!prepared) {
            if (!needsInventoryPreparation(context)) {
                prepared = true;
            } else {
                return prepareInventory(context);
            }
        }
        return delegate.tick(context);
    }

    boolean needsInventoryPreparation(AccountContext context) {
        return context.inventory().isFull() || !context.inventory().hasOnlyItems(keepItemNames);
    }

    private TaskStatus prepareInventory(AccountContext context) {
        if (bankingTask == null) {
            bankingTask = new BankingTask(
                    BankingTask.Mode.DEPOSIT_ALL_EXCEPT,
                    keepItemNames.toArray(new String[0])
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
        return prepared ? delegate.describe() : "Preparing inventory for " + delegate.describe();
    }
}
