package net.runelite.client.plugins.microbot.mntn.builder.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class BankViewTest {

    @Test
    public void plannerReadsDoNotProbeTheLiveBankWidget() {
        TrackingBankView bank = new TrackingBankView();

        bank.getCount("Coins");
        assertEquals(1, bank.openChecks);

        bank.beginPlanningRead();
        bank.getCount("Coins");
        bank.hasItem("Bronze pickaxe");
        bank.endPlanningRead();

        assertEquals(1, bank.openChecks);

        bank.getCount("Coins");
        assertEquals(2, bank.openChecks);
    }

    private static final class TrackingBankView extends BankView {
        private int openChecks;

        @Override
        public boolean isOpen() {
            openChecks++;
            return false;
        }
    }
}
