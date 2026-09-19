package net.runelite.client.plugins.microbot.mntn.aio.utils;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;

public final class MiningUtils
{
    private MiningUtils() {}

    public static Rs2TileObjectModel findNearestRock(int... rockIds)
    {
        WorldPoint playerLocation = Rs2Player.getWorldLocation();

        return Arrays.stream(rockIds)
                .mapToObj(id ->
                        Microbot.getRs2TileObjectCache()
                                .query()
                                .withId(id)
                                .nearest()
                )
                .filter(Objects::nonNull)
                .min(Comparator.comparingInt(object ->
                        playerLocation.distanceTo(object.getWorldLocation())
                ))
                .orElse(null);
    }

    public static String getBestPickaxe(int miningLevel)
    {
        if (miningLevel >= 41) return "Rune pickaxe";
        if (miningLevel >= 31) return "Adamant pickaxe";
        if (miningLevel >= 21) return "Mithril pickaxe";
        if (miningLevel >= 11) return "Black pickaxe";
        if (miningLevel >= 6) return "Steel pickaxe";
        if (miningLevel >= 1) return "Bronze pickaxe";

        return null;
    }

    public static boolean canUsePickaxe(String pickaxe, int miningLevel)
    {
        if (pickaxe == null) return false;

        if (pickaxe.equals("Rune pickaxe")) return miningLevel >= 41;
        if (pickaxe.equals("Adamant pickaxe")) return miningLevel >= 31;
        if (pickaxe.equals("Mithril pickaxe")) return miningLevel >= 21;
        if (pickaxe.equals("Black pickaxe")) return miningLevel >= 11;
        if (pickaxe.equals("Steel pickaxe")) return miningLevel >= 6;
        if (pickaxe.equals("Iron pickaxe")) return miningLevel >= 1;
        if (pickaxe.equals("Bronze pickaxe")) return miningLevel >= 1;

        return false;
    }
}