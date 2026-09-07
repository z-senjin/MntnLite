package net.runelite.client.plugins.microbot.mntn.builder.activities.supply;

import net.runelite.api.coords.WorldPoint;

public class SupplyRoute {

    private final SupplyRouteType type;
    private final String itemName;
    private final int quantity;
    private final String shopNpcName;
    private final WorldPoint location;
    private final int range;
    private final int estimatedUnitPrice;
    private final double priceMultiplier;

    private SupplyRoute(
            SupplyRouteType type,
            String itemName,
            int quantity,
            String shopNpcName,
            WorldPoint location,
            int range,
            int estimatedUnitPrice,
            double priceMultiplier
    ) {
        this.type = type;
        this.itemName = itemName;
        this.quantity = quantity;
        this.shopNpcName = shopNpcName;
        this.location = location;
        this.range = range;
        this.estimatedUnitPrice = estimatedUnitPrice;
        this.priceMultiplier = priceMultiplier;
    }

    public static SupplyRoute bank(String itemName, int quantity) {
        return new SupplyRoute(SupplyRouteType.BANK, itemName, quantity, null, null, 0, 0, 1.0);
    }

    public static SupplyRoute grandExchange(String itemName, int quantity, int estimatedUnitPrice, double priceMultiplier) {
        return new SupplyRoute(SupplyRouteType.GRAND_EXCHANGE, itemName, quantity, null, null, 0,
                estimatedUnitPrice, priceMultiplier);
    }

    public static SupplyRoute shop(String itemName, int quantity, String shopNpcName, WorldPoint location, int estimatedUnitPrice) {
        return new SupplyRoute(SupplyRouteType.SHOP, itemName, quantity, shopNpcName, location, 6,
                estimatedUnitPrice, 1.0);
    }

    public static SupplyRoute groundItem(String itemName, int quantity, WorldPoint location, int range) {
        return new SupplyRoute(SupplyRouteType.GROUND_ITEM, itemName, quantity, null, location, range, 0, 1.0);
    }

    public SupplyRouteType getType() {
        return type;
    }

    public String getItemName() {
        return itemName;
    }

    public int getQuantity() {
        return quantity;
    }

    public String getShopNpcName() {
        return shopNpcName;
    }

    public WorldPoint getLocation() {
        return location;
    }

    public int getRange() {
        return range;
    }

    public int getEstimatedUnitPrice() {
        return estimatedUnitPrice;
    }

    public double getPriceMultiplier() {
        return priceMultiplier;
    }

    public String describe() {
        switch (type) {
            case BANK:
                return "bank";
            case GRAND_EXCHANGE:
                return "Grand Exchange";
            case SHOP:
                return "shop: " + shopNpcName;
            case GROUND_ITEM:
                return "ground pickup";
            case SKILL_ACTIVITY:
                return "skill activity";
            case QUEST_REWARD:
                return "quest reward";
            case OTHER:
            default:
                return "other";
        }
    }
}
