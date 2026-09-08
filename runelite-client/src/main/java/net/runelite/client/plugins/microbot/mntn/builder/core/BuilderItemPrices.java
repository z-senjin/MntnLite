package net.runelite.client.plugins.microbot.mntn.builder.core;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Conservative F2P estimates used by the planner and resilient GE offers. */
public final class BuilderItemPrices {

    private static final Map<String, Integer> ESTIMATES = new HashMap<>();

    static {
        ESTIMATES.put("feather", 3);
        ESTIMATES.put("cowhide", 120);
        ESTIMATES.put("copper ore", 60);
        ESTIMATES.put("tin ore", 50);
        ESTIMATES.put("iron ore", 80);
        ESTIMATES.put("coal", 150);
        ESTIMATES.put("bronze bar", 160);
        ESTIMATES.put("iron bar", 180);
        ESTIMATES.put("logs", 25);
        ESTIMATES.put("raw shrimps", 20);
        ESTIMATES.put("bronze axe", 16);
        ESTIMATES.put("bronze pickaxe", 1);
        ESTIMATES.put("small fishing net", 5);
        ESTIMATES.put("fishing rod", 5);
        ESTIMATES.put("fishing bait", 3);
    }

    private BuilderItemPrices() {
    }

    public static int estimate(String itemName, int fallback) {
        if (itemName == null) {
            return Math.max(1, fallback);
        }
        return ESTIMATES.getOrDefault(itemName.toLowerCase(Locale.ROOT), Math.max(1, fallback));
    }
}
