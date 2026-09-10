package net.runelite.client.plugins.microbot.mntn.builder.activities.woodcutting;

import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.mntn.builder.activities.Strategy;
import net.runelite.client.plugins.microbot.mntn.builder.core.AccountContext;
import net.runelite.client.plugins.microbot.mntn.builder.core.requirements.ItemRequirement;
import net.runelite.client.plugins.microbot.mntn.builder.core.requirements.Requirement;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.Task;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.skilling.WoodcuttingTask;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

public class WoodcuttingStrategy implements Strategy {

    public enum Axe {
        DRAGON("Dragon axe", 61, 60),
        RUNE("Rune axe", 41, 40),
        ADAMANT("Adamant axe", 31, 30),
        MITHRIL("Mithril axe", 21, 20),
        BLACK("Black axe", 11, 10),
        STEEL("Steel axe", 6, 5),
        IRON("Iron axe", 1, 1),
        BRONZE("Bronze axe", 1, 1);

        public final String itemName;
        public final int woodcuttingLevel;
        public final int attackLevel;

        Axe(String itemName, int woodcuttingLevel, int attackLevel) {
            this.itemName = itemName;
            this.woodcuttingLevel = woodcuttingLevel;
            this.attackLevel = attackLevel;
        }

        public static Axe byName(String name) {
            for (Axe axe : values()) {
                if (axe.itemName.equalsIgnoreCase(name)) {
                    return axe;
                }
            }
            return null;
        }
    }

    /** One concrete F2P tree type and its known live object variants. */
    public enum Method {
        NORMAL_TREE(
                1, 25,
                new int[]{1276, 1278},
                "Chop down",
                "Logs",
                new WorldPoint(3225, 3216, 0)
        ),
        OAK_TREE(
                15, 37.5,
                new int[]{4533, 4540, 10820},
                "Chop down",
                "Oak logs",
                new WorldPoint(3181, 3421, 0)
        ),
        WILLOW_TREE(
                30, 67.5,
                new int[]{4534, 4541, 10819, 10829, 10831, 10833},
                "Chop down",
                "Willow logs",
                new WorldPoint(3086, 3228, 0)
        ),
        YEW_TREE(
                60, 175,
                new int[]{4536, 5121, 10822, 10823},
                "Chop down",
                "Yew logs",
                new WorldPoint(3166, 3490, 0)
        );

        public final int requiredLevel;
        public final double xpValue;
        public final int[] treeObjectIds;
        public final String action;
        public final String logItemName;
        public final WorldPoint location;

        Method(int requiredLevel, double xpValue, int[] treeObjectIds, String action,
               String logItemName, WorldPoint location) {
            this.requiredLevel = requiredLevel;
            this.xpValue = xpValue;
            this.treeObjectIds = treeObjectIds;
            this.action = action;
            this.logItemName = logItemName;
            this.location = location;
        }
    }

    /** F2P tree areas evaluated independently by the planner. */
    public enum Location {
        LUMBRIDGE_CASTLE_NORMAL(Method.NORMAL_TREE, new WorldPoint(3225, 3216, 0)),
        VARROCK_GRAND_EXCHANGE_NORMAL(Method.NORMAL_TREE, new WorldPoint(3160, 3383, 0)),
        DRAYNOR_MANOR_NORMAL(Method.NORMAL_TREE, new WorldPoint(3100, 3355, 0)),
        VARROCK_WEST_BANK_OAK(Method.OAK_TREE, new WorldPoint(3181, 3421, 0)),
        DRAYNOR_OAK(Method.OAK_TREE, new WorldPoint(3093, 3245, 0)),
        LUMBRIDGE_GENERAL_STORE_OAK(Method.OAK_TREE, new WorldPoint(3212, 3244, 0)),
        DRAYNOR_WILLOW(Method.WILLOW_TREE, new WorldPoint(3086, 3228, 0)),
        EDGEVILLE_WILLOW(Method.WILLOW_TREE, new WorldPoint(3094, 3491, 0)),
        PORT_SARIM_WILLOW(Method.WILLOW_TREE, new WorldPoint(3048, 3235, 0)),
        VARROCK_GRAND_EXCHANGE_YEW(Method.YEW_TREE, new WorldPoint(3166, 3490, 0));

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
            throw new IllegalArgumentException("No woodcutting location for " + method);
        }
    }

    private final Method method;
    private final Location location;
    private final int locationVariation;

    public WoodcuttingStrategy(Method method) {
        this(method, Location.defaultFor(method));
    }

    public WoodcuttingStrategy(Method method, Location location) {
        if (location.method != method) {
            throw new IllegalArgumentException("Woodcutting location does not support " + method);
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
        int level = context.getRealLevel(Skill.WOODCUTTING);
        if (level < method.requiredLevel) {
            return false;
        }
        // Just needs SOME usable axe to exist somewhere - equipment, inventory or bank.
        return findBestAxe(context, false) != null;
    }

    @Override
    public List<Requirement> requirements(AccountContext context) {
        String axe = findBestAxe(context, false);
        if (axe == null) {
            return Collections.singletonList(new ItemRequirement(Axe.BRONZE.itemName, 1));
        }
        return Collections.emptyList();
    }

    @Override
    public double score(AccountContext context) {
        int level = context.getRealLevel(Skill.WOODCUTTING);
        if (level < method.requiredLevel) {
            return -1000;
        }

        // Base: best-xp-you-qualify-for wins by default, same as Fishing/Cooking.
        double score = method.xpValue;

        String bestHeldOrWorn = findBestAxe(context, true);
        if (bestHeldOrWorn != null) {
            if (context.equipment().hasItem(bestHeldOrWorn)) {
                score += 40 + axeTier(bestHeldOrWorn) * 4;
            } else {
                score += 30 + axeTier(bestHeldOrWorn) * 4;
            }
        } else {
            String bestBankAxe = findBestAxe(context, false);
            if (bestBankAxe != null) {
                // Usable, but needs a bank trip first - smaller nudge, same idea as
                // Fishing/Cooking's inventory-vs-bank convenience split.
                score += 10 + axeTier(bestBankAxe) * 1;
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
        return (int) (method.xpValue * 500);
    }

    /**
     * Best (highest-tier) axe currently available that the account can actually use.
     * inventoryOrEquippedOnly=true searches only equipment and inventory; false also falls back to the bank.
     */
    public static String findBestAxe(AccountContext context, boolean inventoryOrEquippedOnly) {
        int wcLevel = context.getRealLevel(Skill.WOODCUTTING);
        for (Axe axe : Axe.values()) {
            if (wcLevel < axe.woodcuttingLevel) {
                continue;
            }
            boolean has = context.equipment().hasItem(axe.itemName)
                    || context.inventory().hasItem(axe.itemName)
                    || (!inventoryOrEquippedOnly && context.bank().hasItem(axe.itemName));
            if (has) {
                return axe.itemName;
            }
        }
        return null;
    }

    public static boolean canWield(AccountContext context, String axeName) {
        Axe axe = Axe.byName(axeName);
        if (axe == null) return false;
        return context.getRealLevel(Skill.ATTACK) >= axe.attackLevel;
    }

    private static int axeTier(String axeName) {
        Axe[] values = Axe.values();
        for (int i = 0; i < values.length; i++) {
            if (values[i].itemName.equals(axeName)) {
                return values.length - i; // index 0 (Dragon) = highest tier number
            }
        }
        return 0;
    }

    @Override
    public Task createTask(AccountContext context) {
        return new WoodcuttingTask(method, location);
    }

    @Override
    public Duration commitmentDuration(AccountContext context) {
        int minutes = Rs2Random.between(20, 180);
        return Duration.ofMinutes(minutes);
    }
}
