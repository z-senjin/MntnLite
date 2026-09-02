package net.runelite.client.plugins.microbot.mntn.builder.activities.woodcutting;

import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.mntn.builder.activities.Strategy;
import net.runelite.client.plugins.microbot.mntn.builder.core.AccountContext;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.Task;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.skilling.WoodcuttingTask;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;

import java.time.Duration;

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

    /**
     * One concrete tree/method. xpValue uses the real per-log OSRS values (25 / 37.5 / 67.5)
     * since those are static game constants, not something that needs verifying like the
     * placeholder object ids and locations below.
     *
     * treeObjectIds is an array, not a single id - the same visual tree type has several
     * distinct game object ids in OSRS (different rotations/graphical variants scattered
     * around the map), so a single id would miss most of them. WoodcuttingTask tries each id
     * in turn and uses whichever's actually nearby.
     */
    public enum Method {
        NORMAL_TREE(
                1, 25,
                // TODO verify - placeholders. Normal trees have many object id variants;
                // add more as you find them via the OSRS Wiki or RuneLite's object ID tool.
                new int[]{1276, 1278},
                "Chop down",
                "Logs",
                new WorldPoint(3181, 3259, 0) // TODO verify - placeholder
        ),
        OAK_TREE(
                15, 37.5,
                new int[]{10820}, // TODO verify - placeholders
                "Chop down",
                "Oak logs",
                new WorldPoint(3157, 3259, 0) // TODO verify - placeholder
        ),
        WILLOW_TREE(
                30, 67.5,
                new int[]{10831, 10833, 10819}, // TODO verify - placeholders
                "Chop down",
                "Willow logs",
                new WorldPoint(3163, 3266, 0) // TODO verify - placeholder
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

    private final Method method;

    public WoodcuttingStrategy(Method method) {
        this.method = method;
    }

    @Override
    public String name() {
        return method.name();
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

        return score;
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
        return new WoodcuttingTask(method);
    }

    @Override
    public Duration commitmentDuration(AccountContext context) {
        int minutes = Rs2Random.between(20, 180);
        return Duration.ofMinutes(minutes);
    }
}
