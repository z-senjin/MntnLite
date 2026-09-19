package net.runelite.client.plugins.microbot.mntn.aio.strategies.skilling.mining.items;

import net.runelite.api.ItemID;

public final class Items
{
    private Items()
    {
        // Prevent instantiation
    }

    public static final int[] PICKAXES_ID = {
            ItemID.BRONZE_PICKAXE,
            ItemID.IRON_PICKAXE,
            ItemID.STEEL_PICKAXE,
            ItemID.BLACK_PICKAXE,
            ItemID.MITHRIL_PICKAXE,
            ItemID.ADAMANT_PICKAXE,
            ItemID.RUNE_PICKAXE
    };

    public static final String[] PICKAXES_NAME = {
            "Bronze pickaxe",
            "Iron pickaxe",
            "Steel pickaxe",
            "Black pickaxe",
            "Mithril pickaxe",
            "Adamant pickaxe",
            "Rune pickaxe",
            "Dragon pickaxe"

    };

    public static final int[] ORES = {
            ItemID.COPPER_ORE,
            ItemID.TIN_ORE,
            ItemID.IRON_ORE,
            ItemID.COAL,
            ItemID.MITHRIL_ORE,
            ItemID.ADAMANTITE_ORE,
            ItemID.RUNITE_ORE
    };

    public static final int[] BARS = {
            ItemID.BRONZE_BAR,
            ItemID.IRON_BAR,
            ItemID.STEEL_BAR,
            ItemID.MITHRIL_BAR,
            ItemID.ADAMANTITE_BAR,
            ItemID.RUNITE_BAR
    };
}