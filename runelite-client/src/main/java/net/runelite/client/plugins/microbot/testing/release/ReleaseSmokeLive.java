package net.runelite.client.plugins.microbot.testing.release;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import net.runelite.api.GameState;
import net.runelite.api.Tile;
import net.runelite.api.WallObject;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.api.tileobject.models.TileObjectType;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.walker.WalkerState;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

final class ReleaseSmokeLive {

    static final class WalkResult {
        final String state;
        final WorldPoint end;
        final int distance;
        final long durationMs;
        final int maxJump;
        final String maxJumpEdge;

        WalkResult(String state, WorldPoint end, int distance, long durationMs, int maxJump, String maxJumpEdge) {
            this.state = state;
            this.end = end;
            this.distance = distance;
            this.durationMs = durationMs;
            this.maxJump = maxJump;
            this.maxJumpEdge = maxJumpEdge;
        }

        boolean arrived(int tolerance) {
            return WalkerState.ARRIVED.name().equals(state) && distance <= tolerance;
        }

        String describe() {
            return "state=" + state + ", end=" + format(end) + ", distance=" + distance + ", " + durationMs + "ms";
        }
    }

    static final class Door {
        final WorldPoint location;
        final int id;
        final String name;
        final List<String> actions;

        Door(WorldPoint location, int id, String name, List<String> actions) {
            this.location = location;
            this.id = id;
            this.name = name;
            this.actions = actions;
        }

        boolean isClosed() {
            return actions.stream().anyMatch("Open"::equalsIgnoreCase);
        }

        boolean isOpen() {
            return actions.stream().anyMatch("Close"::equalsIgnoreCase);
        }

        String describe() {
            return name + "#" + id + "@" + format(location) + actions;
        }
    }

    private ReleaseSmokeLive() {
    }

    static WorldPoint location() {
        return Rs2Player.getWorldLocation();
    }

    static boolean loggedIn() {
        return Microbot.getClient().getGameState() == GameState.LOGGED_IN && Microbot.isLoggedIn();
    }

    static void requireLoggedIn(CheckContext context) {
        context.require(loggedIn(), "Client is not logged in (game state " + Microbot.getClient().getGameState() + ")");
    }

    static boolean waitFor(BooleanSupplier condition, int timeoutMs) {
        return sleepUntil(condition, timeoutMs);
    }

    static WalkResult walk(WorldPoint destination, int tolerance, long timeoutMs) {
        ExecutorService executor = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder()
                .setNameFormat("ReleaseE2E-walk-%d")
                .setDaemon(true)
                .build());
        long start = System.currentTimeMillis();
        Future<WalkerState> future = executor.submit(() -> Rs2Walker.walkWithState(destination, tolerance));
        WorldPoint previous = location();
        int maxJump = 0;
        String maxJumpEdge = null;
        String state;
        try {
            long deadline = start + timeoutMs;
            while (!future.isDone() && System.currentTimeMillis() < deadline) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }
                sleepUntil(future::isDone, 600);
                WorldPoint current = location();
                if (previous != null && current != null) {
                    int jump = current.distanceTo2D(previous);
                    if (jump > maxJump) {
                        maxJump = jump;
                        maxJumpEdge = format(previous) + "->" + format(current);
                    }
                }
                if (current != null) {
                    previous = current;
                }
            }
            if (future.isDone()) {
                state = future.get().name();
            } else {
                future.cancel(true);
                Rs2Walker.setTarget(null);
                state = Thread.currentThread().isInterrupted() ? "INTERRUPTED" : "TIMEOUT";
            }
        } catch (InterruptedException e) {
            future.cancel(true);
            Rs2Walker.setTarget(null);
            Thread.currentThread().interrupt();
            state = "INTERRUPTED";
        } catch (Exception e) {
            Rs2Walker.setTarget(null);
            state = "ERROR:" + ReleaseCheckRunner.describe(e.getCause() == null ? e : e.getCause());
        } finally {
            executor.shutdownNow();
        }
        WorldPoint end = location();
        return new WalkResult(state, end, distance(end, destination), System.currentTimeMillis() - start, maxJump, maxJumpEdge);
    }

    static WalkResult walkOrFail(CheckContext context, String label, WorldPoint destination, int tolerance, long timeoutMs) {
        WalkResult result = walk(destination, tolerance, timeoutMs);
        context.detail(label, result.describe());
        context.require(result.arrived(tolerance), label + " to " + format(destination) + " failed: " + result.describe());
        return result;
    }

    static WalkResult walkToStart(CheckContext context, String label, WorldPoint destination, int tolerance, long timeoutMs) {
        WorldPoint current = location();
        if (current != null && current.getPlane() == destination.getPlane() && current.distanceTo(destination) <= tolerance) {
            context.detail(label, "already within " + tolerance + " of " + format(destination));
            return null;
        }
        return walkOrFail(context, label, destination, tolerance, timeoutMs);
    }

    static List<Door> doorsNear(WorldPoint anchor, int radius) {
        List<Rs2TileObjectModel> models = Microbot.getRs2TileObjectCache().query()
                .withNames("Door", "Gate", "Large door")
                .within(anchor, radius)
                .toListOnClientThread();
        return models.stream()
                .filter(model -> model.getTileObjectType() == TileObjectType.WALL)
                .map(ReleaseSmokeLive::toDoor)
                .filter(door -> door.isOpen() || door.isClosed())
                .sorted(Comparator.comparingInt(door -> door.location.distanceTo(anchor)))
                .collect(Collectors.toList());
    }

    static Optional<Door> doorAt(WorldPoint anchor, int radius) {
        return doorsNear(anchor, radius).stream().findFirst();
    }

    private static Door toDoor(Rs2TileObjectModel model) {
        String[] actions = model.getObjectComposition().getActions();
        List<String> cleaned = actions == null ? List.of() : Arrays.stream(actions)
                .filter(action -> action != null && !action.isBlank())
                .collect(Collectors.toList());
        return new Door(model.getWorldLocation(), model.getId(), model.getName(), cleaned);
    }

    static boolean clickDoor(WorldPoint location, String action) {
        List<Rs2TileObjectModel> models = Microbot.getRs2TileObjectCache().query()
                .withNames("Door", "Gate", "Large door")
                .within(location, 0)
                .toListOnClientThread();
        for (Rs2TileObjectModel model : models) {
            if (model.getTileObjectType() == TileObjectType.WALL && toDoor(model).actions.stream().anyMatch(action::equalsIgnoreCase)) {
                return model.click(action);
            }
        }
        return false;
    }

    static boolean setDoorState(CheckContext context, String label, WorldPoint anchor, boolean wantClosed, int timeoutMs) {
        Optional<Door> current = doorAt(anchor, 1);
        if (current.isEmpty()) {
            context.detail(label, "no door within 1 tile of " + format(anchor));
            return false;
        }
        if (current.get().isClosed() == wantClosed) {
            context.detail(label, "already " + (wantClosed ? "closed" : "open") + ": " + current.get().describe());
            return true;
        }
        String action = wantClosed ? "Close" : "Open";
        boolean clicked = clickDoor(current.get().location, action);
        boolean changed = waitFor(() -> doorAt(anchor, 1).map(door -> door.isClosed() == wantClosed).orElse(false), timeoutMs);
        context.detail(label, action + " clicked=" + clicked + ", changed=" + changed + ", now="
                + doorAt(anchor, 1).map(Door::describe).orElse("none"));
        return changed;
    }

    static Optional<Integer> wallOrientation(WorldPoint point) {
        return wallObjectAt(point).map(WallObject::getOrientationA);
    }

    private static Optional<WallObject> wallObjectAt(WorldPoint point) {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            WorldView worldView = Microbot.getClient().getTopLevelWorldView();
            LocalPoint localPoint = LocalPoint.fromWorld(worldView, point);
            if (localPoint == null) {
                return null;
            }
            Tile[][][] tiles = worldView.getScene().getTiles();
            Tile tile = tiles[point.getPlane()][localPoint.getSceneX()][localPoint.getSceneY()];
            return tile == null ? null : tile.getWallObject();
        });
    }

    static WorldPoint acrossWall(WorldPoint doorTile, int orientation) {
        switch (orientation) {
            case 1:
                return doorTile.dx(-1);
            case 2:
                return doorTile.dy(1);
            case 4:
                return doorTile.dx(1);
            case 8:
                return doorTile.dy(-1);
            default:
                return null;
        }
    }

    static boolean reachable(WorldPoint point) {
        return Rs2Tile.isTileReachable(point);
    }

    static int distance(WorldPoint from, WorldPoint to) {
        if (from == null || to == null) {
            return Integer.MAX_VALUE;
        }
        if (from.getPlane() != to.getPlane()) {
            return Integer.MAX_VALUE - 1;
        }
        return from.distanceTo(to);
    }

    static String format(WorldPoint point) {
        return point == null ? "null" : point.getX() + "," + point.getY() + "," + point.getPlane();
    }
}
