package net.runelite.client.plugins.microbot.mntn.aio.strategies.skilling.fishing.items;

import net.runelite.api.ItemID;

public final class Items
{
    private Items()
    {
        // Prevent instantiation
    }

    public static final int[] FISHING_RODS_ID = {
            ItemID.FISHING_ROD,
            ItemID.FLY_FISHING_ROD,
            ItemID.OILY_FISHING_ROD,
            ItemID.PEARL_FISHING_ROD,
            ItemID.PEARL_FLY_FISHING_ROD,
            ItemID.OILY_PEARL_FISHING_ROD
    };

    public static final String[] FISHING_RODS_NAME = {
            "Fishing rod",
            "Fly fishing rod",
            "Oily fishing rod",
            "Pearl fishing rod",
            "Pearl fly fishing rod",
            "Oily pearl fishing rod"
    };

    public static final int[] FISHING_BAIT_ID = {
            ItemID.FEATHER
    };

    public static final String[] FISHING_BAIT_NAME = {
            "Feather"
    };

    public static final int[] RAW_FISH = {
            ItemID.RAW_SHRIMPS,
            ItemID.RAW_SARDINE,
            ItemID.RAW_HERRING,
            ItemID.RAW_ANCHOVIES,
            ItemID.RAW_TROUT,
            ItemID.RAW_SALMON,
            ItemID.RAW_TUNA,
            ItemID.RAW_LOBSTER,
            ItemID.RAW_SWORDFISH,
            ItemID.RAW_SHARK,
            ItemID.RAW_MANTA_RAY,
            ItemID.RAW_SEA_TURTLE
    };
}