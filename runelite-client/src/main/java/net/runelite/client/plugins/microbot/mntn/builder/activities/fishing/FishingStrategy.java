package net.runelite.client.plugins.microbot.mntn.builder.activities.fishing;

import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.mntn.builder.activities.Strategy;
import net.runelite.client.plugins.microbot.mntn.builder.core.AccountContext;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.Task;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.skilling.FishingTask;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;

import java.time.Duration;

public class FishingStrategy implements Strategy {

    /**
     * One concrete fishing method.
     *
     * canExecute() requires toolItemName to be available (inventory or bank) before the
     * method is selectable. score() prefers whichever method you qualify for that gives the
     * best combination of xp and material convenience - same pattern as CookingStrategy:
     * inventory beats bank, bank beats nothing, and base xp still dominates when the gap is
     * real (e.g. Cage Lobster's 90 xp vs Net Shrimp's 10 easily clears any materials bonus).
     */
    public enum Method {
        NET_SHRIMP(
                1, 10,
                1530,
                "Net",
                "Small fishing net",
                new WorldPoint(3242, 3149, 0) // TODO verify - placeholder
        ),
        CAGE_LOBSTER(
                40, 90,
                2, // TODO verify - placeholder
                "Cage",
                "Lobster pot",
                new WorldPoint(2674, 3161, 0) // TODO verify - placeholder
        );

        public final int requiredLevel;
        public final double xpValue;
        public final int npcId;
        public final String action;
        public final String toolItemName;
        public final WorldPoint location;

        Method(int requiredLevel, double xpValue, int npcId, String action,
               String toolItemName, WorldPoint location) {
            this.requiredLevel = requiredLevel;
            this.xpValue = xpValue;
            this.npcId = npcId;
            this.action = action;
            this.toolItemName = toolItemName;
            this.location = location;
        }
    }

    private final Method method;

    public FishingStrategy(Method method) {
        this.method = method;
    }

    @Override
    public String name() {
        return method.name();
    }

    @Override
    public boolean canExecute(AccountContext context) {

        int level = context.getRealLevel(Skill.FISHING);

        if (level < method.requiredLevel) {
            return false;
        }

        /*
         * We need the tool either in the inventory
         * or available in the bank.
         */
        return context.inventory().hasItem(method.toolItemName) || context.bank().hasItem(method.toolItemName);
    }

    @Override
    public double score(AccountContext context) {

        int level = context.getRealLevel(Skill.FISHING);

        if (level < method.requiredLevel) {
            return -1000;
        }

        double score = method.xpValue;

        /*
         * Prefer a method whose tool is already in the inventory.
         * This avoids an unnecessary banking trip.
         */
        if (context.inventory().hasItem(method.toolItemName)) {
            score += 30;
        } else {
            score += 10;
        }

        /*
         * A tool sitting in the bank is still usable,
         * but requires a banking trip first.
         */
        if (context.bank().hasItem(method.toolItemName)) {
            score += 10;
        }

        return score;
    }

    @Override
    public Task createTask(AccountContext context) {
        return new FishingTask(method);
    }

    @Override
    public Duration commitmentDuration(AccountContext context) {
        int minutes = Rs2Random.between(20, 180);
        return Duration.ofMinutes(minutes);
    }
}
