package com.majorbonghits.moderncompanions.entity.ai;

/** No-world regression check for ranged fire discipline. */
public final class FireLineRulesTest {
    private static final double CLEARANCE = 1.2D;

    public static void main(String[] args) {
        testAllyDirectlyInTheLine();
        testClearShot();
        testSegmentNotInfiniteLine();
        testBoundary();
        testDegenerateShot();
    }

    private static void testAllyDirectlyInTheLine() {
        // Shooter at origin, target 20 blocks north, owner standing halfway.
        assert FireLineRules.blocksShot(0, 0, 0, 0, 0, 20, 0, 0, 10, CLEARANCE)
                : "an ally standing in the shot line must block the shot";
        // Slightly off-line but still within clearance.
        assert FireLineRules.blocksShot(0, 0, 0, 0, 0, 20, 0.9D, 0, 10, CLEARANCE);
        // Directly in line but at a different height still counts.
        assert FireLineRules.blocksShot(0, 0, 0, 0, 0, 20, 0, 0.5D, 10, CLEARANCE);
    }

    private static void testClearShot() {
        // Well clear of the line: fire.
        assert !FireLineRules.blocksShot(0, 0, 0, 0, 0, 20, 5.0D, 0, 10, CLEARANCE);
        // Standing beside the shooter, not in front of it.
        assert !FireLineRules.blocksShot(0, 0, 0, 0, 0, 20, 3.0D, 0, 0, CLEARANCE);
    }

    private static void testSegmentNotInfiniteLine() {
        // Behind the shooter, on the extended line: not endangered, must not block.
        assert !FireLineRules.blocksShot(0, 0, 0, 0, 0, 20, 0, 0, -10, CLEARANCE)
                : "an ally behind the shooter is not in danger";
        // Beyond the target, on the extended line: also not blocking.
        assert !FireLineRules.blocksShot(0, 0, 0, 0, 0, 20, 0, 0, 40, CLEARANCE)
                : "an ally past the target is not in the shot's path";
    }

    private static void testBoundary() {
        // Exactly at the clearance distance is not blocking; strictly inside is.
        assert !FireLineRules.blocksShot(0, 0, 0, 0, 0, 20, CLEARANCE, 0, 10, CLEARANCE);
        assert FireLineRules.blocksShot(0, 0, 0, 0, 0, 20, CLEARANCE - 0.01D, 0, 10, CLEARANCE);

        double onLine = FireLineRules.distanceToShotLine(0, 0, 0, 0, 0, 20, 0, 0, 10);
        assert Math.abs(onLine) < 1.0E-9D : "a point on the line has zero distance to it";
    }

    private static void testDegenerateShot() {
        // Shooter and target in the same place: fall back to plain distance rather
        // than dividing by a zero-length line.
        double distance = FireLineRules.distanceToShotLine(5, 0, 5, 5, 0, 5, 5, 0, 8);
        assert Math.abs(distance - 3.0D) < 1.0E-9D;
    }
}
