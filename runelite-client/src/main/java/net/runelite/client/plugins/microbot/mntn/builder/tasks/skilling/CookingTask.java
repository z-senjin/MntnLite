package net.runelite.client.plugins.microbot.mntn.builder.tasks.skilling;

import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.mntn.builder.activities.cooking.CookingStrategy;
import net.runelite.client.plugins.microbot.mntn.builder.core.AccountContext;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.Task;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.TaskStatus;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.banking.BankingTask;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.awt.event.KeyEvent;

import static net.runelite.client.plugins.microbot.util.Global.*;

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
    
    private static final int WITHDRAW_QUANTITY = -1;

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

        if(!context.inventory().hasItem(method.rawItemName)){
            phase = Phase.BANKING;
            return TaskStatus.RUNNING;
        }

        if (context.isNear(method.location, 10)) {
            phase = Phase.COOKING;
            return TaskStatus.RUNNING;
        }

        Microbot.log("Not near");

        Rs2Walker.walkTo(method.location);

        return TaskStatus.RUNNING;
    }

    /**
     * Handle the actual cooking.
     */
    private TaskStatus handleCooking(AccountContext context) {

        Microbot.log("We handling cooking");

        if (!Rs2Inventory.contains(method.rawItemName)) {

            int bankCount = context.bank().getCount(method.rawItemName);

            if (bankCount < WITHDRAW_QUANTITY) {
                return TaskStatus.REPLAN;
            }

            phase = Phase.BANKING;
            return TaskStatus.RUNNING;
        }

        /*
         * If we're currently animating,
         * we're probably already cooking.
         */
        if (Microbot.getClient().getLocalPlayer() != null) {

            boolean isMovingOrAnimating = Rs2Player.isAnimating() || Rs2Player.isMoving();

            sleep(300, 1000);

            boolean isMovingOrAnimatingAgain = Rs2Player.isAnimating() || Rs2Player.isMoving();


            if (isMovingOrAnimating || isMovingOrAnimatingAgain) {
                return TaskStatus.RUNNING;
            }
        }

        Microbot.log("We looking for it");

        Rs2TileObjectModel cookingObject = Microbot.getRs2TileObjectCache().query().withId(method.cookingObject).nearest();
        if (cookingObject == null) {
            Microbot.log("Cooking object not found!");
            phase = Phase.WALK_TO_COOKING;
            return TaskStatus.RUNNING;
        }

        sleep(100, 300);
        if (!Rs2Camera.isTileOnScreen(cookingObject.getLocalLocation())) {
            Rs2Camera.turnTo(cookingObject.getLocalLocation());

            sleep(100, 2100);
        }

        sleepUntil(() -> cookingObject.click(method.action), 3000);

        boolean productionWidgetOpen = Rs2Widget.isProductionWidgetOpen();
        if (!productionWidgetOpen) {
            productionWidgetOpen = sleepUntilTrue(Rs2Widget::isProductionWidgetOpen, 200, 12000);
        }

        sleepUntilTrue(() -> !Rs2Player.isMoving(), 200, 8000);

        if (productionWidgetOpen) {
            Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
            Microbot.status = "Cooking " + method.rawItemName;
        }

        Rs2Antiban.actionCooldown();
        Rs2Antiban.takeMicroBreakByChance();

        return TaskStatus.RUNNING;
    }

    /**
     * Handle banking. Unchanged - still your DEPOSIT_ALL_AND_WITHDRAW mode, since we now only
     * ever enter this phase once handleCooking() has already confirmed the bank has at least
     * WITHDRAW_QUANTITY of the raw item.
     */
    private TaskStatus handleBank(AccountContext context) {

        if (!context.inventory().hasItem(method.rawItemName) && !context.bank().hasItem(method.rawItemName)) {
            return TaskStatus.REPLAN;
        }

        if (bankingTask == null) {

            bankingTask =
                    new BankingTask(
                            BankingTask.Mode.DEPOSIT_ALL_AND_WITHDRAW,
                            null,
                            method.rawItemName,
                            WITHDRAW_QUANTITY
                    );
        }

        TaskStatus bankStatus =
                bankingTask.tick(context);

        if (bankStatus == TaskStatus.COMPLETE) {

            bankingTask = null;
            if (!context.inventory().hasItem(method.rawItemName)) {
                return TaskStatus.REPLAN;
            }
            phase = Phase.WALK_TO_COOKING;
        }

        return bankStatus;
    }

    @Override
    public boolean needsReplan(AccountContext context) {

        /*
         * If our Cooking level somehow becomes invalid
         * for the selected strategy, or if raw food is gone, replan.
         */
        if (context.getRealLevel(Skill.COOKING) < method.requiredLevel) {
            return true;
        }
        return !context.inventory().hasItem(method.rawItemName) && !context.bank().hasItem(method.rawItemName);
    }

    @Override
    public String describe() {

        return "Cooking ("
                + method.name()
                + ") - "
                + phase;
    }
}
