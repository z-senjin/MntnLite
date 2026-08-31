package net.runelite.client.plugins.microbot.mntn.builder.activities.cooking;

import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.mntn.builder.activities.Strategy;
import net.runelite.client.plugins.microbot.mntn.builder.core.AccountContext;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.Task;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.skilling.CookingTask;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;

import java.time.Duration;

public class CookingStrategy implements Strategy {

    /**
     * One concrete cooking method.
     *
     * Each Method describes:
     * - Required Cooking level
     * - Raw item
     * - Cooked item
     * - Cooking object/action
     * - Cooking XP
     *
     * Add additional foods/methods here as the builder grows.
     */
    public enum Method {

        COOK_SHRIMP(
                1,
                30,
                "Raw shrimps",
                "Shrimps",
                26181,
                "Cook",
                new WorldPoint(3274, 3180, 0)
        ),

        COOK_ANCHOVIES(
                1,
                30,
                "Raw anchovies",
                "Anchovies",
                26181,
                "Cook",
                new WorldPoint(3274, 3180, 0)
        ),

        COOK_TROUT(
                15,
                70,
                "Raw trout",
                "Trout",
                26181,
                "Cook",
                new WorldPoint(3274, 3180, 0)
        ),

        COOK_SALMON(
                25,
                90,
                "Raw salmon",
                "Salmon",
                26181,
                "Cook",
                new WorldPoint(3274, 3180, 0)
        );

        public final int requiredLevel;
        public final double xpValue;

        public final String rawItemName;
        public final String cookedItemName;

        public final int cookingObject;
        public final String action;

        public final WorldPoint location;

        Method(
                int requiredLevel,
                double xpValue,
                String rawItemName,
                String cookedItemName,
                int cookingObject,
                String action, WorldPoint location
        ) {
            this.requiredLevel = requiredLevel;
            this.xpValue = xpValue;
            this.rawItemName = rawItemName;
            this.cookedItemName = cookedItemName;
            this.cookingObject = cookingObject;
            this.action = action;
            this.location = location;
        }
    }

    private final Method method;

    public CookingStrategy(Method method) {
        this.method = method;
    }

    @Override
    public String name() {
        return method.name();
    }

    @Override
    public boolean canExecute(AccountContext context) {

        int level = context.getRealLevel(Skill.COOKING);

        if (level < method.requiredLevel) {
            return false;
        }

        /*
         * We need the raw item either in the inventory
         * or available in the bank.
         */
        return context.inventory().hasItem(method.rawItemName) || context.bank().hasItem(method.rawItemName);
    }

    @Override
    public double score(AccountContext context) {

        int level = context.getRealLevel(Skill.COOKING);

        if (level < method.requiredLevel) {
            return -1000;
        }

        double score = method.xpValue;

        /*
         * Prefer food that is already in the inventory.
         * This avoids an unnecessary banking trip.
         */
        if (context.inventory().hasItem(method.rawItemName)) {
            score += 30;
        } else {
            score += 10;
        }

        /*
         * Raw food in the bank is still useful,
         * but requires a banking trip.
         */
        if (context.bank().hasItem(method.rawItemName)) {
            score += 10;
        }

        return score;
    }

    @Override
    public Task createTask(AccountContext context) {
        return new CookingTask(method);
    }

    @Override
    public Duration commitmentDuration(AccountContext context) {
        int minutes = Rs2Random.between(40, 180);
        return Duration.ofMinutes(minutes);
    }

    public Method getMethod() {
        return method;
    }
}