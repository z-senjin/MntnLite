package net.runelite.client.plugins.microbot.mntn.builder.activities.moneymaking;

import net.runelite.client.plugins.microbot.mntn.builder.activities.ActivityType;
import net.runelite.client.plugins.microbot.mntn.builder.activities.Strategy;
import net.runelite.client.plugins.microbot.mntn.builder.activities.supply.SupplyRoutePolicy;
import net.runelite.client.plugins.microbot.mntn.builder.core.AccountContext;
import net.runelite.client.plugins.microbot.mntn.builder.core.BankView;
import net.runelite.client.plugins.microbot.mntn.builder.core.InventoryView;
import net.runelite.client.plugins.microbot.mntn.builder.core.requirements.ActivityRequest;
import net.runelite.client.plugins.microbot.mntn.builder.core.requirements.MoneyRequirement;
import net.runelite.api.ItemID;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MoneyMakingActivityTest {

    @Test
    public void shopOnlyPolicyCreatesOnlyGeneralStoreStrategies() {
        MoneyMakingActivity activity = new MoneyMakingActivity(
                new SupplyRoutePolicy(false, true, false)
        );

        List<Strategy> strategies = activity.getStrategies(
                new AccountContext(),
                new ActivityRequest(ActivityType.MONEY_MAKING, new MoneyRequirement(20))
        );

        long sellableAtGeneralStore = java.util.Arrays.stream(MoneyMakingStrategy.Method.values())
                .filter(MoneyMakingStrategy.Method::canSellAtGeneralStore)
                .count();
        assertEquals(sellableAtGeneralStore, strategies.size());
        assertTrue(strategies.stream().allMatch(strategy -> strategy.name().endsWith("_GENERAL_STORE")));
        assertFalse(strategies.stream().anyMatch(
                strategy -> strategy.name().equals("MONEY_CHICKEN_FEATHERS_GENERAL_STORE")
        ));
    }

    @Test
    public void chickenFeathersNeedNoStarterGear() {
        MoneyMakingStrategy strategy = new MoneyMakingStrategy(
                MoneyMakingStrategy.Method.CHICKEN_FEATHERS,
                new MoneyRequirement(20),
                MoneyMakingStrategy.SaleRoute.GENERAL_STORE
        );

        assertTrue(strategy.requirements(new AccountContext()).isEmpty());
        assertFalse(strategy.canExecute(new AccountContext()));
    }

    @Test
    public void moneyRequirementOnlyCountsTheShortfallAfterBankedCoins() {
        AccountContext context = new AccountContext() {
            private final InventoryView inventory = new InventoryView() {
                @Override
                public int getCount(String itemName) {
                    return "Coins".equals(itemName) ? 100 : 0;
                }

                @Override
                public int getCount(int itemId) {
                    return itemId == ItemID.COINS_995 ? 100 : 0;
                }
            };
            private final BankView bank = new BankView() {
                @Override
                public int getCount(String itemName) {
                    return "Coins".equals(itemName) ? 1_975 : 0;
                }

                @Override
                public int getCount(int itemId) {
                    return itemId == ItemID.COINS_995 ? 1_975 : 0;
                }
            };

            @Override
            public InventoryView inventory() {
                return inventory;
            }

            @Override
            public BankView bank() {
                return bank;
            }
        };

        MoneyRequirement requirement = new MoneyRequirement(3_222);

        assertEquals(1_147, requirement.getMissingTotalCoins(context));
    }
}
