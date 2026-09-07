package net.runelite.client.plugins.microbot.mntn.builder.activities.supply;

import net.runelite.client.plugins.microbot.mntn.builder.activities.Strategy;
import net.runelite.client.plugins.microbot.mntn.builder.core.AccountContext;
import net.runelite.client.plugins.microbot.mntn.builder.core.requirements.EquipmentRequirement;
import net.runelite.client.plugins.microbot.mntn.builder.core.requirements.ItemRequirement;
import net.runelite.client.plugins.microbot.mntn.builder.core.requirements.MoneyRequirement;
import net.runelite.client.plugins.microbot.mntn.builder.core.requirements.Requirement;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.Task;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.supply.SupplyTask;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.item.Rs2ItemManager;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

public class SupplyStrategy implements Strategy {

    private final Requirement requirement;
    private final SupplyRoute route;

    public SupplyStrategy(Requirement requirement) {
        this(requirement, SupplyRoute.bank(requirement.description(), 1));
    }

    public SupplyStrategy(Requirement requirement, SupplyRoute route) {
        this.requirement = requirement;
        this.route = route;
    }

    @Override
    public String name() {
        return "SUPPLY_" + route.getType().name() + "_" + requirement.description();
    }

    @Override
    public boolean canExecute(AccountContext context) {
        if (requirement.isSatisfied(context)) {
            return true;
        }

        switch (route.getType()) {
            case BANK:
                return canUseBank(context);
            case GRAND_EXCHANGE:
            case SHOP:
                return hasEnoughCoins(context, estimatedTotalPrice());
            case GROUND_ITEM:
                return route.getLocation() != null;
            case SKILL_ACTIVITY:
            case QUEST_REWARD:
            case OTHER:
            default:
                return false;
        }
    }

    @Override
    public List<Requirement> requirements(AccountContext context) {
        switch (route.getType()) {
            case GRAND_EXCHANGE:
            case SHOP:
                return Collections.singletonList(new MoneyRequirement(estimatedTotalPrice()));
            default:
                return Collections.emptyList();
        }
    }

    private boolean canUseBank(AccountContext context) {
        if (requirement instanceof ItemRequirement) {
            ItemRequirement itemRequirement = (ItemRequirement) requirement;
            return itemRequirement.isAvailableInBank(context);
        }
        if (requirement instanceof EquipmentRequirement) {
            EquipmentRequirement equipmentRequirement = (EquipmentRequirement) requirement;
            return equipmentRequirement.isAvailable(context);
        }
        if (requirement instanceof MoneyRequirement) {
            MoneyRequirement moneyRequirement = (MoneyRequirement) requirement;
            return moneyRequirement.hasEnoughTotalCoins(context);
        }
        return false;
    }

    @Override
    public double score(AccountContext context) {
        if (requirement.isSatisfied(context)) {
            return 100;
        }
        switch (route.getType()) {
            case GRAND_EXCHANGE:
                return hasEnoughCoins(context, estimatedTotalPrice()) ? 45 : -1000;
            case SHOP:
                return hasEnoughCoins(context, estimatedTotalPrice()) ? 55 : -1000;
            case GROUND_ITEM:
                return route.getLocation() != null ? 50 : -1000;
            case BANK:
                break;
            case SKILL_ACTIVITY:
            case QUEST_REWARD:
            case OTHER:
            default:
                return -1000;
        }
        if (requirement instanceof EquipmentRequirement) {
            EquipmentRequirement equipmentRequirement = (EquipmentRequirement) requirement;
            if (context.inventory().hasItem(equipmentRequirement.getItemName())) {
                return 85;
            }
            if (context.bank().hasItem(equipmentRequirement.getItemName())) {
                return 70;
            }
        }
        if (requirement instanceof ItemRequirement) {
            ItemRequirement itemRequirement = (ItemRequirement) requirement;
            if (itemRequirement.isAvailableInBank(context)) {
                return 65;
            }
        }
        if (requirement instanceof MoneyRequirement) {
            MoneyRequirement moneyRequirement = (MoneyRequirement) requirement;
            if (moneyRequirement.hasEnoughTotalCoins(context)) {
                return 60;
            }
        }
        return -1000;
    }

    @Override
    public Task createTask(AccountContext context) {
        return new SupplyTask(requirement, route);
    }

    @Override
    public WorldPoint preferredLocation(AccountContext context) {
        return route.getLocation();
    }

    @Override
    public int estimatedSupplyCost(AccountContext context) {
        switch (route.getType()) {
            case GRAND_EXCHANGE:
            case SHOP:
                return estimatedTotalPrice();
            default:
                return 0;
        }
    }

    @Override
    public Duration commitmentDuration(AccountContext context) {
        return Duration.ofMinutes(Rs2Random.between(2, 8));
    }

    private int estimatedTotalPrice() {
        return Math.max(1, estimatedUnitPrice()) * Math.max(1, route.getQuantity());
    }

    private int estimatedUnitPrice() {
        if (route.getEstimatedUnitPrice() > 0) {
            return route.getEstimatedUnitPrice();
        }

        int itemId = Rs2ItemManager.getItemIdByName(route.getItemName(), false);
        if (itemId <= 0) {
            return 100;
        }

        int offerPrice = Rs2GrandExchange.getOfferPrice(itemId);
        if (offerPrice <= 0) {
            return 100;
        }

        return (int) Math.ceil(offerPrice * route.getPriceMultiplier());
    }

    private boolean hasEnoughCoins(AccountContext context, int coinsNeeded) {
        int inventoryCoins = context.inventory().getCount("Coins");
        int bankCoins = context.bank().getCount("Coins");
        return inventoryCoins + bankCoins >= coinsNeeded;
    }
}
