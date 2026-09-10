package net.runelite.client.plugins.microbot.mntn.builder.activities.firemaking;

import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.mntn.builder.activities.Strategy;
import net.runelite.client.plugins.microbot.mntn.builder.core.AccountContext;
import net.runelite.client.plugins.microbot.mntn.builder.core.requirements.ItemRequirement;
import net.runelite.client.plugins.microbot.mntn.builder.core.requirements.Requirement;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.Task;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.skilling.FiremakingTask;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** F2P Forester's Campfire methods at an outdoor tile just south of Varrock West Bank. */
public class FiremakingStrategy implements Strategy {

    public static final String TINDERBOX = "Tinderbox";
    public static final WorldPoint VARROCK_WEST_CAMPFIRE = new WorldPoint(3187, 3430, 0);
    public static final int[] STARTER_FIRE_IDS = {26185, 26186};
    public static final int[] FORESTERS_CAMPFIRE_IDS = {
            49927, 49928, 49929, 49930, 49931, 49932
    };

    public enum Method {
        LOGS(1, 40.0, "Logs"),
        OAK_LOGS(15, 60.0, "Oak logs"),
        WILLOW_LOGS(30, 90.0, "Willow logs"),
        YEW_LOGS(60, 202.5, "Yew logs");

        public final int requiredLevel;
        public final double xpValue;
        public final String logItemName;

        Method(int requiredLevel, double xpValue, String logItemName) {
            this.requiredLevel = requiredLevel;
            this.xpValue = xpValue;
            this.logItemName = logItemName;
        }
    }

    private final Method method;

    public FiremakingStrategy(Method method) {
        this.method = method;
    }

    @Override
    public String name() {
        return "BURN_" + method.name();
    }

    @Override
    public boolean canExecute(AccountContext context) {
        return context.getRealLevel(Skill.FIREMAKING) >= method.requiredLevel
                && hasAccountItem(context, TINDERBOX)
                && hasAccountItem(context, method.logItemName);
    }

    @Override
    public List<Requirement> requirements(AccountContext context) {
        List<Requirement> requirements = new ArrayList<>();
        addMissingInput(requirements, context, TINDERBOX);
        addMissingInput(requirements, context, method.logItemName);
        return requirements;
    }

    private static void addMissingInput(List<Requirement> requirements, AccountContext context,
                                        String itemName) {
        ItemRequirement input = new ItemRequirement(itemName, 1);
        int missing = input.getMissingAccountQuantity(context);
        if (missing > 0) {
            requirements.add(new ItemRequirement(itemName, missing));
        }
    }

    private static boolean hasAccountItem(AccountContext context, String itemName) {
        return context.inventory().hasItem(itemName) || context.bank().hasItem(itemName);
    }

    @Override
    public double score(AccountContext context) {
        if (context.getRealLevel(Skill.FIREMAKING) < method.requiredLevel) {
            return -1000;
        }

        double score = method.xpValue;
        if (context.inventory().hasItem(method.logItemName)) {
            score += 30;
        } else if (context.bank().hasItem(method.logItemName)) {
            score += 10;
        }
        if (context.inventory().hasItem(TINDERBOX)) {
            score += 10;
        } else if (context.bank().hasItem(TINDERBOX)) {
            score += 5;
        }
        return score;
    }

    @Override
    public WorldPoint preferredLocation(AccountContext context) {
        return VARROCK_WEST_CAMPFIRE;
    }

    @Override
    public int estimatedXpPerHour(AccountContext context) {
        return (int) (method.xpValue * 700);
    }

    @Override
    public Task createTask(AccountContext context) {
        return new FiremakingTask(method);
    }

    @Override
    public Duration commitmentDuration(AccountContext context) {
        return Duration.ofMinutes(Rs2Random.between(20, 120));
    }
}
