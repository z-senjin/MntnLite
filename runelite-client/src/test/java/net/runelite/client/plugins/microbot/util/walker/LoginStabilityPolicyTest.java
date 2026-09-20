package net.runelite.client.plugins.microbot.util.walker;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LoginStabilityPolicyTest {
    @Test
    public void oneFalseSampleThatRecoversDoesNotAbort() {
        assertFalse(LoginStabilityPolicy.shouldExit(false, true, false));
    }

    @Test
    public void stableFalseStateAborts() {
        assertTrue(LoginStabilityPolicy.shouldExit(false, false, false));
    }

    @Test
    public void cancellationOwnsItsOwnExitPath() {
        assertFalse(LoginStabilityPolicy.shouldExit(false, false, true));
    }
}
