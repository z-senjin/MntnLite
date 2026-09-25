/*
 * Role:
 * Describes one location that a fishing strategy can use.
 *
 * Purpose:
 * Stores the fishing spot and bank destinations along with a read-only
 * requirement used to decide whether the account can currently
 * use the location.
 *
 * The requirement must only inspect account state. It should not
 * walk, click, open the bank, or perform any other game action.
 */
package net.runelite.client.plugins.microbot.mntn.aio.strategies.skilling.fishing;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.mntn.aio.core.AccountContext;

import java.util.Objects;
import java.util.function.Predicate;

public final class FishingLocation
{
    private final String name;
    private final WorldPoint fishDestination;
    private final WorldPoint bankDestination;
    private final Predicate<AccountContext> requirement;

    public FishingLocation(
            String name,
            WorldPoint fishDestination,
            WorldPoint bankDestination,
            Predicate<AccountContext> requirement)
    {
        this.name = Objects.requireNonNull(name, "name");
        this.fishDestination = Objects.requireNonNull(
                fishDestination,
                "fishDestination"
        );
        this.bankDestination = Objects.requireNonNull(
                bankDestination,
                "bankDestination"
        );
        this.requirement = Objects.requireNonNull(
                requirement,
                "requirement"
        );
    }

    public boolean canUse(AccountContext context)
    {
        return requirement.test(context);
    }

    public String getName()
    {
        return name;
    }

    public WorldPoint getFishDestination()
    {
        return fishDestination;
    }

    public WorldPoint getBankDestination()
    {
        return bankDestination;
    }

    @Override
    public String toString()
    {
        return name;
    }
}