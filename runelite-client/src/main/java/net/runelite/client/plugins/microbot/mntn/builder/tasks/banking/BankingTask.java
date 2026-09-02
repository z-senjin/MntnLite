package net.runelite.client.plugins.microbot.mntn.builder.tasks.banking;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.mntn.builder.core.AccountContext;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.Task;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.TaskStatus;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntilTrue;

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

    public static class ItemWithdrawal {
        public final String itemName;
        public final int amount;

        public ItemWithdrawal(String itemName, int amount) {
            this.itemName = itemName;
            this.amount = amount;
        }
    }

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

    private final ItemWithdrawal[] withdrawals;

    private final BankLocation bankLocation;

    private final boolean depositEquipment;

    private Phase phase = Phase.WALK;

    public BankingTask(
            Mode mode,
            String... keepItemNames
    ) {
        this(
                mode,
                false,
                keepItemNames
        );
    }

    public BankingTask(
            Mode mode,
            boolean depositEquipment,
            String... keepItemNames
    ) {
        this(
                mode,
                depositEquipment,
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
                false,
                keepItemNames,
                withdrawItemName,
                withdrawAmount,
                null
        );
    }

    public BankingTask(
            Mode mode,
            String[] keepItemNames,
            ItemWithdrawal... withdrawals
    ) {
        this(
                mode,
                false,
                keepItemNames,
                withdrawals
        );
    }

    public BankingTask(
            Mode mode,
            boolean depositEquipment,
            String[] keepItemNames,
            ItemWithdrawal... withdrawals
    ) {
        this.mode = mode;
        this.depositEquipment = depositEquipment;
        this.keepItemNames = keepItemNames;
        this.withdrawals = withdrawals != null ? withdrawals : new ItemWithdrawal[0];
        this.withdrawItemName = this.withdrawals.length > 0 ? this.withdrawals[0].itemName : null;
        this.withdrawAmount = this.withdrawals.length > 0 ? this.withdrawals[0].amount : 0;
        this.bankLocation = null;
    }

    public BankingTask(
            Mode mode,
            String[] keepItemNames,
            String withdrawItemName,
            int withdrawAmount,
            BankLocation bankLocation
    ) {
        this(
                mode,
                false,
                keepItemNames,
                withdrawItemName,
                withdrawAmount,
                bankLocation
        );
    }

    public BankingTask(
            Mode mode,
            boolean depositEquipment,
            String[] keepItemNames,
            String withdrawItemName,
            int withdrawAmount,
            BankLocation bankLocation
    ) {
        this.mode = mode;
        this.depositEquipment = depositEquipment;
        this.keepItemNames = keepItemNames;
        this.withdrawItemName = withdrawItemName;
        this.withdrawAmount = withdrawAmount;
        this.bankLocation = bankLocation;
        this.withdrawals = withdrawItemName != null
                ? new ItemWithdrawal[]{new ItemWithdrawal(withdrawItemName, withdrawAmount)}
                : new ItemWithdrawal[0];
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
        if (mode == Mode.DEPOSIT_ALL_AND_WITHDRAW || (withdrawals != null && withdrawals.length > 0)) {
            return Phase.WITHDRAW;
        }

        return Phase.CLOSE;
    }

    /**
     * Perform the configured deposit operation.
     *
     * When depositEquipment is true, equipment is deposited first
     * (the bank "deposit worn items" button moves them into the bank),
     * then inventory is deposited.
     */
    private void performDeposit() {

        if (depositEquipment && !Rs2Equipment.items().isEmpty()) {
            Rs2Bank.depositEquipment();
            sleepUntilTrue(
                    () -> Rs2Equipment.items().isEmpty(),
                    100,
                    5000
            );
        }

        if (keepItemNames != null && keepItemNames.length > 0) {
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
        if (withdrawals != null && withdrawals.length > 0) {
            for (ItemWithdrawal withdrawal : withdrawals) {
                if (withdrawal == null || withdrawal.itemName == null) continue;
                if (withdrawal.amount == -1) {
                    Rs2Bank.withdrawAll(withdrawal.itemName);
                } else {
                    Rs2Bank.withdrawX(withdrawal.itemName, withdrawal.amount);
                }
            }
            return;
        }

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
