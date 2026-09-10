package net.runelite.client.plugins.microbot.mntn.builder.tasks.combat;

import net.runelite.api.EnumComposition;
import net.runelite.api.EnumID;
import net.runelite.api.ParamID;
import net.runelite.api.Skill;
import net.runelite.api.StructComposition;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.api.tileitem.models.Rs2TileItemModel;
import net.runelite.client.plugins.microbot.mntn.builder.activities.combat.CombatGear;
import net.runelite.client.plugins.microbot.mntn.builder.activities.combat.CombatStrategy;
import net.runelite.client.plugins.microbot.mntn.builder.core.AccountContext;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.Task;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.TaskActionGuard;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.TaskStatus;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.TaskStopReason;
import net.runelite.client.plugins.microbot.mntn.builder.tasks.banking.BankingTask;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.ArrayList;
import java.util.List;

public class CombatTask implements Task {

    private static final int MAX_STYLE_ATTEMPTS = 3;
    private static final int MAX_TARGET_SEARCH_FAILURES = 25;
    private static final int MINIMUM_LOOT_STACK_VALUE = 100;

    private enum Phase {
        CHECK_STATUS,
        BANKING,
        EQUIPPING,
        WALK_TO_MONSTER,
        FIGHTING
    }

    private final CombatStrategy.Monster monster;
    private final Skill targetSkill;
    private final int targetLevel;
    private final int prayerTarget;
    private final String requiredLootName;

    private Phase phase = Phase.BANKING;
    private BankingTask bankingTask;
    private final List<String> plannedLoadout = new ArrayList<>();
    private int equipmentIndex;
    private boolean styleConfigured = false;
    private TaskStopReason lastStopReason = TaskStopReason.NONE;
    private int styleAttempts;
    private long lastStyleAttemptAtMs;
    private int targetSearchFailures;
    private final TaskActionGuard walkGuard = new TaskActionGuard(10, 30_000, 800);
    private final TaskActionGuard attackGuard = new TaskActionGuard(5, 12_000, 700);
    private final TaskActionGuard equipmentGuard = new TaskActionGuard(4, 10_000, 800);

    public CombatTask(CombatStrategy.Monster monster, Skill targetSkill, int targetLevel, int prayerTarget) {
        this(monster, targetSkill, targetLevel, prayerTarget, null);
    }

    /**
     * @param requiredLootName the active money-making drop, which may be collected below the normal value threshold
     */
    public CombatTask(CombatStrategy.Monster monster, Skill targetSkill, int targetLevel, int prayerTarget,
                      String requiredLootName) {
        this.monster = monster;
        this.targetSkill = targetSkill;
        this.targetLevel = targetLevel;
        this.prayerTarget = prayerTarget;
        this.requiredLootName = requiredLootName;
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
            return stop(TaskStatus.BLOCKED, TaskStopReason.NOT_LOGGED_IN);
        }

        // A completed prior withdrawal can leave the bank open. Combat widgets and
        // movement are not reliable until that interface is dismissed.
        if (phase != Phase.BANKING && Rs2Bank.isOpen()) {
            Rs2Bank.closeBank();
            return TaskStatus.RUNNING;
        }

        switch (phase) {
            case CHECK_STATUS:
                return handleCheckStatus(context);
            case BANKING:
                return handleBank(context);
            case EQUIPPING:
                return handleEquip(context);
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

        if (!isPlannedLoadoutEquipped(context)) {
            phase = Phase.EQUIPPING;
            return TaskStatus.RUNNING;
        }

        // Verify weapon is equipped
        CombatGear.GearItem bestEquippedWeapon = CombatGear.findBestWeapon(context, false);
        if (bestEquippedWeapon == null || !context.equipment().hasItem(bestEquippedWeapon.name)) {
            debugLog(context, "No weapon equipped");
            // Check if bank has a weapon
            if (CombatGear.findBestWeapon(context, true) == null) {
                if (CombatStrategy.canFightUnarmed(monster)) {
                    debugLog(context, "No weapon available, continuing unarmed against bootstrap monster");
                } else {
                // No weapon available anywhere - reroll task
                    debugLog(context, "No weapon available anywhere, returning REPLAN");
                    return stop(TaskStatus.REPLAN, TaskStopReason.EQUIPMENT_MISSING);
                }
            } else {
                debugLog(context, "Weapon in bank, switching to BANKING");
                phase = Phase.BANKING;
                return TaskStatus.RUNNING;
            }
        }

        int foodTarget = CombatStrategy.recommendedFoodCount(monster, context);
        if (CombatStrategy.inventoryFoodCount(context) < foodTarget) {
            debugLog(context, "Food below target: " + CombatStrategy.inventoryFoodCount(context) + "/" + foodTarget);
            if (!CombatStrategy.hasFoodInBank(context)) {
                // Out of food completely - reroll task
                debugLog(context, "No food in bank, returning REPLAN");
                return stop(TaskStatus.REPLAN, TaskStopReason.MISSING_SUPPLIES);
            }
            debugLog(context, "Food in bank, switching to BANKING");
            phase = Phase.BANKING;
            return TaskStatus.RUNNING;
        }

        // Bury any stray bones in inventory if training prayer
        if (shouldBuryBones(context) && Rs2Inventory.contains("Bones")) {
            debugLog(context, "Burying bones for prayer training");
            Rs2Inventory.interact("Bones", "Bury");
            return TaskStatus.RUNNING;
        }

        // Configure attack style if not done yet
        if (!styleConfigured) {
            Skill styleSkill = CombatStrategy.selectCombatStyleSkill(context, targetSkill);
            debugLog(context, "Configuring combat style for " + styleSkill.getName());
            CombatStyleResult styleResult = configureCombatStyle(context, styleSkill);
            if (styleResult != CombatStyleResult.CONFIRMED) {
                debugLog(context, "Combat style " + styleResult + " attempt " + styleAttempts + "/" + MAX_STYLE_ATTEMPTS);
                return styleResult == CombatStyleResult.FAILED
                        ? stop(TaskStatus.REPLAN, TaskStopReason.ACTION_FAILED)
                        : TaskStatus.RUNNING;
            }
            styleAttempts = 0;
            lastStyleAttemptAtMs = 0;
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
                if (CombatStrategy.canFightUnarmed(monster)) {
                    debugLog(context, "No weapon available, continuing unarmed against bootstrap monster");
                    phase = Phase.CHECK_STATUS;
                    return TaskStatus.RUNNING;
                }
                // No weapon available anywhere - reroll task
                debugLog(context, "No weapon available anywhere, returning REPLAN");
                return stop(TaskStatus.REPLAN, TaskStopReason.EQUIPMENT_MISSING);
            }

            int foodTarget = CombatStrategy.recommendedFoodCount(monster, context);
            String bestFood = foodTarget > 0 ? CombatStrategy.findBestAvailableFood(context) : null;
            if (foodTarget > 0 && bestFood == null) {
                debugLog(context, "No food available for the combat loadout, returning REPLAN");
                return stop(TaskStatus.REPLAN, TaskStopReason.MISSING_SUPPLIES);
            }

            plannedLoadout.clear();
            plannedLoadout.addAll(CombatGear.getBestLoadout(context));
            equipmentIndex = 0;
            equipmentGuard.reset();

            List<BankingTask.ItemWithdrawal> withdrawals = new ArrayList<>();
            for (String gear : plannedLoadout) {
                withdrawals.add(new BankingTask.ItemWithdrawal(gear, 1));
            }
            if (bestFood != null) {
                withdrawals.add(new BankingTask.ItemWithdrawal(bestFood, foodTarget));
            }

            debugLog(context, "Creating clean combat loadout banking task with " + withdrawals.size() + " withdrawals");
            bankingTask = new BankingTask(
                    BankingTask.Mode.DEPOSIT_ALL_AND_WITHDRAW,
                    true,
                    null,
                    withdrawals.toArray(new BankingTask.ItemWithdrawal[0])
            );
        }

        TaskStatus bankStatus = bankingTask.tick(context);

        if (bankStatus == TaskStatus.COMPLETE) {
            debugLog(context, "Banking complete");
            bankingTask = null;
            styleConfigured = false;
            equipmentIndex = 0;
            equipmentGuard.reset();
            debugLog(context, "Banking complete; equipping the selected loadout next tick");
            phase = Phase.EQUIPPING;
            return TaskStatus.RUNNING;
        }

        if (bankStatus.isUnsuccessfulStop()) {
            debugLog(context, "Banking failed/replan: " + bankStatus);
            bankingTask = null;
            return stop(TaskStatus.REPLAN, TaskStopReason.BANK_FAILED);
        }

        return TaskStatus.RUNNING;
    }

    private TaskStatus handleEquip(AccountContext context) {
        while (equipmentIndex < plannedLoadout.size()
                && context.equipment().hasItem(plannedLoadout.get(equipmentIndex))) {
            equipmentIndex++;
            equipmentGuard.reset();
        }

        if (equipmentIndex >= plannedLoadout.size()) {
            phase = Phase.CHECK_STATUS;
            return TaskStatus.RUNNING;
        }

        String itemName = plannedLoadout.get(equipmentIndex);
        if (!context.inventory().hasItem(itemName)) {
            if (context.bank().hasItem(itemName)) {
                phase = Phase.BANKING;
                return TaskStatus.RUNNING;
            }
            return stop(TaskStatus.REPLAN, TaskStopReason.EQUIPMENT_MISSING);
        }

        TaskActionGuard.Result result = equipmentGuard.evaluate(
                "equip combat loadout " + itemName,
                context.equipment().hasItem(itemName)
        );
        if (result == TaskActionGuard.Result.EXHAUSTED) {
            return stop(TaskStatus.REPLAN, TaskStopReason.EQUIP_FAILED);
        }
        if (result == TaskActionGuard.Result.READY) {
            debugLog(context, "Equipping selected loadout item: " + itemName);
            Rs2Inventory.wield(itemName);
            equipmentGuard.recordAttempt();
        }
        return TaskStatus.RUNNING;
    }

    private TaskStatus handleWalk(AccountContext context) {
        debugLog(context, "handleWalk: hasFood=" + context.inventory().hasFood() + ", health=" + Rs2Player.getHealthPercentage() + "%, nearMonster=" + context.isNear(monster.location, 12));

        if (CombatStrategy.inventoryFoodCount(context) == 0
                && CombatStrategy.recommendedFoodCount(monster, context) > 0
                && Rs2Player.getHealthPercentage() <= 50) {
            debugLog(context, "No food and low health, switching to BANKING");
            phase = Phase.BANKING;
            return TaskStatus.RUNNING;
        }

        if (context.isNear(monster.location, 12)) {
            walkGuard.reset();
            debugLog(context, "Near monster location, switching to FIGHTING");
            phase = Phase.FIGHTING;
            return TaskStatus.RUNNING;
        }

        TaskActionGuard.Result walkResult = walkGuard.evaluate("walk to " + monster.displayName, false);
        if (walkResult == TaskActionGuard.Result.EXHAUSTED) {
            debugLog(context, "Walk to monster exhausted after " + walkGuard.attempts() + " attempts");
            return stop(TaskStatus.REPLAN, TaskStopReason.TRAVEL_FAILED);
        }
        if (walkResult == TaskActionGuard.Result.READY) {
            debugLog(context, "Walking to monster location: " + monster.location
                    + " attempt " + (walkGuard.attempts() + 1));
            Rs2Walker.walkTo(monster.location);
            walkGuard.recordAttempt();
        }
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
            attackGuard.reset();
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
            return TaskStatus.RUNNING;
        }

        // If inventory is full, bank loot and return through CHECK_STATUS to resume this combat task.
        if (Rs2Inventory.isFull()) {
            debugLog(context, "Inventory full, switching to BANKING");
            phase = Phase.BANKING;
            return TaskStatus.RUNNING;
        }

        // Loot only this account's drops. Bones are exclusively for an unfinished Prayer goal;
        // all other drops must be worth more than 100 gp per ground stack, except the active
        // money-making drop which is deliberately gathered for sale.
        Rs2TileItemModel loot = Microbot.getRs2TileItemCache().query()
                .within(8)
                .where(Rs2TileItemModel::isOwned)
                .where(item -> shouldLoot(item, context))
                .nearest();

        if (loot != null) {
            debugLog(context, "Picking up loot: " + loot.getName());
            loot.pickup();
            targetSearchFailures = 0;

            if (shouldBuryBones(context) && Rs2Inventory.contains("Bones")) {
                Rs2Inventory.interact("Bones", "Bury");
            }
            return TaskStatus.RUNNING;
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
            String attackAction = "attack " + monster.displayName + "#" + target.getIndex();
            TaskActionGuard.Result attackResult = attackGuard.evaluate(attackAction, false);
            if (attackResult == TaskActionGuard.Result.EXHAUSTED) {
                debugLog(context, "Attack action exhausted after " + attackGuard.attempts() + " attempts");
                return stop(TaskStatus.REPLAN, TaskStopReason.ACTION_FAILED);
            }
            if (attackResult == TaskActionGuard.Result.READY) {
                debugLog(context, "Attacking target: " + target.getName()
                        + " attempt " + (attackGuard.attempts() + 1));
                boolean clicked = target.click("Attack");
                attackGuard.recordAttempt();
                if (!clicked) {
                    debugLog(context, "Attack click was not accepted");
                }
            }
            targetSearchFailures = 0;
        } else {
            // If no monster is nearby, re-center on the spawn area
            if (!context.isNear(monster.location, 10)) {
                debugLog(context, "No target found, not near spawn, switching to WALK_TO_MONSTER");
                phase = Phase.WALK_TO_MONSTER;
                targetSearchFailures = 0;
            } else {
                targetSearchFailures++;
                debugLog(context, "No target found near spawn attempt " + targetSearchFailures + "/" + MAX_TARGET_SEARCH_FAILURES);
                if (targetSearchFailures >= MAX_TARGET_SEARCH_FAILURES) {
                    return stop(TaskStatus.REPLAN, TaskStopReason.RESOURCE_NOT_FOUND);
                }
            }
        }

        return TaskStatus.RUNNING;
    }

    private boolean isPlannedLoadoutEquipped(AccountContext context) {
        for (String itemName : plannedLoadout) {
            if (!context.equipment().hasItem(itemName)) {
                return false;
            }
        }
        return true;
    }

    private enum CombatStyleResult {
        CONFIRMED,
        WAITING,
        FAILED
    }

    private CombatStyleResult configureCombatStyle(AccountContext context, Skill styleSkill) {
        int styleIndex = findExclusiveStyleIndex(styleSkill);
        if (styleIndex < 0) {
            debugLog(context, "No exact " + styleSkill.getName() + " style is available for the equipped weapon");
            return CombatStyleResult.FAILED;
        }

        if (Microbot.getVarbitPlayerValue(VarPlayerID.COM_MODE) == styleIndex) {
            Rs2Tab.switchToInventoryTab();
            return CombatStyleResult.CONFIRMED;
        }

        if (styleAttempts >= MAX_STYLE_ATTEMPTS) {
            return CombatStyleResult.FAILED;
        }

        long now = System.currentTimeMillis();
        if (lastStyleAttemptAtMs != 0 && now - lastStyleAttemptAtMs < 600) {
            return CombatStyleResult.WAITING;
        }

        Rs2Tab.switchToCombatOptionsTab();
        boolean autoRetaliateSet = Rs2Combat.setAutoRetaliate(true);
        WidgetInfo styleWidget = combatStyleWidget(styleIndex);
        boolean styleSet = styleWidget != null && Rs2Combat.setAttackStyle(styleWidget);
        styleAttempts++;
        lastStyleAttemptAtMs = now;
        debugLog(context, "Requested " + styleSkill.getName() + " style index=" + styleIndex
                + " accepted=" + styleSet + " autoRetaliate=" + autoRetaliateSet);
        return CombatStyleResult.WAITING;
    }

    private int findExclusiveStyleIndex(Skill targetSkill) {
        EnumComposition weaponStyleLookup = Microbot.getEnum(EnumID.WEAPON_STYLES);
        if (weaponStyleLookup == null) {
            return -1;
        }

        int weaponType = Microbot.getVarbitValue(VarbitID.COMBAT_WEAPON_CATEGORY);
        int weaponStyleEnumId = weaponStyleLookup.getIntValue(weaponType);
        if (weaponStyleEnumId < 0) {
            return -1;
        }

        EnumComposition weaponStyles = Microbot.getEnum(weaponStyleEnumId);
        int[] styleStructIds = weaponStyles != null ? weaponStyles.getIntVals() : null;
        if (styleStructIds == null) {
            return -1;
        }

        for (int index = 0; index < styleStructIds.length; index++) {
            StructComposition style = Microbot.getStructComposition(styleStructIds[index]);
            if (style != null && trainsOnly(style.getStringValue(ParamID.ATTACK_STYLE_NAME), targetSkill)) {
                return index;
            }
        }
        return -1;
    }

    static boolean trainsOnly(String styleName, Skill targetSkill) {
        if (styleName == null || targetSkill == null) {
            return false;
        }
        switch (styleName) {
            case "Accurate":
                return targetSkill == Skill.ATTACK;
            case "Aggressive":
                return targetSkill == Skill.STRENGTH;
            case "Defensive":
                return targetSkill == Skill.DEFENCE;
            default:
                return false;
        }
    }

    static WidgetInfo combatStyleWidget(int styleIndex) {
        switch (styleIndex) {
            case 0:
                return WidgetInfo.COMBAT_STYLE_ONE;
            case 1:
                return WidgetInfo.COMBAT_STYLE_TWO;
            case 2:
                return WidgetInfo.COMBAT_STYLE_THREE;
            case 3:
                return WidgetInfo.COMBAT_STYLE_FOUR;
            default:
                return null;
        }
    }

    private boolean shouldBuryBones(AccountContext context) {
        return prayerTarget > 0 && context.getRealLevel(Skill.PRAYER) < prayerTarget;
    }

    private boolean shouldLoot(Rs2TileItemModel item, AccountContext context) {
        String itemName = item.getName();
        if ("Bones".equalsIgnoreCase(itemName)) {
            return shouldBuryBones(context);
        }
        if (requiredLootName != null && requiredLootName.equalsIgnoreCase(itemName)) {
            return true;
        }
        return exceedsLootValueThreshold(item.getTotalValue());
    }

    static boolean exceedsLootValueThreshold(int totalValue) {
        return totalValue > MINIMUM_LOOT_STACK_VALUE;
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
    public TaskStopReason getReplanStopReason(AccountContext context) {
        if (context.getRealLevel(targetSkill) >= targetLevel) {
            return TaskStopReason.REQUIREMENT_SATISFIED;
        }
        return TaskStopReason.TASK_REQUESTED_REPLAN;
    }

    @Override
    public TaskStopReason getLastStopReason() {
        return lastStopReason;
    }

    private TaskStatus stop(TaskStatus status, TaskStopReason reason) {
        lastStopReason = reason != null ? reason : TaskStopReason.UNKNOWN;
        return status;
    }

    @Override
    public String describe() {
        return "Combat (" + monster.displayName + " - " + targetSkill.getName() + ") - " + phase;
    }
}
