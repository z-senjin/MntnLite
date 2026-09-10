package net.runelite.client.plugins.microbot.mntn.builder.activities.crafting;

import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.mntn.builder.activities.Strategy;
import net.runelite.client.plugins.microbot.mntn.builder.core.AccountContext;
import net.runelite.client.plugins.microbot.mntn.builder.core.ContentAccess;
import net.runelite.client.plugins.microbot.mntn.builder.core.SupplyBatchPolicy;
import net.runelite.client.plugins.microbot.mntn.builder.core.requirements.ItemRequirement;
import net.runelite.client.plugins.microbot.mntn.builder.core.requirements.Requirement;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.Task;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.skilling.CraftingTask;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class CraftingStrategy implements Strategy {

    public enum Mode {
        GEM_CUTTING,
        GOLD_JEWELLERY,
        SILVER_JEWELLERY
    }

    public static final class Input {
        public final String itemName;
        public final boolean consumed;

        private Input(String itemName, boolean consumed) {
            this.itemName = itemName;
            this.consumed = consumed;
        }

        public static Input consumed(String itemName) {
            return new Input(itemName, true);
        }

        public static Input tool(String itemName) {
            return new Input(itemName, false);
        }
    }

    /**
     * Gold and silver widget child indices are the current client indices for the selected
     * product. They are kept with the method so the task remains a simple select-and-make flow.
     */
    public enum Method {
        CUT_OPAL(ContentAccess.MEMBERS, 1, 15.0, Mode.GEM_CUTTING, "Uncut opal", "Opal", 0, 0,
                Input.consumed("Uncut opal"), Input.tool("Chisel")),
        CUT_JADE(ContentAccess.MEMBERS, 13, 20.0, Mode.GEM_CUTTING, "Uncut jade", "Jade", 0, 0,
                Input.consumed("Uncut jade"), Input.tool("Chisel")),
        CUT_RED_TOPAZ(ContentAccess.MEMBERS, 16, 25.0, Mode.GEM_CUTTING, "Uncut red topaz", "Red topaz", 0, 0,
                Input.consumed("Uncut red topaz"), Input.tool("Chisel")),
        GOLD_RING(5, 15.0, Mode.GOLD_JEWELLERY, "Gold bar", "Gold ring", 446, 7,
                Input.consumed("Gold bar"), Input.tool("Ring mould")),
        GOLD_NECKLACE(6, 20.0, Mode.GOLD_JEWELLERY, "Gold bar", "Gold necklace", 446, 21,
                Input.consumed("Gold bar"), Input.tool("Necklace mould")),
        GOLD_AMULET(8, 30.0, Mode.GOLD_JEWELLERY, "Gold bar", "Gold amulet (u)", 446, 34,
                Input.consumed("Gold bar"), Input.tool("Amulet mould")),
        CUT_SAPPHIRE(20, 50.0, Mode.GEM_CUTTING, "Uncut sapphire", "Sapphire", 0, 0,
                Input.consumed("Uncut sapphire"), Input.tool("Chisel")),
        SAPPHIRE_RING(20, 40.0, Mode.GOLD_JEWELLERY, "Gold bar", "Sapphire ring", 446, 8,
                Input.consumed("Gold bar"), Input.consumed("Sapphire"), Input.tool("Ring mould")),
        SAPPHIRE_NECKLACE(22, 55.0, Mode.GOLD_JEWELLERY, "Gold bar", "Sapphire necklace", 446, 22,
                Input.consumed("Gold bar"), Input.consumed("Sapphire"), Input.tool("Necklace mould")),
        SILVER_TIARA(23, 52.5, Mode.SILVER_JEWELLERY, "Silver bar", "Tiara", 6, 28,
                Input.consumed("Silver bar"), Input.tool("Tiara mould")),
        SAPPHIRE_AMULET(24, 65.0, Mode.GOLD_JEWELLERY, "Gold bar", "Sapphire amulet (u)", 446, 35,
                Input.consumed("Gold bar"), Input.consumed("Sapphire"), Input.tool("Amulet mould")),
        CUT_EMERALD(27, 67.5, Mode.GEM_CUTTING, "Uncut emerald", "Emerald", 0, 0,
                Input.consumed("Uncut emerald"), Input.tool("Chisel")),
        CUT_RUBY(34, 85.0, Mode.GEM_CUTTING, "Uncut ruby", "Ruby", 0, 0,
                Input.consumed("Uncut ruby"), Input.tool("Chisel")),
        CUT_DIAMOND(43, 107.5, Mode.GEM_CUTTING, "Uncut diamond", "Diamond", 0, 0,
                Input.consumed("Uncut diamond"), Input.tool("Chisel"));

        public final int requiredLevel;
        public final double xpValue;
        public final Mode mode;
        public final String primaryInput;
        public final String productName;
        public final int craftingWidgetGroup;
        public final int productWidgetChild;
        public final Input[] inputs;
        public final ContentAccess contentAccess;

        Method(int requiredLevel, double xpValue, Mode mode, String primaryInput, String productName,
               int craftingWidgetGroup, int productWidgetChild, Input... inputs) {
            this(ContentAccess.FREE_TO_PLAY, requiredLevel, xpValue, mode, primaryInput, productName,
                    craftingWidgetGroup, productWidgetChild, inputs);
        }

        Method(ContentAccess contentAccess, int requiredLevel, double xpValue, Mode mode, String primaryInput,
               String productName, int craftingWidgetGroup, int productWidgetChild, Input... inputs) {
            this.contentAccess = contentAccess;
            this.requiredLevel = requiredLevel;
            this.xpValue = xpValue;
            this.mode = mode;
            this.primaryInput = primaryInput;
            this.productName = productName;
            this.craftingWidgetGroup = craftingWidgetGroup;
            this.productWidgetChild = productWidgetChild;
            this.inputs = inputs;
        }

        public boolean usesFurnace() {
            return mode == Mode.GOLD_JEWELLERY || mode == Mode.SILVER_JEWELLERY;
        }

        public int makeAllWidgetChild() {
            return mode == Mode.GOLD_JEWELLERY ? 60 : 36;
        }
    }

    public static final WorldPoint AL_KHARID_FURNACE = new WorldPoint(3274, 3186, 0);

    private final Method method;

    public CraftingStrategy(Method method) {
        this.method = method;
    }

    @Override
    public String name() {
        return "CRAFT_" + method.name();
    }

    @Override
    public ContentAccess contentAccess() {
        return method.contentAccess;
    }

    @Override
    public boolean canExecute(AccountContext context) {
        if (context.getRealLevel(Skill.CRAFTING) < method.requiredLevel) {
            return false;
        }
        for (Input input : method.inputs) {
            if (!hasAccountItem(context, input.itemName)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public List<Requirement> requirements(AccountContext context) {
        List<Requirement> requirements = new ArrayList<>();
        for (Input input : method.inputs) {
            if (!input.consumed && accountQuantity(context, input.itemName) < 1) {
                requirements.add(new ItemRequirement(input.itemName, 1));
            }
        }
        for (Input input : method.inputs) {
            if (input.consumed) {
                int requiredQuantity = SupplyBatchPolicy.consumableBatchSize(context, input.itemName);
                if (accountQuantity(context, input.itemName) < requiredQuantity) {
                    requirements.add(new ItemRequirement(input.itemName, requiredQuantity));
                }
            }
        }
        return requirements;
    }

    @Override
    public double score(AccountContext context) {
        if (context.getRealLevel(Skill.CRAFTING) < method.requiredLevel) {
            return -1000;
        }

        double score = method.xpValue;
        for (Input input : method.inputs) {
            if (context.inventory().hasItem(input.itemName)) {
                score += 15;
            } else if (context.bank().hasItem(input.itemName)) {
                score += 5;
            }
        }
        return score + Rs2Random.betweenInclusive(0, 2);
    }

    @Override
    public WorldPoint preferredLocation(AccountContext context) {
        return method.usesFurnace() ? AL_KHARID_FURNACE : null;
    }

    @Override
    public int estimatedXpPerHour(AccountContext context) {
        return (int) (method.xpValue * (method.usesFurnace() ? 900 : 1_500));
    }

    @Override
    public Task createTask(AccountContext context) {
        return new CraftingTask(method);
    }

    @Override
    public Duration commitmentDuration(AccountContext context) {
        return Duration.ofMinutes(Rs2Random.between(20, 180));
    }

    private boolean hasAccountItem(AccountContext context, String itemName) {
        return accountQuantity(context, itemName) > 0;
    }

    private int accountQuantity(AccountContext context, String itemName) {
        return context.inventory().getCount(itemName) + context.bank().getCount(itemName);
    }
}
