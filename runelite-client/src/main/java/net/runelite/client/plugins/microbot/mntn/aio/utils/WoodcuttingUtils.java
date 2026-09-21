package net.runelite.client.plugins.microbot.mntn.aio.utils;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;

public final class WoodcuttingUtils
{
    private WoodcuttingUtils() {}

    public static Rs2TileObjectModel findNearestTree(int... rockIds)
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

    public static String getBestAxe(int woodcuttingLevel)
    {
        if (woodcuttingLevel >= 41) return "Rune axe";
        if (woodcuttingLevel >= 31) return "Adamant axe";
        if (woodcuttingLevel >= 21) return "Mithril axe";
        if (woodcuttingLevel >= 11) return "Black axe";
        if (woodcuttingLevel >= 6) return "Steel axe";
        if (woodcuttingLevel >= 1) return "Bronze axe";

        return null;
    }

    public static boolean canUseAxe(String axe, int woodcuttingLevel)
    {
        if (axe == null) return false;

        if (axe.equals("Rune axe")) return woodcuttingLevel >= 41;
        if (axe.equals("Adamant axe")) return woodcuttingLevel >= 31;
        if (axe.equals("Mithril axe")) return woodcuttingLevel >= 21;
        if (axe.equals("Black axe")) return woodcuttingLevel >= 11;
        if (axe.equals("Steel axe")) return woodcuttingLevel >= 6;
        if (axe.equals("Iron axe")) return woodcuttingLevel >= 1;
        if (axe.equals("Bronze axe")) return woodcuttingLevel >= 1;

        return false;
    }
}