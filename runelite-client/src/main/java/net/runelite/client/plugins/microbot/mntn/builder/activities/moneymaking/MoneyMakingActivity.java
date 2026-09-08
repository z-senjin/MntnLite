package net.runelite.client.plugins.microbot.mntn.builder.activities.moneymaking;

import net.runelite.client.plugins.microbot.mntn.builder.activities.Activity;
import net.runelite.client.plugins.microbot.mntn.builder.activities.ActivityType;
import net.runelite.client.plugins.microbot.mntn.builder.activities.Strategy;
import net.runelite.client.plugins.microbot.mntn.builder.core.AccountContext;
import net.runelite.client.plugins.microbot.mntn.builder.core.requirements.ActivityRequest;
import net.runelite.client.plugins.microbot.mntn.builder.core.requirements.MoneyRequirement;
import net.runelite.client.plugins.microbot.mntn.builder.activities.supply.SupplyRoutePolicy;
import net.runelite.client.plugins.microbot.mntn.builder.activities.supply.SupplyRouteType;

import java.util.ArrayList;
import java.util.List;

public class MoneyMakingActivity implements Activity {

    private final SupplyRoutePolicy routePolicy;

    public MoneyMakingActivity() {
        this(SupplyRoutePolicy.allowAll());
    }

    public MoneyMakingActivity(SupplyRoutePolicy routePolicy) {
        this.routePolicy = routePolicy == null ? SupplyRoutePolicy.allowAll() : routePolicy;
    }

    @Override
    public ActivityType type() {
        return ActivityType.MONEY_MAKING;
    }

    @Override
    public boolean canProvide(ActivityRequest request, AccountContext context) {
        return request.type() == ActivityType.MONEY_MAKING
                && request.payload() instanceof MoneyRequirement;
    }

    @Override
    public List<Strategy> getStrategies(AccountContext context, ActivityRequest request) {
        List<Strategy> strategies = new ArrayList<>();
        if (!(request.payload() instanceof MoneyRequirement)) {
            return strategies;
        }

        MoneyRequirement requirement = (MoneyRequirement) request.payload();
        for (MoneyMakingStrategy.Method method : MoneyMakingStrategy.Method.values()) {
            if (routePolicy.allows(SupplyRouteType.SHOP) && method.canSellAtGeneralStore()) {
                strategies.add(new MoneyMakingStrategy(
                        method,
                        requirement,
                        MoneyMakingStrategy.SaleRoute.GENERAL_STORE
                ));
            }
            if (routePolicy.allows(SupplyRouteType.GRAND_EXCHANGE)) {
                strategies.add(new MoneyMakingStrategy(
                        method,
                        requirement,
                        MoneyMakingStrategy.SaleRoute.GRAND_EXCHANGE
                ));
            }
        }
        return strategies;
    }
}
