package net.runelite.client.plugins.microbot.mntn.builder.activities.firemaking;

import net.runelite.client.plugins.microbot.mntn.builder.tasks.skilling.FiremakingTask;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class FiremakingStrategyTest {

    @Test
    public void exposesF2pLogMethodsWithCorrectFiremakingLevels() {
        assertEquals(1, FiremakingStrategy.Method.LOGS.requiredLevel);
        assertEquals(15, FiremakingStrategy.Method.OAK_LOGS.requiredLevel);
        assertEquals(30, FiremakingStrategy.Method.WILLOW_LOGS.requiredLevel);
        assertEquals(60, FiremakingStrategy.Method.YEW_LOGS.requiredLevel);
    }

    @Test
    public void namesMethodsAsBurningActivities() {
        assertEquals("BURN_WILLOW_LOGS",
                new FiremakingStrategy(FiremakingStrategy.Method.WILLOW_LOGS).name());
    }

    @Test
    public void usesBoundedLogTripsAndKeepsTheFinalPartialTrip() {
        assertEquals(27, FiremakingTask.logsForTrip(374));
        assertEquals(19, FiremakingTask.logsForTrip(19));
    }

    @Test
    public void knowsAllForestersCampfireObjectVariants() {
        assertEquals(6, FiremakingStrategy.FORESTERS_CAMPFIRE_IDS.length);
        assertEquals(49927, FiremakingStrategy.FORESTERS_CAMPFIRE_IDS[0]);
        assertEquals(49932, FiremakingStrategy.FORESTERS_CAMPFIRE_IDS[5]);
    }

    @Test
    public void knowsTheObservedOrdinaryFireVariantsUsedToStartCampfires() {
        assertEquals(2, FiremakingStrategy.STARTER_FIRE_IDS.length);
        assertEquals(26185, FiremakingStrategy.STARTER_FIRE_IDS[0]);
    }
}
