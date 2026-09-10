package net.runelite.client.plugins.microbot.mntn.builder.tasks.supply;

import net.runelite.client.plugins.microbot.mntn.builder.core.AccountContext;
import net.runelite.client.plugins.microbot.mntn.builder.core.EquipmentView;
import net.runelite.client.plugins.microbot.mntn.builder.core.InventoryView;
import net.runelite.client.plugins.microbot.mntn.builder.core.requirements.EquipmentRequirement;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.banking.BankingTask;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.TaskStatus;
import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeRequest;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SupplyTaskTest {

    @Test
    public void fullInventoryUsesDepositThenWithdrawForBankSupply() {
        assertEquals(BankingTask.Mode.DEPOSIT_ALL_AND_WITHDRAW, SupplyTask.withdrawalModeFor(true));
        assertEquals(BankingTask.Mode.WITHDRAW, SupplyTask.withdrawalModeFor(false));
    }

    @Test
    public void purchaseTripsWithdrawTheEntireAvailableCoinStack() {
        assertEquals(-1, SupplyTask.purchaseCoinWithdrawalAmount(1));
        assertEquals(-1, SupplyTask.purchaseCoinWithdrawalAmount(10_000));
        assertEquals(0, SupplyTask.purchaseCoinWithdrawalAmount(0));
    }

    @Test
    public void grandExchangeBuyUsesTheNativeTwentyPercentAdjustmentWithoutTypingAPrice() {
        GrandExchangeRequest request = SupplyTask.grandExchangeBuyRequest("Uncut opal", 500);

        assertEquals(0, request.getPrice());
        assertEquals(20, request.getPercent());
        assertEquals(500, request.getQuantity());
    }

    @Test
    public void carriedEquipmentEntersEquipPhaseInsteadOfCompletingSupply() {
        SupplyTask task = new SupplyTask(
                new EquipmentRequirement("Steel scimitar"),
                net.runelite.client.plugins.microbot.mntn.builder.activities.supply.SupplyRoute.bank("Steel scimitar", 1)
        );

        assertEquals(TaskStatus.RUNNING, task.tick(new EquippedItemContext()));
        org.junit.Assert.assertTrue(task.describe().endsWith("EQUIP"));
    }

    private static final class EquippedItemContext extends AccountContext {
        private final InventoryView inventory = new InventoryView() {
            @Override
            public boolean hasItem(String itemName) {
                return "Steel scimitar".equals(itemName);
            }
        };
        private final EquipmentView equipment = new EquipmentView() {
            @Override
            public boolean hasItem(String itemName) {
                return false;
            }
        };

        @Override
        public boolean isLoggedIn() {
            return true;
        }

        @Override
        public InventoryView inventory() {
            return inventory;
        }

        @Override
        public EquipmentView equipment() {
            return equipment;
        }
    }
}
