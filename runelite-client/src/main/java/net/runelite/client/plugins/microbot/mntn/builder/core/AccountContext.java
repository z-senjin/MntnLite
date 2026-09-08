package net.runelite.client.plugins.microbot.mntn.builder.core;

import net.runelite.api.GameState;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.WorldType;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

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

    public int getCombatLevel() {
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
}
