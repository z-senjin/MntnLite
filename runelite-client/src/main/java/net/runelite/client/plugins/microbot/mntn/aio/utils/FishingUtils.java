package net.runelite.client.plugins.microbot.mntn.aio.utils;

import net.runelite.api.NPC;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.game.FishingSpot;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;

public final class FishingUtils
{
    private FishingUtils()
    {
    }

    public static Rs2NpcModel findNearestFishingSpot(int... spotIds)
    {
        return Microbot.getRs2NpcCache().query()
                .withIds(spotIds)
                .nearest();
    }

    /**
     * Finds the nearest net fishing spot (shrimp/anchovies).
     * NPC ID: 1525
     */
    public static Rs2NpcModel findNearestNetSpot()
    {
        return findNearestFishingSpot(1525, 1528);
    }

    /**
     * Finds the nearest lure/bait/fly fishing spot (trout/salmon/sardine/herring).
     * NPC ID: 1526
     */
    public static Rs2NpcModel findNearestLureSpot()
    {
        return findNearestFishingSpot(1526, 1527);
    }
}