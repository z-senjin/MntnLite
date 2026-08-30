package net.runelite.client.plugins.microbot.mntn.builder.core;

import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

/**
 * Thin read-through wrapper around live Microbot/RuneLite state.
 *
 * The doc's version of this class snapshots state once per planning pass. For the vertical
 * slice it's simpler (and fine) to just read live values on every call - Tasks and Strategies
 * only ever call this during their own tick, never store it. Once you add real caching/
 * snapshotting later, this is the ONLY class that needs to change - nothing above it
 * (Goal/Requirement/Activity/Strategy) touches Microbot directly, which is the whole point
 * of having this layer.
 *
 * Inventory and bank are exposed as their own sub-views (context.inventory()/context.bank())
 * rather than flat methods here, since they're the two areas with many possible queries.
 * Everything else (skills, location) stays flat since there's only ever "one" of them.
 */
public class AccountContext {

    private final InventoryView inventory = new InventoryView();
    private final BankView bank = new BankView();

    public InventoryView inventory() {
        return inventory;
    }

    public BankView bank() {
        return bank;
    }

    public boolean isLoggedIn() {
        return Microbot.isLoggedIn();
    }

    public int getRealLevel(Skill skill) {
        if (!isLoggedIn()) return 0;
        return Microbot.getClient().getRealSkillLevel(skill);
    }

    public int getBoostedLevel(Skill skill) {
        if (!isLoggedIn()) return 0;
        return Microbot.getClient().getBoostedSkillLevel(skill);
    }

    public WorldPoint getLocation() {
        return Rs2Player.getWorldLocation();
    }

    public boolean isNear(WorldPoint location, int distance) {
        boolean amINear = Rs2Walker.isNear(location);
        int distanceTo = Rs2Walker.getDistanceBetween(Rs2Player.getWorldLocation(), location);
        return amINear && distanceTo <= distance;
    }
}
