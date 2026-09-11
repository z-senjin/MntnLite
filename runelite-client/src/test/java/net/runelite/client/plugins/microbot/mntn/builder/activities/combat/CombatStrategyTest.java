package net.runelite.client.plugins.microbot.mntn.builder.activities.combat;

import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.mntn.builder.core.AccountContext;
import net.runelite.client.plugins.microbot.mntn.builder.core.BankView;
import net.runelite.client.plugins.microbot.mntn.builder.core.EquipmentView;
import net.runelite.client.plugins.microbot.mntn.builder.core.InventoryView;
import net.runelite.client.plugins.microbot.mntn.builder.core.requirements.Requirement;
import org.junit.Test;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class CombatStrategyTest {

    @Test
    public void chickensCanBootstrapWithoutWeaponOrFood() {
        TestContext context = new TestContext();
        CombatStrategy strategy = new CombatStrategy(CombatStrategy.Monster.CHICKENS, Skill.STRENGTH, 10, 0);

        List<Requirement> requirements = strategy.requirements(context);

        assertTrue(requirements.isEmpty());
    }

    @Test
    public void chickensLetCombatTaskLoadABankedWeapon() {
        TestContext context = new TestContext();
        context.bank.items.put("Bronze dagger", 1);
        CombatStrategy strategy = new CombatStrategy(CombatStrategy.Monster.CHICKENS, Skill.STRENGTH, 10, 0);

        List<Requirement> requirements = strategy.requirements(context);

        assertTrue(requirements.isEmpty());
    }

    @Test
    public void riskyMonstersSkipWhenTheirBankedFoodReserveIsTooSmall() {
        TestContext context = new TestContext();
        context.bank.items.put("Bronze dagger", 1);
        CombatStrategy strategy = new CombatStrategy(CombatStrategy.Monster.COWS, Skill.STRENGTH, 20, 0);

        assertTrue(strategy.requirements(context).isEmpty());
        assertFalse(strategy.canExecute(context));
    }

    @Test
    public void alKharidWarriorsAlwaysPlanAFoodLoadoutBelowTheirRecommendedLevel() {
        TestContext context = new TestContext();
        CombatStrategy.Monster monster = CombatStrategy.Monster.AL_KHARID_WARRIORS;

        assertEquals(20, monster.minCombatLevel);
        assertEquals(40, monster.maxRecommendedCombatLevel);
        assertEquals(10, monster.recommendedFood);
        assertEquals(10, CombatStrategy.recommendedFoodCount(monster, context));
        assertEquals("Al Kharid warrior", monster.npcNames[0]);
        assertEquals(3295, monster.location.getX());
        assertEquals(3170, monster.location.getY());
    }

    @Test
    public void combatFoodReserveCountsAllCookedFoodStacksInTheBank() {
        TestContext context = new TestContext();
        context.bank.items.put("Bronze dagger", 1);
        context.bank.items.put("Shrimps", 6);
        context.bank.items.put("Bread", 4);
        context.inventory.items.put("Salmon", 1);
        CombatStrategy strategy = new CombatStrategy(CombatStrategy.Monster.GOBLINS, Skill.STRENGTH, 20, 0);

        assertEquals(10, CombatStrategy.foodCountInBank(context));
        assertTrue(CombatStrategy.hasMinimumFoodReserveInBank(context));
        assertEquals(3, CombatStrategy.foodNamesForCombatLoadout(context).size());
        assertTrue(strategy.canExecute(context));
    }

    @Test
    public void prayerTrainingUsesLowestMeleeStyle() {
        TestContext context = new TestContext();
        context.realLevels.put(Skill.ATTACK, 5);
        context.realLevels.put(Skill.STRENGTH, 3);
        context.realLevels.put(Skill.DEFENCE, 7);

        assertEquals(Skill.STRENGTH, CombatStrategy.selectCombatStyleSkill(context, Skill.PRAYER));

        context.realLevels.put(Skill.ATTACK, 2);
        context.realLevels.put(Skill.STRENGTH, 10);

        assertEquals(Skill.ATTACK, CombatStrategy.selectCombatStyleSkill(context, Skill.PRAYER));
    }

    @Test
    public void combatUpgradesKeepTheCoinReserve() {
        TestContext context = new TestContext();
        context.inventory.items.put("Coins", CombatGear.COMBAT_GEAR_COIN_RESERVE + 31);

        assertNull(CombatGear.findNextPurchasableUpgrade(context, false));
    }

    @Test
    public void combatBuysTheBestAffordableWeaponBeforeArmour() {
        TestContext context = new TestContext();
        context.realLevels.put(Skill.ATTACK, 5);
        context.inventory.items.put("Coins", CombatGear.COMBAT_GEAR_COIN_RESERVE + 400);

        CombatGear.PurchasableGear upgrade = CombatGear.findNextPurchasableUpgrade(context, true);

        assertNotNull(upgrade);
        assertEquals("Steel scimitar", upgrade.item.name);
    }

    @Test
    public void combatDoesNotBlockOnAnUpgradeWhenOwnedGearIsBanked() {
        TestContext context = new TestContext();
        context.realLevels.put(Skill.ATTACK, 5);
        context.inventory.items.put("Coins", CombatGear.COMBAT_GEAR_COIN_RESERVE + 400);
        context.bank.items.put("Bronze dagger", 1);
        CombatStrategy strategy = new CombatStrategy(CombatStrategy.Monster.CHICKENS, Skill.STRENGTH, 10, 0);

        assertTrue(strategy.requirements(context).isEmpty());
    }

    @Test
    public void combatDoesNotBlockOnBodyArmourUpgrade() {
        TestContext context = new TestContext();
        context.realLevels.put(Skill.ATTACK, 5);
        context.realLevels.put(Skill.DEFENCE, 5);
        context.inventory.items.put("Coins", CombatGear.COMBAT_GEAR_COIN_RESERVE + 2000);
        context.equipment.items.put("Steel scimitar", 1);
        context.bank.items.put(CombatStrategy.STARTER_FOOD, 4);
        CombatStrategy strategy = new CombatStrategy(CombatStrategy.Monster.GOBLINS, Skill.STRENGTH, 20, 0);

        assertTrue(strategy.requirements(context).isEmpty());
    }

    @Test
    public void combatSelectsTheHighestOwnedF2pGearTierAllowedByLevels() {
        TestContext context = new TestContext();
        context.realLevels.put(Skill.ATTACK, 20);
        context.realLevels.put(Skill.DEFENCE, 20);
        context.bank.items.put("Bronze scimitar", 1);
        context.bank.items.put("Steel scimitar", 1);
        context.bank.items.put("Mithril scimitar", 1);
        context.bank.items.put("Mithril full helm", 1);
        context.bank.items.put("Mithril platebody", 1);
        context.bank.items.put("Mithril platelegs", 1);
        context.bank.items.put("Mithril kiteshield", 1);

        assertEquals("Mithril scimitar", CombatGear.findBestWeapon(context, true).name);
        assertTrue(CombatGear.getBankGearUpgrades(context).contains("Mithril kiteshield"));
        assertTrue(CombatGear.getBankGearUpgrades(context).contains("Mithril full helm"));
        assertTrue(CombatGear.getBankGearUpgrades(context).contains("Mithril platebody"));
        assertTrue(CombatGear.getBankGearUpgrades(context).contains("Mithril platelegs"));
    }

    @Test
    public void combatCanSelectRuneInsteadOfLowerTiersWhenEligible() {
        TestContext context = new TestContext();
        context.realLevels.put(Skill.ATTACK, 40);
        context.bank.items.put("Mithril scimitar", 1);
        context.bank.items.put("Adamant scimitar", 1);
        context.bank.items.put("Rune scimitar", 1);

        assertEquals("Rune scimitar", CombatGear.findBestWeapon(context, true).name);
    }

    @Test
    public void combatLoadoutUsesOneBestValidItemForEachSlot() {
        TestContext context = new TestContext();
        context.realLevels.put(Skill.ATTACK, 20);
        context.realLevels.put(Skill.DEFENCE, 20);
        context.bank.items.put("Mithril scimitar", 1);
        context.bank.items.put("Mithril kiteshield", 1);
        context.bank.items.put("Mithril full helm", 1);
        context.bank.items.put("Mithril platebody", 1);
        context.bank.items.put("Mithril platelegs", 1);
        context.bank.items.put("Bronze sword", 1);

        List<String> loadout = CombatGear.getBestLoadout(context);

        assertEquals(5, loadout.size());
        assertTrue(loadout.contains("Mithril scimitar"));
        assertTrue(loadout.contains("Mithril kiteshield"));
        assertTrue(loadout.contains("Mithril full helm"));
        assertTrue(loadout.contains("Mithril platebody"));
        assertTrue(loadout.contains("Mithril platelegs"));
        assertFalse(loadout.contains("Bronze sword"));
    }

    @Test
    public void twoHandedCombatLoadoutDoesNotIncludeAShield() {
        TestContext context = new TestContext();
        context.realLevels.put(Skill.ATTACK, 40);
        context.realLevels.put(Skill.DEFENCE, 40);
        context.bank.items.put("Rune 2h sword", 1);
        context.bank.items.put("Rune kiteshield", 1);

        List<String> loadout = CombatGear.getBestLoadout(context);

        assertTrue(loadout.contains("Rune 2h sword"));
        assertFalse(loadout.contains("Rune kiteshield"));
    }

    private static class TestContext extends AccountContext {
        private final TestInventoryView inventory = new TestInventoryView();
        private final TestBankView bank = new TestBankView();
        private final TestEquipmentView equipment = new TestEquipmentView();
        private final Map<Skill, Integer> realLevels = new EnumMap<>(Skill.class);
        private int combatLevel = 20;

        private TestContext() {
            realLevels.put(Skill.ATTACK, 1);
            realLevels.put(Skill.STRENGTH, 1);
            realLevels.put(Skill.DEFENCE, 1);
        }

        @Override
        public InventoryView inventory() {
            return inventory;
        }

        @Override
        public BankView bank() {
            return bank;
        }

        @Override
        public EquipmentView equipment() {
            return equipment;
        }

        @Override
        public boolean isLoggedIn() {
            return false;
        }

        @Override
        public int getRealLevel(Skill skill) {
            return realLevels.getOrDefault(skill, 1);
        }

        @Override
        public int getCombatLevel() {
            return combatLevel;
        }
    }

    private static class TestInventoryView extends InventoryView {
        private final Map<String, Integer> items = new HashMap<>();

        @Override
        public boolean hasItem(String itemName) {
            return getCount(itemName) > 0;
        }

        @Override
        public int getCount(String itemName) {
            return items.getOrDefault(itemName, 0);
        }
    }

    private static class TestBankView extends BankView {
        private final Map<String, Integer> items = new HashMap<>();

        @Override
        public boolean hasItem(String itemName) {
            return getCount(itemName) > 0;
        }

        @Override
        public int getCount(String itemName) {
            return items.getOrDefault(itemName, 0);
        }
    }

    private static class TestEquipmentView extends EquipmentView {
        private final Map<String, Integer> items = new HashMap<>();

        @Override
        public boolean hasItem(String itemName) {
            return items.getOrDefault(itemName, 0) > 0;
        }
    }
}
