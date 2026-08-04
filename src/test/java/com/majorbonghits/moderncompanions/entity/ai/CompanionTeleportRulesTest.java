package com.majorbonghits.moderncompanions.entity.ai;

import com.majorbonghits.moderncompanions.core.CompanionTeleportPolicy;

/** No-world regression check for the teleport-policy boundary and view cone. */
public final class CompanionTeleportRulesTest {
    public static void main(String[] args) {
        testNeverRefusesEverything();
        testLegacyAlwaysAllows();
        testLastResortNeedsEveryCondition();
        testViewCone();
    }

    private static void testNeverRefusesEverything() {
        assert !CompanionTeleportRules.shouldTeleport(CompanionTeleportPolicy.NEVER,
                999.0D, 64.0D, 9999, 300, false, false, 99999, 1200)
                : "NEVER must refuse even when every other condition is satisfied";
    }

    private static void testLegacyAlwaysAllows() {
        assert CompanionTeleportRules.shouldTeleport(CompanionTeleportPolicy.LEGACY,
                0.0D, 64.0D, 0, 300, true, true, 0, 1200)
                : "LEGACY preserves the original unconditional behavior";
    }

    private static void testLastResortNeedsEveryCondition() {
        // Baseline: all five conditions satisfied.
        assert CompanionTeleportRules.shouldTeleport(CompanionTeleportPolicy.LAST_RESORT,
                80.0D, 64.0D, 400, 300, false, false, 1500, 1200);

        // Each condition individually withheld must block the teleport.
        assert !CompanionTeleportRules.shouldTeleport(CompanionTeleportPolicy.LAST_RESORT,
                63.0D, 64.0D, 400, 300, false, false, 1500, 1200) : "too close must block";
        assert !CompanionTeleportRules.shouldTeleport(CompanionTeleportPolicy.LAST_RESORT,
                80.0D, 64.0D, 299, 300, false, false, 1500, 1200) : "still routing must block";
        assert !CompanionTeleportRules.shouldTeleport(CompanionTeleportPolicy.LAST_RESORT,
                80.0D, 64.0D, 400, 300, true, false, 1500, 1200) : "in combat must block";
        assert !CompanionTeleportRules.shouldTeleport(CompanionTeleportPolicy.LAST_RESORT,
                80.0D, 64.0D, 400, 300, false, true, 1500, 1200) : "owner watching must block";
        assert !CompanionTeleportRules.shouldTeleport(CompanionTeleportPolicy.LAST_RESORT,
                80.0D, 64.0D, 400, 300, false, false, 1199, 1200) : "cooldown must block";

        // Exact boundaries are inclusive.
        assert CompanionTeleportRules.shouldTeleport(CompanionTeleportPolicy.LAST_RESORT,
                64.0D, 64.0D, 300, 300, false, false, 1200, 1200) : "boundaries are inclusive";
    }

    private static void testViewCone() {
        double cos60 = Math.cos(Math.toRadians(60.0D)); // 120-degree cone

        // Directly ahead of the owner is visible.
        assert CompanionTeleportRules.withinViewCone(0.0D, 1.0D, 0.0D, 10.0D, cos60);
        // Directly behind the owner is not.
        assert !CompanionTeleportRules.withinViewCone(0.0D, 1.0D, 0.0D, -10.0D, cos60);
        // Exactly perpendicular sits outside a 120-degree cone.
        assert !CompanionTeleportRules.withinViewCone(0.0D, 1.0D, 10.0D, 0.0D, cos60);
        // Just inside the cone edge is visible.
        assert CompanionTeleportRules.withinViewCone(0.0D, 1.0D, 5.0D, 10.0D, cos60);
        // Degenerate inputs fail safe to "visible" so a teleport is suppressed.
        assert CompanionTeleportRules.withinViewCone(0.0D, 0.0D, 0.0D, 10.0D, cos60);
        assert CompanionTeleportRules.withinViewCone(0.0D, 1.0D, 0.0D, 0.0D, cos60);
    }
}
