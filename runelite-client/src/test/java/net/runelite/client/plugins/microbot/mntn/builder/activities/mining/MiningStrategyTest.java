package net.runelite.client.plugins.microbot.mntn.builder.activities.mining;

import net.runelite.client.plugins.microbot.mntn.builder.core.AccountContext;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MiningStrategyTest {

    @Test
    public void alKharidMiningLocationsRequireCombatLevelTwentyNine() {
        AccountContext belowRequirement = combatLevel(28);
        AccountContext atRequirement = combatLevel(29);

        assertFalse(MiningStrategy.Location.AL_KHARID_IRON.meetsCombatRequirement(belowRequirement));
        assertFalse(MiningStrategy.Location.AL_KHARID_SILVER.meetsCombatRequirement(belowRequirement));
        assertTrue(MiningStrategy.Location.AL_KHARID_IRON.meetsCombatRequirement(atRequirement));
        assertTrue(MiningStrategy.Location.AL_KHARID_SILVER.meetsCombatRequirement(atRequirement));
    }

    @Test
    public void nonAlKharidMiningLocationsHaveNoCombatGate() {
        assertTrue(MiningStrategy.Location.VARROCK_EAST_IRON.meetsCombatRequirement(combatLevel(3)));
    }

    private AccountContext combatLevel(int combatLevel) {
        return new AccountContext() {
            @Override
            public int getCombatLevel() {
                return combatLevel;
            }
        };
    }
}
