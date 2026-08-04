package com.majorbonghits.moderncompanions.entity.ai;

import com.majorbonghits.moderncompanions.core.CompanionCreeperPolicy;

/** No-world regression check for creeper engagement, spacing, and owner protection. */
public final class CreeperEngagementRulesTest {
    public static void main(String[] args) {
        testPolicyGate();
        testMeleeSpacing();
        testBodyBlock();
        testInterception();
        testChargedIsWider();
    }

    private static void testPolicyGate() {
        // ENGAGE lets everyone fight creepers; this is the requested default.
        assert CreeperEngagementRules.mayEngage(CompanionCreeperPolicy.ENGAGE, false);
        assert CreeperEngagementRules.mayEngage(CompanionCreeperPolicy.ENGAGE, true);
        // RANGED_ONLY keeps melee out of it.
        assert CreeperEngagementRules.mayEngage(CompanionCreeperPolicy.RANGED_ONLY, true);
        assert !CreeperEngagementRules.mayEngage(CompanionCreeperPolicy.RANGED_ONLY, false);
        // AVOID restores the original never-target behavior.
        assert !CreeperEngagementRules.mayEngage(CompanionCreeperPolicy.AVOID, true);
        assert !CreeperEngagementRules.mayEngage(CompanionCreeperPolicy.AVOID, false);
    }

    private static void testMeleeSpacing() {
        // Not swelling: melee presses the attack rather than backing off.
        assert !CreeperEngagementRules.shouldBackOff(false, false, false, 1.0D);
        // Swelling and inside the blast: back off. This is spacing, not fleeing.
        assert CreeperEngagementRules.shouldBackOff(false, true, false, 2.0D);
        // Swelling but already clear: hold position.
        assert !CreeperEngagementRules.shouldBackOff(false, true, false, 6.0D);
        // Ranged companions never back off; they already fight from outside the blast.
        assert !CreeperEngagementRules.shouldBackOff(true, true, false, 1.0D);
    }

    private static void testBodyBlock() {
        // Owner inside the blast, companion close enough to arrive: interpose.
        assert CreeperEngagementRules.shouldBodyBlockForOwner(true, false, 2.0D, 5.0D);
        // Owner safely outside the blast: nothing to block.
        assert !CreeperEngagementRules.shouldBodyBlockForOwner(true, false, 9.0D, 5.0D);
        // Companion too far to reach in time.
        assert !CreeperEngagementRules.shouldBodyBlockForOwner(true, false, 2.0D, 30.0D);
        // No fuse burning: nothing to block yet.
        assert !CreeperEngagementRules.shouldBodyBlockForOwner(false, false, 2.0D, 5.0D);
    }

    private static void testInterception() {
        assert CreeperEngagementRules.shouldInterceptForOwner(5.0D, false);
        assert !CreeperEngagementRules.shouldInterceptForOwner(20.0D, false);
    }

    private static void testChargedIsWider() {
        assert CreeperEngagementRules.blastRadius(true) > CreeperEngagementRules.blastRadius(false);
        assert CreeperEngagementRules.safeDistance(true) > CreeperEngagementRules.safeDistance(false);
        // A distance that is clear of a normal creeper is still inside a charged blast.
        assert !CreeperEngagementRules.shouldBackOff(false, true, false, 5.0D);
        assert CreeperEngagementRules.shouldBackOff(false, true, true, 5.0D);
        // Owner protection widens with the charged blast too.
        assert !CreeperEngagementRules.shouldBodyBlockForOwner(true, false, 5.0D, 5.0D);
        assert CreeperEngagementRules.shouldBodyBlockForOwner(true, true, 5.0D, 5.0D);
    }
}
