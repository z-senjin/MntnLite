package net.runelite.client.plugins.microbot.mntn.builder.activities;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.mntn.builder.activities.fishing.FishingActivity;
import net.runelite.client.plugins.microbot.mntn.builder.activities.fishing.FishingStrategy;
import net.runelite.client.plugins.microbot.mntn.builder.activities.mining.MiningActivity;
import net.runelite.client.plugins.microbot.mntn.builder.activities.mining.MiningStrategy;
import net.runelite.client.plugins.microbot.mntn.builder.activities.woodcutting.WoodcuttingActivity;
import net.runelite.client.plugins.microbot.mntn.builder.activities.woodcutting.WoodcuttingStrategy;
import net.runelite.client.plugins.microbot.mntn.builder.core.AccountContext;
import net.runelite.client.plugins.microbot.mntn.builder.core.requirements.ActivityRequest;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TrainingLocationActivityTest {

    @Test
    public void fishingActivityCreatesStrategyForEveryF2pLocation() {
        List<Strategy> strategies = new FishingActivity().getStrategies(
                new AccountContext(), new ActivityRequest(ActivityType.FISHING, null));

        assertEquals(FishingStrategy.Location.values().length, strategies.size());
        assertTrue(strategies.stream().anyMatch(strategy -> strategy.name().contains("LUMBRIDGE_SWAMP_SHRIMP")));
        assertTrue(strategies.stream().anyMatch(strategy -> strategy.name().contains("BARBARIAN_VILLAGE_FLY")));
    }

    @Test
    public void woodcuttingActivityCreatesStrategyForEveryF2pLocation() {
        List<Strategy> strategies = new WoodcuttingActivity().getStrategies(
                new AccountContext(), new ActivityRequest(ActivityType.WOODCUTTING, null));

        assertEquals(WoodcuttingStrategy.Location.values().length, strategies.size());
        assertTrue(strategies.stream().anyMatch(strategy -> strategy.name().contains("VARROCK_WEST_BANK_OAK")));
        assertTrue(strategies.stream().anyMatch(strategy -> strategy.name().contains("DRAYNOR_WILLOW")));
    }

    @Test
    public void miningActivityCreatesStrategyForEveryF2pLocation() {
        List<Strategy> strategies = new MiningActivity().getStrategies(
                new AccountContext(), new ActivityRequest(ActivityType.MINING, null));

        assertEquals(MiningStrategy.Location.values().length, strategies.size());
        assertTrue(strategies.stream().anyMatch(strategy -> strategy.name().contains("LUMBRIDGE_SWAMP_COPPER")));
        assertTrue(strategies.stream().anyMatch(strategy -> strategy.name().contains("AL_KHARID_IRON")));
    }

    @Test
    public void strategyUsesItsExactSelectedLocation() {
        FishingStrategy strategy = new FishingStrategy(
                FishingStrategy.Method.NET_SHRIMP,
                FishingStrategy.Location.DRAYNOR_SHRIMP
        );

        assertEquals(new WorldPoint(3084, 3231, 0), strategy.preferredLocation(new AccountContext()));
    }
}
