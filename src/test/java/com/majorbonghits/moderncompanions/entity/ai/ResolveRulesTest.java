package com.majorbonghits.moderncompanions.entity.ai;

import com.majorbonghits.moderncompanions.core.CompanionSurvivalProfile;

/**
 * No-world regression check for the survival model. Each anti-cowardice
 * guarantee from the plan is asserted by name so a future change that breaks one
 * fails loudly rather than quietly making companions timid.
 */
public final class ResolveRulesTest {
    private static final double THRESHOLD = 0.30D;
    private static final CompanionSurvivalProfile DISCIPLINED = CompanionSurvivalProfile.DISCIPLINED;

    public static void main(String[] args) {
        testBaselineWithdrawal();
        testGuarantee1NeverAbandonOwnerInCombat();
        testGuarantee3NeverFleeCreepers();
        testGuarantee4CorneredFightsToTheDeath();
        testGuarantee5AvengingSuspendsSelfPreservation();
        testProfiles();
        testReengageHysteresis();
        testSecondWind();
    }

    /** Badly hurt, owner safe, retreat available: breaking off is correct. */
    private static void testBaselineWithdrawal() {
        assert ResolveRules.shouldWithdraw(0.20D, THRESHOLD, false, false, false, false, DISCIPLINED);
        // Healthy companions keep fighting.
        assert !ResolveRules.shouldWithdraw(0.80D, THRESHOLD, false, false, false, false, DISCIPLINED);
        // Exactly at the threshold counts as needing to break off.
        assert ResolveRules.shouldWithdraw(0.30D, THRESHOLD, false, false, false, false, DISCIPLINED);
    }

    private static void testGuarantee1NeverAbandonOwnerInCombat() {
        // At one hit from death, with the owner fighting nearby, it must still hold.
        assert !ResolveRules.shouldWithdraw(0.01D, THRESHOLD, true, false, false, false, DISCIPLINED)
                : "guarantee 1: companions never disengage while the owner is fighting nearby";
        assert !ResolveRules.shouldWithdraw(0.01D, THRESHOLD, true, false, false, false,
                CompanionSurvivalProfile.CAUTIOUS)
                : "guarantee 1 holds even on the most cautious profile";
    }

    private static void testGuarantee3NeverFleeCreepers() {
        assert !ResolveRules.shouldWithdraw(0.05D, THRESHOLD, false, true, false, false, DISCIPLINED)
                : "guarantee 3: creepers are spaced, never fled";
    }

    private static void testGuarantee4CorneredFightsToTheDeath() {
        assert !ResolveRules.shouldWithdraw(0.05D, THRESHOLD, false, false, true, false, DISCIPLINED)
                : "guarantee 4: cornered with no ally and no route means fight";
    }

    private static void testGuarantee5AvengingSuspendsSelfPreservation() {
        assert !ResolveRules.shouldWithdraw(0.05D, THRESHOLD, false, false, false, true, DISCIPLINED)
                : "guarantee 5: avenging a downed owner suspends self-preservation";
    }

    private static void testProfiles() {
        // RECKLESS never breaks off, even at one hit from death.
        assert !ResolveRules.shouldWithdraw(0.01D, THRESHOLD, false, false, false, false,
                CompanionSurvivalProfile.RECKLESS);
        // CAUTIOUS breaks off earlier than DISCIPLINED would.
        assert ResolveRules.shouldWithdraw(0.40D, THRESHOLD, false, false, false, false,
                CompanionSurvivalProfile.CAUTIOUS);
        assert !ResolveRules.shouldWithdraw(0.40D, THRESHOLD, false, false, false, false, DISCIPLINED);
        // A configured threshold above the cautious floor still wins.
        assert ResolveRules.withdrawThreshold(0.60D, CompanionSurvivalProfile.CAUTIOUS) == 0.60D;
    }

    private static void testReengageHysteresis() {
        // Just above the break-off point is not enough; it would oscillate.
        assert !ResolveRules.shouldReengage(0.35D, THRESHOLD, DISCIPLINED);
        // Clear headroom means rejoin the fight.
        assert ResolveRules.shouldReengage(0.60D, THRESHOLD, DISCIPLINED);
        // Re-engage must always be strictly harder to reach than withdrawal.
        assert ResolveRules.withdrawThreshold(THRESHOLD, DISCIPLINED) < 0.55D;
    }

    private static void testSecondWind() {
        assert ResolveRules.shouldGrantSecondWind(true, true, false, 0.10D);
        // Only while actually withdrawing, and only once per fight.
        assert !ResolveRules.shouldGrantSecondWind(true, false, false, 0.10D);
        assert !ResolveRules.shouldGrantSecondWind(true, true, true, 0.10D);
        // Not a general low-health buff; it is specifically the last-ditch case.
        assert !ResolveRules.shouldGrantSecondWind(true, true, false, 0.40D);
        assert !ResolveRules.shouldGrantSecondWind(false, true, false, 0.10D);
    }
}
