package com.majorbonghits.moderncompanions.squad;

/**
 * No-world regression check for stance derivation. The important property is
 * that every one of the eight legacy boolean combinations maps to exactly one
 * stance, and that round-tripping a stance through the booleans is lossless.
 */
public final class CompanionStanceTest {
    public static void main(String[] args) {
        testEveryLegacyCombinationResolves();
        testRoundTrip();
        testIdRoundTrip();
        testAnchoring();
    }

    private static void testEveryLegacyCombinationResolves() {
        // All eight combinations, including the contradictory ones a legacy save
        // could contain, must produce a defined stance rather than throwing.
        for (int mask = 0; mask < 8; mask++) {
            boolean following = (mask & 1) != 0;
            boolean patrolling = (mask & 2) != 0;
            boolean guarding = (mask & 4) != 0;
            CompanionStance stance = CompanionStance.derive(following, patrolling, guarding);
            assert stance != null;
            // Documented precedence: guard beats patrol beats follow.
            if (guarding) {
                assert stance == CompanionStance.WARD;
            } else if (patrolling) {
                assert stance == CompanionStance.PATROL;
            } else if (following) {
                assert stance == CompanionStance.ESCORT;
            } else {
                assert stance == CompanionStance.HOLD;
            }
        }
    }

    private static void testRoundTrip() {
        for (CompanionStance stance : CompanionStance.values()) {
            CompanionStance back = CompanionStance.derive(
                    stance.following(), stance.patrolling(), stance.guarding());
            assert back == stance : "stance must survive a round trip through the legacy booleans: " + stance;
        }
    }

    private static void testIdRoundTrip() {
        for (CompanionStance stance : CompanionStance.values()) {
            assert CompanionStance.fromId(stance.id()) == stance;
        }
        // Unknown and null ids fall back to a safe default rather than failing.
        assert CompanionStance.fromId("nonsense") == CompanionStance.ESCORT;
        assert CompanionStance.fromId(null) == CompanionStance.ESCORT;
    }

    private static void testAnchoring() {
        assert CompanionStance.PATROL.needsAnchor();
        assert CompanionStance.WARD.needsAnchor();
        assert !CompanionStance.ESCORT.needsAnchor();
        assert !CompanionStance.HOLD.needsAnchor();
        // Exactly one stance sets each legacy boolean.
        int following = 0, patrolling = 0, guarding = 0;
        for (CompanionStance stance : CompanionStance.values()) {
            if (stance.following()) following++;
            if (stance.patrolling()) patrolling++;
            if (stance.guarding()) guarding++;
        }
        assert following == 1 && patrolling == 1 && guarding == 1;
    }
}
