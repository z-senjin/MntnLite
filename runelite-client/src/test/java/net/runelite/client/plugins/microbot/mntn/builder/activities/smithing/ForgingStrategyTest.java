package net.runelite.client.plugins.microbot.mntn.builder.activities.smithing;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ForgingStrategyTest {

    @Test
    public void recordsExactHighTierForgeUnlocks() {
        assertEquals(68, ForgingStrategy.BarType.MITHRIL.platebodyLevel);
        assertEquals(88, ForgingStrategy.BarType.ADAMANT.platebodyLevel);
        assertEquals(99, ForgingStrategy.BarType.RUNE.platebodyLevel);
    }
}
