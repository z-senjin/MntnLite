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

    private void debugLog(AccountContext context, String message) {
        if (context.isDebugLogging()) {
            Microbot.log("[MntnBuilder][FishingTask][DEBUG] " + message);
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
            case WALK_TO_SPOT:
                return handleWalk(context);
            case FISHING:
                return handleFish(context);
            case BANKING:
                return handleBank(context);
            default:
                debugLog(context, "Unknown phase, returning RUNNING");
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
     * True only if every tool this method needs is currently in inventory OR in the bank.
     * If ANY required tool is missing from BOTH, returns false.
     */
    private boolean hasAllToolsAvailable(AccountContext context) {
        for (ToolRequirement requirement : method.toolRequirements) {
            boolean hasTool = context.inventory().hasItem(requirement.itemName)
                    || context.bank().hasItem(requirement.itemName);
            if (!hasTool) {
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
        debugLog(context, "handleWalk: checking tools");

        if (!hasAllTools(context)) {
            debugLog(context, "Missing tools, switching to BANKING phase");
            phase = Phase.BANKING;
            return TaskStatus.RUNNING;
        }

        Rs2NpcModel spot = Microbot.getRs2NpcCache().query().withId(method.npcId).nearest();
        if (spot != null) {
            debugLog(context, "Fishing spot found at " + spot.getWorldLocation() + ", switching to FISHING phase");
            phase = Phase.FISHING;
            return TaskStatus.RUNNING;
        }
        debugLog(context, "No fishing spot nearby, walking to " + method.location);
        Rs2Walker.walkTo(method.location);
        return TaskStatus.RUNNING;
    }

    private TaskStatus handleFish(AccountContext context) {
        debugLog(context, "handleFish: inventoryFull=" + context.inventory().isFull() + ", hasAllTools=" + hasAllTools(context));

        if (context.inventory().isFull() || !hasAllTools(context)) {
            debugLog(context, "Inventory full or missing tools, switching to BANKING phase");
            phase = Phase.BANKING;
            return TaskStatus.RUNNING;
        }

        Rs2NpcModel spot = Microbot.getRs2NpcCache().query().withId(method.npcId).nearest();
        if (spot == null) {
            debugLog(context, "Fishing spot not found, switching to WALK_TO_SPOT phase");
            phase = Phase.WALK_TO_SPOT;
            return TaskStatus.RUNNING;
        }

        boolean isAnimating = Microbot.getClient().getLocalPlayer() != null
                && Microbot.getClient().getLocalPlayer().getAnimation() != -1;
        sleep(100, 400);
        boolean areWeReallyAnimating = Microbot.getClient().getLocalPlayer() != null
                && Microbot.getClient().getLocalPlayer().getAnimation() != -1;
        if (!isAnimating && !areWeReallyAnimating) {
            debugLog(context, "Not animating, clicking fishing spot with action: " + method.action);
            sleepUntil(() -> spot.click(method.action), 2000);
        } else {
            debugLog(context, "Already animating, waiting");
        }
        return TaskStatus.RUNNING;
    }

    private TaskStatus handleBank(AccountContext context) {
        debugLog(context, "handleBank: inventoryFull=" + context.inventory().isFull() + ", hasAllTools=" + hasAllTools(context) + ", hasAllToolsAvailable=" + hasAllToolsAvailable(context));

        if (!hasAllToolsAvailable(context)) {
            debugLog(context, "Missing required tool(s) everywhere (inventory + bank) -> REPLAN");
            return TaskStatus.REPLAN;
        }

        // Create the banking task only once.
        if (bankingTask == null) {

            if (context.inventory().isFull()) {
                debugLog(context, "Inventory full, creating DEPOSIT_ALL_EXCEPT banking task with keepItems=" + java.util.Arrays.toString(ownedTools(context)));
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
                    debugLog(context, "Missing tool in inventory: " + missing.itemName + " (qty=" + missing.quantity + ")");
                    // Check if the missing tool actually exists in the bank before withdrawing
                    if (!context.bank().hasItem(missing.itemName)) {
                        debugLog(context, "Missing tool " + missing.itemName + " not in bank -> REPLAN");
                        return TaskStatus.REPLAN;
                    }

                    // Withdraw one missing tool AT ITS OWN REQUESTED QUANTITY - 1 for a rod,
                    // WITHDRAW_ALL (-1) for feathers, or whatever a future tool asks for.
                    // BankingTask already treats -1 as "withdraw all" natively, so this
                    // passes straight through with no translation needed.
                    // If more than one tool is missing, handleBank() loops back here again
                    // once this completes and picks up the next one.
                    debugLog(context, "Creating WITHDRAW banking task for " + missing.itemName + " qty=" + missing.quantity);
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
                    debugLog(context, "Not full, nothing missing, creating DEPOSIT_ALL_EXCEPT banking task");
                    bankingTask = new BankingTask(
                            BankingTask.Mode.DEPOSIT_ALL_EXCEPT,
                            ownedTools(context)
                    );
                }
            }
            // BankingTask now uses context.isDebugLogging() directly
        }

        TaskStatus bankStatus = bankingTask.tick(context);

        debugLog(context, "BankStatus: " + bankStatus);
        debugLog(context, "BankTask: " + bankingTask.describe());

        if (bankStatus == TaskStatus.COMPLETE) {

            bankingTask = null;

            // Check whether we still need more banking (e.g. we just freed up space, or just
            // withdrew ONE of several missing tools) before heading back out to fish.
            if (!hasAllTools(context)) {
                if (!hasAllToolsAvailable(context)) {
                    debugLog(context, "Still missing required tool after banking -> REPLAN");
                    return TaskStatus.REPLAN;
                }
                debugLog(context, "Tools still missing from inventory, staying in BANKING phase");
                phase = Phase.BANKING;
                return TaskStatus.RUNNING;
            }

            debugLog(context, "All tools in inventory, switching to WALK_TO_SPOT phase");
            phase = Phase.WALK_TO_SPOT;

            return TaskStatus.RUNNING;
        }

        if (bankStatus == TaskStatus.FAILED
                || bankStatus == TaskStatus.REPLAN) {

            debugLog(context, "Banking task failed/replan, clearing banking task");
            bankingTask = null;
            return bankStatus;
        }

        return TaskStatus.RUNNING;
    }

    @Override
    public boolean needsReplan(AccountContext context) {
        // Guard against something external invalidating this strategy mid-run
        // (e.g. a config change lowering the goal below the current level, or running out of tools).
        boolean levelCheck = context.getRealLevel(Skill.FISHING) < method.requiredLevel;
        boolean toolsCheck = !hasAllToolsAvailable(context);
        if (levelCheck || toolsCheck) {
            debugLog(context, "needsReplan: levelCheck=" + levelCheck + " (current=" + context.getRealLevel(Skill.FISHING) + ", required=" + method.requiredLevel + "), toolsCheck=" + toolsCheck);
        }
        return levelCheck || toolsCheck;
    }

    @Override
    public String describe() {
        return "Fishing (" + method.name() + ") - " + phase;
    }
}
