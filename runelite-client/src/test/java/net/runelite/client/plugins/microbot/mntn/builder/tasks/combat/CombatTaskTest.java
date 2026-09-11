package net.runelite.client.plugins.microbot.mntn.builder.tasks.combat;

import net.runelite.api.Skill;
import net.runelite.api.widgets.WidgetInfo;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CombatTaskTest {

    @Test
    public void combatStyleSelectionUsesTrainingTypeInsteadOfFixedWidgetPositions() {
        assertTrue(CombatTask.trainsOnly("Accurate", Skill.ATTACK));
        assertTrue(CombatTask.trainsOnly("Aggressive", Skill.STRENGTH));
        assertTrue(CombatTask.trainsOnly("Defensive", Skill.DEFENCE));
        assertFalse(CombatTask.trainsOnly("Controlled", Skill.DEFENCE));
        assertEquals(WidgetInfo.COMBAT_STYLE_THREE, CombatTask.combatStyleWidget(2));
    }

    @Test
    public void valuableLootRequiresMoreThanOneHundredGp() {
        assertFalse(CombatTask.exceedsLootValueThreshold(100));
        assertTrue(CombatTask.exceedsLootValueThreshold(101));
    }

    @Test
    public void combatFoodWithdrawalsUseTheBankingTaskWithdrawAllSentinel() {
        assertEquals(-1, CombatTask.combatFoodWithdrawalAmount());
    }
}
