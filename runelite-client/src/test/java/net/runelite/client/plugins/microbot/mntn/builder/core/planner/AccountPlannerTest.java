package net.runelite.client.plugins.microbot.mntn.builder.core.planner;

import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.mntn.builder.activities.Activity;
import net.runelite.client.plugins.microbot.mntn.builder.activities.ActivityType;
import net.runelite.client.plugins.microbot.mntn.builder.activities.Strategy;
import net.runelite.client.plugins.microbot.mntn.builder.activities.combat.CombatActivity;
import net.runelite.client.plugins.microbot.mntn.builder.activities.mining.MiningActivity;
import net.runelite.client.plugins.microbot.mntn.builder.activities.smithing.SmithingActivity;
import net.runelite.client.plugins.microbot.mntn.builder.activities.supply.SupplyActivity;
import net.runelite.client.plugins.microbot.mntn.builder.activities.supply.SupplyRoutePolicy;
import net.runelite.client.plugins.microbot.mntn.builder.activities.supply.SupplyRouteType;
import net.runelite.client.plugins.microbot.mntn.builder.activities.supply.SupplyStrategy;
import net.runelite.client.plugins.microbot.mntn.builder.MntnBuilderConfig;
import net.runelite.client.plugins.microbot.mntn.builder.core.AccountContext;
import net.runelite.client.plugins.microbot.mntn.builder.core.AccountSnapshot;
import net.runelite.client.plugins.microbot.mntn.builder.core.AllowedContent;
import net.runelite.client.plugins.microbot.mntn.builder.core.BankView;
import net.runelite.client.plugins.microbot.mntn.builder.core.ContentAccess;
import net.runelite.client.plugins.microbot.mntn.builder.core.EquipmentView;
import net.runelite.client.plugins.microbot.mntn.builder.core.InventoryView;
import net.runelite.client.plugins.microbot.mntn.builder.core.goals.Goal;
import net.runelite.client.plugins.microbot.mntn.builder.core.requirements.ActivityRequest;
import net.runelite.client.plugins.microbot.mntn.builder.core.requirements.ItemRequirement;
import net.runelite.client.plugins.microbot.mntn.builder.core.requirements.MoneyRequirement;
import net.runelite.client.plugins.microbot.mntn.builder.core.requirements.Requirement;
import net.runelite.client.plugins.microbot.mntn.builder.core.requirements.SkillRequirement;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.Task;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.TaskStatus;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.TaskStopReason;
import org.junit.Test;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class AccountPlannerTest {

    @Test
    public void moneyRequirementsOnlyOfferTheBankedCoinSupplyPath() {
        List<ActivityRequest> requests = new MoneyRequirement(100).getWaysToSatisfy(new TestContext());

        assertEquals(1, requests.size());
        assertEquals(ActivityType.SUPPLY, requests.get(0).type());
    }

    @Test
    public void plannerEvaluatesEachGoalRequirementProviderAndStrategyOncePerPass() {
        TestContext context = new TestContext();
        TestRequirement requirement = new TestRequirement(ActivityType.FISHING, "Catch fish");
        TestGoal goal = new TestGoal(requirement);
        TestStrategy strategy = new TestStrategy("Fishing", ContentAccess.FREE_TO_PLAY, 10);
        TestActivity activity = new TestActivity(ActivityType.FISHING, strategy);

        Plan plan = planner(goal, activity).plan(context);

        assertNotNull(plan);
        assertEquals(1, goal.completionChecks);
        assertEquals(1, requirement.satisfiedChecks);
        assertEquals(1, activity.providerChecks);
        assertEquals(1, strategy.executionChecks);
        assertEquals(1, strategy.scoreChecks);
    }

    @Test
    public void filtersMembersStrategiesWhenConfiguredForF2pOnly() {
        TestContext context = new TestContext();
        context.membersWorld = true;
        TestRequirement requirement = new TestRequirement(ActivityType.FISHING, "Catch fish");
        TestGoal goal = new TestGoal(requirement);
        TestActivity activity = new TestActivity(
                ActivityType.FISHING,
                new TestStrategy("F2P fishing", ContentAccess.FREE_TO_PLAY, 10),
                new TestStrategy("Members fishing", ContentAccess.MEMBERS, 100)
        );

        AccountPlanner planner = planner(goal, activity);
        planner.setAllowedContent(AllowedContent.F2P_ONLY);

        Plan plan = planner.plan(context);

        assertNotNull(plan);
        assertEquals("F2P fishing", plan.strategy().name());
    }

    @Test
    public void f2pPlanningDoesNotCaptureFullAccountSnapshot() {
        TestContext context = new TestContext();
        context.snapshotForbidden = true;
        TestRequirement requirement = new TestRequirement(ActivityType.FISHING, "Catch fish");
        TestGoal goal = new TestGoal(requirement);
        TestActivity activity = new TestActivity(
                ActivityType.FISHING,
                new TestStrategy("F2P fishing", ContentAccess.FREE_TO_PLAY, 10)
        );

        Plan plan = planner(goal, activity).plan(context);

        assertNotNull(plan);
        assertEquals("F2P fishing", plan.strategy().name());
    }

    @Test
    public void rerollsFromLiveStateAfterEachPrerequisite() {
        TestContext context = new TestContext();
        TestRequirement goalRequirement = new TestRequirement(ActivityType.SMITHING, "Train Smithing");
        TestGoal goal = new TestGoal(goalRequirement);
        TestStrategy smeltBronze = new TestStrategy("Smelt bronze", ContentAccess.FREE_TO_PLAY, 100)
                .withRequirements(
                        new ItemRequirement("Copper ore", 14),
                        new ItemRequirement("Tin ore", 14)
                );
        TestStrategy withdrawSupply = new TestStrategy("Withdraw supply", ContentAccess.FREE_TO_PLAY, 10);
        TestActivity smithing = new TestActivity(ActivityType.SMITHING, smeltBronze);
        TestActivity supply = new PayloadActivity(ActivityType.SUPPLY, ItemRequirement.class, withdrawSupply);
        AccountPlanner planner = planner(goal, smithing, supply);

        Plan copperPlan = planner.plan(context);

        assertNotNull(copperPlan);
        assertEquals("Withdraw supply", copperPlan.strategy().name());

        context.inventory.items.put("Copper ore", 14);
        Plan tinPlan = planner.plan(context);

        assertNotNull(tinPlan);
        assertEquals("Withdraw supply", tinPlan.strategy().name());
        assertEquals("Have 14 x Tin ore in inventory", tinPlan.requirement().description());

        context.inventory.items.put("Tin ore", 14);
        Plan smeltingPlan = planner.plan(context);

        assertNotNull(smeltingPlan);
        assertEquals("Smelt bronze", smeltingPlan.strategy().name());
    }

    @Test
    public void readyWorkBeatsUnrelatedBankSupply() {
        TestContext context = new TestContext();
        TestGoal blockedGoal = new TestGoal(new TestRequirement(ActivityType.SMITHING, "Train Smithing"));
        TestGoal readyGoal = new TestGoal(new TestRequirement(ActivityType.MINING, "Train Mining"));
        TestStrategy blockedSmelting = new TestStrategy("Smelt bronze", ContentAccess.FREE_TO_PLAY, 100)
                .withRequirements(new ItemRequirement("Copper ore", 14));
        TestStrategy readyMining = new TestStrategy("Mine copper", ContentAccess.FREE_TO_PLAY, 1);
        TestStrategy withdrawSupply = new TestStrategy("Withdraw copper", ContentAccess.FREE_TO_PLAY, 100);
        TestActivity smithing = new TestActivity(ActivityType.SMITHING, blockedSmelting);
        TestActivity mining = new TestActivity(ActivityType.MINING, readyMining);
        TestActivity supply = new PayloadActivity(ActivityType.SUPPLY, ItemRequirement.class, withdrawSupply);
        AccountPlanner planner = new AccountPlanner(
                Arrays.asList(blockedGoal, readyGoal),
                Arrays.asList(smithing, mining, supply)
        );

        Plan plan = planner.plan(context);

        assertNotNull(plan);
        assertEquals("Mine copper", plan.strategy().name());
    }

    @Test
    public void bankedSmithingInputsStartSmeltingInsteadOfASupplyTask() {
        TestContext context = new TestContext();
        context.bank.items.put("Copper ore", 14);
        context.bank.items.put("Tin ore", 14);
        TestGoal goal = new TestGoal(new TestRequirement(ActivityType.SMITHING, "Train Smithing"));

        Plan plan = planner(goal, new SmithingActivity(), new SupplyActivity()).plan(context);

        assertNotNull(plan);
        assertEquals(ActivityType.SMITHING, plan.activity().type());
        assertEquals("SMELT_BRONZE_BAR", plan.strategy().name());
    }

    @Test
    public void purchasableSupplyRouteOwnsCoinWithdrawalInsteadOfPlanningCoinsSeparately() {
        TestContext context = new TestContext();
        context.bank.items.put("Coins", 100_000);
        ItemRequirement opals = new ItemRequirement("Uncut opal", 500);
        TestGoal goal = new TestGoal(opals);

        Plan plan = planner(goal, new SupplyActivity()).plan(context);

        assertNotNull(plan);
        assertEquals(ActivityType.SUPPLY, plan.activity().type());
        assertEquals(opals.description(), plan.requirement().description());
        assertTrue(plan.strategy() instanceof SupplyStrategy);
        assertEquals(SupplyRouteType.GRAND_EXCHANGE, ((SupplyStrategy) plan.strategy()).routeType());
        assertTrue(plan.strategy().requirements(context).isEmpty());
    }

    @Test
    public void prefersMiningCoalOverFundingAGrandExchangePurchase() {
        TestContext context = new TestContext();
        context.realLevels.put(Skill.MINING, 47);
        context.bank.items.put("Bronze pickaxe", 1);
        TestRequirement goalRequirement = new TestRequirement(ActivityType.SMITHING, "Train Smithing");
        TestGoal goal = new TestGoal(goalRequirement);
        TestStrategy smeltSteel = new TestStrategy("Smelt steel", ContentAccess.FREE_TO_PLAY, 100)
                .withRequirements(new ItemRequirement("Coal", 18));
        TestActivity smithing = new TestActivity(ActivityType.SMITHING, smeltSteel);
        AccountPlanner planner = planner(goal, smithing, new MiningActivity(), new SupplyActivity());

        Plan coalPlan = planner.plan(context);

        assertNotNull(coalPlan);
        assertEquals(ActivityType.MINING, coalPlan.activity().type());
        assertTrue(coalPlan.strategy().name().startsWith("COAL_ORE_"));
    }

    @Test
    public void doesNotSelectStrategyWhoseUnmetPrerequisiteHasNoRoute() {
        TestContext context = new TestContext();
        TestRequirement goalRequirement = new TestRequirement(ActivityType.FISHING, "Catch fish");
        TestGoal goal = new TestGoal(goalRequirement);
        TestStrategy fishing = new TestStrategy("Fishing with missing supplies", ContentAccess.FREE_TO_PLAY, 100)
                .withRequirements(new MoneyRequirement(100));
        TestActivity activity = new TestActivity(ActivityType.FISHING, fishing);

        Plan plan = planner(goal, activity).plan(context);

        assertNull("A strategy cannot run when its declared prerequisite has no plan", plan);
    }

    @Test
    public void cooldownMemoryPenalizesRecentlyFailedStrategies() {
        TestContext context = new TestContext();
        TestRequirement requirement = new TestRequirement(ActivityType.MINING, "Mine ore");
        TestGoal goal = new TestGoal(requirement);
        TestStrategy failed = new TestStrategy("Copper ore", ContentAccess.FREE_TO_PLAY, 80);
        TestStrategy fallback = new TestStrategy("Tin ore", ContentAccess.FREE_TO_PLAY, 70);
        TestActivity activity = new TestActivity(ActivityType.MINING, failed, fallback);
        AccountMemory memory = new AccountMemory();
        memory.recordOutcome(
                new Plan(goal, requirement, activity, failed, 80),
                TaskStatus.FAILED,
                TaskStopReason.BANK_FAILED
        );

        AccountPlanner planner = planner(goal, activity);
        planner.setMemory(memory);

        Plan plan = planner.plan(context);

        assertNotNull(plan);
        assertEquals("Tin ore", plan.strategy().name());
    }

    @Test
    public void cooldownMemoryMakesARecentlyFailedStrategyIneligible() {
        TestContext context = new TestContext();
        TestRequirement requirement = new TestRequirement(ActivityType.MINING, "Mine ore");
        TestGoal goal = new TestGoal(requirement);
        TestStrategy failed = new TestStrategy("Copper ore", ContentAccess.FREE_TO_PLAY, 80);
        TestActivity activity = new TestActivity(ActivityType.MINING, failed);
        AccountMemory memory = new AccountMemory();
        memory.recordOutcome(
                new Plan(goal, requirement, activity, failed, 80),
                TaskStatus.BLOCKED,
                TaskStopReason.INVENTORY_FULL
        );

        AccountPlanner planner = planner(goal, activity);
        planner.setMemory(memory);

        assertNull(planner.plan(context));
    }

    @Test
    public void fullGrandExchangeIsTrackedAndClearedWithPlannerMemory() {
        TestContext context = new TestContext();
        TestRequirement requirement = new TestRequirement(ActivityType.MINING, "Mine ore");
        TestGoal goal = new TestGoal(requirement);
        TestStrategy failed = new TestStrategy("GE route", ContentAccess.FREE_TO_PLAY, 80);
        TestActivity activity = new TestActivity(ActivityType.MINING, failed);
        AccountMemory memory = new AccountMemory();
        memory.recordOutcome(
                new Plan(goal, requirement, activity, failed, 80),
                TaskStatus.BLOCKED,
                TaskStopReason.GE_NO_OPEN_SLOT
        );

        assertTrue(memory.isGrandExchangeUnavailable());
        memory.clear();
        assertFalse(memory.isGrandExchangeUnavailable());
    }

    @Test
    public void itemRequirementsCanSelectItemProducingActivities() {
        TestContext context = new TestContext();
        ItemRequirement requirement = new ItemRequirement("Logs", 1);
        TestGoal goal = new TestGoal(requirement);
        TestActivity emptySupply = new TestActivity(ActivityType.SUPPLY);
        TestActivity woodcutting = new PayloadActivity(
                ActivityType.WOODCUTTING,
                ItemRequirement.class,
                new TestStrategy("Cut logs", ContentAccess.FREE_TO_PLAY, 75)
        );

        AccountPlanner planner = planner(goal, emptySupply, woodcutting);

        Plan plan = planner.plan(context);

        assertNotNull(plan);
        assertEquals(ActivityType.WOODCUTTING, plan.activity().type());
        assertEquals("Cut logs", plan.strategy().name());
    }

    @Test
    public void sessionFlavorBiasesPlannerTowardMatchingActivities() {
        TestContext context = new TestContext();
        MultiRequestRequirement requirement = new MultiRequestRequirement(
                "Choose a session activity",
                ActivityType.FISHING,
                ActivityType.QUESTING
        );
        TestGoal goal = new TestGoal(requirement);
        TestActivity fishing = new TestActivity(
                ActivityType.FISHING,
                new TestStrategy("Fish casually", ContentAccess.FREE_TO_PLAY, 50)
        );
        TestActivity questing = new TestActivity(
                ActivityType.QUESTING,
                new TestStrategy("Do quest", ContentAccess.FREE_TO_PLAY, 50)
        );

        AccountPlanner planner = planner(goal, fishing, questing);
        planner.setSessionFlavor(SessionFlavor.QUEST_FOCUSED);

        Plan plan = planner.plan(context);

        assertNotNull(plan);
        assertEquals(ActivityType.QUESTING, plan.activity().type());
    }

    @Test
    public void freshF2pCombatTargetsCanBootstrapWithUnarmedChickens() {
        TestContext context = new TestContext();
        FreshCombatConfig config = new FreshCombatConfig();
        List<Goal> goals = new net.runelite.client.plugins.microbot.mntn.builder.MntnBuilderScript()
                .buildGoals(config);
        AccountPlanner planner = new AccountPlanner(
                goals,
                Arrays.asList(
                        new CombatActivity(config),
                        new SupplyActivity(new SupplyRoutePolicy(false, true, true))
                )
        );

        Plan plan = planner.plan(context);

        assertNotNull(planner.diagnoseNoPlan(context), plan);
        assertEquals(ActivityType.COMBAT, plan.activity().type());
        assertTrue("Selected combat style must match the selected combat goal",
                plan.strategy().name().endsWith("_" + plan.goal().name().split(" ")[0]));
        assertNotNull("A selected fresh-F2P plan must create a runnable task", plan.strategy().createTask(context));
    }

    @Test
    public void combatBootstrapRemainsRunnableWithOnlyABankedBronzeAxe() {
        TestContext context = new TestContext();
        context.bank.items.put("Bronze axe", 1);
        context.bank.items.put("Coins", 31);
        FreshCombatConfig config = new FreshCombatConfig();
        List<Goal> goals = new net.runelite.client.plugins.microbot.mntn.builder.MntnBuilderScript()
                .buildGoals(config);
        AccountPlanner planner = new AccountPlanner(
                goals,
                Arrays.asList(
                        new CombatActivity(config),
                        new SupplyActivity(new SupplyRoutePolicy(true, true, true))
                )
        );

        Plan plan = planner.planCombatBootstrap(context);

        assertNotNull(plan);
        assertEquals(ActivityType.COMBAT, plan.activity().type());
        assertTrue(plan.strategy().name().startsWith("Chickens_"));
    }

    @Test
    public void bankedBronzeAxeLetsCombatOwnItsLoadout() {
        TestContext context = new TestContext();
        context.bank.items.put("Bronze axe", 1);
        context.bank.items.put("Coins", 31);
        FreshCombatConfig config = new FreshCombatConfig();
        List<Goal> goals = new net.runelite.client.plugins.microbot.mntn.builder.MntnBuilderScript()
                .buildGoals(config);
        AccountPlanner planner = new AccountPlanner(
                goals,
                Arrays.asList(
                        new CombatActivity(config),
                        new SupplyActivity(new SupplyRoutePolicy(true, true, true))
                )
        );

        Plan plan = planner.plan(context);

        assertNotNull(plan);
        assertEquals(ActivityType.COMBAT, plan.activity().type());
        assertTrue(plan.strategy().name().startsWith("Chickens_"));
    }

    private static AccountPlanner planner(Goal goal, Activity... activities) {
        return new AccountPlanner(Collections.singletonList(goal), Arrays.asList(activities));
    }

    private static class FreshCombatConfig implements MntnBuilderConfig {
        @Override
        public int fishingTarget() {
            return 0;
        }

        @Override
        public int cookingTarget() {
            return 0;
        }

        @Override
        public int woodcuttingTarget() {
            return 0;
        }

        @Override
        public int miningTarget() {
            return 0;
        }

        @Override
        public int smithingTarget() {
            return 0;
        }

        @Override
        public int attackTarget() {
            return 5;
        }

        @Override
        public int strengthTarget() {
            return 5;
        }

        @Override
        public int defenceTarget() {
            return 5;
        }

        @Override
        public int prayerTarget() {
            return 5;
        }

        @Override
        public boolean enableCooksAssistant() {
            return false;
        }

        @Override
        public boolean enableDoricsQuest() {
            return false;
        }
    }

    private static class TestContext extends AccountContext {
        private final TestInventoryView inventory = new TestInventoryView();
        private final TestBankView bank = new TestBankView();
        private final TestEquipmentView equipment = new TestEquipmentView();
        private final Map<Skill, Integer> realLevels = new EnumMap<>(Skill.class);
        private final Map<Quest, QuestState> quests = new EnumMap<>(Quest.class);
        private boolean membersWorld;
        private boolean snapshotForbidden;

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
            return true;
        }

        @Override
        public boolean isMembersWorld() {
            return membersWorld;
        }

        @Override
        public AccountSnapshot snapshot() {
            if (snapshotForbidden) {
                throw new AssertionError("F2P planning must not capture an account snapshot");
            }
            return super.snapshot();
        }

        @Override
        public int getRealLevel(Skill skill) {
            return realLevels.getOrDefault(skill, 1);
        }

        @Override
        public int getBoostedLevel(Skill skill) {
            return getRealLevel(skill);
        }

        @Override
        public QuestState getQuestState(Quest quest) {
            return quests.getOrDefault(quest, QuestState.NOT_STARTED);
        }

        @Override
        public WorldPoint getLocation() {
            return new WorldPoint(3200, 3200, 0);
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

        @Override
        public int getCount(int itemId) {
            return 0;
        }
    }

    private static class TestBankView extends BankView {
        private final Map<String, Integer> items = new HashMap<>();

        @Override
        public boolean isCachePopulated() {
            return true;
        }

        @Override
        public boolean hasItem(String itemName) {
            return getCount(itemName) > 0;
        }

        @Override
        public int getCount(String itemName) {
            return items.getOrDefault(itemName, 0);
        }

        @Override
        public int getCount(int itemId) {
            return 0;
        }
    }

    private static class TestEquipmentView extends EquipmentView {
        @Override
        public boolean hasItem(String itemName) {
            return false;
        }
    }

    private static class TestGoal implements Goal {
        private final Requirement requirement;
        private int completionChecks;

        private TestGoal(Requirement requirement) {
            this.requirement = requirement;
        }

        @Override
        public String name() {
            return "Test goal";
        }

        @Override
        public boolean isComplete(AccountContext context) {
            completionChecks++;
            return false;
        }

        @Override
        public List<Requirement> requirements(AccountContext context) {
            return Collections.singletonList(requirement);
        }

        @Override
        public double priority(AccountContext context) {
            return 10;
        }
    }

    private static class TestRequirement implements Requirement {
        private final ActivityType type;
        private final String description;
        private int satisfiedChecks;

        private TestRequirement(ActivityType type, String description) {
            this.type = type;
            this.description = description;
        }

        @Override
        public boolean isSatisfied(AccountContext context) {
            satisfiedChecks++;
            return false;
        }

        @Override
        public List<ActivityRequest> getWaysToSatisfy(AccountContext context) {
            return Collections.singletonList(new ActivityRequest(type, this));
        }

        @Override
        public double urgency(AccountContext context) {
            return 0;
        }

        @Override
        public String description() {
            return description;
        }
    }

    private static class MultiRequestRequirement implements Requirement {
        private final String description;
        private final List<ActivityType> types;

        private MultiRequestRequirement(String description, ActivityType... types) {
            this.description = description;
            this.types = Arrays.asList(types);
        }

        @Override
        public boolean isSatisfied(AccountContext context) {
            return false;
        }

        @Override
        public List<ActivityRequest> getWaysToSatisfy(AccountContext context) {
            java.util.ArrayList<ActivityRequest> requests = new java.util.ArrayList<>();
            for (ActivityType type : types) {
                requests.add(new ActivityRequest(type, this));
            }
            return requests;
        }

        @Override
        public double urgency(AccountContext context) {
            return 0;
        }

        @Override
        public String description() {
            return description;
        }
    }

    private static class TestActivity implements Activity {
        private final ActivityType type;
        private final List<Strategy> strategies;
        private int providerChecks;

        private TestActivity(ActivityType type, Strategy... strategies) {
            this.type = type;
            this.strategies = Arrays.asList(strategies);
        }

        @Override
        public ActivityType type() {
            return type;
        }

        @Override
        public boolean canProvide(ActivityRequest request, AccountContext context) {
            providerChecks++;
            return request.type() == type;
        }

        @Override
        public List<Strategy> getStrategies(AccountContext context, ActivityRequest request) {
            return strategies;
        }
    }

    private static class PayloadActivity extends TestActivity {
        private final Class<?> payloadType;

        private PayloadActivity(ActivityType type, Class<?> payloadType, Strategy... strategies) {
            super(type, strategies);
            this.payloadType = payloadType;
        }

        @Override
        public boolean canProvide(ActivityRequest request, AccountContext context) {
            return super.canProvide(request, context) && payloadType.isInstance(request.payload());
        }
    }

    private static class TestStrategy implements Strategy {
        private final String name;
        private final ContentAccess contentAccess;
        private final double score;
        private List<Requirement> requirements = Collections.emptyList();
        private int executionChecks;
        private int scoreChecks;

        private TestStrategy(String name, ContentAccess contentAccess, double score) {
            this.name = name;
            this.contentAccess = contentAccess;
            this.score = score;
        }

        private TestStrategy withRequirements(Requirement... requirements) {
            this.requirements = Arrays.asList(requirements);
            return this;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public ContentAccess contentAccess() {
            return contentAccess;
        }

        @Override
        public List<Requirement> requirements(AccountContext context) {
            return requirements;
        }

        @Override
        public boolean canExecute(AccountContext context) {
            executionChecks++;
            return true;
        }

        @Override
        public double score(AccountContext context) {
            scoreChecks++;
            return score;
        }

        @Override
        public Task createTask(AccountContext context) {
            return NOOP_TASK;
        }

        @Override
        public Duration commitmentDuration(AccountContext context) {
            return Duration.ofMinutes(5);
        }
    }

    private static final Task NOOP_TASK = new Task() {
        @Override
        public TaskStatus tick(AccountContext context) {
            return TaskStatus.COMPLETE;
        }

        @Override
        public boolean needsReplan(AccountContext context) {
            return false;
        }
    };
}
