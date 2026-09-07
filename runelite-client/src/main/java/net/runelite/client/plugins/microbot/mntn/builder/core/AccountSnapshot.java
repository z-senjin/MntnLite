package net.runelite.client.plugins.microbot.mntn.builder.core;

import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;

import java.util.EnumMap;
import java.util.Map;

/**
 * Read-only account state captured once for a planner pass.
 *
 * This starts deliberately small: it gives the planner stable skill, quest, location,
 * and world-membership facts without changing every task to snapshot-based execution yet.
 * Inventory, bank, and equipment snapshots will be added when the supply system lands.
 */
public class AccountSnapshot {

    private final boolean loggedIn;
    private final boolean membersWorld;
    private final WorldPoint location;
    private final Map<Skill, Integer> realLevels;
    private final Map<Skill, Integer> boostedLevels;
    private final Map<Quest, QuestState> questStates;

    private AccountSnapshot(
            boolean loggedIn,
            boolean membersWorld,
            WorldPoint location,
            Map<Skill, Integer> realLevels,
            Map<Skill, Integer> boostedLevels,
            Map<Quest, QuestState> questStates
    ) {
        this.loggedIn = loggedIn;
        this.membersWorld = membersWorld;
        this.location = location;
        this.realLevels = realLevels;
        this.boostedLevels = boostedLevels;
        this.questStates = questStates;
    }

    public static AccountSnapshot capture(AccountContext context) {
        boolean loggedIn = context.isLoggedIn();
        boolean membersWorld = loggedIn && context.isMembersWorld();
        WorldPoint location = loggedIn ? context.getLocation() : null;

        Map<Skill, Integer> realLevels = new EnumMap<>(Skill.class);
        Map<Skill, Integer> boostedLevels = new EnumMap<>(Skill.class);
        for (Skill skill : Skill.values()) {
            realLevels.put(skill, context.getRealLevel(skill));
            boostedLevels.put(skill, context.getBoostedLevel(skill));
        }

        Map<Quest, QuestState> questStates = new EnumMap<>(Quest.class);
        for (Quest quest : Quest.values()) {
            questStates.put(quest, context.getQuestState(quest));
        }

        return new AccountSnapshot(
                loggedIn,
                membersWorld,
                location,
                realLevels,
                boostedLevels,
                questStates
        );
    }

    public boolean isLoggedIn() {
        return loggedIn;
    }

    public boolean isMembersWorld() {
        return membersWorld;
    }

    public WorldPoint getLocation() {
        return location;
    }

    public int getRealLevel(Skill skill) {
        return realLevels.getOrDefault(skill, 0);
    }

    public int getBoostedLevel(Skill skill) {
        return boostedLevels.getOrDefault(skill, 0);
    }

    public QuestState getQuestState(Quest quest) {
        return questStates.getOrDefault(quest, QuestState.NOT_STARTED);
    }
}
