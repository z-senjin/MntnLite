package net.runelite.client.plugins.microbot.mntn.builder.activities.moneymaking;

import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.mntn.builder.activities.Strategy;
import net.runelite.client.plugins.microbot.mntn.builder.activities.combat.CombatGear;
import net.runelite.client.plugins.microbot.mntn.builder.activities.combat.CombatStrategy;
import net.runelite.client.plugins.microbot.mntn.builder.activities.fishing.FishingStrategy;
import net.runelite.client.plugins.microbot.mntn.builder.activities.mining.MiningStrategy;
import net.runelite.client.plugins.microbot.mntn.builder.activities.smithing.SmeltingStrategy;
import net.runelite.client.plugins.microbot.mntn.builder.activities.woodcutting.WoodcuttingStrategy;
import net.runelite.client.plugins.microbot.mntn.builder.core.AccountContext;
import net.runelite.client.plugins.microbot.mntn.builder.core.requirements.ItemRequirement;
import net.runelite.client.plugins.microbot.mntn.builder.core.requirements.MoneyRequirement;
import net.runelite.client.plugins.microbot.mntn.builder.core.requirements.Requirement;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.Task;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.moneymaking.MoneyMakingTask;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.item.Rs2ItemManager;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

public class MoneyMakingStrategy implements Strategy {

    public enum SaleRoute {
        GENERAL_STORE,
        GRAND_EXCHANGE
    }

    public enum SourceType {
        COMBAT,
        FISHING,
        MINING,
        SMELTING,
        WOODCUTTING
    }

    public enum Method {
        CHICKEN_FEATHERS(
                SourceType.COMBAT,
                "Feather",
                3,
                0,
                20,
                CombatStrategy.Monster.CHICKENS.location
        ),
        COWHIDES(
                SourceType.COMBAT,
                "Cowhide",
                120,
                0,
                10,
                CombatStrategy.Monster.COWS.location
        ),
        COPPER_ORE(
                SourceType.MINING,
                MiningStrategy.Method.COPPER_ORE.oreItemName,
                60,
                0,
                14,
                MiningStrategy.Method.COPPER_ORE.location
        ),
        IRON_ORE(
                SourceType.MINING,
                MiningStrategy.Method.IRON_ORE.oreItemName,
                80,
                2,
                10,
                MiningStrategy.Method.IRON_ORE.location
        ),
        BRONZE_BAR(
                SourceType.SMELTING,
                SmeltingStrategy.Bar.BRONZE_BAR.barItemName,
                160,
                1,
                8,
                SmeltingStrategy.Bar.BRONZE_BAR.furnaceLocation
        ),
        IRON_BAR(
                SourceType.SMELTING,
                SmeltingStrategy.Bar.IRON_BAR.barItemName,
                180,
                2,
                8,
                SmeltingStrategy.Bar.IRON_BAR.furnaceLocation
        ),
        LOGS(
                SourceType.WOODCUTTING,
                WoodcuttingStrategy.Method.NORMAL_TREE.logItemName,
                25,
                0,
                14,
                WoodcuttingStrategy.Method.NORMAL_TREE.location
        ),
        RAW_SHRIMPS(
                SourceType.FISHING,
                FishingStrategy.Method.NET_SHRIMP.fishItemName,
                20,
                0,
                14,
                FishingStrategy.Method.NET_SHRIMP.location
        );

        public final SourceType sourceType;
        public final String itemName;
        public final int fallbackUnitPrice;
        public final int minimumGeneralStoreUnitPrice;
        public final int minimumSellQuantity;
        public final WorldPoint location;

        Method(SourceType sourceType, String itemName, int fallbackUnitPrice, int generalStoreUnitPrice,
               int minimumSellQuantity, WorldPoint location) {
            this.sourceType = sourceType;
            this.itemName = itemName;
            this.fallbackUnitPrice = fallbackUnitPrice;
            this.minimumGeneralStoreUnitPrice = generalStoreUnitPrice;
            this.minimumSellQuantity = minimumSellQuantity;
            this.location = location;
        }

        /**
         * A shop route is only offered for items with a known non-zero floor at
         * Lumbridge's 40% general store. Low-value loot belongs on the GE.
         */
        public boolean canSellAtGeneralStore() {
            return minimumGeneralStoreUnitPrice > 0;
        }
    }

    private final Method method;
    private final MoneyRequirement requirement;
    private final SaleRoute saleRoute;

    public MoneyMakingStrategy(Method method, MoneyRequirement requirement) {
        this(method, requirement, SaleRoute.GRAND_EXCHANGE);
    }

    public MoneyMakingStrategy(Method method, MoneyRequirement requirement, SaleRoute saleRoute) {
        this.method = method;
        this.requirement = requirement;
        this.saleRoute = saleRoute;
    }

    @Override
    public String name() {
        return "MONEY_" + method.name() + "_" + saleRoute.name();
    }

    @Override
    public List<Requirement> requirements(AccountContext context) {
        switch (method.sourceType) {
            case COMBAT:
                if (method == Method.CHICKEN_FEATHERS) {
                    return Collections.emptyList();
                }
                CombatGear.GearItem weapon = CombatGear.findBestWeapon(context, true);
                if (weapon != null) {
                    return Collections.singletonList(new ItemRequirement(weapon.name, 1));
                }
                return Collections.emptyList();
            case FISHING:
                return new FishingStrategy(FishingStrategy.Method.NET_SHRIMP).requirements(context);
            case MINING:
                MiningStrategy.Method miningMethod = method == Method.IRON_ORE
                        ? MiningStrategy.Method.IRON_ORE
                        : MiningStrategy.Method.COPPER_ORE;
                return new MiningStrategy(miningMethod).requirements(context);
            case SMELTING:
                SmeltingStrategy.Bar bar = method == Method.IRON_BAR
                        ? SmeltingStrategy.Bar.IRON_BAR
                        : SmeltingStrategy.Bar.BRONZE_BAR;
                return new SmeltingStrategy(bar).requirements(context);
            case WOODCUTTING:
                return new WoodcuttingStrategy(WoodcuttingStrategy.Method.NORMAL_TREE).requirements(context);
            default:
                return Collections.emptyList();
        }
    }

    @Override
    public boolean canExecute(AccountContext context) {
        if (saleRoute == SaleRoute.GENERAL_STORE && !method.canSellAtGeneralStore()) {
            return false;
        }
        if (requirement.isSatisfied(context)) {
            return false;
        }
        if (hasSellableItems(context)) {
            return true;
        }

        switch (method.sourceType) {
            case COMBAT:
                int currentStrength = Math.max(1, context.getRealLevel(Skill.STRENGTH));
                return new CombatStrategy(combatMonster(), Skill.STRENGTH,
                        currentStrength + 1, 0).canExecute(context);
            case FISHING:
                return new FishingStrategy(FishingStrategy.Method.NET_SHRIMP).canExecute(context);
            case MINING:
                MiningStrategy.Method miningMethod = method == Method.IRON_ORE
                        ? MiningStrategy.Method.IRON_ORE
                        : MiningStrategy.Method.COPPER_ORE;
                return new MiningStrategy(miningMethod).canExecute(context);
            case SMELTING:
                SmeltingStrategy.Bar bar = method == Method.IRON_BAR
                        ? SmeltingStrategy.Bar.IRON_BAR
                        : SmeltingStrategy.Bar.BRONZE_BAR;
                return new SmeltingStrategy(bar).canExecute(context);
            case WOODCUTTING:
                return new WoodcuttingStrategy(WoodcuttingStrategy.Method.NORMAL_TREE).canExecute(context);
            default:
                return false;
        }
    }

    @Override
    public double score(AccountContext context) {
        if (requirement.isSatisfied(context)) {
            return -1000;
        }
        if (!canExecute(context)) {
            return -1000;
        }

        double score = 35;
        int available = context.inventory().getCount(method.itemName) + context.bank().getCount(method.itemName);
        if (available > 0) {
            score += 40 + Math.min(30, available);
        }
        score += Math.min(25, estimatedUnitPrice() / 10.0);

        if (method == Method.COWHIDES && CombatGear.findBestWeapon(context, true) != null) {
            score += 10;
        }
        if (method == Method.IRON_ORE && context.getRealLevel(Skill.MINING) >= MiningStrategy.Method.IRON_ORE.requiredLevel) {
            score += 12;
        }
        if (method.sourceType == SourceType.SMELTING && context.getRealLevel(Skill.SMITHING) >= 15) {
            score += 8;
        }

        return score;
    }

    @Override
    public Task createTask(AccountContext context) {
        int currentInventoryCoins = context.inventory().getCount("Coins");
        int targetInventoryCoins = currentInventoryCoins + requirement.getMissingTotalCoins(context);
        return new MoneyMakingTask(method, targetInventoryCoins, saleRoute);
    }

    @Override
    public Duration commitmentDuration(AccountContext context) {
        return Duration.ofMinutes(Rs2Random.between(10, 25));
    }

    @Override
    public WorldPoint preferredLocation(AccountContext context) {
        return method.location;
    }

    @Override
    public int estimatedProfitPerHour(AccountContext context) {
        return estimatedUnitPrice() * 400;
    }

    private boolean hasSellableItems(AccountContext context) {
        return context.inventory().hasItem(method.itemName)
                || context.bank().hasItem(method.itemName);
    }

    private int estimatedUnitPrice() {
        if (saleRoute == SaleRoute.GENERAL_STORE) {
            return method.minimumGeneralStoreUnitPrice;
        }
        try {
            int itemId = Rs2ItemManager.getItemIdByName(method.itemName, false);
            if (itemId > 0) {
                int offerPrice = Rs2GrandExchange.getOfferPrice(itemId);
                if (offerPrice > 0) {
                    return offerPrice;
                }
            }
        } catch (RuntimeException ignored) {
            // Planning can run before the live item and bank caches are available.
        }
        return method.fallbackUnitPrice;
    }

    private CombatStrategy.Monster combatMonster() {
        return method == Method.CHICKEN_FEATHERS
                ? CombatStrategy.Monster.CHICKENS
                : CombatStrategy.Monster.COWS;
    }
}
