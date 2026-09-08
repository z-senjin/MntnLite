package net.runelite.client.plugins.microbot.mntn.builder.tasks.banking;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.mntn.builder.core.AccountContext;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.Task;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.TaskActionGuard;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.TaskStopReason;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.TaskStatus;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

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

    private static final int BANK_DISTANCE = 8;
    private static final int MAX_WALK_ATTEMPTS = 45;
    private static final int MAX_OPEN_ATTEMPTS = 6;
    private static final int MAX_OPEN_RECOVERY_CYCLES = 3;
    private static final int MAX_DEPOSIT_ATTEMPTS = 3;
    private static final int MAX_WITHDRAW_ATTEMPTS = 3;
    private static final int MAX_CLOSE_ATTEMPTS = 3;
    private static final int MAX_BANK_RESOLVE_ATTEMPTS = 5;
    private static final int BANK_RESOLVE_COOLDOWN_TICKS = 10;
    private static final int BANK_RESELECT_WALK_ATTEMPTS = 10;
    private static final int MAX_AVOIDED_BANKS = 4;
    private static final BankLocation[] F2P_FALLBACK_BANKS = {
            BankLocation.VARROCK_EAST,
            BankLocation.VARROCK_WEST,
            BankLocation.GRAND_EXCHANGE,
            BankLocation.DRAYNOR_VILLAGE,
            BankLocation.FALADOR_EAST,
            BankLocation.FALADOR_WEST,
            BankLocation.EDGEVILLE,
            BankLocation.AL_KHARID,
            BankLocation.LUMBRIDGE_TOP,
            BankLocation.LUMBRIDGE_FRONT
    };

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

    private enum DepositOutcome {
        COMPLETE,
        WAITING,
        FAILED
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
    private TaskStopReason lastStopReason = TaskStopReason.NONE;
    private int walkAttempts;
    private int openAttempts;
    private int openRecoveryCycles;
    private int depositAttempts;
    private int withdrawAttempts;
    private int closeAttempts;
    private int bankResolveAttempts;
    private int bankResolveCooldownTicks;
    private BankLocation cachedTargetBank;
    private final Set<BankLocation> avoidedBanks = new HashSet<>();
    private final TaskActionGuard equipmentDepositGuard = new TaskActionGuard(4, 8_000, 700);

    private void debugLog(AccountContext context, String message) {
        if (context.isDebugLogging()) {
            Microbot.log("[MntnBuilder][BankingTask][DEBUG] " + message);
        }
    }

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

        debugLog(context, "tick: phase=" + phase + ", mode=" + mode);

        switch (phase) {

            case WALK:

                if (Rs2Bank.isOpen()) {
                    debugLog(context, "Bank already open, moving to next phase");
                    walkAttempts = 0;
                    phase = determineNextPhase();
                    return TaskStatus.RUNNING;
                }

                return handleWalkToBank(context);


            case OPEN:
                debugLog(context, "OPEN phase");
                if (!Rs2Bank.isOpen()) {
                    if (!isNearTargetBank(context)) {
                        debugLog(context, "Not near bank while opening, returning to WALK phase");
                        abandonCachedTarget(context, "not near bank during open");
                        phase = Phase.WALK;
                        return TaskStatus.RUNNING;
                    }

                    debugLog(context, "Bank not open, opening bank");
                    if (!Rs2Bank.openBank()) {
                        openAttempts++;
                        debugLog(context, "Open bank failed attempt " + openAttempts + "/" + MAX_OPEN_ATTEMPTS);
                        if (openAttempts >= MAX_OPEN_ATTEMPTS) {
                            openAttempts = 0;
                            openRecoveryCycles++;
                            debugLog(context, "Open recovery cycle " + openRecoveryCycles + "/" + MAX_OPEN_RECOVERY_CYCLES);
                            if (openRecoveryCycles >= MAX_OPEN_RECOVERY_CYCLES) {
                                return stop(TaskStatus.BLOCKED, TaskStopReason.BANK_FAILED);
                            }
                            abandonCachedTarget(context, "open failed repeatedly");
                            phase = Phase.WALK;
                        }
                    }
                    return TaskStatus.RUNNING;
                }

                openAttempts = 0;
                openRecoveryCycles = 0;
                /*
                 * Refresh our account context after opening.
                 */
                context.bank().refresh();
                debugLog(context, "Bank opened, bank cache refreshed");

                phase = determineNextPhase();
                debugLog(context, "Next phase determined: " + phase);

                return TaskStatus.RUNNING;


            case DEPOSIT:

                if (!Rs2Bank.isOpen()) {
                    debugLog(context, "Bank closed unexpectedly, returning to OPEN phase");
                    phase = Phase.OPEN;
                    return TaskStatus.RUNNING;
                }

                debugLog(context, "Performing deposit (mode=" + mode + ", keepItems=" + (keepItemNames != null ? java.util.Arrays.toString(keepItemNames) : "none") + ", depositEquipment=" + depositEquipment + ")");
                DepositOutcome depositOutcome = performDeposit(context);
                if (depositOutcome == DepositOutcome.WAITING) {
                    return TaskStatus.RUNNING;
                }
                if (depositOutcome == DepositOutcome.FAILED) {
                    depositAttempts++;
                    debugLog(context, "Deposit failed attempt " + depositAttempts + "/" + MAX_DEPOSIT_ATTEMPTS);
                    if (depositAttempts >= MAX_DEPOSIT_ATTEMPTS) {
                        return stop(TaskStatus.BLOCKED, TaskStopReason.BANK_FAILED);
                    }
                    phase = Rs2Bank.isOpen() ? Phase.DEPOSIT : Phase.OPEN;
                    return TaskStatus.RUNNING;
                }

                depositAttempts = 0;

                /*
                 * Give the bank operation a chance to complete
                 * before refreshing.
                 */
                context.bank().refresh();
                debugLog(context, "Deposit performed, bank cache refreshed");

                phase = determinePhaseAfterDeposit();
                debugLog(context, "Next phase after deposit: " + phase);

                return TaskStatus.RUNNING;


            case WITHDRAW:

                debugLog(context, "WITHDRAW phase (withdrawItemName=" + withdrawItemName + ", withdrawAmount=" + withdrawAmount + ", withdrawals=" + (withdrawals != null ? withdrawals.length : 0) + ")");
                if (!Rs2Bank.isOpen()) {
                    debugLog(context, "Bank closed unexpectedly, returning to OPEN phase");
                    phase = Phase.OPEN;
                    return TaskStatus.RUNNING;
                }

                if (!performWithdraw(context)) {
                    withdrawAttempts++;
                    debugLog(context, "Withdraw failed attempt " + withdrawAttempts + "/" + MAX_WITHDRAW_ATTEMPTS);
                    if (withdrawAttempts >= MAX_WITHDRAW_ATTEMPTS) {
                        return stop(TaskStatus.BLOCKED, TaskStopReason.BANK_FAILED);
                    }
                    phase = Rs2Bank.isOpen() ? Phase.WITHDRAW : Phase.OPEN;
                    return TaskStatus.RUNNING;
                }

                withdrawAttempts = 0;

                /*
                 * Refresh our bank snapshot.
                 */
                context.bank().refresh();
                debugLog(context, "Withdrawal performed, bank cache refreshed");

                phase = Phase.CLOSE;

                return TaskStatus.RUNNING;


            case CLOSE:

                if (Rs2Bank.isOpen()) {
                    debugLog(context, "Closing bank");
                    boolean closed = Rs2Bank.closeBank();
                    if (!closed && Rs2Bank.isOpen()) {
                        closeAttempts++;
                        debugLog(context, "Close bank failed attempt " + closeAttempts + "/" + MAX_CLOSE_ATTEMPTS);
                        if (closeAttempts >= MAX_CLOSE_ATTEMPTS) {
                            debugLog(context, "Bank close did not confirm; finishing completed bank operation anyway");
                            closeAttempts = 0;
                            phase = Phase.DONE;
                        }
                    }
                    return TaskStatus.RUNNING;
                }

                closeAttempts = 0;
                phase = Phase.DONE;

                return TaskStatus.RUNNING;


            case DONE:
            default:

                debugLog(context, "Task complete");
                return TaskStatus.COMPLETE;
        }
    }

    private TaskStatus handleWalkToBank(AccountContext context) {
        BankLocation targetBank = targetBank(context);
        if (targetBank == null) {
            walkAttempts++;
            debugLog(context, "No reachable bank target resolved attempt " + walkAttempts + "/" + MAX_WALK_ATTEMPTS);
            if (walkAttempts >= MAX_WALK_ATTEMPTS) {
                return stop(TaskStatus.BLOCKED, TaskStopReason.BANK_FAILED);
            }
            return TaskStatus.RUNNING;
        }

        debugLog(context, "Walking to bank: " + targetBank);
        boolean arrived = Rs2Bank.walkToBank(targetBank);
        if (!arrived && !Rs2Bank.isNearBank(targetBank, BANK_DISTANCE)) {
            walkAttempts++;
            debugLog(context, "Walk to bank pending attempt " + walkAttempts + "/" + MAX_WALK_ATTEMPTS);
            if (walkAttempts >= MAX_WALK_ATTEMPTS) {
                return stop(TaskStatus.BLOCKED, TaskStopReason.BANK_FAILED);
            }
            if (walkAttempts % BANK_RESELECT_WALK_ATTEMPTS == 0) {
                abandonCachedTarget(context, "walk stalled");
            }
            return TaskStatus.RUNNING;
        }

        walkAttempts = 0;
        bankResolveAttempts = 0;
        phase = Phase.OPEN;
        return TaskStatus.RUNNING;
    }

    private BankLocation targetBank(AccountContext context) {
        if (bankLocation != null) {
            return bankLocation;
        }
        if (cachedTargetBank != null) {
            return cachedTargetBank;
        }

        BankLocation nearest = bankResolveCooldownTicks > 0 ? null : resolveNearestBank(context);
        if (nearest != null && !avoidedBanks.contains(nearest)) {
            return rememberTarget(context, nearest, "nearest");
        }
        if (bankResolveCooldownTicks > 0) {
            bankResolveCooldownTicks--;
            debugLog(context, "Skipping nearest-bank lookup during recovery cooldown ("
                    + bankResolveCooldownTicks + " ticks left)");
        }

        BankLocation fallback = nearestFallbackBank(context);
        if (fallback != null) {
            return rememberTarget(context, fallback, "fallback");
        }

        return null;
    }

    private BankLocation resolveNearestBank(AccountContext context) {
        try {
            bankResolveAttempts++;
            BankLocation nearest = Rs2Bank.getNearestBank();
            if (nearest != null) {
                bankResolveAttempts = 0;
                return nearest;
            }
            debugLog(context, "Nearest-bank lookup returned null attempt "
                    + bankResolveAttempts + "/" + MAX_BANK_RESOLVE_ATTEMPTS);
        } catch (RuntimeException ex) {
            debugLog(context, "Nearest-bank lookup failed attempt "
                    + bankResolveAttempts + "/" + MAX_BANK_RESOLVE_ATTEMPTS
                    + ": " + ex.getClass().getSimpleName());
        }

        if (bankResolveAttempts >= MAX_BANK_RESOLVE_ATTEMPTS) {
            debugLog(context, "Nearest-bank lookup exhausted; using fallback bank resolver");
            bankResolveAttempts = 0;
            bankResolveCooldownTicks = BANK_RESOLVE_COOLDOWN_TICKS;
        }
        return null;
    }

    private BankLocation nearestFallbackBank(AccountContext context) {
        WorldPoint location = context.getLocation();
        if (location == null) {
            return null;
        }

        return Arrays.stream(F2P_FALLBACK_BANKS)
                .filter(candidate -> candidate != null && !avoidedBanks.contains(candidate))
                .min(Comparator.comparingInt(candidate -> candidate.getWorldPoint().distanceTo(location)))
                .orElseGet(() -> {
                    if (!avoidedBanks.isEmpty()) {
                        avoidedBanks.clear();
                        debugLog(context, "All fallback banks were avoided; clearing avoided-bank list");
                        return nearestFallbackBank(context);
                    }
                    return null;
                });
    }

    private BankLocation rememberTarget(AccountContext context, BankLocation target, String source) {
        cachedTargetBank = target;
        debugLog(context, "Selected " + source + " bank target: " + target);
        return target;
    }

    private void abandonCachedTarget(AccountContext context, String reason) {
        if (bankLocation != null || cachedTargetBank == null) {
            return;
        }

        debugLog(context, "Abandoning bank target " + cachedTargetBank + " because " + reason);
        if (avoidedBanks.size() >= MAX_AVOIDED_BANKS) {
            avoidedBanks.clear();
            debugLog(context, "Avoided-bank list reached limit; clearing it");
        }
        avoidedBanks.add(cachedTargetBank);
        cachedTargetBank = null;
        bankResolveAttempts = 0;
        bankResolveCooldownTicks = BANK_RESOLVE_COOLDOWN_TICKS;
    }

    private boolean isNearTargetBank(AccountContext context) {
        BankLocation targetBank = targetBank(context);
        return targetBank != null && Rs2Bank.isNearBank(targetBank, BANK_DISTANCE);
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
    private DepositOutcome performDeposit(AccountContext context) {
        debugLog(context, "performDeposit: depositEquipment=" + depositEquipment + ", equipmentItems=" + Rs2Equipment.items().size());

        if (depositEquipment && !Rs2Equipment.items().isEmpty()) {
            TaskActionGuard.Result depositResult = equipmentDepositGuard.evaluate(
                    "deposit worn equipment",
                    Rs2Equipment.items().isEmpty()
            );
            if (depositResult == TaskActionGuard.Result.EXHAUSTED) {
                return DepositOutcome.FAILED;
            }
            if (depositResult == TaskActionGuard.Result.READY) {
                debugLog(context, "Depositing equipment");
                Rs2Bank.depositEquipment();
                equipmentDepositGuard.recordAttempt();
            }
            return DepositOutcome.WAITING;
        }
        equipmentDepositGuard.reset();

        if (keepItemNames != null && keepItemNames.length > 0) {
            debugLog(context, "Depositing all except: " + java.util.Arrays.toString(keepItemNames));
            return Rs2Bank.depositAllExcept(
                    false,
                    keepItemNames
            ) ? DepositOutcome.COMPLETE : DepositOutcome.FAILED;
        }

        debugLog(context, "Depositing all inventory");
        return Rs2Bank.depositAll() ? DepositOutcome.COMPLETE : DepositOutcome.FAILED;
    }

    /**
     * Perform the configured withdrawal.
     */
    private boolean performWithdraw(AccountContext context) {
        debugLog(context, "performWithdraw: withdrawals=" + (withdrawals != null ? withdrawals.length : 0) + ", withdrawItemName=" + withdrawItemName + ", withdrawAmount=" + withdrawAmount);
        if (withdrawals != null && withdrawals.length > 0) {
            boolean success = true;
            for (ItemWithdrawal withdrawal : withdrawals) {
                if (withdrawal == null || withdrawal.itemName == null) continue;
                if (withdrawal.amount == -1) {
                    debugLog(context, "Withdrawing all: " + withdrawal.itemName);
                    success &= Rs2Bank.withdrawAll(withdrawal.itemName);
                } else {
                    debugLog(context, "Withdrawing " + withdrawal.amount + " x " + withdrawal.itemName);
                    success &= Rs2Bank.withdrawX(withdrawal.itemName, withdrawal.amount);
                }
            }
            return success;
        }

        if (withdrawItemName == null) {
            debugLog(context, "No item to withdraw");
            return true;
        }

        if (withdrawAmount == -1) {
            debugLog(context, "Withdrawing all: " + withdrawItemName);
            return Rs2Bank.withdrawAll(
                    withdrawItemName
            );
        }

        debugLog(context, "Withdrawing " + withdrawAmount + " x " + withdrawItemName);
        return Rs2Bank.withdrawX(
                withdrawItemName,
                withdrawAmount
        );
    }

    @Override
    public boolean needsReplan(AccountContext context) {
        return false;
    }

    @Override
    public TaskStopReason getLastStopReason() {
        return lastStopReason;
    }

    private TaskStatus stop(TaskStatus status, TaskStopReason reason) {
        lastStopReason = reason != null ? reason : TaskStopReason.UNKNOWN;
        return status;
    }

    @Override
    public String describe() {
        return "Banking (" + mode + ") - " + phase;
    }
}
