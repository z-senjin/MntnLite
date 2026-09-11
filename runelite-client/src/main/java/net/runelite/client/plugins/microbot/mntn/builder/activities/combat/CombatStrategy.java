package net.runelite.client.plugins.microbot.mntn.builder.activities.combat;

import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.mntn.builder.activities.Strategy;
import net.runelite.client.plugins.microbot.mntn.builder.core.AccountContext;
import net.runelite.client.plugins.microbot.mntn.builder.core.requirements.ItemRequirement;
import net.runelite.client.plugins.microbot.mntn.builder.core.requirements.Requirement;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.Task;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.combat.CombatTask;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CombatStrategy implements Strategy {

    public static final String STARTER_WEAPON = "Bronze scimitar";
    public static final String STARTER_FOOD = "Shrimps";
    public static final int MINIMUM_COMBAT_FOOD_RESERVE = 10;

    public static final String[] COOKED_FOODS = {
            "Cooked karambwan",
            "Shark",
            "Swordfish",
            "Lobster",
            "Bass",
            "Tuna",
            "Salmon",
            "Trout",
            "Pike",
            "Cod",
            "Herring",
            "Sardine",
            "Bread",
            "Cooked meat",
            "Cooked chicken",
            "Shrimps",
            "Anchovies"
    };

    public enum Monster {
        CHICKENS(
                "Chickens",
                1,
                10,
                0,
                new String[]{"Chicken", "Rooster"},
                new WorldPoint(3230, 3297, 0),
                new String[]{"Bones", "Feather"}
        ),
        GOBLINS(
                "Goblins",
                5,
                20,
                4,
                new String[]{"Goblin"},
                new WorldPoint(3255, 3235, 0),
                new String[]{"Bones", "Coins"}
        ),
        COWS(
                "Cows",
                10,
                60,
                8,
                new String[]{"Cow", "Cow calf"},
                new WorldPoint(3256, 3266, 0),
                new String[]{"Bones", "Cowhide"}
        ),
        AL_KHARID_WARRIORS(
                "Al Kharid Warriors",
                20,
                40,
                10,
                new String[]{"Al Kharid warrior", "Al-Kharid warrior"},
                new WorldPoint(3295, 3170, 0),
                new String[]{"Bones", "Coins"}
        );

        public final String displayName;
        public final int minCombatLevel;
        public final int maxRecommendedCombatLevel;
        public final int recommendedFood;
        public final String[] npcNames;
        public final WorldPoint location;
        public final String[] lootNames;

        Monster(String displayName, int minCombatLevel, int maxRecommendedCombatLevel,
                int recommendedFood, String[] npcNames, WorldPoint location, String[] lootNames) {
            this.displayName = displayName;
            this.minCombatLevel = minCombatLevel;
            this.maxRecommendedCombatLevel = maxRecommendedCombatLevel;
            this.recommendedFood = recommendedFood;
            this.npcNames = npcNames;
            this.location = location;
            this.lootNames = lootNames;
        }
    }

    private final Monster monster;
    private final Skill targetSkill;
    private final int targetLevel;
    private final int prayerTarget;

    public CombatStrategy(Monster monster, Skill targetSkill, int targetLevel, int prayerTarget) {
        this.monster = monster;
        this.targetSkill = targetSkill;
        this.targetLevel = targetLevel;
        this.prayerTarget = prayerTarget;
    }

    @Override
    public String name() {
        return monster.displayName + "_" + targetSkill.getName();
    }

    @Override
    public boolean canExecute(AccountContext context) {
        int combatLevel = getCombatLevel(context);
        if (combatLevel < monster.minCombatLevel) {
            return false;
        }

        // Chickens are the fresh-F2P bootstrap fallback and can be fought unarmed.
        if (CombatGear.findBestWeapon(context, true) == null && !canFightUnarmed(monster)) {
            return false;
        }

        if (requiresFoodReserve(monster) && !hasMinimumFoodReserveInBank(context)) {
            return false;
        }

        return context.getRealLevel(targetSkill) < targetLevel;
    }

    @Override
    public List<Requirement> requirements(AccountContext context) {
        // Combat never creates a food-supply detour. It either starts with a
        // meaningful banked reserve or the planner selects another activity.
        if (requiresFoodReserve(monster) && !hasMinimumFoodReserveInBank(context)) {
            return Collections.emptyList();
        }

        CombatGear.GearItem weapon = selectedWeapon(context);
        if (weapon == null && !canFightUnarmed(monster)) {
            return Collections.singletonList(new ItemRequirement(STARTER_WEAPON, 1));
        }

        return Collections.emptyList();
    }

    @Override
    public double score(AccountContext context) {
        int combatLevel = getCombatLevel(context);
        if (combatLevel < monster.minCombatLevel) {
            return -1000;
        }

        if (requiresFoodReserve(monster) && !hasMinimumFoodReserveInBank(context)) {
            return -1000;
        }

        CombatGear.GearItem weapon = CombatGear.findBestWeapon(context, true);
        if (weapon == null && !canFightUnarmed(monster)) {
            return -1000;
        }

        double score = weapon != null ? 50.0 : 25.0;

        // Weapon tier bonus
        if (weapon != null) {
            score += weapon.requiredLevel;
        }

        // Weapon equipped bonus
        if (weapon != null && context.equipment().hasItem(weapon.name)) {
            score += 20;
        } else if (weapon != null && context.inventory().hasItem(weapon.name)) {
            score += 10;
        }

        // Tier appropriateness bonus
        if (monster == Monster.CHICKENS) {
            if (combatLevel <= 10) {
                score += 30;
            } else {
                score -= 10;
            }
        } else if (monster == Monster.GOBLINS) {
            if (combatLevel >= 8 && combatLevel <= 20) {
                score += 30;
            } else if (combatLevel < 8) {
                score += 10;
            }
        } else if (monster == Monster.COWS) {
            if (combatLevel >= 15) {
                score += 35;
            } else {
                score += 15;
            }
        } else if (monster == Monster.AL_KHARID_WARRIORS) {
            if (combatLevel <= 35) {
                score += 35;
            } else {
                score += 10;
            }
        }

        // Convenience bonus for food on hand
        int foodCount = inventoryFoodCount(context);
        if (foodCount >= recommendedFoodCount(context)) {
            score += 15;
        } else if (foodCount > 0) {
            score += 8;
        } else if (hasFoodInBank(context)) {
            score += 5;
        }

        // Prayer bonus if prayer target set and monster drops bones
        if (prayerTarget > 0 && context.getRealLevel(Skill.PRAYER) < prayerTarget) {
            score += 10;
        }

        return score;
    }

    @Override
    public WorldPoint preferredLocation(AccountContext context) {
        return monster.location;
    }

    @Override
    public int estimatedXpPerHour(AccountContext context) {
        return Math.max(1000, getCombatLevel(context) * 600);
    }

    @Override
    public double safetyScore(AccountContext context) {
        int combatLevel = getCombatLevel(context);
        if (combatLevel >= monster.maxRecommendedCombatLevel) {
            return 10;
        }
        if (combatLevel >= monster.minCombatLevel + 5) {
            return 5;
        }
        return -10;
    }

    @Override
    public Task createTask(AccountContext context) {
        return new CombatTask(monster, targetSkill, targetLevel, prayerTarget);
    }

    @Override
    public Duration commitmentDuration(AccountContext context) {
        int minutes = Rs2Random.between(15, 35);
        return Duration.ofMinutes(minutes);
    }

    public static boolean hasFood(AccountContext context) {
        if (inventoryFoodCount(context) > 0) {
            return true;
        }
        return hasFoodInBank(context);
    }

    public static int inventoryFoodCount(AccountContext context) {
        int count = 0;
        for (String food : COOKED_FOODS) {
            count += context.inventory().getCount(food);
        }
        return count;
    }

    public static int recommendedFoodCount(Monster monster, AccountContext context) {
        int combatLevel = getCombatLevel(context);
        if (!requiresFoodReserve(monster)) {
            return 0;
        }
        if (combatLevel >= monster.minCombatLevel + 10) {
            return Math.max(2, monster.recommendedFood / 2);
        }
        return monster.recommendedFood;
    }

    public static boolean hasFoodInBank(AccountContext context) {
        return foodCountInBank(context) > 0;
    }

    public static boolean hasMinimumFoodReserveInBank(AccountContext context) {
        return foodCountInBank(context) >= MINIMUM_COMBAT_FOOD_RESERVE;
    }

    public static int foodCountInBank(AccountContext context) {
        int count = 0;
        for (String food : COOKED_FOODS) {
            count += context.bank().getCount(food);
        }
        return count;
    }

    public static List<String> foodNamesForCombatLoadout(AccountContext context) {
        List<String> foods = new ArrayList<>();
        for (String food : COOKED_FOODS) {
            if (context.bank().getCount(food) > 0 || context.inventory().getCount(food) > 0) {
                foods.add(food);
            }
        }
        return foods;
    }

    public static String findBestFoodInBank(AccountContext context) {
        for (String food : COOKED_FOODS) {
            if (context.bank().hasItem(food)) {
                return food;
            }
        }
        return null;
    }

    public static String findBestAvailableFood(AccountContext context) {
        for (String food : COOKED_FOODS) {
            if (context.inventory().hasItem(food) || context.bank().hasItem(food)) {
                return food;
            }
        }
        return null;
    }

    public static boolean canFightUnarmed(Monster monster) {
        return monster == Monster.CHICKENS;
    }

    public static boolean requiresFoodReserve(Monster monster) {
        return monster.recommendedFood > 0;
    }

    public int recommendedFoodCount(AccountContext context) {
        return recommendedFoodCount(monster, context);
    }

    public static Skill selectCombatStyleSkill(AccountContext context, Skill targetSkill) {
        if (targetSkill != Skill.PRAYER) {
            return targetSkill;
        }

        Skill selected = Skill.STRENGTH;
        int selectedLevel = context.getRealLevel(selected);
        for (Skill candidate : new Skill[]{Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE}) {
            int level = context.getRealLevel(candidate);
            if (level < selectedLevel) {
                selected = candidate;
                selectedLevel = level;
            }
        }
        return selected;
    }

    private CombatGear.GearItem selectedWeapon(AccountContext context) {
        return CombatGear.findBestWeapon(context, true);
    }

    private static int getCombatLevel(AccountContext context) {
        return context.getCombatLevel();
    }

    public Monster getMonster() {
        return monster;
    }

    public Skill getTargetSkill() {
        return targetSkill;
    }
}
