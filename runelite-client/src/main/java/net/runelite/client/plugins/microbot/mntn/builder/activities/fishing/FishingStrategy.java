package net.runelite.client.plugins.microbot.mntn.builder.activities.fishing;

import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.mntn.builder.activities.Strategy;
import net.runelite.client.plugins.microbot.mntn.builder.core.AccountContext;
import net.runelite.client.plugins.microbot.mntn.builder.core.requirements.ItemRequirement;
import net.runelite.client.plugins.microbot.mntn.builder.core.requirements.Requirement;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.Task;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.skilling.FishingTask;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class FishingStrategy implements Strategy {

    /**
     * Pass as a ToolRequirement's quantity to mean "withdraw everything available" rather
     * than a fixed count - matches BankingTask's own -1 = "withdraw all" convention, so it
     * threads straight through with no translation needed.
     */
    public static final int WITHDRAW_ALL = -1;

    /**
     * One (item, quantity) pair a Method needs. quantity is what gets withdrawn when this
     * item is missing from the inventory during banking - e.g. a Fishing rod wants exactly
     * 1, Feathers usually want WITHDRAW_ALL (they're stackable, so "all" doesn't cost extra
     * inventory space the way stacking multiple non-stackable tools would).
     */
    public static final class ToolRequirement {
        public final String itemName;
        public final int quantity;

        public ToolRequirement(String itemName, int quantity) {
            this.itemName = itemName;
            this.quantity = quantity;
        }
    }

    /**
     * One concrete fishing method.
     *
     * toolRequirements supports spots that need MORE than one item to fish (e.g. fly fishing
     * needs a Fishing rod AND Feathers), each with its own withdraw quantity. canExecute()
     * requires every item to be available (inventory or bank) before the method is
     * selectable.
     *
     * score() prefers whichever method you qualify for that gives the best combination of
     * xp and material convenience - same pattern as CookingStrategy, averaged across however
     * many tools this method needs so a 2-tool method doesn't get an unfair bonus over a
     * 1-tool method just for having more items to check.
     */
    public enum Method {
        NET_SHRIMP(
                1, 10,
                1530,
                "Net",
                "Raw shrimps",
                new ToolRequirement[]{
                        new ToolRequirement("Small fishing net", 1)
                },
                new WorldPoint(3242, 3149, 0) // TODO verify - placeholder
        ),
        FLY_FISH_SALMON(
                30, 70, 1527, "Lure", "Raw salmon", new ToolRequirement[]{
                new ToolRequirement("Fly fishing rod", 1), new ToolRequirement("Feather", WITHDRAW_ALL)
        }, new WorldPoint(3241, 3243, 0)
        ),
        CAGE_LOBSTER(
                40, 90,
                2, // TODO verify - placeholder
                "Cage",
                "Raw lobster",
                new ToolRequirement[]{
                        new ToolRequirement("Lobster pot", 1)
                },
                new WorldPoint(2924, 3178, 0)
        );

        public final int requiredLevel;
        public final double xpValue;
        public final int npcId;
        public final String action;
        public final String fishItemName;
        public final ToolRequirement[] toolRequirements;
        public final WorldPoint location;

        Method(int requiredLevel, double xpValue, int npcId, String action,
               String fishItemName, ToolRequirement[] toolRequirements, WorldPoint location) {
            this.requiredLevel = requiredLevel;
            this.xpValue = xpValue;
            this.npcId = npcId;
            this.action = action;
            this.fishItemName = fishItemName;
            this.toolRequirements = toolRequirements;
            this.location = location;
        }
    }

    /**
     * Verified F2P fishing areas. Methods retain their default location above for callers
     * that only need a representative route, while the planner evaluates every concrete
     * method/location pair below.
     */
    public enum Location {
        LUMBRIDGE_SWAMP_SHRIMP(Method.NET_SHRIMP, new WorldPoint(3242, 3149, 0)),
        DRAYNOR_SHRIMP(Method.NET_SHRIMP, new WorldPoint(3084, 3231, 0)),
        LUMBRIDGE_RIVER_FLY(Method.FLY_FISH_SALMON, new WorldPoint(3241, 3243, 0)),
        BARBARIAN_VILLAGE_FLY(Method.FLY_FISH_SALMON, new WorldPoint(3107, 3432, 0)),
        KARAMJA_LOBSTER(Method.CAGE_LOBSTER, new WorldPoint(2924, 3178, 0));

        public final Method method;
        public final WorldPoint point;

        Location(Method method, WorldPoint point) {
            this.method = method;
            this.point = point;
        }

        public static Location defaultFor(Method method) {
            for (Location location : values()) {
                if (location.method == method) {
                    return location;
                }
            }
            throw new IllegalArgumentException("No fishing location for " + method);
        }
    }

    private final Method method;
    private final Location location;
    private final int locationVariation;

    public FishingStrategy(Method method) {
        this(method, Location.defaultFor(method));
    }

    public FishingStrategy(Method method, Location location) {
        if (location.method != method) {
            throw new IllegalArgumentException("Fishing location does not support " + method);
        }
        this.method = method;
        this.location = location;
        this.locationVariation = Rs2Random.betweenInclusive(0, 2);
    }

    @Override
    public String name() {
        return method.name() + "_" + location.name();
    }

    @Override
    public boolean canExecute(AccountContext context) {

        int level = context.getRealLevel(Skill.FISHING);

        if (level < method.requiredLevel) {
            return false;
        }

        /*
         * Every required tool needs to be available - either in the
         * inventory or in the bank. (Existence only, not the requested quantity - a bank
         * with only 3 feathers when we'd like WITHDRAW_ALL still counts as "available".)
         */
        for (ToolRequirement requirement : method.toolRequirements) {
            boolean hasTool = context.inventory().hasItem(requirement.itemName)
                    || context.bank().hasItem(requirement.itemName);
            if (!hasTool) {
                return false;
            }
        }

        return true;
    }

    @Override
    public List<Requirement> requirements(AccountContext context) {
        List<Requirement> requirements = new ArrayList<>();
        for (ToolRequirement requirement : method.toolRequirements) {
            if (!context.inventory().hasItem(requirement.itemName)
                    && !context.bank().hasItem(requirement.itemName)) {
                requirements.add(new ItemRequirement(requirement.itemName, 1));
            }
        }
        return requirements;
    }

    @Override
    public double score(AccountContext context) {

        int level = context.getRealLevel(Skill.FISHING);

        if (level < method.requiredLevel) {
            return -1000;
        }

        double score = method.xpValue;

        /*
         * Average the materials-convenience bonus across every required tool, rather than
         * summing it - otherwise a method needing 2 tools would always out-score an
         * equally-convenient 1-tool method purely by having more items to add points for.
         *
         * Per tool: inventory beats bank beats neither (same weighting as CookingStrategy).
         */
        double convenienceTotal = 0;
        for (ToolRequirement requirement : method.toolRequirements) {
            if (context.inventory().hasItem(requirement.itemName)) {
                convenienceTotal += 30;
            } else if (context.bank().hasItem(requirement.itemName)) {
                convenienceTotal += 10;
            }
            // else: 0 - shouldn't happen here since canExecute() already required it
            // to be somewhere, but scores 0 defensively if that ever changes.
        }

        score += convenienceTotal / method.toolRequirements.length;

        return score + locationVariation;
    }

    @Override
    public WorldPoint preferredLocation(AccountContext context) {
        return location.point;
    }

    @Override
    public int estimatedXpPerHour(AccountContext context) {
        return (int) (method.xpValue * 450);
    }

    @Override
    public Task createTask(AccountContext context) {
        return new FishingTask(method, location);
    }

    @Override
    public Duration commitmentDuration(AccountContext context) {
        int minutes = Rs2Random.between(20, 180);
        return Duration.ofMinutes(minutes);
    }
}
