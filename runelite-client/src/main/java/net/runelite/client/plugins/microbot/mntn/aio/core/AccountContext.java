/*
 * Role:
 * Provides a small, read-only view of the account's current state.
 *
 * Purpose:
 * Keeps RuneLite and Microbot API calls out of Goal, Planner, and most
 * strategy-selection logic. This gives every strategy the same meaning
 * for levels, coins, quests, and available items.
 *
 * Bank warning:
 * Bank item counts may not be dependable until the bank has been opened
 * at least once during the current session.
 */
package net.runelite.client.plugins.microbot.mntn.aio.core;

import net.runelite.api.ItemID;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.mntn.aio.utils.bank.BankCache;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.util.Objects;

public final class AccountContext
{

    private final BankCache bankCache = new BankCache();

    public BankCache getBankCache()
    {
        return bankCache;
    }

    /**
     * Returns the account's real skill level without temporary boosts
     * or reductions.
     */
    public int getLevel(Skill skill)
    {
        Objects.requireNonNull(skill, "skill");
        return Rs2Player.getRealSkillLevel(skill);
    }

    /**
     * Returns true when RuneLite reports that the quest is finished.
     */
    public boolean isQuestComplete(Quest quest)
    {
        Objects.requireNonNull(quest, "quest");

        return Rs2Player.getQuestState(quest)
                == QuestState.FINISHED;
    }



    /**
     * Returns the total coins currently known in the inventory and bank.
     */
    public long getCoins()
    {
        long inventoryCoins =
                Rs2Inventory.count(ItemID.COINS_995);

        long bankCoins =
                Rs2Bank.count(ItemID.COINS_995);

        return inventoryCoins + bankCoins;
    }

    /**
     * Checks the inventory, bank, and equipment for an item.
     */
    public boolean hasItemAnywhere(int itemId, int requiredAmount)
    {
        if (requiredAmount <= 0)
        {
            return true;
        }

        int inventoryAmount = Rs2Inventory.count(itemId);
        int bankAmount = Rs2Bank.count(itemId);
        int equipmentAmount =
                Rs2Equipment.isWearing(itemId) ? 1 : 0;

        return inventoryAmount
                + bankAmount
                + equipmentAmount
                >= requiredAmount;
    }
}