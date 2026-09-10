package net.runelite.client.plugins.microbot.mntn.builder.activities.crafting;

import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.mntn.builder.core.AccountContext;
import net.runelite.client.plugins.microbot.mntn.builder.core.BankView;
import net.runelite.client.plugins.microbot.mntn.builder.core.InventoryView;
import net.runelite.client.plugins.microbot.mntn.builder.core.requirements.ActivityRequest;
import net.runelite.client.plugins.microbot.mntn.builder.core.requirements.ItemRequirement;
import org.junit.Test;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CraftingStrategyTest {

    @Test
    public void opalCuttingProvidesALevelOneCraftingRoute() {
        TestContext context = new TestContext();
        context.bank.items.put("Uncut opal", 27);
        context.bank.items.put("Chisel", 1);

        assertTrue(new CraftingStrategy(CraftingStrategy.Method.CUT_OPAL).canExecute(context));
    }

    @Test
    public void goldRingRequiresTheRingMouldAsWellAsGoldBars() {
        TestContext context = new TestContext();
        context.realLevels.put(Skill.CRAFTING, 5);
        context.bank.items.put("Gold bar", 27);
        CraftingStrategy strategy = new CraftingStrategy(CraftingStrategy.Method.GOLD_RING);

        assertFalse(strategy.canExecute(context));
        ItemRequirement requirement = (ItemRequirement) strategy.requirements(context).get(0);
        assertEquals("Ring mould", requirement.getItemName());

        context.bank.items.put("Ring mould", 1);
        assertTrue(strategy.canExecute(context));
    }

    @Test
    public void consumedCraftingInputsRequestAnAffordableBulkPurchaseButToolsRemainSingleItems() {
        TestContext context = new TestContext();
        context.bank.items.put("Coins", 100_000);
        context.bank.items.put("Chisel", 1);

        CraftingStrategy opalCutting = new CraftingStrategy(CraftingStrategy.Method.CUT_OPAL);
        ItemRequirement opals = (ItemRequirement) opalCutting.requirements(context).get(0);

        assertEquals("Uncut opal", opals.getItemName());
        assertEquals(500, opals.getQuantity());

        CraftingStrategy goldRing = new CraftingStrategy(CraftingStrategy.Method.GOLD_RING);
        ItemRequirement goldBars = goldRing.requirements(context).stream()
                .map(ItemRequirement.class::cast)
                .filter(requirement -> "Gold bar".equals(requirement.getItemName()))
                .findFirst()
                .orElseThrow(AssertionError::new);
        assertEquals("Gold bar", goldBars.getItemName());
        assertEquals(500, goldBars.getQuantity());

        context.bank.items.remove("Chisel");
        ItemRequirement chisel = opalCutting.requirements(context).stream()
                .map(ItemRequirement.class::cast)
                .filter(requirement -> "Chisel".equals(requirement.getItemName()))
                .findFirst()
                .orElseThrow(AssertionError::new);
        assertEquals("Chisel", chisel.getItemName());
        assertEquals(1, chisel.getQuantity());
    }

    @Test
    public void sapphireJewelleryRespectsItsCraftingLevel() {
        TestContext context = new TestContext();
        context.bank.items.put("Gold bar", 1);
        context.bank.items.put("Sapphire", 1);
        context.bank.items.put("Ring mould", 1);
        CraftingStrategy strategy = new CraftingStrategy(CraftingStrategy.Method.SAPPHIRE_RING);

        assertFalse(strategy.canExecute(context));
        context.realLevels.put(Skill.CRAFTING, 20);
        assertTrue(strategy.canExecute(context));
    }

    @Test
    public void craftingActivityOnlyOffersMethodsThatProduceTheRequestedItem() {
        CraftingActivity activity = new CraftingActivity();
        ItemRequirement sapphire = new ItemRequirement("Sapphire", 1);
        ActivityRequest request = sapphire.getWaysToSatisfy(new TestContext()).stream()
                .filter(candidate -> candidate.type() == net.runelite.client.plugins.microbot.mntn.builder.activities.ActivityType.CRAFTING)
                .findFirst()
                .orElseThrow(AssertionError::new);

        assertTrue(activity.canProvide(request, new TestContext()));
        assertEquals(1, activity.getStrategies(new TestContext(), request).size());
        assertEquals("CRAFT_CUT_SAPPHIRE", activity.getStrategies(new TestContext(), request).get(0).name());
    }

    private static final class TestContext extends AccountContext {
        private final TestInventoryView inventory = new TestInventoryView();
        private final TestBankView bank = new TestBankView();
        private final Map<Skill, Integer> realLevels = new EnumMap<>(Skill.class);

        @Override
        public InventoryView inventory() {
            return inventory;
        }

        @Override
        public BankView bank() {
            return bank;
        }

        @Override
        public int getRealLevel(Skill skill) {
            return realLevels.getOrDefault(skill, 1);
        }
    }

    private static final class TestInventoryView extends InventoryView {
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

    private static final class TestBankView extends BankView {
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
}
