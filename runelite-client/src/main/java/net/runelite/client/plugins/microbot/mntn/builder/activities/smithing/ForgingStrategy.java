package net.runelite.client.plugins.microbot.mntn.builder.activities.smithing;

import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.mntn.builder.activities.Strategy;
import net.runelite.client.plugins.microbot.mntn.builder.core.AccountContext;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.Task;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.skilling.ForgingTask;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;

import java.time.Duration;

public class ForgingStrategy implements Strategy {

    public enum BarType {
        BRONZE(1, 12.5, "Bronze bar"),
        IRON(15, 25.0, "Iron bar"),
        STEEL(30, 37.5, "Steel bar");

        public final int requiredLevel;
        public final double xpPerBar;
        public final String barItemName;

        BarType(int requiredLevel, double xpPerBar, String barItemName) {
            this.requiredLevel = requiredLevel;
            this.xpPerBar = xpPerBar;
            this.barItemName = barItemName;
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
    public Task createTask(AccountContext context) {
        return new ForgingTask(barType);
    }

    @Override
    public Duration commitmentDuration(AccountContext context) {
        int minutes = Rs2Random.between(20, 180);
        return Duration.ofMinutes(minutes);
    }
}
