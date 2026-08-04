package com.majorbonghits.moderncompanions.entity.ai;

/**
 * No-world regression check for target priority. Each assertion states a
 * priority rule in plain terms, so a future weight change that inverts one of
 * them fails loudly rather than quietly making companions fight badly.
 */
public final class ThreatAssessmentTest {
    private static final double RANGE = 24.0D;

    public static void main(String[] args) {
        testOwnerAttackerOutranksEverything();
        testImminentThreatsJumpTheQueue();
        testWoundedTargetsAreFinished();
        testProximity();
        testZoneIntrusion();
        testFocusFireSpreading();
        testConfidence();
        testSwitchHysteresis();
    }

    /** The thing hurting the player is always the thing to deal with. */
    private static void testOwnerAttackerOutranksEverything() {
        double ownerAttackerFarAway = ThreatAssessment.score(
                23.0D, RANGE, true, false, false, 1.0D, false, 0, 1.0F);
        double healthyNeighbourInMelee = ThreatAssessment.score(
                1.0D, RANGE, false, false, false, 1.0D, false, 0, 1.0F);
        assert ownerAttackerFarAway > healthyNeighbourInMelee
                : "a distant target attacking the owner beats a close one that is not";

        // Even against something already attacking us.
        double attackingSelf = ThreatAssessment.score(
                2.0D, RANGE, false, true, false, 1.0D, false, 0, 1.0F);
        assert ownerAttackerFarAway > attackingSelf;
    }

    private static void testImminentThreatsJumpTheQueue() {
        // A creeper about to detonate outranks an ordinary mob already on us.
        double creeper = ThreatAssessment.score(6.0D, RANGE, false, false, true, 1.0D, false, 0, 1.0F);
        double ordinary = ThreatAssessment.score(2.0D, RANGE, false, true, false, 1.0D, false, 0, 1.0F);
        assert creeper > ordinary : "imminent threats outrank ordinary attackers";
    }

    private static void testWoundedTargetsAreFinished() {
        double wounded = ThreatAssessment.score(5.0D, RANGE, false, false, false, 0.1D, false, 0, 1.0F);
        double healthy = ThreatAssessment.score(5.0D, RANGE, false, false, false, 1.0D, false, 0, 1.0F);
        assert wounded > healthy : "all else equal, finish the wounded one";
    }

    private static void testProximity() {
        double near = ThreatAssessment.score(1.0D, RANGE, false, false, false, 1.0D, false, 0, 1.0F);
        double far = ThreatAssessment.score(20.0D, RANGE, false, false, false, 1.0D, false, 0, 1.0F);
        assert near > far;

        assert ThreatAssessment.proximityTerm(0.0D, RANGE) == 1.0D;
        assert ThreatAssessment.proximityTerm(RANGE, RANGE) == 0.0D;
        // Beyond range clamps rather than going negative.
        assert ThreatAssessment.proximityTerm(RANGE * 3.0D, RANGE) == 0.0D;
        // A zero range must not divide by zero.
        assert ThreatAssessment.proximityTerm(5.0D, 0.0D) == 0.0D;
    }

    private static void testZoneIntrusion() {
        double intruder = ThreatAssessment.score(15.0D, RANGE, false, false, false, 1.0D, true, 0, 1.0F);
        double outsider = ThreatAssessment.score(15.0D, RANGE, false, false, false, 1.0D, false, 0, 1.0F);
        assert intruder > outsider : "something inside the base outranks something outside it";
    }

    private static void testFocusFireSpreading() {
        double unclaimed = ThreatAssessment.score(5.0D, RANGE, false, false, false, 1.0D, false, 0, 1.0F);
        double oneAlly = ThreatAssessment.score(5.0D, RANGE, false, false, false, 1.0D, false, 1, 1.0F);
        double twoAllies = ThreatAssessment.score(5.0D, RANGE, false, false, false, 1.0D, false, 2, 1.0F);
        assert unclaimed > oneAlly && oneAlly > twoAllies : "squads should spread across targets";

        // The discount saturates, so a third and fourth ally change nothing.
        double manyAllies = ThreatAssessment.score(5.0D, RANGE, false, false, false, 1.0D, false, 9, 1.0F);
        assert manyAllies == twoAllies : "the ally discount must saturate";

        // Spreading is a preference, not a rule: the owner's attacker still wins
        // even when two allies are already on it.
        double ownerAttackerCrowded = ThreatAssessment.score(
                5.0D, RANGE, true, false, false, 1.0D, false, 2, 1.0F);
        assert ownerAttackerCrowded > unclaimed;
    }

    private static void testConfidence() {
        double sure = ThreatAssessment.score(5.0D, RANGE, false, false, false, 1.0D, false, 0, 1.0F);
        double vague = ThreatAssessment.score(5.0D, RANGE, false, false, false, 1.0D, false, 0, 0.2F);
        assert sure > vague : "a half-remembered contact is worth less than one in plain sight";
    }

    private static void testSwitchHysteresis() {
        // A marginally better target is not worth abandoning a fight for.
        assert !ThreatAssessment.shouldSwitch(100.0D, 110.0D);
        // A decisively better one is.
        assert ThreatAssessment.shouldSwitch(100.0D, 200.0D);
        // Switching from nothing is always allowed.
        assert ThreatAssessment.shouldSwitch(0.0D, 20.0D);
    }
}
