package net.runelite.client.plugins.microbot.mntn.builder.activities.combat;

import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.mntn.builder.activities.Strategy;
import net.runelite.client.plugins.microbot.mntn.builder.core.AccountContext;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.Task;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.combat.CombatTask;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;

import java.time.Duration;

public class CombatStrategy implements Strategy {

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
                new String[]{"Chicken", "Rooster"},
                new WorldPoint(3230, 3297, 0),
                new String[]{"Bones", "Feather"}
        ),
        GOBLINS(
                "Goblins",
                5,
                20,
                new String[]{"Goblin"},
                new WorldPoint(3255, 3235, 0),
                new String[]{"Bones", "Coins"}
        ),
        COWS(
                "Cows",
                10,
                60,
                new String[]{"Cow", "Cow calf"},
                new WorldPoint(3256, 3266, 0),
                new String[]{"Bones", "Cowhide"}
        );

        public final String displayName;
        public final int minCombatLevel;
        public final int maxRecommendedCombatLevel;
        public final String[] npcNames;
        public final WorldPoint location;
        public final String[] lootNames;

        Monster(String displayName, int minCombatLevel, int maxRecommendedCombatLevel,
                String[] npcNames, WorldPoint location, String[] lootNames) {
            this.displayName = displayName;
            this.minCombatLevel = minCombatLevel;
            this.maxRecommendedCombatLevel = maxRecommendedCombatLevel;
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
        int combatLevel = getCombatLevel();
        if (combatLevel < monster.minCombatLevel) {
            return false;
        }

        // Must have a usable weapon in equipment, inventory, or bank
        if (CombatGear.findBestWeapon(context, true) == null) {
            return false;
        }

        // Must have food in inventory or in bank
        if (!hasFood(context)) {
            return false;
        }

        return context.getRealLevel(targetSkill) < targetLevel;
    }

    @Override
    public double score(AccountContext context) {
        int combatLevel = getCombatLevel();
        if (combatLevel < monster.minCombatLevel) {
            return -1000;
        }

        if (!hasFood(context)) {
            return -1000;
        }

        CombatGear.GearItem weapon = CombatGear.findBestWeapon(context, true);
        if (weapon == null) {
            return -1000;
        }

        double score = 50.0;

        // Weapon tier bonus
        score += weapon.requiredLevel;

        // Weapon equipped bonus
        if (context.equipment().hasItem(weapon.name)) {
            score += 20;
        } else if (context.inventory().hasItem(weapon.name)) {
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
        }

        // Convenience bonus for food on hand
        if (!Rs2Inventory.getInventoryFood().isEmpty()) {
            score += 15;
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
    public Task createTask(AccountContext context) {
        return new CombatTask(monster, targetSkill, targetLevel, prayerTarget);
    }

    @Override
    public Duration commitmentDuration(AccountContext context) {
        int minutes = Rs2Random.between(15, 35);
        return Duration.ofMinutes(minutes);
    }

    public static boolean hasFood(AccountContext context) {
        if (!Rs2Inventory.getInventoryFood().isEmpty()) {
            return true;
        }
        return hasFoodInBank(context);
    }

    public static boolean hasFoodInBank(AccountContext context) {
        for (String food : COOKED_FOODS) {
            if (context.bank().hasItem(food)) {
                return true;
            }
        }
        return false;
    }

    public static String findBestFoodInBank(AccountContext context) {
        for (String food : COOKED_FOODS) {
            if (context.bank().hasItem(food)) {
                return food;
            }
        }
        return null;
    }

    private int getCombatLevel() {
        if (Microbot.getClient().getLocalPlayer() != null) {
            return Microbot.getClient().getLocalPlayer().getCombatLevel();
        }
        return 3;
    }

    public Monster getMonster() {
        return monster;
    }

    public Skill getTargetSkill() {
        return targetSkill;
    }
}
