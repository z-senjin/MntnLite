package net.runelite.client.plugins.microbot.mntn.builder.activities.questing.quests.sheepshearer;

import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.plugins.microbot.mntn.builder.activities.Strategy;
import net.runelite.client.plugins.microbot.mntn.builder.activities.questing.QuestCatalog;
import net.runelite.client.plugins.microbot.mntn.builder.activities.questing.QuestMetadata;
import net.runelite.client.plugins.microbot.mntn.builder.core.AccountContext;
import net.runelite.client.plugins.microbot.mntn.builder.core.requirements.ItemRequirement;
import net.runelite.client.plugins.microbot.mntn.builder.core.requirements.Requirement;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.Task;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.questing.SheepShearerTask;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class SheepShearerStrategy implements Strategy {

    public static final String BALL_OF_WOOL = "Ball of wool";
    public static final int BALLS_NEEDED = 20;
    public static final WorldPoint FRED_LOCATION = new WorldPoint(3190, 3273, 0);

    private static final QuestMetadata METADATA = QuestCatalog.get(Quest.SHEEP_SHEARER)
            .orElseThrow(IllegalStateException::new);

    @Override
    public String name() {
        return "SHEEP_SHEARER";
    }

    @Override
    public boolean canExecute(AccountContext context) {
        return context.getQuestState(Quest.SHEEP_SHEARER) != QuestState.FINISHED;
    }

    @Override
    public List<Requirement> requirements(AccountContext context) {
        List<Requirement> requirements = new ArrayList<>();
        for (Requirement requirement : METADATA.requirements()) {
            if (!(requirement instanceof ItemRequirement)) {
                requirements.add(requirement);
                continue;
            }

            ItemRequirement item = (ItemRequirement) requirement;
            int requiredQuantity = BALL_OF_WOOL.equals(item.getItemName())
                    ? remainingBallsRequired(context)
                    : item.getQuantity();
            int availableQuantity = context.inventory().getCount(item.getItemName())
                    + context.bank().getCount(item.getItemName());
            int missing = Math.max(0, requiredQuantity - availableQuantity);
            if (missing > 0) {
                requirements.add(new ItemRequirement(item.getItemName(), missing));
            }
        }
        return requirements;
    }

    @Override
    public double score(AccountContext context) {
        QuestState questState = context.getQuestState(Quest.SHEEP_SHEARER);
        if (questState == QuestState.FINISHED) {
            return -1000;
        }

        int ballsAvailable = Math.min(remainingBallsRequired(context),
                context.inventory().getCount(BALL_OF_WOOL) + context.bank().getCount(BALL_OF_WOOL));
        double completionPriority = questState == QuestState.IN_PROGRESS ? 300.0 : 50.0;
        return completionPriority + ballsAvailable * 2;
    }

    @Override
    public WorldPoint preferredLocation(AccountContext context) {
        return METADATA.getPreferredLocation();
    }

    @Override
    public double unlockValue(AccountContext context) {
        return context.getQuestState(Quest.SHEEP_SHEARER) == QuestState.FINISHED ? 0 : METADATA.unlockValue(context);
    }

    @Override
    public Task createTask(AccountContext context) {
        return new SheepShearerTask();
    }

    @Override
    public Duration commitmentDuration(AccountContext context) {
        return Duration.ofMinutes(Rs2Random.between(10, 20));
    }

    /**
     * Sheep Shearer's varplayer starts at 0/1 and advances once per accepted ball of wool.
     * This mirrors the bundled Quest Helper so a partially completed hand-in only requests
     * the balls Fred still needs instead of restarting the full twenty-ball requirement.
     */
    public static int remainingBallsRequired(AccountContext context) {
        if (context.getQuestState(Quest.SHEEP_SHEARER) == QuestState.FINISHED) {
            return 0;
        }
        int sheepVarp = context.getVarpValue(VarPlayerID.SHEEP);
        return sheepVarp > 1 ? Math.max(0, BALLS_NEEDED + 1 - sheepVarp) : BALLS_NEEDED;
    }
}
