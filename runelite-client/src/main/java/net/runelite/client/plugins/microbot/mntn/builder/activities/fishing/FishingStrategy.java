package net.runelite.client.plugins.microbot.mntn.builder.activities.fishing;

import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.mntn.builder.activities.Strategy;
import net.runelite.client.plugins.microbot.mntn.builder.core.AccountContext;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.Task;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.skilling.FishingTask;

public class FishingStrategy implements Strategy {

    /**
     * One concrete fishing method. NPC_ID values below are PLACEHOLDERS - fishing spot NPC
     * IDs differ per location in OSRS (there isn't one universal "net fishing spot" id).
     * Verify the real id for the spot you pick by either:
     *   - checking the OSRS Wiki page for that fishing spot (id is usually listed), or
     *   - right-clicking the NPC in-game with RuneLite's NPC ID indicator/plugin enabled.
     * Same goes for AREA (WorldPoint) in FishingTask - pick an actual F2P fishing location.
     */
    public enum Method {
        NET_SHRIMP(1, 10, /* npcId */ 1530, "Net", "Small fishing net"),
        CAGE_LOBSTER(40, 90, /* npcId */ 2, "Cage", "Lobster pot");

        public final int requiredLevel;
        public final double xpValue;
        public final int npcId;
        public final String action;
        public final String toolItemName;

        Method(int requiredLevel, double xpValue, int npcId, String action, String toolItemName) {
            this.requiredLevel = requiredLevel;
            this.xpValue = xpValue;
            this.npcId = npcId;
            this.action = action;
            this.toolItemName = toolItemName;
        }
    }

    private final Method method;

    public FishingStrategy(Method method) {
        this.method = method;
    }

    @Override
    public String name() {
        return method.name();
    }

    @Override
    public boolean canExecute(AccountContext context) {
        return context.getRealLevel(Skill.FISHING) >= method.requiredLevel;
    }

    @Override
    public double score(AccountContext context) {
        // Deterministic, additive - matches the doc's scoring model. Extend with
        // NearbyBonus/TravelCost/etc once more than one method is actually viable at once.
        int level = context.getRealLevel(Skill.FISHING);
        if (level < method.requiredLevel) {
            return -1000; // shouldn't be reached since canExecute() already filters this
        }
        return method.xpValue;
    }

    @Override
    public Task createTask(AccountContext context) {
        return new FishingTask(method);
    }
}
