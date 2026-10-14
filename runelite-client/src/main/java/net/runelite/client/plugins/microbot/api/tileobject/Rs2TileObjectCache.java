package net.runelite.client.plugins.microbot.api.tileobject;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.Player;
import net.runelite.api.Tile;
import net.runelite.api.WorldView;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

@Singleton
public final class Rs2TileObjectCache {

    private final Client client;
    private final ClientThread clientThread;

    private volatile int lastUpdateObjects = 0;
    private volatile List<Rs2TileObjectModel> tileObjects = new ArrayList<>();

    @Inject
    public Rs2TileObjectCache(Client client, ClientThread clientThread) {
        this.client = client;
        this.clientThread = clientThread;
    }

    public Rs2TileObjectQueryable query() {
        return new Rs2TileObjectQueryable();
    }

    /**
     * Get all tile objects in the current scene
     *
     * @return Stream of Rs2TileObjectModel
     */
    public Stream<Rs2TileObjectModel> getStream() {
        return clientThread.runOnClientThreadOptional(() -> {
            int tick = client.getTickCount();
            if (lastUpdateObjects >= tick) {
                return tileObjects;
            }

            Player player = client.getLocalPlayer();
            if (player == null) return Collections.<Rs2TileObjectModel>emptyList();

            List<Rs2TileObjectModel> result = new ArrayList<>();

            for (var id : Microbot.getWorldViewIds()) {
                WorldView worldView = client.getWorldView(id);
                if (worldView == null) {
                    continue;
                }
                var tileValues = client.getWorldView(worldView.getId()).getScene().getTiles()[worldView.getPlane()];
                for (Tile[] tileValue : tileValues) {
                    for (Tile tile : tileValue) {
                        if (tile == null) continue;

                        if (tile.getGameObjects() != null) {
                            for (GameObject gameObject : tile.getGameObjects()) {
                                if (gameObject == null) continue;
                                if (gameObject.getSceneMinLocation().equals(tile.getSceneLocation())) {
                                    result.add(new Rs2TileObjectModel(gameObject));
                                }
                            }
                        }
                        if (tile.getGroundObject() != null) {
                            result.add(new Rs2TileObjectModel(tile.getGroundObject()));
                        }
                        if (tile.getWallObject() != null) {
                            result.add(new Rs2TileObjectModel(tile.getWallObject()));
                        }
                        if (tile.getDecorativeObject() != null) {
                            result.add(new Rs2TileObjectModel(tile.getDecorativeObject()));
                        }
                    }
                }
            }
            tileObjects = result;
            lastUpdateObjects = tick;
            return result;
        }).orElse(Collections.emptyList()).stream();
    }

    /**
     * @deprecated Use {@link Microbot#getRs2TileObjectCache()}.getStream() instead
     */
    @Deprecated(since = "2.1.8", forRemoval = true)
    public static Stream<Rs2TileObjectModel> getObjectsStream() {
        return Microbot.getRs2TileObjectCache().getStream();
    }
}
