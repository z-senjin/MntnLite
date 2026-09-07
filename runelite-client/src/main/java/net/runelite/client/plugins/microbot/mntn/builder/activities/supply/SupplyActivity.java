package net.runelite.client.plugins.microbot.mntn.builder.activities.supply;

import net.runelite.client.plugins.microbot.mntn.builder.activities.Activity;
import net.runelite.client.plugins.microbot.mntn.builder.activities.ActivityType;
import net.runelite.client.plugins.microbot.mntn.builder.activities.Strategy;
import net.runelite.client.plugins.microbot.mntn.builder.core.AccountContext;
import net.runelite.client.plugins.microbot.mntn.builder.core.requirements.ActivityRequest;
import net.runelite.client.plugins.microbot.mntn.builder.core.requirements.EquipmentRequirement;
import net.runelite.client.plugins.microbot.mntn.builder.core.requirements.ItemRequirement;
import net.runelite.client.plugins.microbot.mntn.builder.core.requirements.MoneyRequirement;
import net.runelite.client.plugins.microbot.mntn.builder.core.requirements.Requirement;

import java.util.ArrayList;
import java.util.List;

public class SupplyActivity implements Activity {

    private final SupplyCatalog catalog = new SupplyCatalog();
    private final SupplyRoutePolicy routePolicy;

    public SupplyActivity() {
        this(SupplyRoutePolicy.allowAll());
    }

    public SupplyActivity(SupplyRoutePolicy routePolicy) {
        this.routePolicy = routePolicy != null ? routePolicy : SupplyRoutePolicy.allowAll();
    }

    @Override
    public ActivityType type() {
        return ActivityType.SUPPLY;
    }

    @Override
    public boolean canProvide(ActivityRequest request, AccountContext context) {
        return request.type() == ActivityType.SUPPLY
                && request.payload() instanceof Requirement
                && isSupported((Requirement) request.payload());
    }

    @Override
    public List<Strategy> getStrategies(AccountContext context, ActivityRequest request) {
        List<Strategy> strategies = new ArrayList<>();
        if (!(request.payload() instanceof Requirement)) {
            return strategies;
        }
        Requirement requirement = (Requirement) request.payload();
        if (!isSupported(requirement)) {
            return strategies;
        }
        for (SupplyRoute route : catalog.routesFor(requirement, context)) {
            if (!routePolicy.allows(route.getType())) {
                continue;
            }
            strategies.add(new SupplyStrategy(requirement, route));
        }
        return strategies;
    }

    private boolean isSupported(Requirement requirement) {
        return requirement instanceof ItemRequirement
                || requirement instanceof EquipmentRequirement
                || requirement instanceof MoneyRequirement;
    }
}
