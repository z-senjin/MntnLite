package net.runelite.client.plugins.microbot.mntn.builder.activities.smithing;

import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.mntn.builder.activities.Strategy;
import net.runelite.client.plugins.microbot.mntn.builder.core.AccountContext;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.Task;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.skilling.SmeltingTask;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;

import java.time.Duration;

public class SmeltingStrategy implements Strategy {

    public static final class OreRequirement {
        public final String itemName;
        public final int withdrawAmount;

        public OreRequirement(String itemName, int withdrawAmount) {
            this.itemName = itemName;
            this.withdrawAmount = withdrawAmount;
        }
    }

    public enum Bar {
        BRONZE_BAR(
                1, 6.2,
                "Bronze bar",
                new OreRequirement[]{
                        new OreRequirement("Copper ore", 14),
                        new OreRequirement("Tin ore", 14)
                },
                new WorldPoint(3274, 3186, 0)
        ),
        IRON_BAR(
                15, 12.5,
                "Iron bar",
                new OreRequirement[]{
                        new OreRequirement("Iron ore", 28)
                },
                new WorldPoint(3274, 3186, 0)
        ),
        STEEL_BAR(
                30, 17.5,
                "Steel bar",
                new OreRequirement[]{
                        new OreRequirement("Iron ore", 9),
                        new OreRequirement("Coal", 18)
                },
                new WorldPoint(3274, 3186, 0)
        ),
        GOLD_BAR(
                40, 22.5,
                "Gold bar",
                new OreRequirement[]{
                        new OreRequirement("Gold ore", 28)
                },
                new WorldPoint(3274, 3186, 0)
        );

        public final int requiredLevel;
        public final double xpValue;
        public final String barItemName;
        public final OreRequirement[] ingredients;
        public final WorldPoint furnaceLocation;

        Bar(int requiredLevel, double xpValue, String barItemName,
            OreRequirement[] ingredients, WorldPoint furnaceLocation) {
            this.requiredLevel = requiredLevel;
            this.xpValue = xpValue;
            this.barItemName = barItemName;
            this.ingredients = ingredients;
            this.furnaceLocation = furnaceLocation;
        }
    }

    private final Bar bar;

    public SmeltingStrategy(Bar bar) {
        this.bar = bar;
    }

    @Override
    public String name() {
        return "SMELT_" + bar.name();
    }

    @Override
    public boolean canExecute(AccountContext context) {
        int level = context.getRealLevel(Skill.SMITHING);
        if (level < bar.requiredLevel) {
            return false;
        }

        for (OreRequirement req : bar.ingredients) {
            boolean has = context.inventory().hasItem(req.itemName) || context.bank().hasItem(req.itemName);
            if (!has) {
                return false;
            }
        }
        return true;
    }

    @Override
    public double score(AccountContext context) {
        int level = context.getRealLevel(Skill.SMITHING);
        if (level < bar.requiredLevel) {
            return -1000;
        }

        double score = bar.xpValue;

        double convenienceTotal = 0;
        for (OreRequirement req : bar.ingredients) {
            if (context.inventory().hasItem(req.itemName)) {
                convenienceTotal += 30;
            } else if (context.bank().hasItem(req.itemName)) {
                convenienceTotal += 10;
            }
        }
        score += convenienceTotal / bar.ingredients.length;

        return score;
    }

    @Override
    public Task createTask(AccountContext context) {
        return new SmeltingTask(bar);
    }

    @Override
    public Duration commitmentDuration(AccountContext context) {
        int minutes = Rs2Random.between(20, 180);
        return Duration.ofMinutes(minutes);
    }
}
