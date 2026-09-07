package net.runelite.client.plugins.microbot.mntn.builder.activities.combat;

import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.mntn.builder.core.AccountContext;
import net.runelite.client.plugins.microbot.mntn.builder.core.BankView;
import net.runelite.client.plugins.microbot.mntn.builder.core.EquipmentView;
import net.runelite.client.plugins.microbot.mntn.builder.core.InventoryView;
import net.runelite.client.plugins.microbot.mntn.builder.core.requirements.EquipmentRequirement;
import net.runelite.client.plugins.microbot.mntn.builder.core.requirements.ItemRequirement;
import net.runelite.client.plugins.microbot.mntn.builder.core.requirements.Requirement;
import org.junit.Test;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

public class CombatStrategyTest {

    @Test
    public void chickensCanBootstrapWithoutWeaponOrFood() {
        TestContext context = new TestContext();
        CombatStrategy strategy = new CombatStrategy(CombatStrategy.Monster.CHICKENS, Skill.STRENGTH, 10, 0);

        List<Requirement> requirements = strategy.requirements(context);

        assertFalse(requirements.stream().anyMatch(EquipmentRequirement.class::isInstance));
        assertFalse(requirements.stream().anyMatch(ItemRequirement.class::isInstance));
    }

    @Test
    public void chickensUseBankedWeaponWhenAvailable() {
        TestContext context = new TestContext();
        context.bank.items.put("Bronze dagger", 1);
        CombatStrategy strategy = new CombatStrategy(CombatStrategy.Monster.CHICKENS, Skill.STRENGTH, 10, 0);

        List<Requirement> requirements = strategy.requirements(context);

        EquipmentRequirement weapon = requirements.stream()
                .filter(EquipmentRequirement.class::isInstance)
                .map(EquipmentRequirement.class::cast)
                .findFirst()
                .orElse(null);

        assertNotNull(weapon);
        assertEquals("Bronze dagger", weapon.getItemName());
        assertFalse(requirements.stream().anyMatch(ItemRequirement.class::isInstance));
    }

    @Test
    public void riskyMonstersRequestStarterFoodWhenNoFoodIsAvailable() {
        TestContext context = new TestContext();
        CombatStrategy strategy = new CombatStrategy(CombatStrategy.Monster.COWS, Skill.STRENGTH, 20, 0);

        ItemRequirement food = strategy.requirements(context).stream()
                .filter(ItemRequirement.class::isInstance)
                .map(ItemRequirement.class::cast)
                .findFirst()
                .orElse(null);

        assertNotNull(food);
        assertEquals(CombatStrategy.STARTER_FOOD, food.getItemName());
        assertEquals(8, food.getQuantity());
    }

    @Test
    public void foodRequirementOnlyRequestsMissingInventoryFood() {
        TestContext context = new TestContext();
        context.inventory.items.put(CombatStrategy.STARTER_FOOD, 3);
        CombatStrategy strategy = new CombatStrategy(CombatStrategy.Monster.GOBLINS, Skill.STRENGTH, 20, 0);

        ItemRequirement food = strategy.requirements(context).stream()
                .filter(ItemRequirement.class::isInstance)
                .map(ItemRequirement.class::cast)
                .findFirst()
                .orElse(null);

        assertNotNull(food);
        assertEquals(CombatStrategy.STARTER_FOOD, food.getItemName());
        assertEquals(1, food.getQuantity());
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

    private static class TestContext extends AccountContext {
        private final TestInventoryView inventory = new TestInventoryView();
        private final TestBankView bank = new TestBankView();
        private final TestEquipmentView equipment = new TestEquipmentView();
        private final Map<Skill, Integer> realLevels = new EnumMap<>(Skill.class);

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
        @Override
        public boolean hasItem(String itemName) {
            return false;
        }
    }
}
