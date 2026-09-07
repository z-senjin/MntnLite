package net.runelite.client.plugins.microbot.mntn.builder.activities.supply;

import net.runelite.client.plugins.microbot.mntn.builder.MntnBuilderConfig;

public class SupplyRoutePolicy {

    private final boolean allowGrandExchange;
    private final boolean allowShops;
    private final boolean allowGroundPickups;

    public SupplyRoutePolicy(boolean allowGrandExchange, boolean allowShops, boolean allowGroundPickups) {
        this.allowGrandExchange = allowGrandExchange;
        this.allowShops = allowShops;
        this.allowGroundPickups = allowGroundPickups;
    }

    public static SupplyRoutePolicy allowAll() {
        return new SupplyRoutePolicy(true, true, true);
    }

    public static SupplyRoutePolicy fromConfig(MntnBuilderConfig config) {
        if (config == null) {
            return allowAll();
        }
        return new SupplyRoutePolicy(
                config.allowGrandExchange(),
                config.allowShops(),
                config.allowGroundPickups()
        );
    }

    public boolean allows(SupplyRouteType type) {
        switch (type) {
            case GRAND_EXCHANGE:
                return allowGrandExchange;
            case SHOP:
                return allowShops;
            case GROUND_ITEM:
                return allowGroundPickups;
            case BANK:
            case SKILL_ACTIVITY:
            case QUEST_REWARD:
            case OTHER:
            default:
                return true;
        }
    }
}
