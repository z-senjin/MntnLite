package net.runelite.client.plugins.microbot.mntn.builder.activities.supply;

import net.runelite.client.plugins.microbot.mntn.builder.activities.Strategy;
import net.runelite.client.plugins.microbot.mntn.builder.core.AccountContext;
import net.runelite.client.plugins.microbot.mntn.builder.core.InventoryView;
import net.runelite.client.plugins.microbot.mntn.builder.core.requirements.ActivityRequest;
import net.runelite.client.plugins.microbot.mntn.builder.core.requirements.ItemRequirement;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SupplyActivityTest {

    @Test
    public void routePolicyCanDisableGrandExchangeForFreshF2pTesting() {
        SupplyActivity activity = new SupplyActivity(new SupplyRoutePolicy(false, true, true));
        List<Strategy> strategies = activity.getStrategies(
                new TestContext(),
                new ActivityRequest(activity.type(), new ItemRequirement("Bucket", 1))
        );

        assertTrue(hasRoute(strategies, "BANK"));
        assertTrue(hasRoute(strategies, "SHOP"));
        assertFalse(hasRoute(strategies, "GRAND_EXCHANGE"));
    }

    @Test
    public void routePolicyCanDisableGroundPickups() {
        SupplyActivity activity = new SupplyActivity(new SupplyRoutePolicy(true, true, false));
        List<Strategy> strategies = activity.getStrategies(
                new TestContext(),
                new ActivityRequest(activity.type(), new ItemRequirement("Egg", 1))
        );

        assertTrue(hasRoute(strategies, "BANK"));
        assertTrue(hasRoute(strategies, "GRAND_EXCHANGE"));
        assertFalse(hasRoute(strategies, "GROUND_ITEM"));
    }

    private boolean hasRoute(List<Strategy> strategies, String routeType) {
        return strategies.stream().anyMatch(strategy -> strategy.name().contains("SUPPLY_" + routeType + "_"));
    }

    private static class TestContext extends AccountContext {
        private final TestInventoryView inventory = new TestInventoryView();

        @Override
        public InventoryView inventory() {
            return inventory;
        }
    }

    private static class TestInventoryView extends InventoryView {
        @Override
        public int getCount(String itemName) {
            return 0;
        }
    }
}
