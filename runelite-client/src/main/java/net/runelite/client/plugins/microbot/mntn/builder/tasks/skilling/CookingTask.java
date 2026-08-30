package net.runelite.client.plugins.microbot.mntn.builder.tasks.skilling;

import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectQueryable;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.mntn.builder.activities.cooking.CookingStrategy;
import net.runelite.client.plugins.microbot.mntn.builder.core.AccountContext;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.Task;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.TaskStatus;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.banking.BankingTask;

import static net.runelite.client.plugins.microbot.util.Global.sleep;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

/**
 * Generic Cooking Task.
 *
 * The task is parameterized by CookingStrategy.Method.
 *
 * This means the same task can handle:
 *
 *     COOK_SHRIMP
 *     COOK_TROUT
 *     COOK_SALMON
 *     etc.
 *
 * The Strategy decides WHAT to cook.
 * The Task decides HOW to execute the cooking process.
 */
public class CookingTask implements Task {

    private enum Phase {
        WALK_TO_COOKING,
        COOKING,
        BANKING
    }


    private final CookingStrategy.Method method;

    private Phase phase = Phase.WALK_TO_COOKING;

    private BankingTask bankingTask;

    public CookingTask(CookingStrategy.Method method) {
        this.method = method;
    }

    @Override
    public TaskStatus tick(AccountContext context) {

        if (!context.isLoggedIn()) {
            return TaskStatus.BLOCKED;
        }

        switch (phase) {

            case WALK_TO_COOKING:
                return handleWalk(context);

            case COOKING:
                return handleCooking(context);

            case BANKING:
                return handleBank(context);

            default:
                return TaskStatus.RUNNING;
        }
    }

    /**
     * Walk to the cooking location.
     */
    private TaskStatus handleWalk(AccountContext context) {

        /*
         * If we're already close enough to the cooking area,
         * start cooking.
         *
         * You may want to replace this with a proper
         * distance/area check.
         */
        if (context.isNear(method.location, 10)) {
            phase = Phase.COOKING;
            return TaskStatus.RUNNING;
        }

        Rs2Walker.walkTo(method.location);

        return TaskStatus.RUNNING;
    }

    /**
     * Handle the actual cooking.
     */
    private TaskStatus handleCooking(AccountContext context) {

        /*
         * We don't have raw food.
         *
         * This means we need to go to the bank.
         */
        if (!context.hasItem(method.rawItemName)) {
            //TODO
            phase = Phase.BANKING;
            return TaskStatus.RUNNING;
//            if (context.bank().hasItem(method.rawItemName)) {
//                phase = Phase.BANKING;
//                return TaskStatus.RUNNING;
//            }

            /*
             * We don't have the raw item anywhere.
             *
             * This is important for the future dependency system.
             *
             * Eventually this should cause the planner to say:
             *
             *     "I need Raw Trout"
             *
             * and select Fishing as the provider.
             */
//            return TaskStatus.REPLAN;
        }

        /*
         * If we're currently animating,
         * we're probably already cooking.
         */
        if (Microbot.getClient().getLocalPlayer() != null) {

            int animation =
                    Microbot.getClient()
                            .getLocalPlayer()
                            .getAnimation();

            if (animation != -1) {
                return TaskStatus.RUNNING;
            }
        }

        /*
         * Find a cooking object.
         *
         * This is intentionally generic.
         *
         * Later you may want the Strategy to provide
         * the exact GameObject ID.
         */
        Rs2TileObjectQueryable cookingObject = new Rs2TileObjectQueryable()
                .withName(method.cookingObject);

        if (cookingObject == null) {
            phase = Phase.WALK_TO_COOKING;
            return TaskStatus.RUNNING;
        }

        /*
         * Start cooking.
         */
        sleep(100, 300);

        sleepUntil(
                () -> cookingObject.interact(method.action),
                2000
        );

        return TaskStatus.RUNNING;
    }

    /**
     * Handle banking.
     */
    private TaskStatus handleBank(AccountContext context) {

        if (bankingTask == null) {

            /*
             * Deposit everything except the raw food.
             *
             * This keeps our cooking ingredient.
             */
            bankingTask =
                    new BankingTask(
                            BankingTask.Mode.DEPOSIT_ALL_EXCEPT,
                            method.rawItemName,
                            BankLocation.AL_KHARID
                    );
        }

        TaskStatus bankStatus =
                bankingTask.tick(context);

        if (bankStatus == TaskStatus.COMPLETE) {

            bankingTask = null;

            /*
             * We now need to withdraw the raw food.
             *
             * Your current BankingTask may need a
             * WITHDRAW mode for this.
             */
            phase = Phase.WALK_TO_COOKING;
        }

        return bankStatus;
    }

    @Override
    public boolean needsReplan(AccountContext context) {

        /*
         * If our Cooking level somehow becomes invalid
         * for the selected strategy, replan.
         */
        return context.getRealLevel(Skill.COOKING)
                < method.requiredLevel;
    }

    @Override
    public String describe() {

        return "Cooking ("
                + method.name()
                + ") - "
                + phase;
    }
}