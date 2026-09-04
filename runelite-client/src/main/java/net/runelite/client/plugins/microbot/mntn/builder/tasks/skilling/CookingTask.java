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

    private void debugLog(AccountContext context, String message) {
        if (context.isDebugLogging()) {
            Microbot.log("[MntnBuilder][CookingTask][DEBUG] " + message);
        }
    }

    @Override
    public TaskStatus tick(AccountContext context) {
        debugLog(context, "tick: phase=" + phase + ", method=" + method.name());

        if (!context.isLoggedIn()) {
            debugLog(context, "Not logged in, returning BLOCKED");
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
                debugLog(context, "Unknown phase, returning RUNNING");
                return TaskStatus.RUNNING;
        }
    }

    /**
     * Walk to the cooking location.
     */
    private TaskStatus handleWalk(AccountContext context) {
        debugLog(context, "handleWalk: hasRawItem=" + context.inventory().hasItem(method.rawItemName) + ", nearLocation=" + context.isNear(method.location, 10));

        if(!context.inventory().hasItem(method.rawItemName)){
            debugLog(context, "Missing raw item in inventory, switching to BANKING phase");
            phase = Phase.BANKING;
            return TaskStatus.RUNNING;
        }

        if (context.isNear(method.location, 10)) {
            debugLog(context, "Near cooking location, switching to COOKING phase");
            phase = Phase.COOKING;
            return TaskStatus.RUNNING;
        }

        debugLog(context, "Not near cooking location, walking to " + method.location);
        Rs2Walker.walkTo(method.location);

        return TaskStatus.RUNNING;
    }

    /**
     * Handle the actual cooking.
     */
    private TaskStatus handleCooking(AccountContext context) {
        debugLog(context, "handleCooking: hasRawItemInInventory=" + Rs2Inventory.contains(method.rawItemName));

        if (!Rs2Inventory.contains(method.rawItemName)) {
            int bankCount = context.bank().getCount(method.rawItemName);
            debugLog(context, "No raw item in inventory, bank count=" + bankCount);

            if (bankCount < WITHDRAW_QUANTITY) {
                debugLog(context, "Not enough raw items in bank (" + bankCount + "), returning REPLAN");
                return TaskStatus.REPLAN;
            }

            debugLog(context, "Switching to BANKING phase to withdraw raw items");
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
                debugLog(context, "Already animating/moving, waiting");
                return TaskStatus.RUNNING;
            }
        }

        debugLog(context, "Looking for cooking object: " + method.cookingObject);

        Rs2TileObjectModel cookingObject = Microbot.getRs2TileObjectCache().query().withId(method.cookingObject).nearest();
        if (cookingObject == null) {
            debugLog(context, "Cooking object not found! Switching to WALK_TO_COOKING");
            phase = Phase.WALK_TO_COOKING;
            return TaskStatus.RUNNING;
        }

        debugLog(context, "Found cooking object at " + cookingObject.getWorldLocation());
        sleep(100, 300);
        if (!Rs2Camera.isTileOnScreen(cookingObject.getLocalLocation())) {
            debugLog(context, "Cooking object not on screen, turning camera");
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
            debugLog(context, "Production widget open, pressing SPACE to cook");
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
        debugLog(context, "handleBank: hasRawItemInInventory=" + context.inventory().hasItem(method.rawItemName) + ", hasRawItemInBank=" + context.bank().hasItem(method.rawItemName));

        if (!context.inventory().hasItem(method.rawItemName) && !context.bank().hasItem(method.rawItemName)) {
            debugLog(context, "No raw item in inventory or bank, returning REPLAN");
            return TaskStatus.REPLAN;
        }

        if (bankingTask == null) {
            debugLog(context, "Creating DEPOSIT_ALL_AND_WITHDRAW banking task for " + method.rawItemName);

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
            debugLog(context, "Banking complete");
            bankingTask = null;
            if (!context.inventory().hasItem(method.rawItemName)) {
                debugLog(context, "Still no raw item in inventory after banking, returning REPLAN");
                return TaskStatus.REPLAN;
            }
            debugLog(context, "Raw item in inventory, switching to WALK_TO_COOKING");
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
        boolean levelCheck = context.getRealLevel(Skill.COOKING) < method.requiredLevel;
        boolean itemCheck = !context.inventory().hasItem(method.rawItemName) && !context.bank().hasItem(method.rawItemName);
        if (levelCheck || itemCheck) {
            debugLog(context, "needsReplan: levelCheck=" + levelCheck + " (current=" + context.getRealLevel(Skill.COOKING) + ", required=" + method.requiredLevel + "), itemCheck=" + itemCheck);
        }
        return levelCheck || itemCheck;
    }

    @Override
    public String describe() {

        return "Cooking ("
                + method.name()
                + ") - "
                + phase;
    }
}
