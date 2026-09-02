package net.runelite.client.plugins.microbot.mntn.builder.tasks.skilling;

import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.mntn.builder.activities.fishing.FishingStrategy;
import net.runelite.client.plugins.microbot.mntn.builder.activities.fishing.FishingStrategy.ToolRequirement;
import net.runelite.client.plugins.microbot.mntn.builder.core.AccountContext;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.Task;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.TaskStatus;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.banking.BankingTask;

import java.util.ArrayList;
import java.util.List;

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
                return handleWalk(context);
            case FISHING:
                return handleFish(context);
            case BANKING:
                return handleBank(context);
            default:
                return TaskStatus.RUNNING;
        }
    }

    /**
     * True only if every tool this method needs is currently in the inventory.
     */
    private boolean hasAllTools(AccountContext context) {
        for (ToolRequirement requirement : method.toolRequirements) {
            if (!context.inventory().hasItem(requirement.itemName)) {
                return false;
            }
        }
        return true;
    }

    /**
     * The first required tool NOT currently in the inventory, or null if we have everything.
     * Returns the whole ToolRequirement (not just the name) so handleBank() can withdraw it
     * at ITS requested quantity - 1 for a rod, WITHDRAW_ALL for feathers, etc.
     * Used to withdraw one missing tool at a time - handleBank() loops back through here
     * after each completed banking sub-task until nothing is missing anymore.
     */
    private ToolRequirement findMissingTool(AccountContext context) {
        for (ToolRequirement requirement : method.toolRequirements) {
            if (!context.inventory().hasItem(requirement.itemName)) {
                return requirement;
            }
        }
        return null;
    }

    /**
     * Every required tool we already have in the inventory right now - this is the keep-list
     * passed to DEPOSIT_ALL_EXCEPT so we don't accidentally bank a tool we already have.
     */
    private String[] ownedTools(AccountContext context) {
        List<String> owned = new ArrayList<>();
        for (ToolRequirement requirement : method.toolRequirements) {
            if (context.inventory().hasItem(requirement.itemName)) {
                owned.add(requirement.itemName);
            }
        }
        return owned.toArray(new String[0]);
    }

    private TaskStatus handleWalk(AccountContext context) {

        if (!hasAllTools(context)) {
            phase = Phase.BANKING;
            return TaskStatus.RUNNING;
        }

        Rs2NpcModel spot = Microbot.getRs2NpcCache().query().withId(method.npcId).nearest();
        if (spot != null) {
            phase = Phase.FISHING;
            return TaskStatus.RUNNING;
        }
        Rs2Walker.walkTo(method.location);
        return TaskStatus.RUNNING;
    }

    private TaskStatus handleFish(AccountContext context) {
        if (context.inventory().isFull() || !hasAllTools(context)) {
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
        Microbot.log("Handling banking");

        // Create the banking task only once.
        if (bankingTask == null) {

            if (context.inventory().isFull()) {
                // Free up space first (regardless of what's missing), protecting whatever
                // tools we already have. If we're also missing a tool, we'll catch that on
                // the next pass once there's room to withdraw it into.
                bankingTask = new BankingTask(
                        BankingTask.Mode.DEPOSIT_ALL_EXCEPT,
                        ownedTools(context)
                );

            } else {
                ToolRequirement missing = findMissingTool(context);

                if (missing != null) {
                    // Withdraw one missing tool AT ITS OWN REQUESTED QUANTITY - 1 for a rod,
                    // WITHDRAW_ALL (-1) for feathers, or whatever a future tool asks for.
                    // BankingTask already treats -1 as "withdraw all" natively, so this
                    // passes straight through with no translation needed.
                    // If more than one tool is missing, handleBank() loops back here again
                    // once this completes and picks up the next one.
                    bankingTask = new BankingTask(
                            BankingTask.Mode.WITHDRAW,
                            null,
                            missing.itemName,
                            missing.quantity
                    );
                } else {
                    // Not full, nothing missing - shouldn't normally reach BANKING in this
                    // state, but guard with a no-op-ish deposit-except pass rather than
                    // getting stuck with bankingTask staying null forever.
                    bankingTask = new BankingTask(
                            BankingTask.Mode.DEPOSIT_ALL_EXCEPT,
                            ownedTools(context)
                    );
                }
            }
        }

        TaskStatus bankStatus = bankingTask.tick(context);

        Microbot.log("BankStatus: " + bankStatus);
        Microbot.log("BankTask: " + bankingTask.describe());

        if (bankStatus == TaskStatus.COMPLETE) {

            bankingTask = null;

            // Check whether we still need more banking (e.g. we just freed up space, or just
            // withdrew ONE of several missing tools) before heading back out to fish.
            if (!hasAllTools(context)) {
                phase = Phase.BANKING;
                return TaskStatus.RUNNING;
            }

            phase = Phase.WALK_TO_SPOT;

            return TaskStatus.RUNNING;
        }

        if (bankStatus == TaskStatus.FAILED
                || bankStatus == TaskStatus.REPLAN) {

            bankingTask = null;
            return bankStatus;
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
