package net.runelite.client.plugins.microbot.mntn.builder.activities.supply;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.mntn.builder.core.AccountContext;
import net.runelite.client.plugins.microbot.mntn.builder.core.requirements.EquipmentRequirement;
import net.runelite.client.plugins.microbot.mntn.builder.core.requirements.ItemRequirement;
import net.runelite.client.plugins.microbot.mntn.builder.core.requirements.MoneyRequirement;
import net.runelite.client.plugins.microbot.mntn.builder.core.requirements.Requirement;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SupplyCatalog {

    private static final WorldPoint PORT_SARIM_FISHING_SHOP = new WorldPoint(3014, 3224, 0);
    private static final WorldPoint LUMBRIDGE_AXE_SHOP = new WorldPoint(3231, 3203, 0);
    private static final WorldPoint LUMBRIDGE_GENERAL_STORE = new WorldPoint(3212, 3246, 0);
    private static final WorldPoint VARROCK_ARCHERY_SHOP = new WorldPoint(3231, 3424, 0);
    private static final WorldPoint VARROCK_STAFF_SHOP = new WorldPoint(3203, 3434, 0);
    private static final WorldPoint VARROCK_ARMOUR_SHOP = new WorldPoint(3208, 3424, 0);
    private static final WorldPoint AL_KHARID_SCIMITAR_SHOP = new WorldPoint(3288, 3189, 0);
    private static final WorldPoint DWARVEN_MINE_PICKAXE_SHOP = new WorldPoint(3019, 9847, 0);
    private static final WorldPoint LUMBRIDGE_EGG_SPAWN = new WorldPoint(3229, 3300, 0);

    public List<SupplyRoute> routesFor(Requirement requirement, AccountContext context) {
        List<SupplyRoute> routes = new ArrayList<>();
        String itemName = itemName(requirement);
        int quantity = quantity(requirement, context);
        if (itemName == null || quantity <= 0) {
            return routes;
        }

        routes.add(SupplyRoute.bank(itemName, quantity));

        if (requirement instanceof MoneyRequirement) {
            return routes;
        }

        List<SupplyRoute> upgradeFallbacks = new ArrayList<>();
        addKnownRoutes(routes, upgradeFallbacks, itemName, quantity);
        routes.add(SupplyRoute.grandExchange(itemName, quantity, 0, 1.25));
        routes.addAll(upgradeFallbacks);
        return routes;
    }

    private void addKnownRoutes(List<SupplyRoute> routes, List<SupplyRoute> upgradeFallbacks,
                                String itemName, int quantity) {
        String normalized = itemName.toLowerCase(Locale.ROOT);

        switch (normalized) {
            case "egg":
                routes.add(SupplyRoute.groundItem(itemName, quantity, LUMBRIDGE_EGG_SPAWN, 15));
                break;
            case "pot":
            case "bucket":
            case "jug":
            case "bowl":
            case "cake tin":
            case "empty pot":
            case "hammer":
            case "chisel":
            case "tinderbox":
            case "shears":
                routes.add(SupplyRoute.shop(itemName, quantity, "Shop keeper", LUMBRIDGE_GENERAL_STORE, 10));
                break;
            case "small fishing net":
            case "fishing rod":
            case "fly fishing rod":
                routes.add(SupplyRoute.shop(itemName, quantity, "Gerrant", PORT_SARIM_FISHING_SHOP, 5));
                break;
            case "fishing bait":
                routes.add(SupplyRoute.shop(itemName, quantity, "Gerrant", PORT_SARIM_FISHING_SHOP, 3));
                break;
            case "feather":
                routes.add(SupplyRoute.shop(itemName, quantity, "Gerrant", PORT_SARIM_FISHING_SHOP, 6));
                break;
            case "lobster pot":
                routes.add(SupplyRoute.shop(itemName, quantity, "Gerrant", PORT_SARIM_FISHING_SHOP, 20));
                break;
            case "harpoon":
                routes.add(SupplyRoute.shop(itemName, quantity, "Gerrant", PORT_SARIM_FISHING_SHOP, 45));
                break;
            case "bronze axe":
                routes.add(SupplyRoute.shop(itemName, quantity, "Bob", LUMBRIDGE_AXE_SHOP, 16));
                addAxeUpgradeFallbacks(upgradeFallbacks, quantity);
                break;
            case "iron axe":
                routes.add(SupplyRoute.shop(itemName, quantity, "Bob", LUMBRIDGE_AXE_SHOP, 56));
                break;
            case "steel axe":
                routes.add(SupplyRoute.shop(itemName, quantity, "Bob", LUMBRIDGE_AXE_SHOP, 200));
                break;
            case "bronze pickaxe":
                // Bob is a much safer first stop for a fresh account. Nurmof remains
                // an alternate shop route if Bob is out of stock or unreachable.
                routes.add(SupplyRoute.shop(itemName, quantity, "Bob", LUMBRIDGE_AXE_SHOP, 1));
                routes.add(SupplyRoute.shop(itemName, quantity, "Nurmof", DWARVEN_MINE_PICKAXE_SHOP, 1));
                addPickaxeUpgradeFallbacks(upgradeFallbacks, quantity);
                break;
            case "iron pickaxe":
                routes.add(SupplyRoute.shop(itemName, quantity, "Nurmof", DWARVEN_MINE_PICKAXE_SHOP, 140));
                break;
            case "steel pickaxe":
                routes.add(SupplyRoute.shop(itemName, quantity, "Nurmof", DWARVEN_MINE_PICKAXE_SHOP, 500));
                break;
            case "mithril pickaxe":
                routes.add(SupplyRoute.shop(itemName, quantity, "Nurmof", DWARVEN_MINE_PICKAXE_SHOP, 1300));
                break;
            case "adamant pickaxe":
                routes.add(SupplyRoute.shop(itemName, quantity, "Nurmof", DWARVEN_MINE_PICKAXE_SHOP, 3200));
                break;
            case "rune pickaxe":
                routes.add(SupplyRoute.shop(itemName, quantity, "Nurmof", DWARVEN_MINE_PICKAXE_SHOP, 32000));
                break;
            case "shortbow":
            case "longbow":
            case "oak shortbow":
            case "oak longbow":
            case "willow shortbow":
            case "willow longbow":
            case "bronze arrow":
            case "iron arrow":
            case "steel arrow":
            case "mithril arrow":
            case "adamant arrow":
                routes.add(SupplyRoute.shop(itemName, quantity, "Lowe", VARROCK_ARCHERY_SHOP, 50));
                break;
            case "staff":
            case "staff of air":
            case "staff of water":
            case "staff of earth":
            case "staff of fire":
                routes.add(SupplyRoute.shop(itemName, quantity, "Zaff", VARROCK_STAFF_SHOP, 1500));
                break;
            case "bronze scimitar":
                routes.add(SupplyRoute.shop(itemName, quantity, "Zeke", AL_KHARID_SCIMITAR_SHOP, 32));
                break;
            case "iron scimitar":
                routes.add(SupplyRoute.shop(itemName, quantity, "Zeke", AL_KHARID_SCIMITAR_SHOP, 112));
                break;
            case "steel scimitar":
                routes.add(SupplyRoute.shop(itemName, quantity, "Zeke", AL_KHARID_SCIMITAR_SHOP, 400));
                break;
            case "mithril scimitar":
                routes.add(SupplyRoute.shop(itemName, quantity, "Zeke", AL_KHARID_SCIMITAR_SHOP, 1040));
                break;
            case "bronze platebody":
                routes.add(SupplyRoute.shop(itemName, quantity, "Horvik", VARROCK_ARMOUR_SHOP, 160));
                break;
            case "iron platebody":
                routes.add(SupplyRoute.shop(itemName, quantity, "Horvik", VARROCK_ARMOUR_SHOP, 560));
                break;
            case "steel platebody":
                routes.add(SupplyRoute.shop(itemName, quantity, "Horvik", VARROCK_ARMOUR_SHOP, 2000));
                break;
            case "black platebody":
                routes.add(SupplyRoute.shop(itemName, quantity, "Horvik", VARROCK_ARMOUR_SHOP, 3840));
                break;
            case "mithril platebody":
                routes.add(SupplyRoute.shop(itemName, quantity, "Horvik", VARROCK_ARMOUR_SHOP, 5200));
                break;
            default:
                break;
        }
    }

    private void addAxeUpgradeFallbacks(List<SupplyRoute> fallbackRoutes, int quantity) {
        fallbackRoutes.add(SupplyRoute.shop("Iron axe", quantity, "Bob", LUMBRIDGE_AXE_SHOP, 56));
        fallbackRoutes.add(SupplyRoute.shop("Steel axe", quantity, "Bob", LUMBRIDGE_AXE_SHOP, 200));
    }

    private void addPickaxeUpgradeFallbacks(List<SupplyRoute> fallbackRoutes, int quantity) {
        fallbackRoutes.add(SupplyRoute.shop("Iron pickaxe", quantity, "Nurmof", DWARVEN_MINE_PICKAXE_SHOP, 140));
        fallbackRoutes.add(SupplyRoute.shop("Steel pickaxe", quantity, "Nurmof", DWARVEN_MINE_PICKAXE_SHOP, 500));
        fallbackRoutes.add(SupplyRoute.shop("Mithril pickaxe", quantity, "Nurmof", DWARVEN_MINE_PICKAXE_SHOP, 1300));
        fallbackRoutes.add(SupplyRoute.shop("Adamant pickaxe", quantity, "Nurmof", DWARVEN_MINE_PICKAXE_SHOP, 3200));
        fallbackRoutes.add(SupplyRoute.shop("Rune pickaxe", quantity, "Nurmof", DWARVEN_MINE_PICKAXE_SHOP, 32000));
    }

    private String itemName(Requirement requirement) {
        if (requirement instanceof ItemRequirement) {
            return ((ItemRequirement) requirement).getItemName();
        }
        if (requirement instanceof EquipmentRequirement) {
            return ((EquipmentRequirement) requirement).getItemName();
        }
        if (requirement instanceof MoneyRequirement) {
            return ((MoneyRequirement) requirement).getItemName();
        }
        return null;
    }

    private int quantity(Requirement requirement, AccountContext context) {
        if (requirement instanceof ItemRequirement) {
            return ((ItemRequirement) requirement).getMissingInventoryQuantity(context);
        }
        if (requirement instanceof EquipmentRequirement) {
            return requirement.isSatisfied(context) ? 0 : 1;
        }
        if (requirement instanceof MoneyRequirement) {
            return ((MoneyRequirement) requirement).getMissingInventoryCoins(context);
        }
        return 0;
    }
}
