/*
 * Role:
 * Describes one location that a woodcutting strategy can use.
 *
 * Purpose:
 * Stores the tree and bank destinations along with a read-only
 * requirement used to decide whether the account can currently
 * use the location.
 *
 * The requirement must only inspect account state. It should not
 * walk, click, open the bank, or perform any other game action.
 */
package net.runelite.client.plugins.microbot.mntn.aio.strategies.skilling.woodcutting;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.mntn.aio.core.AccountContext;

import java.util.Objects;
import java.util.function.Predicate;

public final class WoodcuttingLocation
{
    private final String name;
    private final WorldPoint chopDestination;
    private final WorldPoint bankDestination;
    private final Predicate<AccountContext> requirement;
    private final Boolean powerchop;

    public WoodcuttingLocation(
            String name,
            WorldPoint chopDestination,
            WorldPoint bankDestination,
            Predicate<AccountContext> requirement)
    {
        this.name = Objects.requireNonNull(name, "name");
        this.chopDestination = Objects.requireNonNull(
                chopDestination,
                "chopDestination"
        );
        this.bankDestination = Objects.requireNonNull(
                bankDestination,
                "bankDestination"
        );
        this.requirement = Objects.requireNonNull(
                requirement,
                "requirement"
        );

        this.powerchop = false;
    }

    public WoodcuttingLocation(
            String name,
            WorldPoint chopDestination,
            WorldPoint bankDestination,
            Predicate<AccountContext> requirement, Boolean powerchop)
    {
        this.name = Objects.requireNonNull(name, "name");
        this.chopDestination = Objects.requireNonNull(
                chopDestination,
                "chopDestination"
        );
        this.bankDestination = Objects.requireNonNull(
                bankDestination,
                "bankDestination"
        );
        this.requirement = Objects.requireNonNull(
                requirement,
                "requirement"
        );

        this.powerchop = powerchop;
    }

    public boolean canUse(AccountContext context)
    {
        return requirement.test(context);
    }

    public String getName()
    {
        return name;
    }

    public WorldPoint getchopDestination()
    {
        return chopDestination;
    }

    public WorldPoint getBankDestination()
    {
        return bankDestination;
    }

    public boolean getpowerchop() {return powerchop; }

    @Override
    public String toString()
    {
        return name;
    }
}