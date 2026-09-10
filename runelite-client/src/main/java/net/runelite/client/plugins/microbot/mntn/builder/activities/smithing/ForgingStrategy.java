package net.runelite.client.plugins.microbot.mntn.builder.activities.smithing;

import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.mntn.builder.activities.Strategy;
import net.runelite.client.plugins.microbot.mntn.builder.core.AccountContext;
import net.runelite.client.plugins.microbot.mntn.builder.core.requirements.ItemRequirement;
import net.runelite.client.plugins.microbot.mntn.builder.core.requirements.Requirement;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.Task;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.skilling.ForgingTask;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class ForgingStrategy implements Strategy {

    public enum BarType {
        BRONZE(1, 12.5, "Bronze bar", 1, 4, 16, 18),
        IRON(15, 25.0, "Iron bar", 15, 19, 31, 33),
        STEEL(30, 37.5, "Steel bar", 30, 34, 46, 48),
        MITHRIL(50, 50.0, "Mithril bar", 50, 54, 66, 68),
        ADAMANT(70, 62.5, "Adamantite bar", 70, 74, 86, 88),
        RUNE(85, 75.0, "Rune bar", 85, 89, 99, 99);

        public final int requiredLevel;
        public final double xpPerBar;
        public final String barItemName;
        public final int daggerLevel;
        public final int swordLevel;
        public final int platelegsLevel;
        public final int platebodyLevel;

        BarType(int requiredLevel, double xpPerBar, String barItemName, int daggerLevel,
                int swordLevel, int platelegsLevel, int platebodyLevel) {
            this.requiredLevel = requiredLevel;
            this.xpPerBar = xpPerBar;
            this.barItemName = barItemName;
            this.daggerLevel = daggerLevel;
            this.swordLevel = swordLevel;
            this.platelegsLevel = platelegsLevel;
            this.platebodyLevel = platebodyLevel;
        }
    }

    public static final String HAMMER = "Hammer";
    public static final WorldPoint VARROCK_ANVIL = new WorldPoint(3187, 3427, 0);

    private final BarType barType;

    public ForgingStrategy(BarType barType) {
        this.barType = barType;
    }

    @Override
    public String name() {
        return "FORGE_" + barType.name();
    }

    @Override
    public boolean canExecute(AccountContext context) {
        int level = context.getRealLevel(Skill.SMITHING);
        if (level < barType.requiredLevel) {
            return false;
        }

        boolean hasHammer = context.inventory().hasItem(HAMMER) || context.bank().hasItem(HAMMER);
        if (!hasHammer) {
            return false;
        }

        boolean hasBars = context.inventory().hasItem(barType.barItemName)
                || context.bank().hasItem(barType.barItemName);
        return hasBars;
    }

    @Override
    public List<Requirement> requirements(AccountContext context) {
        List<Requirement> requirements = new ArrayList<>();
        addMissingInput(requirements, context, HAMMER, 1);
        addMissingInput(requirements, context, barType.barItemName, 1);
        return requirements;
    }

    private static void addMissingInput(List<Requirement> requirements, AccountContext context,
                                        String itemName, int quantity) {
        ItemRequirement input = new ItemRequirement(itemName, quantity);
        int missing = input.getMissingAccountQuantity(context);
        if (missing > 0) {
            requirements.add(new ItemRequirement(itemName, missing));
        }
    }

    @Override
    public double score(AccountContext context) {
        int level = context.getRealLevel(Skill.SMITHING);
        if (level < barType.requiredLevel) {
            return -1000;
        }

        double score = barType.xpPerBar;

        if (context.inventory().hasItem(barType.barItemName)) {
            score += 30;
        } else if (context.bank().hasItem(barType.barItemName)) {
            score += 10;
        }

        if (context.inventory().hasItem(HAMMER)) {
            score += 10;
        }

        return score;
    }

    @Override
    public WorldPoint preferredLocation(AccountContext context) {
        return VARROCK_ANVIL;
    }

    @Override
    public int estimatedXpPerHour(AccountContext context) {
        return (int) (barType.xpPerBar * 650);
    }

    @Override
    public Task createTask(AccountContext context) {
        return new ForgingTask(barType);
    }

    @Override
    public Duration commitmentDuration(AccountContext context) {
        int minutes = Rs2Random.between(20, 180);
        return Duration.ofMinutes(minutes);
    }
}
