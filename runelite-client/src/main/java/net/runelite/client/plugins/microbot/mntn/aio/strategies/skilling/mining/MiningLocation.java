/*
 * Role:
 * Describes one location that a mining strategy can use.
 *
 * Purpose:
 * Stores the mine and bank destinations along with a read-only
 * requirement used to decide whether the account can currently
 * use the location.
 *
 * The requirement must only inspect account state. It should not
 * walk, click, open the bank, or perform any other game action.
 */
package net.runelite.client.plugins.microbot.mntn.aio.strategies.skilling.mining;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.mntn.aio.core.AccountContext;

import java.util.Objects;
import java.util.function.Predicate;

public final class MiningLocation
{
    private final String name;
    private final WorldPoint mineDestination;
    private final WorldPoint bankDestination;
    private final Predicate<AccountContext> requirement;
    private final Boolean powermine;

    public MiningLocation(
            String name,
            WorldPoint mineDestination,
            WorldPoint bankDestination,
            Predicate<AccountContext> requirement)
    {
        this.name = Objects.requireNonNull(name, "name");
        this.mineDestination = Objects.requireNonNull(
                mineDestination,
                "mineDestination"
        );
        this.bankDestination = Objects.requireNonNull(
                bankDestination,
                "bankDestination"
        );
        this.requirement = Objects.requireNonNull(
                requirement,
                "requirement"
        );

        this.powermine = false;
    }

    public MiningLocation(
        String name,
        WorldPoint mineDestination,
        WorldPoint bankDestination,
        Predicate<AccountContext> requirement, Boolean powermine)
{
    this.name = Objects.requireNonNull(name, "name");
    this.mineDestination = Objects.requireNonNull(
            mineDestination,
            "mineDestination"
    );
    this.bankDestination = Objects.requireNonNull(
            bankDestination,
            "bankDestination"
    );
    this.requirement = Objects.requireNonNull(
            requirement,
            "requirement"
    );

    this.powermine = powermine;
}

    public boolean canUse(AccountContext context)
    {
        return requirement.test(context);
    }

    public String getName()
    {
        return name;
    }

    public WorldPoint getMineDestination()
    {
        return mineDestination;
    }

    public WorldPoint getBankDestination()
    {
        return bankDestination;
    }

    public boolean getPowermine() {return powermine; }

    @Override
    public String toString()
    {
        return name;
    }
}