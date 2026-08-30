package net.runelite.client.plugins.microbot.mntn.builder.tasks.skilling;

import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.mntn.builder.activities.fishing.FishingStrategy;
import net.runelite.client.plugins.microbot.mntn.builder.core.AccountContext;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.Task;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.TaskStatus;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.banking.BankingTask;

import static net.runelite.client.plugins.microbot.util.Global.sleep;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

/**
 * Generic fishing Task, parameterized by FishingStrategy.Method - this is the
 * "new WoodcuttingTask(Tree.OAK, InventoryMode.DROP, ...)" pattern from the doc, applied to
 * fishing. One class handles shrimp, lobster, or any future Method you add to the enum;
 * you should NOT need a ShrimpTask/LobsterTask/etc.
 *
 * Internal phase machine mirrors your GemCrabKillerScript's WALKING/FIGHTING/BANKING states -
 * same idea, just owned by this Task instead of the top-level script.
 */
public class FishingTask implements Task {

    private enum Phase {
        WALK_TO_SPOT, FISHING, BANKING
    }

    // TODO: replace with an actual F2P fishing area WorldPoint for the chosen Method
    // (e.g. Lumbridge swamp/Al Kharid for net fishing, Catherby for cage fishing, etc).
    private static final WorldPoint FISHING_AREA = new WorldPoint(3242, 3149, 0);

    private final FishingStrategy.Method method;
    private Phase phase = Phase.WALK_TO_SPOT;
    private BankingTask bankingTask;

    public FishingTask(FishingStrategy.Method method) {
        this.method = method;
    }

    @Override
    public TaskStatus tick(AccountContext context) {
        if (!context.isLoggedIn()) {
            return TaskStatus.BLOCKED;
        }

        switch (phase) {
            case WALK_TO_SPOT:
                return handleWalk();
            case FISHING:
                return handleFish(context);
            case BANKING:
                return handleBank(context);
            default:
                return TaskStatus.RUNNING;
        }
    }

    private TaskStatus handleWalk() {
        Rs2NpcModel spot = Microbot.getRs2NpcCache().query().withId(method.npcId).nearest();
        if (spot != null) {
            phase = Phase.FISHING;
            return TaskStatus.RUNNING;
        }
        Rs2Walker.walkTo(FISHING_AREA);
        return TaskStatus.RUNNING;
    }

    private TaskStatus handleFish(AccountContext context) {
        if (context.isInventoryFull()) {
            phase = Phase.BANKING;
            return TaskStatus.RUNNING;
        }

        Rs2NpcModel spot = Microbot.getRs2NpcCache().query().withId(method.npcId).nearest();
        if (spot == null) {
            phase = Phase.WALK_TO_SPOT;
            return TaskStatus.RUNNING;
        }

        boolean isAnimating = Microbot.getClient().getLocalPlayer() != null
                && Microbot.getClient().getLocalPlayer().getAnimation() != -1;
        sleep(100, 400);
        boolean areWeReallyAnimating = Microbot.getClient().getLocalPlayer() != null
                && Microbot.getClient().getLocalPlayer().getAnimation() != -1;
        if (!isAnimating && !areWeReallyAnimating) {
            sleepUntil(() -> spot.click(method.action), 2000);
        }
        return TaskStatus.RUNNING;
    }

    private TaskStatus handleBank(AccountContext context) {
        if (bankingTask == null) {
            bankingTask = new BankingTask(BankingTask.Mode.DEPOSIT_ALL_EXCEPT, method.toolItemName);
        }
        TaskStatus bankStatus = bankingTask.tick(context);
        if (bankStatus == TaskStatus.COMPLETE) {
            bankingTask = null;
            phase = Phase.WALK_TO_SPOT;
        }
        return TaskStatus.RUNNING;
    }

    @Override
    public boolean needsReplan(AccountContext context) {
        // Guard against something external invalidating this strategy mid-run
        // (e.g. a config change lowering the goal below the current level).
        return context.getRealLevel(Skill.FISHING) < method.requiredLevel;
    }

    @Override
    public String describe() {
        return "Fishing (" + method.name() + ") - " + phase;
    }
}
