package net.runelite.client.plugins.microbot.mntn.builder.activities.mining;

import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.mntn.builder.activities.Strategy;
import net.runelite.client.plugins.microbot.mntn.builder.core.AccountContext;
import net.runelite.client.plugins.microbot.mntn.builder.core.requirements.ItemRequirement;
import net.runelite.client.plugins.microbot.mntn.builder.core.requirements.Requirement;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.Task;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.skilling.MiningTask;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

public class MiningStrategy implements Strategy {

    public enum Pickaxe {
        DRAGON("Dragon pickaxe", 61, 60),
        RUNE("Rune pickaxe", 41, 40),
        ADAMANT("Adamant pickaxe", 31, 30),
        MITHRIL("Mithril pickaxe", 21, 20),
        BLACK("Black pickaxe", 11, 10),
        STEEL("Steel pickaxe", 6, 5),
        IRON("Iron pickaxe", 1, 1),
        BRONZE("Bronze pickaxe", 1, 1);

        public final String itemName;
        public final int miningLevel;
        public final int attackLevel;

        Pickaxe(String itemName, int miningLevel, int attackLevel) {
            this.itemName = itemName;
            this.miningLevel = miningLevel;
            this.attackLevel = attackLevel;
        }
    }

    public enum Method {
        TIN_ORE(
                1, 17.5,
                new int[]{11360, 11361},
                "Mine",
                "Tin ore",
                new WorldPoint(3285, 3365, 0)
        ),
        COPPER_ORE(
                1, 17.5,
                new int[]{10943, 11161},
                "Mine",
                "Copper ore",
                new WorldPoint(3288, 3363, 0)
        ),
        IRON_ORE(
                15, 35.0,
                new int[]{11364, 11365},
                "Mine",
                "Iron ore",
                new WorldPoint(3286, 3369, 0)
        ),
        COAL_ORE(
                30, 50.0,
                new int[]{11366, 11367},
                "Mine",
                "Coal",
                new WorldPoint(3082, 3423, 0)
        );

        public final int requiredLevel;
        public final double xpValue;
        public final int[] rockObjectIds;
        public final String action;
        public final String oreItemName;
        public final WorldPoint location;

        Method(int requiredLevel, double xpValue, int[] rockObjectIds, String action,
               String oreItemName, WorldPoint location) {
            this.requiredLevel = requiredLevel;
            this.xpValue = xpValue;
            this.rockObjectIds = rockObjectIds;
            this.action = action;
            this.oreItemName = oreItemName;
            this.location = location;
        }
    }

    /** F2P mine areas evaluated independently by the planner. */
    public enum Location {
        VARROCK_EAST_TIN(Method.TIN_ORE, new WorldPoint(3285, 3365, 0)),
        LUMBRIDGE_SWAMP_TIN(Method.TIN_ORE, new WorldPoint(3227, 3148, 0)),
        RIMMINGTON_TIN(Method.TIN_ORE, new WorldPoint(2970, 3247, 0)),
        VARROCK_EAST_COPPER(Method.COPPER_ORE, new WorldPoint(3288, 3363, 0)),
        LUMBRIDGE_SWAMP_COPPER(Method.COPPER_ORE, new WorldPoint(3227, 3148, 0)),
        RIMMINGTON_COPPER(Method.COPPER_ORE, new WorldPoint(2970, 3247, 0)),
        VARROCK_EAST_IRON(Method.IRON_ORE, new WorldPoint(3286, 3369, 0)),
        AL_KHARID_IRON(Method.IRON_ORE, new WorldPoint(3297, 3317, 0)),
        RIMMINGTON_IRON(Method.IRON_ORE, new WorldPoint(2970, 3247, 0)),
        FALADOR_NORTH_COAL(Method.COAL_ORE, new WorldPoint(3082, 3423, 0));

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
            throw new IllegalArgumentException("No mining location for " + method);
        }
    }

    private final Method method;
    private final Location location;
    private final int locationVariation;

    public MiningStrategy(Method method) {
        this(method, Location.defaultFor(method));
    }

    public MiningStrategy(Method method, Location location) {
        if (location.method != method) {
            throw new IllegalArgumentException("Mining location does not support " + method);
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
        int level = context.getRealLevel(Skill.MINING);
        if (level < method.requiredLevel) {
            return false;
        }
        return findBestPickaxe(context, false) != null;
    }

    @Override
    public List<Requirement> requirements(AccountContext context) {
        Pickaxe pickaxe = findBestPickaxe(context, false);
        if (pickaxe == null) {
            return Collections.singletonList(new ItemRequirement(Pickaxe.BRONZE.itemName, 1));
        }
        return Collections.singletonList(new ItemRequirement(pickaxe.itemName, 1));
    }

    @Override
    public double score(AccountContext context) {
        int level = context.getRealLevel(Skill.MINING);
        if (level < method.requiredLevel) {
            return -1000;
        }

        double score = method.xpValue;

        Pickaxe bestHeldOrWorn = findBestPickaxe(context, true);
        if (bestHeldOrWorn != null) {
            if (context.equipment().hasItem(bestHeldOrWorn.itemName)) {
                score += 40 + pickaxeTier(bestHeldOrWorn) * 4;
            } else {
                score += 30 + pickaxeTier(bestHeldOrWorn) * 4;
            }
        } else {
            Pickaxe bestBank = findBestPickaxe(context, false);
            if (bestBank != null) {
                score += 10 + pickaxeTier(bestBank) * 1;
            }
        }

        return score + locationVariation;
    }

    @Override
    public WorldPoint preferredLocation(AccountContext context) {
        return location.point;
    }

    @Override
    public int estimatedXpPerHour(AccountContext context) {
        return (int) (method.xpValue * 650);
    }

    /**
     * Finds the highest tier pickaxe available that the player has the required Mining level to use.
     * Checks equipment, inventory, and optionally bank.
     */
    public static Pickaxe findBestPickaxe(AccountContext context, boolean inventoryOrEquippedOnly) {
        int miningLevel = context.getRealLevel(Skill.MINING);

        for (Pickaxe pickaxe : Pickaxe.values()) {
            if (miningLevel < pickaxe.miningLevel) {
                continue;
            }

            boolean has = context.equipment().hasItem(pickaxe.itemName)
                    || context.inventory().hasItem(pickaxe.itemName)
                    || (!inventoryOrEquippedOnly && context.bank().hasItem(pickaxe.itemName));

            if (has) {
                return pickaxe;
            }
        }
        return null;
    }

    public static boolean canWield(AccountContext context, Pickaxe pickaxe) {
        if (pickaxe == null) return false;
        return context.getRealLevel(Skill.ATTACK) >= pickaxe.attackLevel;
    }

    private static int pickaxeTier(Pickaxe pickaxe) {
        Pickaxe[] values = Pickaxe.values();
        for (int i = 0; i < values.length; i++) {
            if (values[i] == pickaxe) {
                return values.length - i;
            }
        }
        return 0;
    }

    @Override
    public Task createTask(AccountContext context) {
        return new MiningTask(method, location);
    }

    @Override
    public Duration commitmentDuration(AccountContext context) {
        int minutes = Rs2Random.between(20, 180);
        return Duration.ofMinutes(minutes);
    }
}
