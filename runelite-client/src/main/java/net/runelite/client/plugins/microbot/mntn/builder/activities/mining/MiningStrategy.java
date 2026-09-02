package net.runelite.client.plugins.microbot.mntn.builder.activities.mining;

import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.mntn.builder.activities.Strategy;
import net.runelite.client.plugins.microbot.mntn.builder.core.AccountContext;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.Task;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.skilling.MiningTask;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;

import java.time.Duration;

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

    private final Method method;

    public MiningStrategy(Method method) {
        this.method = method;
    }

    @Override
    public String name() {
        return method.name();
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

        return score;
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
        return new MiningTask(method);
    }

    @Override
    public Duration commitmentDuration(AccountContext context) {
        int minutes = Rs2Random.between(20, 180);
        return Duration.ofMinutes(minutes);
    }
}
