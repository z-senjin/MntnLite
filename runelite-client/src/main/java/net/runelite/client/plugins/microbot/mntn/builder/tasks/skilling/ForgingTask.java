package net.runelite.client.plugins.microbot.mntn.builder.tasks.skilling;

import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.mntn.builder.activities.smithing.ForgingStrategy;
import net.runelite.client.plugins.microbot.mntn.builder.core.AccountContext;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.Task;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.TaskStatus;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.banking.BankingTask;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import static net.runelite.client.plugins.microbot.util.Global.sleep;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntilTrue;

public class ForgingTask implements Task {

    private static final int ANVIL_OBJECT_ID = 2097;

    private enum Phase {
        WALK_TO_ANVIL, FORGING, BANKING
    }

    private final ForgingStrategy.BarType barType;
    private Phase phase = Phase.WALK_TO_ANVIL;
    private BankingTask bankingTask;

    public ForgingTask(ForgingStrategy.BarType barType) {
        this.barType = barType;
    }

    @Override
    public TaskStatus tick(AccountContext context) {
        if (!context.isLoggedIn()) {
            return TaskStatus.BLOCKED;
        }

        switch (phase) {
            case WALK_TO_ANVIL:
                return handleWalk(context);
            case FORGING:
                return handleForging(context);
            case BANKING:
                return handleBank(context);
            default:
                return TaskStatus.RUNNING;
        }
    }

    private TaskStatus handleWalk(AccountContext context) {
        if (!hasHammerAndBars(context)) {
            phase = Phase.BANKING;
            return TaskStatus.RUNNING;
        }

        if (context.isNear(ForgingStrategy.FALADOR_ANVIL, 10)) {
            phase = Phase.FORGING;
            return TaskStatus.RUNNING;
        }

        Rs2Walker.walkTo(ForgingStrategy.FALADOR_ANVIL);
        return TaskStatus.RUNNING;
    }

    private TaskStatus handleForging(AccountContext context) {
        if (!hasHammerAndBars(context)) {
            phase = Phase.BANKING;
            return TaskStatus.RUNNING;
        }

        if (Rs2Player.isAnimating() || Rs2Player.isMoving()) {
            return TaskStatus.RUNNING;
        }

        Rs2TileObjectModel anvil = Microbot.getRs2TileObjectCache().query()
                .withId(ANVIL_OBJECT_ID)
                .within(15)
                .nearest();

        if (anvil == null) {
            phase = Phase.WALK_TO_ANVIL;
            return TaskStatus.RUNNING;
        }

        if (!Rs2Widget.isSmithingWidgetOpen()) {
            anvil.click("Smith");
            boolean open = sleepUntilTrue(Rs2Widget::isSmithingWidgetOpen, 200, 5000);
            if (!open) {
                return TaskStatus.RUNNING;
            }
        }

        int barCount = context.inventory().getCount(barType.barItemName);
        String targetItem = determineBestItemToForge(context, barCount);
        if (targetItem != null) {
            Rs2Widget.clickWidget(targetItem);
            sleep(600, 1000);
        }

        return TaskStatus.RUNNING;
    }

    private TaskStatus handleBank(AccountContext context) {
        if (bankingTask == null) {
            boolean hasHammer = context.inventory().hasItem(ForgingStrategy.HAMMER);
            if (hasHammer) {
                bankingTask = new BankingTask(
                        BankingTask.Mode.DEPOSIT_ALL_AND_WITHDRAW,
                        new String[]{ForgingStrategy.HAMMER},
                        new BankingTask.ItemWithdrawal(barType.barItemName, -1)
                );
            } else {
                bankingTask = new BankingTask(
                        BankingTask.Mode.DEPOSIT_ALL_AND_WITHDRAW,
                        null,
                        new BankingTask.ItemWithdrawal(ForgingStrategy.HAMMER, 1),
                        new BankingTask.ItemWithdrawal(barType.barItemName, -1)
                );
            }
        }

        TaskStatus bankStatus = bankingTask.tick(context);

        if (bankStatus == TaskStatus.COMPLETE) {
            bankingTask = null;

            if (!hasHammerAndBars(context)) {
                // Out of bars or missing hammer in bank
                return TaskStatus.REPLAN;
            }

            phase = Phase.WALK_TO_ANVIL;
            return TaskStatus.RUNNING;
        }

        if (bankStatus == TaskStatus.FAILED || bankStatus == TaskStatus.REPLAN) {
            bankingTask = null;
            return bankStatus;
        }

        return TaskStatus.RUNNING;
    }

    private boolean hasHammerAndBars(AccountContext context) {
        return context.inventory().hasItem(ForgingStrategy.HAMMER)
                && context.inventory().hasItem(barType.barItemName);
    }

    /**
     * Option C: Bar-efficient max XP. Prioritizes Platebody (5 bars) -> Platelegs (3 bars)
     * -> Sword (1 bar) -> Dagger (1 bar), based on current Smithing level and available bars.
     */
    private String determineBestItemToForge(AccountContext context, int barCount) {
        int smithingLevel = context.getRealLevel(Skill.SMITHING);
        String capitalBar = barType.name().substring(0, 1).toUpperCase() + barType.name().substring(1).toLowerCase();

        int baseOffset = 0;
        if (barType == ForgingStrategy.BarType.IRON) {
            baseOffset = 15;
        } else if (barType == ForgingStrategy.BarType.STEEL) {
            baseOffset = 30;
        }

        int platebodyReq = (barType == ForgingStrategy.BarType.BRONZE) ? 18 : (baseOffset + 18);
        int platelegsReq = (barType == ForgingStrategy.BarType.BRONZE) ? 16 : (baseOffset + 16);
        int swordReq = (barType == ForgingStrategy.BarType.BRONZE) ? 4 : (baseOffset + 4);
        int daggerReq = (barType == ForgingStrategy.BarType.BRONZE) ? 1 : baseOffset;

        if (barCount >= 5 && smithingLevel >= platebodyReq) {
            return capitalBar + " platebody";
        }
        if (barCount >= 3 && smithingLevel >= platelegsReq) {
            return capitalBar + " platelegs";
        }
        if (barCount >= 1 && smithingLevel >= swordReq) {
            return capitalBar + " sword";
        }
        if (barCount >= 1 && smithingLevel >= daggerReq) {
            return capitalBar + " dagger";
        }
        return null;
    }

    @Override
    public boolean needsReplan(AccountContext context) {
        return context.getRealLevel(Skill.SMITHING) < barType.requiredLevel;
    }

    @Override
    public String describe() {
        return "Forging (" + barType.name() + ") - " + phase;
    }
}
