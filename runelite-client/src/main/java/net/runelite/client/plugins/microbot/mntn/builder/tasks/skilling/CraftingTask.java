package net.runelite.client.plugins.microbot.mntn.builder.tasks.skilling;

import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.mntn.builder.activities.crafting.CraftingStrategy;
import net.runelite.client.plugins.microbot.mntn.builder.core.AccountContext;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.Task;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.TaskActionGuard;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.TaskStatus;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.TaskStopReason;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.banking.BankingTask;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

/** One bounded Crafting batch: loadout, optional furnace production, then re-evaluate. */
public class CraftingTask implements Task {

    private static final int[] FURNACE_OBJECT_IDS = {24009};
    private static final int MAX_BATCH_SIZE = 27;

    private enum Phase {
        BANKING,
        WALK_TO_FURNACE,
        OPEN_CRAFTING_WIDGET,
        SELECT_PRODUCT,
        START_PRODUCTION,
        PRODUCING,
        CUTTING_GEMS
    }

    private final CraftingStrategy.Method method;
    private Phase phase;
    private BankingTask bankingTask;
    private TaskStopReason lastStopReason = TaskStopReason.NONE;
    private final TaskActionGuard walkGuard = new TaskActionGuard(10, 30_000, 800);
    private final TaskActionGuard widgetGuard = new TaskActionGuard(4, 12_000, 900);
    private final TaskActionGuard selectGuard = new TaskActionGuard(4, 8_000, 900);
    private final TaskActionGuard productionGuard = new TaskActionGuard(4, 12_000, 900);
    private final TaskActionGuard cutGuard = new TaskActionGuard(5, 10_000, 750);
    private int primaryInputBeforeAction;

    public CraftingTask(CraftingStrategy.Method method) {
        this.method = method;
        this.phase = method.usesFurnace() ? Phase.BANKING : Phase.BANKING;
    }

    @Override
    public TaskStatus tick(AccountContext context) {
        if (!context.isLoggedIn()) {
            return stop(TaskStatus.BLOCKED, TaskStopReason.NOT_LOGGED_IN);
        }

        switch (phase) {
            case BANKING:
                return handleBanking(context);
            case WALK_TO_FURNACE:
                return handleWalkToFurnace(context);
            case OPEN_CRAFTING_WIDGET:
                return handleOpenCraftingWidget(context);
            case SELECT_PRODUCT:
                return handleSelectProduct(context);
            case START_PRODUCTION:
                return handleStartProduction(context);
            case PRODUCING:
                return handleProduction(context);
            case CUTTING_GEMS:
                return handleGemCutting(context);
            default:
                return stop(TaskStatus.REPLAN, TaskStopReason.TASK_REQUESTED_REPLAN);
        }
    }

    private TaskStatus handleBanking(AccountContext context) {
        if (!hasAccountInputs(context)) {
            return stop(TaskStatus.REPLAN, TaskStopReason.MISSING_SUPPLIES);
        }

        if (bankingTask == null) {
            bankingTask = new BankingTask(BankingTask.Mode.DEPOSIT_ALL_AND_WITHDRAW, null,
                    withdrawalsFor(context));
        }

        TaskStatus status = bankingTask.tick(context);
        if (status == TaskStatus.COMPLETE) {
            bankingTask = null;
            resetActionGuards();
            phase = method.usesFurnace() ? Phase.WALK_TO_FURNACE : Phase.CUTTING_GEMS;
            return TaskStatus.RUNNING;
        }
        if (status.isUnsuccessfulStop()) {
            bankingTask = null;
            return stop(status, TaskStopReason.BANK_FAILED);
        }
        return TaskStatus.RUNNING;
    }

    private BankingTask.ItemWithdrawal[] withdrawalsFor(AccountContext context) {
        List<BankingTask.ItemWithdrawal> withdrawals = new ArrayList<>();
        for (CraftingStrategy.Input input : method.inputs) {
            int accountCount = context.inventory().getCount(input.itemName) + context.bank().getCount(input.itemName);
            int amount = input.consumed ? Math.min(MAX_BATCH_SIZE, accountCount) : 1;
            withdrawals.add(new BankingTask.ItemWithdrawal(input.itemName, Math.max(1, amount)));
        }
        return withdrawals.toArray(new BankingTask.ItemWithdrawal[0]);
    }

    private TaskStatus handleWalkToFurnace(AccountContext context) {
        if (!hasInventoryInputs(context)) {
            phase = Phase.BANKING;
            return TaskStatus.RUNNING;
        }
        if (context.isNear(CraftingStrategy.AL_KHARID_FURNACE, 10)) {
            walkGuard.reset();
            phase = Phase.OPEN_CRAFTING_WIDGET;
            return TaskStatus.RUNNING;
        }

        TaskActionGuard.Result result = walkGuard.evaluate("walk to crafting furnace " + method.name(), false);
        if (result == TaskActionGuard.Result.EXHAUSTED) {
            return stop(TaskStatus.REPLAN, TaskStopReason.TRAVEL_FAILED);
        }
        if (result == TaskActionGuard.Result.READY) {
            Rs2Walker.walkTo(CraftingStrategy.AL_KHARID_FURNACE);
            walkGuard.recordAttempt();
        }
        return TaskStatus.RUNNING;
    }

    private TaskStatus handleOpenCraftingWidget(AccountContext context) {
        if (!hasInventoryInputs(context)) {
            phase = Phase.BANKING;
            return TaskStatus.RUNNING;
        }
        if (isCraftingWidgetOpen()) {
            widgetGuard.reset();
            phase = Phase.SELECT_PRODUCT;
            return TaskStatus.RUNNING;
        }

        Rs2TileObjectModel furnace = Microbot.getRs2TileObjectCache().query()
                .withIds(FURNACE_OBJECT_IDS)
                .within(15)
                .nearest();
        if (furnace == null) {
            return stop(TaskStatus.REPLAN, TaskStopReason.RESOURCE_NOT_FOUND);
        }

        TaskActionGuard.Result result = widgetGuard.evaluate("open crafting furnace " + method.name(), false);
        if (result == TaskActionGuard.Result.EXHAUSTED) {
            return stop(TaskStatus.REPLAN, TaskStopReason.PRODUCTION_WIDGET_FAILED);
        }
        if (result == TaskActionGuard.Result.READY) {
            furnace.click("Smelt");
            widgetGuard.recordAttempt();
        }
        return TaskStatus.RUNNING;
    }

    private TaskStatus handleSelectProduct(AccountContext context) {
        if (!isCraftingWidgetOpen()) {
            phase = Phase.OPEN_CRAFTING_WIDGET;
            return TaskStatus.RUNNING;
        }

        TaskActionGuard.Result result = selectGuard.evaluate("select crafting product " + method.productName, false);
        if (result == TaskActionGuard.Result.EXHAUSTED) {
            return stop(TaskStatus.REPLAN, TaskStopReason.PRODUCTION_WIDGET_FAILED);
        }
        if (result == TaskActionGuard.Result.READY) {
            if (!Rs2Widget.clickWidget(method.craftingWidgetGroup, method.productWidgetChild)) {
                selectGuard.recordAttempt();
                return TaskStatus.RUNNING;
            }
            selectGuard.reset();
            phase = Phase.START_PRODUCTION;
        }
        return TaskStatus.RUNNING;
    }

    private TaskStatus handleStartProduction(AccountContext context) {
        if (!isCraftingWidgetOpen()) {
            phase = Phase.OPEN_CRAFTING_WIDGET;
            return TaskStatus.RUNNING;
        }

        TaskActionGuard.Result result = selectGuard.evaluate("start crafting batch " + method.productName, false);
        if (result == TaskActionGuard.Result.EXHAUSTED) {
            return stop(TaskStatus.REPLAN, TaskStopReason.PRODUCTION_WIDGET_FAILED);
        }
        if (result == TaskActionGuard.Result.READY) {
            if (!Rs2Widget.clickWidget(method.craftingWidgetGroup, method.makeAllWidgetChild())) {
                selectGuard.recordAttempt();
                return TaskStatus.RUNNING;
            }
            primaryInputBeforeAction = primaryInputCount(context);
            selectGuard.reset();
            phase = Phase.PRODUCING;
        }
        return TaskStatus.RUNNING;
    }

    private TaskStatus handleProduction(AccountContext context) {
        if (!hasInventoryInputs(context)) {
            phase = Phase.BANKING;
            return TaskStatus.RUNNING;
        }
        if (Rs2Player.isAnimating() || Rs2Player.isMoving()) {
            return TaskStatus.RUNNING;
        }

        int inputCount = primaryInputCount(context);
        boolean inputConsumed = primaryInputBeforeAction > inputCount;
        TaskActionGuard.Result result = productionGuard.evaluate("craft " + method.productName, inputConsumed);
        if (result == TaskActionGuard.Result.EXHAUSTED) {
            return stop(TaskStatus.REPLAN, TaskStopReason.PRODUCTION_WIDGET_FAILED);
        }
        if (result == TaskActionGuard.Result.CONFIRMED) {
            primaryInputBeforeAction = inputCount;
            return TaskStatus.RUNNING;
        }
        if (result == TaskActionGuard.Result.READY) {
            primaryInputBeforeAction = inputCount;
            Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
            productionGuard.recordAttempt();
        }
        return TaskStatus.RUNNING;
    }

    private TaskStatus handleGemCutting(AccountContext context) {
        if (!hasInventoryInputs(context)) {
            phase = Phase.BANKING;
            return TaskStatus.RUNNING;
        }

        if (Rs2Player.isAnimating() || Rs2Player.isMoving()) {
            cutGuard.reset();
            return TaskStatus.RUNNING;
        }

        int inputCount = primaryInputCount(context);
        boolean inputConsumed = primaryInputBeforeAction > inputCount;
        TaskActionGuard.Result result = cutGuard.evaluate("cut " + method.primaryInput, inputConsumed);
        if (result == TaskActionGuard.Result.EXHAUSTED) {
            return stop(TaskStatus.REPLAN, TaskStopReason.ACTION_FAILED);
        }
        if (result == TaskActionGuard.Result.CONFIRMED) {
            primaryInputBeforeAction = inputCount;
            return TaskStatus.RUNNING;
        }
        if (result == TaskActionGuard.Result.READY) {
            primaryInputBeforeAction = inputCount;
            if (!Rs2Inventory.interact(method.primaryInput, "Cut", true)) {
                cutGuard.recordAttempt();
                return TaskStatus.RUNNING;
            }
            cutGuard.recordAttempt();
        }
        return TaskStatus.RUNNING;
    }

    private boolean isCraftingWidgetOpen() {
        return method.mode == CraftingStrategy.Mode.GOLD_JEWELLERY
                ? Rs2Widget.isGoldCraftingWidgetOpen()
                : Rs2Widget.isSilverCraftingWidgetOpen();
    }

    private boolean hasAccountInputs(AccountContext context) {
        for (CraftingStrategy.Input input : method.inputs) {
            if (context.inventory().getCount(input.itemName) + context.bank().getCount(input.itemName) <= 0) {
                return false;
            }
        }
        return true;
    }

    private boolean hasInventoryInputs(AccountContext context) {
        for (CraftingStrategy.Input input : method.inputs) {
            if (!context.inventory().hasItem(input.itemName)) {
                return false;
            }
        }
        return true;
    }

    private int primaryInputCount(AccountContext context) {
        return context.inventory().getCount(method.primaryInput);
    }

    private void resetActionGuards() {
        walkGuard.reset();
        widgetGuard.reset();
        selectGuard.reset();
        productionGuard.reset();
        cutGuard.reset();
        primaryInputBeforeAction = 0;
    }

    @Override
    public boolean needsReplan(AccountContext context) {
        return context.getRealLevel(Skill.CRAFTING) < method.requiredLevel;
    }

    @Override
    public TaskStopReason getReplanStopReason(AccountContext context) {
        return context.getRealLevel(Skill.CRAFTING) < method.requiredLevel
                ? TaskStopReason.LEVEL_TOO_LOW
                : TaskStopReason.TASK_REQUESTED_REPLAN;
    }

    @Override
    public TaskStopReason getLastStopReason() {
        return lastStopReason;
    }

    private TaskStatus stop(TaskStatus status, TaskStopReason reason) {
        lastStopReason = reason;
        return status;
    }

    @Override
    public String describe() {
        return "Crafting (" + method.name() + ") - " + phase;
    }
}
