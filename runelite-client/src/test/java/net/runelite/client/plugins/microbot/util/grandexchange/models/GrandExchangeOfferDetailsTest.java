package net.runelite.client.plugins.microbot.util.grandexchange.models;

import net.runelite.api.GrandExchangeOfferState;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class GrandExchangeOfferDetailsTest {
    @Test
    public void preservesPricesAndProceedsAboveIntegerRange() {
        long price = (long) Integer.MAX_VALUE + 1;
        long spent = price * 3;
        GrandExchangeOfferDetails details = new GrandExchangeOfferDetails(
                995, 3, 4, price, spent, GrandExchangeOfferState.BUYING, false, null);

        assertEquals(price, details.getPrice());
        assertEquals(spent, details.getSpent());
        assertEquals(75, details.getProgressPercentage());
    }

    @Test
    public void preservesZeroPriceAndProceeds() {
        GrandExchangeOfferDetails details = new GrandExchangeOfferDetails(
                995, 0, 0, 0L, 0L, GrandExchangeOfferState.EMPTY, false, null);

        assertEquals(0L, details.getPrice());
        assertEquals(0L, details.getSpent());
        assertEquals(0, details.getProgressPercentage());
    }
}
