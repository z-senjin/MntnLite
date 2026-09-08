package net.runelite.client.plugins.microbot.mntn.builder.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class BuilderItemPricesTest {

    @Test
    public void usesKnownLocalEstimateBeforeFallback() {
        assertEquals(60, BuilderItemPrices.estimate("Copper ore", 100));
        assertEquals(16, BuilderItemPrices.estimate("BRONZE AXE", 100));
    }

    @Test
    public void usesPositiveFallbackForUnknownItems() {
        assertEquals(250, BuilderItemPrices.estimate("Unlisted item", 250));
        assertEquals(1, BuilderItemPrices.estimate(null, 0));
    }
}
