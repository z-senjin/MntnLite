package net.runelite.client.plugins.microbot.mntn.builder.activities.supply;

import net.runelite.client.plugins.microbot.mntn.builder.core.AccountContext;
import net.runelite.client.plugins.microbot.mntn.builder.core.requirements.ItemRequirement;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SupplyCatalogTest {

    private final SupplyCatalog catalog = new SupplyCatalog();
    private final AccountContext context = new AccountContext();

    @Test
    public void bronzePickaxeUsesNearbyShopThenDwarvenMineFallbackBeforeGe() {
        List<SupplyRoute> routes = routesFor("Bronze pickaxe");

        assertEquals(SupplyRouteType.BANK, routes.get(0).getType());
        assertEquals("Bob", routes.get(1).getShopNpcName());
        assertEquals(1, routes.get(1).getEstimatedUnitPrice());
        assertEquals("Nurmof", routes.get(2).getShopNpcName());
        assertEquals(SupplyRouteType.GRAND_EXCHANGE, routes.get(3).getType());
        assertEquals("Iron pickaxe", routes.get(4).getItemName());
    }

    @Test
    public void bronzeToolsOfferHigherShopTiersOnlyAfterStarterRoutes() {
        List<SupplyRoute> axeRoutes = routesFor("Bronze axe");

        assertEquals("Bronze axe", axeRoutes.get(1).getItemName());
        assertEquals(SupplyRouteType.GRAND_EXCHANGE, axeRoutes.get(2).getType());
        assertEquals("Iron axe", axeRoutes.get(3).getItemName());
        assertEquals("Steel axe", axeRoutes.get(4).getItemName());
    }

    @Test
    public void starterToolsUseActualLowShopPrices() {
        assertEquals(16, firstShopRoute("Bronze axe").getEstimatedUnitPrice());
        assertEquals(5, firstShopRoute("Small fishing net").getEstimatedUnitPrice());
        assertEquals(20, firstShopRoute("Lobster pot").getEstimatedUnitPrice());
    }

    @Test
    public void toolsNotStockedByKnownF2pShopsUseGeInsteadOfFalseShopRoutes() {
        List<SupplyRoute> routes = routesFor("Black axe");

        assertTrue(routes.stream().anyMatch(route -> route.getType() == SupplyRouteType.GRAND_EXCHANGE));
        assertFalse(routes.stream().anyMatch(route -> route.getType() == SupplyRouteType.SHOP));
    }

    @Test
    public void combatShopsUseVerifiedF2pStockAndPrices() {
        assertEquals(32, firstShopRoute("Bronze scimitar").getEstimatedUnitPrice());
        assertEquals(400, firstShopRoute("Steel scimitar").getEstimatedUnitPrice());
        assertEquals(1040, firstShopRoute("Mithril scimitar").getEstimatedUnitPrice());
        assertEquals("Horvik", firstShopRoute("Steel platebody").getShopNpcName());
        assertEquals(2000, firstShopRoute("Steel platebody").getEstimatedUnitPrice());
    }

    @Test
    public void scimitarsNotSoldByZekeFallBackToGe() {
        List<SupplyRoute> routes = routesFor("Adamant scimitar");

        assertTrue(routes.stream().anyMatch(route -> route.getType() == SupplyRouteType.GRAND_EXCHANGE));
        assertFalse(routes.stream().anyMatch(route -> route.getType() == SupplyRouteType.SHOP));
    }

    @Test
    public void grandExchangeRoutesBudgetTwentyPercentAboveTheirBaseEstimate() {
        SupplyRoute route = routesFor("Black axe").stream()
                .filter(candidate -> candidate.getType() == SupplyRouteType.GRAND_EXCHANGE)
                .findFirst()
                .orElseThrow(AssertionError::new);

        assertEquals(1.20, route.getPriceMultiplier(), 0.0);
    }

    private List<SupplyRoute> routesFor(String itemName) {
        return catalog.routesFor(new ItemRequirement(itemName, 1), context);
    }

    private SupplyRoute firstShopRoute(String itemName) {
        return routesFor(itemName).stream()
                .filter(route -> route.getType() == SupplyRouteType.SHOP)
                .findFirst()
                .orElseThrow(AssertionError::new);
    }
}
