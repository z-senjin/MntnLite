package net.runelite.client.plugins.microbot.mntn.builder.activities.smithing;

import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.mntn.builder.activities.Strategy;
import net.runelite.client.plugins.microbot.mntn.builder.core.AccountContext;
import net.runelite.client.plugins.microbot.mntn.builder.core.requirements.ItemRequirement;
import net.runelite.client.plugins.microbot.mntn.builder.core.requirements.Requirement;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.Task;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.skilling.SmeltingTask;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

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
        ),
        SILVER_BAR(
                20, 13.7,
                "Silver bar",
                new OreRequirement[]{
                        new OreRequirement("Silver ore", 28)
                },
                new WorldPoint(3274, 3186, 0)
        ),
        MITHRIL_BAR(
                50, 30.0,
                "Mithril bar",
                new OreRequirement[]{
                        new OreRequirement("Mithril ore", 5),
                        new OreRequirement("Coal", 20)
                },
                new WorldPoint(3274, 3186, 0)
        ),
        ADAMANTITE_BAR(
                70, 37.5,
                "Adamantite bar",
                new OreRequirement[]{
                        new OreRequirement("Adamantite ore", 4),
                        new OreRequirement("Coal", 24)
                },
                new WorldPoint(3274, 3186, 0)
        ),
        RUNITE_BAR(
                85, 50.0,
                "Runite bar",
                new OreRequirement[]{
                        new OreRequirement("Runite ore", 3),
                        new OreRequirement("Coal", 24)
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
    public List<Requirement> requirements(AccountContext context) {
        List<Requirement> requirements = new ArrayList<>();
        for (OreRequirement ingredient : bar.ingredients) {
            ItemRequirement input = new ItemRequirement(ingredient.itemName, ingredient.withdrawAmount);
            int missing = input.getMissingAccountQuantity(context);
            if (missing > 0) {
                requirements.add(new ItemRequirement(ingredient.itemName, missing));
            }
        }
        return requirements;
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
    public WorldPoint preferredLocation(AccountContext context) {
        return bar.furnaceLocation;
    }

    @Override
    public int estimatedXpPerHour(AccountContext context) {
        return (int) (bar.xpValue * 900);
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
