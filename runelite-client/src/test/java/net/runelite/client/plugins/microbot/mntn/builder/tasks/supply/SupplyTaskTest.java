package net.runelite.client.plugins.microbot.mntn.builder.tasks.supply;

import net.runelite.client.plugins.microbot.mntn.builder.tasks.banking.BankingTask;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SupplyTaskTest {

    @Test
    public void fullInventoryUsesDepositThenWithdrawForBankSupply() {
        assertEquals(BankingTask.Mode.DEPOSIT_ALL_AND_WITHDRAW, SupplyTask.withdrawalModeFor(true));
        assertEquals(BankingTask.Mode.WITHDRAW, SupplyTask.withdrawalModeFor(false));
    }
}
