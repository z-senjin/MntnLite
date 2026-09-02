package net.runelite.client.plugins.microbot.mntn.builder.tasks.banking;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.mntn.builder.core.AccountContext;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.Task;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.TaskStatus;

/**
 * Reusable banking Task.
 *
 * Supports:
 *
 * DEPOSIT_ALL
 * DEPOSIT_ALL_EXCEPT
 * WITHDRAW
 * DEPOSIT_ALL_AND_WITHDRAW
 *
 * Examples:
 *
 * Deposit everything:
 *
 * new BankingTask(
 *     BankingTask.Mode.DEPOSIT_ALL,
 *     (String) null
 * );
 *
 * Deposit everything except a tool:
 *
 * new BankingTask(
 *     BankingTask.Mode.DEPOSIT_ALL_EXCEPT,
 *     "Small fishing net"
 * );
 *
 * Deposit everything except several tools (e.g. a fishing method needing rod + feathers):
 *
 * new BankingTask(
 *     BankingTask.Mode.DEPOSIT_ALL_EXCEPT,
 *     "Fishing rod", "Feather"
 * );
 *
 * Withdraw 28 raw trout:
 *
 * new BankingTask(
 *     BankingTask.Mode.WITHDRAW,
 *     null,
 *     "Raw trout",
 *     28
 * );
 *
 * Deposit everything and withdraw 28 raw trout:
 *
 * new BankingTask(
 *     BankingTask.Mode.DEPOSIT_ALL_AND_WITHDRAW,
 *     null,
 *     "Raw trout",
 *     28
 * );
 */
public class BankingTask implements Task {

    public enum Mode {
        DEPOSIT_ALL_EXCEPT,
        DEPOSIT_ALL,
        WITHDRAW,
        DEPOSIT_ALL_AND_WITHDRAW
    }

    private enum Phase {
        WALK,
        OPEN,
        DEPOSIT,
        WITHDRAW,
        CLOSE,
        DONE
    }

    private final Mode mode;

    /**
     * Items that should remain in the inventory when using DEPOSIT_ALL_EXCEPT. Changed from
     * a single String to String[] so a fishing method needing multiple tools at once (e.g.
     * Fishing rod + Feathers for fly fishing) can keep all of them during a deposit trip, not
     * just one. The simple 2-arg constructor stays varargs, so a single-tool call like
     * `new BankingTask(Mode.DEPOSIT_ALL_EXCEPT, "Small fishing net")` still compiles exactly
     * as before - nothing that already calls that constructor needs to change.
     */
    private final String[] keepItemNames;

    /**
     * Item we want to withdraw.
     */
    private final String withdrawItemName;

    /**
     * Amount to withdraw.
     *
     * -1 can be used to mean "withdraw all".
     */
    private final int withdrawAmount;

    private final BankLocation bankLocation;

    private Phase phase = Phase.WALK;

    public BankingTask(
            Mode mode,
            String... keepItemNames
    ) {
        this(
                mode,
                keepItemNames,
                null,
                0,
                null
        );
    }

    public BankingTask(
            Mode mode,
            String[] keepItemNames,
            String withdrawItemName,
            int withdrawAmount
    ) {
        this(
                mode,
                keepItemNames,
                withdrawItemName,
                withdrawAmount,
                null
        );
    }

    public BankingTask(
            Mode mode,
            String[] keepItemNames,
            String withdrawItemName,
            int withdrawAmount,
            BankLocation bankLocation
    ) {
        this.mode = mode;
        this.keepItemNames = keepItemNames;
        this.withdrawItemName = withdrawItemName;
        this.withdrawAmount = withdrawAmount;
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

                Rs2Bank.walkToBank();

                phase = Phase.OPEN;

                return TaskStatus.RUNNING;


            case OPEN:
                Microbot.log("OPEN");
                if (!Rs2Bank.isOpen()) {
                    Microbot.log("oepning bank");
                    Rs2Bank.openBank();
                    return TaskStatus.RUNNING;
                }

                /*
                 * Refresh our account context after opening.
                 */
                context.bank().refresh();

                phase = determineNextPhase();

                return TaskStatus.RUNNING;


            case DEPOSIT:

                if (!Rs2Bank.isOpen()) {
                    phase = Phase.OPEN;
                    return TaskStatus.RUNNING;
                }

                performDeposit();

                /*
                 * Give the bank operation a chance to complete
                 * before refreshing.
                 */
                context.bank().refresh();

                phase = determinePhaseAfterDeposit();

                return TaskStatus.RUNNING;


            case WITHDRAW:

                Microbot.log("withdrawing??");
                if (!Rs2Bank.isOpen()) {
                    phase = Phase.OPEN;
                    return TaskStatus.RUNNING;
                }

                performWithdraw();

                /*
                 * Refresh our bank snapshot.
                 */
                context.bank().refresh();

                phase = Phase.CLOSE;

                return TaskStatus.RUNNING;


            case CLOSE:

                if (Rs2Bank.isOpen()) {
                    Rs2Bank.closeBank();
                    return TaskStatus.RUNNING;
                }

                phase = Phase.DONE;

                return TaskStatus.RUNNING;


            case DONE:
            default:

                return TaskStatus.COMPLETE;
        }
    }


    /**
     * Determine what should happen immediately after
     * opening the bank.
     */
    private Phase determineNextPhase() {

        switch (mode) {

            case DEPOSIT_ALL:
            case DEPOSIT_ALL_EXCEPT:
            case DEPOSIT_ALL_AND_WITHDRAW:
                return Phase.DEPOSIT;

            case WITHDRAW:
                return Phase.WITHDRAW;

            default:
                return Phase.CLOSE;
        }
    }

    /**
     * Determine what should happen after depositing.
     */
    private Phase determinePhaseAfterDeposit() {

        if (mode == Mode.DEPOSIT_ALL_AND_WITHDRAW) {
            return Phase.WITHDRAW;
        }

        return Phase.CLOSE;
    }

    /**
     * Perform the configured deposit operation.
     *
     * TODO: verify Rs2Bank.depositAllExcept(boolean, String...) actually accepts multiple
     * item names in your Microbot version - only single-item usage was ever confirmed. If it
     * turns out to only accept one name, you'd need to loop item-by-item instead.
     */
    private void performDeposit() {

        if (mode == Mode.DEPOSIT_ALL_EXCEPT
                && keepItemNames != null
                && keepItemNames.length > 0) {

            Rs2Bank.depositAllExcept(
                    false,
                    keepItemNames
            );

            return;
        }

        Rs2Bank.depositAll();
    }

    /**
     * Perform the configured withdrawal.
     */
    private void performWithdraw() {
        if (withdrawItemName == null) {
            return;
        }

        if (withdrawAmount == -1) {

            Rs2Bank.withdrawAll(
                    withdrawItemName
            );

            return;
        }

        Rs2Bank.withdrawX(
                withdrawItemName,
                withdrawAmount
        );
    }

    @Override
    public boolean needsReplan(AccountContext context) {
        return false;
    }

    @Override
    public String describe() {
        return "Banking (" + mode + ") - " + phase;
    }
}
