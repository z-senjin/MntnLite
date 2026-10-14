package net.runelite.client.plugins.microbot.util.tile;

import net.runelite.api.CollisionDataFlag;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class Rs2TileObjectReachableTest {

    private static final int SIZE = 104;
    private static final int WALL_WEST = 1;
    private static final int WALL_NORTH = 2;
    private static final int NO_WALL = 0;

    private static int[][] openField() {
        return new int[SIZE][SIZE];
    }

    private static void wallBetweenColumns(int[][] flags, int westX) {
        for (int y = 0; y < SIZE; y++) {
            flags[westX][y] |= CollisionDataFlag.BLOCK_MOVEMENT_EAST;
            flags[westX + 1][y] |= CollisionDataFlag.BLOCK_MOVEMENT_WEST;
        }
    }

    private static void solidColumn(int[][] flags, int x) {
        for (int y = 0; y < SIZE; y++) {
            flags[x][y] |= CollisionDataFlag.BLOCK_MOVEMENT_FULL;
        }
    }

    private static boolean reach(int[][] flags, int playerX, int playerY, int x, int y, int orientation) {
        boolean[][] reachable = Rs2Tile.reachableFrom(flags, playerX, playerY);
        return Rs2Tile.canInteractFromReachableTile(flags, reachable, x, y, orientation);
    }

    private static boolean ownTileReachable(int[][] flags, int playerX, int playerY, int x, int y) {
        return Rs2Tile.reachableFrom(flags, playerX, playerY)[x][y];
    }

    @Test
    public void wallObjectOnBlockedTileIsReachableFromTheTileItFaces() {
        int[][] flags = openField();
        solidColumn(flags, 50);
        flags[49][50] |= CollisionDataFlag.BLOCK_MOVEMENT_EAST;

        assertFalse("own-tile rule rejects the vein", ownTileReachable(flags, 40, 50, 50, 50));
        assertTrue(reach(flags, 40, 50, 50, 50, WALL_WEST));
    }

    @Test
    public void wallObjectIsUnreachableWhenNoTileBesideItIs() {
        int[][] flags = openField();
        solidColumn(flags, 50);
        wallBetweenColumns(flags, 48);
        flags[49][50] |= CollisionDataFlag.BLOCK_MOVEMENT_EAST;

        assertFalse(reach(flags, 40, 50, 50, 50, WALL_WEST));
    }

    @Test
    public void wallObjectOnBlockedTileIsUnreachableFromBehindItsFace() {
        int[][] flags = openField();
        solidColumn(flags, 50);

        assertFalse(reach(flags, 60, 50, 50, 50, WALL_WEST));
        assertTrue("same tile from the front", reach(flags, 40, 50, 50, 50, WALL_WEST));
    }

    @Test
    public void closedDoorIsReachableFromBothSides() {
        int[][] flags = openField();
        wallBetweenColumns(flags, 49);

        assertFalse("own-tile rule rejects the door from the far side", ownTileReachable(flags, 40, 50, 50, 50));
        assertTrue("far side", reach(flags, 40, 50, 50, 50, WALL_WEST));
        assertTrue("near side", reach(flags, 60, 50, 50, 50, WALL_WEST));
    }

    @Test
    public void doorBetweenTwoUnreachableRoomsStaysUnreachable() {
        int[][] flags = openField();
        wallBetweenColumns(flags, 49);
        solidColumn(flags, 30);
        solidColumn(flags, 70);

        assertFalse(reach(flags, 20, 50, 50, 50, WALL_WEST));
        assertFalse(reach(flags, 80, 50, 50, 50, WALL_WEST));
    }

    @Test
    public void doorOnNorthEdgeUsesTheTileAcrossThatEdge() {
        int[][] flags = openField();
        for (int x = 0; x < SIZE; x++) {
            flags[x][50] |= CollisionDataFlag.BLOCK_MOVEMENT_NORTH;
            flags[x][51] |= CollisionDataFlag.BLOCK_MOVEMENT_SOUTH;
        }

        assertTrue(reach(flags, 50, 60, 50, 50, WALL_NORTH));
        assertFalse("orientation decides the side", reach(flags, 50, 60, 50, 50, WALL_WEST));
    }

    @Test
    public void decorativeObjectOnWallTileIsReachableFromOpenNeighbour() {
        int[][] flags = openField();
        solidColumn(flags, 50);

        assertFalse("own-tile rule rejects the decoration", ownTileReachable(flags, 45, 50, 50, 50));
        assertTrue(reach(flags, 45, 50, 50, 50, NO_WALL));
    }

    @Test
    public void decorativeObjectBehindWallStaysUnreachable() {
        int[][] flags = openField();
        wallBetweenColumns(flags, 49);

        assertFalse(reach(flags, 40, 50, 50, 50, NO_WALL));
    }

    @Test
    public void groundObjectOnSolidTileIsReachableFromNeighbour() {
        int[][] flags = openField();
        flags[50][50] = CollisionDataFlag.BLOCK_MOVEMENT_FULL;

        assertTrue(reach(flags, 45, 45, 50, 50, NO_WALL));
    }

    @Test
    public void objectOnReachableTileIsReachable() {
        assertTrue(reach(openField(), 10, 10, 50, 50, NO_WALL));
    }

    @Test
    public void objectOutsideSceneIsUnreachable() {
        assertFalse(reach(openField(), 10, 10, SIZE, 50, WALL_WEST));
    }
}
