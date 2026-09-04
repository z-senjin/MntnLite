package net.runelite.client.plugins.microbot.mntn.builder.tasks.combat;

import net.runelite.api.Skill;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.api.tileitem.models.Rs2TileItemModel;
import net.runelite.client.plugins.microbot.mntn.builder.activities.combat.CombatGear;
import net.runelite.client.plugins.microbot.mntn.builder.activities.combat.CombatStrategy;
import net.runelite.client.plugins.microbot.mntn.builder.core.AccountContext;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.Task;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.TaskStatus;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.banking.BankingTask;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.ArrayList;
import java.util.List;

import static net.runelite.client.plugins.microbot.util.Global.sleep;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntilTrue;

public class CombatTask implements Task {

    private enum Phase {
        CHECK_STATUS,
        BANKING,
        WALK_TO_MONSTER,
        FIGHTING
    }

    private final CombatStrategy.Monster monster;
    private final Skill targetSkill;
    private final int targetLevel;
    private final int prayerTarget;

    private Phase phase = Phase.CHECK_STATUS;
    private BankingTask bankingTask;
    private boolean styleConfigured = false;

    public CombatTask(CombatStrategy.Monster monster, Skill targetSkill, int targetLevel, int prayerTarget) {
        this.monster = monster;
        this.targetSkill = targetSkill;
        this.targetLevel = targetLevel;
        this.prayerTarget = prayerTarget;
    }

    private void debugLog(AccountContext context, String message) {
        if (context.isDebugLogging()) {
            Microbot.log("[MntnBuilder][CombatTask][DEBUG] " + message);
        }
    }

    @Override
    public TaskStatus tick(AccountContext context) {
        debugLog(context, "tick: phase=" + phase + ", monster=" + monster.displayName + ", targetSkill=" + targetSkill.getName());

        if (!context.isLoggedIn()) {
            debugLog(context, "Not logged in, returning BLOCKED");
            return TaskStatus.BLOCKED;
        }

        switch (phase) {
            case CHECK_STATUS:
                return handleCheckStatus(context);
            case BANKING:
                return handleBank(context);
            case WALK_TO_MONSTER:
                return handleWalk(context);
            case FIGHTING:
                return handleFight(context);
            default:
                debugLog(context, "Unknown phase, returning RUNNING");
                return TaskStatus.RUNNING;
        }
    }

    private TaskStatus handleCheckStatus(AccountContext context) {
        debugLog(context, "handleCheckStatus: health=" + Rs2Player.getHealthPercentage() + "%, styleConfigured=" + styleConfigured);

        // Eat if HP is low
        if (Rs2Player.getHealthPercentage() <= 50) {
            debugLog(context, "Health <= 50%, eating");
            Rs2Player.eatAt(50);
        }

        // Equip any gear currently in inventory
        equipAvailableGear(context);

        // Verify weapon is equipped
        CombatGear.GearItem bestEquippedWeapon = CombatGear.findBestWeapon(context, false);
        if (bestEquippedWeapon == null || !context.equipment().hasItem(bestEquippedWeapon.name)) {
            debugLog(context, "No weapon equipped");
            // Check if bank has a weapon
            if (CombatGear.findBestWeapon(context, true) == null) {
                // No weapon available anywhere - reroll task
                debugLog(context, "No weapon available anywhere, returning REPLAN");
                return TaskStatus.REPLAN;
            }
            debugLog(context, "Weapon in bank, switching to BANKING");
            phase = Phase.BANKING;
            return TaskStatus.RUNNING;
        }

        // Check if we need food
        if (Rs2Inventory.getInventoryFood().isEmpty()) {
            debugLog(context, "No food in inventory");
            if (!CombatStrategy.hasFoodInBank(context)) {
                // Out of food completely - reroll task
                debugLog(context, "No food in bank, returning REPLAN");
                return TaskStatus.REPLAN;
            }
            debugLog(context, "Food in bank, switching to BANKING");
            phase = Phase.BANKING;
            return TaskStatus.RUNNING;
        }

        // Bury any stray bones in inventory if training prayer
        if (shouldBuryBones(context) && Rs2Inventory.contains("Bones")) {
            debugLog(context, "Burying bones for prayer training");
            Rs2Inventory.interact("Bones", "Bury");
            sleep(600, 900);
        }

        // Configure attack style if not done yet
        if (!styleConfigured) {
            debugLog(context, "Configuring combat style for " + targetSkill.getName());
            configureCombatStyle(context);
            styleConfigured = true;
        }

        debugLog(context, "Switching to WALK_TO_MONSTER");
        phase = Phase.WALK_TO_MONSTER;
        return TaskStatus.RUNNING;
    }

    private TaskStatus handleBank(AccountContext context) {
        debugLog(context, "handleBank: bankingTask=" + (bankingTask != null ? bankingTask.describe() : "null"));

        if (bankingTask == null) {
            CombatGear.GearItem bestWeapon = CombatGear.findBestWeapon(context, true);
            if (bestWeapon == null) {
                // No weapon available anywhere - reroll task
                debugLog(context, "No weapon available anywhere, returning REPLAN");
                return TaskStatus.REPLAN;
            }

            String bestFood = CombatStrategy.findBestFoodInBank(context);
            if (bestFood == null && Rs2Inventory.getInventoryFood().isEmpty()) {
                // No food in bank to withdraw - reroll task
                debugLog(context, "No food in bank and inventory empty, returning REPLAN");
                return TaskStatus.REPLAN;
            }

            List<String> gearUpgrades = CombatGear.getBankGearUpgrades(context);
            List<BankingTask.ItemWithdrawal> withdrawals = new ArrayList<>();
            for (String gear : gearUpgrades) {
                withdrawals.add(new BankingTask.ItemWithdrawal(gear, 1));
            }
            if (bestFood != null) {
                withdrawals.add(new BankingTask.ItemWithdrawal(bestFood, 12));
            }

            List<String> keepItems = new ArrayList<>();
            for (String f : CombatStrategy.COOKED_FOODS) keepItems.add(f);
            keepItems.addAll(CombatGear.getAllKnownGearNames());

            debugLog(context, "Creating DEPOSIT_ALL_AND_WITHDRAW banking task with " + withdrawals.size() + " withdrawals");
            bankingTask = new BankingTask(
                    BankingTask.Mode.DEPOSIT_ALL_AND_WITHDRAW,
                    keepItems.toArray(new String[0]),
                    withdrawals.toArray(new BankingTask.ItemWithdrawal[0])
            );
        }

        TaskStatus bankStatus = bankingTask.tick(context);

        if (bankStatus == TaskStatus.COMPLETE) {
            debugLog(context, "Banking complete");
            bankingTask = null;

            equipAvailableGear(context);

            CombatGear.GearItem equippedWeapon = CombatGear.findBestWeapon(context, false);
            if (equippedWeapon == null || !context.equipment().hasItem(equippedWeapon.name)) {
                // Failed to acquire/equip a weapon
                debugLog(context, "Failed to acquire/equip weapon, returning REPLAN");
                return TaskStatus.REPLAN;
            }

            if (Rs2Inventory.getInventoryFood().isEmpty()) {
                // Failed to get food from bank
                debugLog(context, "Failed to get food from bank, returning REPLAN");
                return TaskStatus.REPLAN;
            }

            debugLog(context, "Switching to CHECK_STATUS");
            phase = Phase.CHECK_STATUS;
            return TaskStatus.RUNNING;
        }

        if (bankStatus == TaskStatus.FAILED || bankStatus == TaskStatus.REPLAN) {
            debugLog(context, "Banking failed/replan: " + bankStatus);
            bankingTask = null;
            return TaskStatus.REPLAN;
        }

        return TaskStatus.RUNNING;
    }

    private TaskStatus handleWalk(AccountContext context) {
        debugLog(context, "handleWalk: hasFood=" + !Rs2Inventory.getInventoryFood().isEmpty() + ", health=" + Rs2Player.getHealthPercentage() + "%, nearMonster=" + context.isNear(monster.location, 12));

        if (Rs2Inventory.getInventoryFood().isEmpty() && Rs2Player.getHealthPercentage() <= 50) {
            debugLog(context, "No food and low health, switching to BANKING");
            phase = Phase.BANKING;
            return TaskStatus.RUNNING;
        }

        if (context.isNear(monster.location, 12)) {
            debugLog(context, "Near monster location, switching to FIGHTING");
            phase = Phase.FIGHTING;
            return TaskStatus.RUNNING;
        }

        debugLog(context, "Walking to monster location: " + monster.location);
        Rs2Walker.walkTo(monster.location);
        return TaskStatus.RUNNING;
    }

    private boolean isAlive(Rs2NpcModel npc) {
        if (npc == null || npc.isDead()) {
            return false;
        }
        return !(npc.getHealthScale() > 0 && npc.getHealthRatio() == 0);
    }

    private TaskStatus handleFight(AccountContext context) {
        // Health check: eat if health <= 50%
        if (Rs2Player.getHealthPercentage() <= 50) {
            debugLog(context, "Health <= 50%, eating");
            Rs2Player.eatAt(50);
            if (Rs2Inventory.getInventoryFood().isEmpty() && Rs2Player.getHealthPercentage() <= 50) {
                // Low health and no food left: retreat to bank
                debugLog(context, "Low health and no food, switching to BANKING");
                phase = Phase.BANKING;
                return TaskStatus.RUNNING;
            }
        }

        // Check if currently actively in combat - don't interrupt fighting to wander/loot
        if (Rs2Combat.inCombat() || (Rs2Player.isInteracting() && Rs2Player.isInCombat())) {
            if (Rs2Player.getHealthPercentage() <= 50) {
                Rs2Player.eatAt(50);
            }
            debugLog(context, "In combat, waiting");
            return TaskStatus.RUNNING;
        }

        // Bury bones in inventory if prayer training
        if (shouldBuryBones(context) && Rs2Inventory.contains("Bones")) {
            debugLog(context, "Burying bones for prayer");
            Rs2Inventory.interact("Bones", "Bury");
            sleep(600, 900);
            return TaskStatus.RUNNING;
        }

        // If inventory is full, bank loot
        if (Rs2Inventory.isFull() && !Rs2Inventory.contains("Bones")) {
            debugLog(context, "Inventory full, switching to BANKING");
            phase = Phase.BANKING;
            return TaskStatus.RUNNING;
        }

        // Loot drops within 8 tiles - ONLY items owned by this player
        if (!Rs2Inventory.isFull()) {
            Rs2TileItemModel loot = Microbot.getRs2TileItemCache().query()
                    .withNames(monster.lootNames)
                    .within(8)
                    .where(Rs2TileItemModel::isOwned)
                    .nearest();

            if (loot != null) {
                debugLog(context, "Picking up loot: " + loot.getName());
                loot.pickup();
                sleepUntilTrue(() -> !Rs2Player.isMoving(), 200, 3000);

                if (shouldBuryBones(context) && Rs2Inventory.contains("Bones")) {
                    Rs2Inventory.interact("Bones", "Bury");
                    sleep(600, 900);
                }
                return TaskStatus.RUNNING;
            }
        }

        // Find monster target:
        // 1. Prioritize monster attacking player
        Rs2NpcModel target = Microbot.getRs2NpcCache().query()
                .withNames(monster.npcNames)
                .within(15)
                .where(Rs2NpcModel::isInteractingWithPlayer)
                .where(this::isAlive)
                .nearest();

        // 2. Otherwise find nearest unattacked monster with line of sight
        if (target == null) {
            target = Microbot.getRs2NpcCache().query()
                    .withNames(monster.npcNames)
                    .within(15)
                    .where(this::isAlive)
                    .where(npc -> !npc.isInteracting() || npc.isInteractingWithPlayer() || Rs2Player.isInMulti())
                    .where(Rs2NpcModel::hasLineOfSight)
                    .nearest();
        }

        // 3. Fallback: nearest unattacked monster without strict line-of-sight requirement (interact walks to it)
        if (target == null) {
            target = Microbot.getRs2NpcCache().query()
                    .withNames(monster.npcNames)
                    .within(15)
                    .where(this::isAlive)
                    .where(npc -> !npc.isInteracting() || npc.isInteractingWithPlayer() || Rs2Player.isInMulti())
                    .nearest();
        }

        if (target != null) {
            debugLog(context, "Attacking target: " + target.getName());
            target.click("Attack");
            sleep(600, 1000);
        } else {
            // If no monster is nearby, re-center on the spawn area
            if (!context.isNear(monster.location, 10)) {
                debugLog(context, "No target found, not near spawn, switching to WALK_TO_MONSTER");
                phase = Phase.WALK_TO_MONSTER;
            }
        }

        return TaskStatus.RUNNING;
    }

    private void equipAvailableGear(AccountContext context) {
        CombatGear.GearItem bestWeapon = CombatGear.findBestWeapon(context, false);
        if (bestWeapon != null && !context.equipment().hasItem(bestWeapon.name) && context.inventory().hasItem(bestWeapon.name)) {
            debugLog(context, "Equipping weapon: " + bestWeapon.name);
            Rs2Inventory.wield(bestWeapon.name);
            sleep(300, 600);
        }

        boolean usingTwoHanded = (bestWeapon != null && CombatGear.isTwoHanded(bestWeapon));

        if (!usingTwoHanded) {
            CombatGear.GearItem bestShield = CombatGear.findBestArmor(context, CombatGear.SHIELDS, false);
            if (bestShield != null && !context.equipment().hasItem(bestShield.name) && context.inventory().hasItem(bestShield.name)) {
                debugLog(context, "Equipping shield: " + bestShield.name);
                Rs2Inventory.wield(bestShield.name);
                sleep(300, 600);
            }
        }

        CombatGear.GearItem bestHelm = CombatGear.findBestArmor(context, CombatGear.HELMETS, false);
        if (bestHelm != null && !context.equipment().hasItem(bestHelm.name) && context.inventory().hasItem(bestHelm.name)) {
            debugLog(context, "Equipping helm: " + bestHelm.name);
            Rs2Inventory.wield(bestHelm.name);
            sleep(300, 600);
        }

        CombatGear.GearItem bestBody = CombatGear.findBestArmor(context, CombatGear.BODIES, false);
        if (bestBody != null && !context.equipment().hasItem(bestBody.name) && context.inventory().hasItem(bestBody.name)) {
            debugLog(context, "Equipping body: " + bestBody.name);
            Rs2Inventory.wield(bestBody.name);
            sleep(300, 600);
        }

        CombatGear.GearItem bestLegs = CombatGear.findBestArmor(context, CombatGear.LEGS, false);
        if (bestLegs != null && !context.equipment().hasItem(bestLegs.name) && context.inventory().hasItem(bestLegs.name)) {
            debugLog(context, "Equipping legs: " + bestLegs.name);
            Rs2Inventory.wield(bestLegs.name);
            sleep(300, 600);
        }
    }

    private void configureCombatStyle(AccountContext context) {
        debugLog(context, "Configuring combat style for " + targetSkill.getName());
        Rs2Tab.switchToCombatOptionsTab();
        sleep(200, 400);

        Rs2Combat.setAutoRetaliate(true);

        WidgetInfo styleWidget;
        switch (targetSkill) {
            case ATTACK:
                styleWidget = WidgetInfo.COMBAT_STYLE_ONE;
                break;
            case DEFENCE:
                styleWidget = WidgetInfo.COMBAT_STYLE_FOUR;
                break;
            case STRENGTH:
            default:
                styleWidget = WidgetInfo.COMBAT_STYLE_TWO;
                break;
        }

        Rs2Combat.setAttackStyle(styleWidget);
        sleep(200, 400);

        Rs2Tab.switchToInventoryTab();
    }

    private boolean shouldBuryBones(AccountContext context) {
        return prayerTarget > 0 && context.getRealLevel(Skill.PRAYER) < prayerTarget;
    }

    @Override
    public boolean needsReplan(AccountContext context) {
        boolean levelCheck = context.getRealLevel(targetSkill) >= targetLevel;
        if (levelCheck) {
            debugLog(context, "needsReplan: target level reached (current=" + context.getRealLevel(targetSkill) + ", target=" + targetLevel + ")");
        }
        return levelCheck;
    }

    @Override
    public String describe() {
        return "Combat (" + monster.displayName + " - " + targetSkill.getName() + ") - " + phase;
    }
}
