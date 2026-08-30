package net.runelite.client.plugins.microbot.mntn.builder.tasks.banking;

import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.mntn.builder.core.AccountContext;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.Task;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.TaskStatus;

/**
 * Reusable banking Task, per the doc's "don't duplicate banking logic in every skill" rule.
 * Your GemCrabKillerScript.handleBanking() inlines this exact walk -> open -> deposit -> close
 * sequence directly in the crab script; this pulls it out so FishingTask, CookingTask,
 * CombatTask etc. can all share one implementation instead of copy-pasting it.
 */
public class BankingTask implements Task {

    public enum Mode {
        DEPOSIT_ALL_EXCEPT,
        DEPOSIT_ALL
    }

    private enum Phase {
        WALK, OPEN, DEPOSIT, CLOSE, DONE
    }

    private final Mode mode;
    private final String keepItemName;
    private final BankLocation bankLocation;
    private Phase phase = Phase.WALK;

    public BankingTask(Mode mode, String keepItemName) {
        // TODO: pick the actual nearest/appropriate BankLocation for wherever this task runs -
        // hardcoded to LUMBRIDGE here just so this compiles as a starting point.
        this(mode, keepItemName, BankLocation.LUMBRIDGE_TOP);
    }

    public BankingTask(Mode mode, String keepItemName, BankLocation bankLocation) {
        this.mode = mode;
        this.keepItemName = keepItemName;
        this.bankLocation = bankLocation;
    }

    @Override
    public TaskStatus tick(AccountContext context) {
        switch (phase) {
            case WALK:
                if (Rs2Bank.isOpen()) {
                    phase = Phase.DEPOSIT;
                    return TaskStatus.RUNNING;
                }
                Rs2Bank.walkToBank(bankLocation);
                phase = Phase.OPEN;
                return TaskStatus.RUNNING;

            case OPEN:
                Rs2Bank.openBank();
                if (Rs2Bank.isOpen()) {
                    // Cache what's already in there BEFORE we deposit anything, so a query
                    // like "does the bank have raw shrimp" reflects reality even if this trip
                    // turns out to be a no-op deposit (e.g. inventory was already empty).
                    context.bank().refresh();
                    phase = Phase.DEPOSIT;
                }
                return TaskStatus.RUNNING;

            case DEPOSIT:
                if (!Rs2Bank.isOpen()) {
                    phase = Phase.OPEN;
                    return TaskStatus.RUNNING;
                }
                if (mode == Mode.DEPOSIT_ALL_EXCEPT && keepItemName != null) {
                    Rs2Bank.depositAllExcept(false, keepItemName);
                } else {
                    // TODO: verify Rs2Bank has a no-arg depositAll() in your Microbot version -
                    // if not, depositAllExcept(false) with no keep-list is the usual equivalent.
                    Rs2Bank.depositAll();
                }
                // Refresh again now that the deposit actually changed bank contents - this is
                // the snapshot that matters most, since it's what every later hasItem()/
                // getCount() call will see until the next banking trip.
                context.bank().refresh();
                phase = Phase.CLOSE;
                return TaskStatus.RUNNING;

            case CLOSE:
                Rs2Bank.closeBank();
                phase = Phase.DONE;
                return TaskStatus.RUNNING;

            case DONE:
            default:
                return TaskStatus.COMPLETE;
        }
    }

    @Override
    public boolean needsReplan(AccountContext context) {
        return false;
    }
}
