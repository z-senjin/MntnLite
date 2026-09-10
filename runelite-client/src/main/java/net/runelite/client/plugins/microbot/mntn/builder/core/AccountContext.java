package net.runelite.client.plugins.microbot.mntn.builder.core;

import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.WorldType;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.EnumMap;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Small read-through wrapper around live Microbot/RuneLite state.
 *
 * Builder code reads game state through this class so strategies and tasks do not need raw
 * RuneLite client access. Values that belong to the client thread are read through the client
 * thread helper; the planner remains a simple consumer of the resulting values.
 */
public class AccountContext {

    private final InventoryView inventory = new InventoryView();
    private final BankView bank = new BankView();
    private final EquipmentView equipment = new EquipmentView();
    private boolean debugLogging = false;
    private PlanningSnapshot planningSnapshot;

    public InventoryView inventory() {
        return inventory;
    }

    public BankView bank() {
        return bank;
    }

    public EquipmentView equipment() {
        return equipment;
    }

    public boolean isDebugLogging() {
        return debugLogging;
    }

    public void setDebugLogging(boolean debugLogging) {
        this.debugLogging = debugLogging;
    }

    public boolean isLoggedIn() {
        return Microbot.isLoggedIn();
    }

    /**
     * LoginManager can report success before the game has restored the local player. Tasks
     * must not touch banks, walkers, or planners until this client-thread check is true.
     */
    public boolean isGameplayReady() {
        if (!isLoggedIn() || Microbot.getClient() == null) {
            return false;
        }
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
                Microbot.getClient() != null
                        && Microbot.getClient().getGameState() == GameState.LOGGED_IN
                        && Microbot.getClient().getLocalPlayer() != null
        ).orElse(false);
    }

    public AccountSnapshot snapshot() {
        return AccountSnapshot.capture(this);
    }

    public boolean isMembersWorld() {
        if (planningSnapshot != null) {
            return planningSnapshot.membersWorld;
        }
        if (!isLoggedIn() || Microbot.getClient() == null) {
            return false;
        }
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
                Microbot.getClient().getWorldType().contains(WorldType.MEMBERS)
        ).orElse(false);
    }

    public int getRealLevel(Skill skill) {
        return getSkillLevel(skill, false);
    }

    public int getBoostedLevel(Skill skill) {
        return getSkillLevel(skill, true);
    }

    /** Reads all skill levels in one client-thread call for display-only status snapshots. */
    public Map<Skill, Integer> getRealSkillLevels() {
        if (planningSnapshot != null) {
            return Collections.unmodifiableMap(new EnumMap<>(planningSnapshot.realLevels));
        }
        if (!isLoggedIn() || Microbot.getClient() == null || Microbot.getClientThread() == null) {
            return Collections.emptyMap();
        }
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Map<Skill, Integer> levels = new EnumMap<>(Skill.class);
            if (Microbot.getClient() == null) {
                return Collections.unmodifiableMap(levels);
            }
            for (Skill skill : Skill.values()) {
                levels.put(skill, Microbot.getClient().getRealSkillLevel(skill));
            }
            return Collections.unmodifiableMap(levels);
        }).orElse(Collections.emptyMap());
    }

    public int getCombatLevel() {
        if (planningSnapshot != null) {
            return planningSnapshot.combatLevel;
        }
        if (!isLoggedIn() || Microbot.getClient() == null || Microbot.getClientThread() == null) {
            return 3;
        }
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
                Microbot.getClient() != null && Microbot.getClient().getLocalPlayer() != null
                        ? Microbot.getClient().getLocalPlayer().getCombatLevel()
                        : 3
        ).orElse(3);
    }

    public int getPlane() {
        if (planningSnapshot != null) {
            return planningSnapshot.plane;
        }
        if (!isLoggedIn() || Microbot.getClient() == null || Microbot.getClientThread() == null) {
            return 0;
        }
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
                Microbot.getClient() != null ? Microbot.getClient().getPlane() : 0
        ).orElse(0);
    }

    public QuestState getQuestState(Quest quest) {
        if (!isLoggedIn()) return QuestState.NOT_STARTED;
        return Rs2Player.getQuestState(quest);
    }

    public WorldPoint getLocation() {
        if (planningSnapshot != null) {
            return planningSnapshot.location;
        }
        return Rs2Player.getWorldLocation();
    }

    public boolean isNear(WorldPoint location, int distance) {
        WorldPoint currentLocation = getLocation();
        if (currentLocation == null || location == null) {
            return false;
        }
        int distanceTo = Rs2Walker.getDistanceBetween(currentLocation, location);
        return distanceTo <= distance;
    }

    private int getSkillLevel(Skill skill, boolean boosted) {
        if (planningSnapshot != null) {
            return (boosted ? planningSnapshot.boostedLevels : planningSnapshot.realLevels)
                    .getOrDefault(skill, 0);
        }
        if (!isLoggedIn() || skill == null || Microbot.getClient() == null || Microbot.getClientThread() == null) {
            return 0;
        }
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            if (Microbot.getClient() == null) {
                return 0;
            }
            return boosted
                    ? Microbot.getClient().getBoostedSkillLevel(skill)
                    : Microbot.getClient().getRealSkillLevel(skill);
        }).orElse(0);
    }

    /** Captures repeated planner reads once; tasks continue to use live state. */
    public void beginPlanningSnapshot() {
        bank.beginPlanningRead();
        if (planningSnapshot != null || !isLoggedIn() || Microbot.getClient() == null
                || Microbot.getClientThread() == null) {
            return;
        }

        planningSnapshot = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Map<Skill, Integer> realLevels = new EnumMap<>(Skill.class);
            Map<Skill, Integer> boostedLevels = new EnumMap<>(Skill.class);
            for (Skill skill : Skill.values()) {
                realLevels.put(skill, Microbot.getClient().getRealSkillLevel(skill));
                boostedLevels.put(skill, Microbot.getClient().getBoostedSkillLevel(skill));
            }

            Map<String, Integer> inventoryByName = new HashMap<>();
            Map<Integer, Integer> inventoryById = new HashMap<>();
            boolean hasFood = false;
            int occupiedSlots = 0;
            ItemContainer inventoryContainer = Microbot.getClient().getItemContainer(InventoryID.INV);
            if (inventoryContainer != null) {
                for (Item item : inventoryContainer.getItems()) {
                    if (item == null || item.getId() == -1) {
                        continue;
                    }
                    occupiedSlots++;
                    int quantity = Math.max(0, item.getQuantity());
                    inventoryById.merge(item.getId(), quantity, Integer::sum);
                    ItemComposition definition = Microbot.getClient().getItemDefinition(item.getId());
                    if (definition == null) {
                        continue;
                    }
                    inventoryByName.merge(definition.getName(), quantity, Integer::sum);
                    hasFood |= Arrays.stream(definition.getInventoryActions())
                            .anyMatch(action -> "Eat".equalsIgnoreCase(action));
                }
            }

            return new PlanningSnapshot(
                    Microbot.getClient().getWorldType().contains(WorldType.MEMBERS),
                    Microbot.getClient().getLocalPlayer() != null
                            ? Microbot.getClient().getLocalPlayer().getCombatLevel() : 3,
                    Microbot.getClient().getPlane(),
                    Microbot.getClient().getLocalPlayer() != null
                            ? Microbot.getClient().getLocalPlayer().getWorldLocation() : null,
                    realLevels,
                    boostedLevels,
                    inventoryByName,
                    inventoryById,
                    occupiedSlots >= 28,
                    hasFood
            );
        }).orElse(null);
        if (planningSnapshot != null) {
            inventory.beginPlanningRead(
                    planningSnapshot.inventoryByName,
                    planningSnapshot.inventoryById,
                    planningSnapshot.inventoryFull,
                    planningSnapshot.inventoryHasFood
            );
        }
    }

    public void endPlanningSnapshot() {
        planningSnapshot = null;
        inventory.endPlanningRead();
        bank.endPlanningRead();
    }

    private static final class PlanningSnapshot {
        private final boolean membersWorld;
        private final int combatLevel;
        private final int plane;
        private final WorldPoint location;
        private final Map<Skill, Integer> realLevels;
        private final Map<Skill, Integer> boostedLevels;
        private final Map<String, Integer> inventoryByName;
        private final Map<Integer, Integer> inventoryById;
        private final boolean inventoryFull;
        private final boolean inventoryHasFood;

        private PlanningSnapshot(boolean membersWorld, int combatLevel, int plane, WorldPoint location,
                                 Map<Skill, Integer> realLevels, Map<Skill, Integer> boostedLevels,
                                 Map<String, Integer> inventoryByName, Map<Integer, Integer> inventoryById,
                                 boolean inventoryFull, boolean inventoryHasFood) {
            this.membersWorld = membersWorld;
            this.combatLevel = combatLevel;
            this.plane = plane;
            this.location = location;
            this.realLevels = realLevels;
            this.boostedLevels = boostedLevels;
            this.inventoryByName = inventoryByName;
            this.inventoryById = inventoryById;
            this.inventoryFull = inventoryFull;
            this.inventoryHasFood = inventoryHasFood;
        }
    }
}
