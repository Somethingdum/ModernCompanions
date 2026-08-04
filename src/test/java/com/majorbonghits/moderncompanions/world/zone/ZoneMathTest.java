package com.majorbonghits.moderncompanions.world.zone;

/** No-world regression check for perimeter zone geometry. */
public final class ZoneMathTest {
    public static void main(String[] args) {
        testCornerOrderDoesNotMatter();
        testVerticalExpansion();
        testContainment();
        testEdgeDistance();
        testVolume();
        testBoundarySampling();
    }

    /** Clicking corners in either order must produce the same box. */
    private static void testCornerOrderDoesNotMatter() {
        assert ZoneMath.minOf(10, -4) == ZoneMath.minOf(-4, 10);
        assert ZoneMath.maxOf(10, -4) == ZoneMath.maxOf(-4, 10);
        assert ZoneMath.minOf(10, -4) == -4;
        assert ZoneMath.maxOf(10, -4) == 10;
    }

    private static void testVerticalExpansion() {
        // Expands down from the lower corner and up from the higher one, which is
        // what covers tunnelling from below and flying in from above.
        assert ZoneMath.lowerBound(70, 64, 12, -64) == 52;
        assert ZoneMath.upperBound(70, 64, 24, 320) == 94;
        // World bounds always win so a zone can never extend outside the world.
        assert ZoneMath.lowerBound(-60, -60, 32, -64) == -64;
        assert ZoneMath.upperBound(310, 310, 32, 320) == 320;
    }

    private static void testContainment() {
        // Bounds are inclusive of the full block, so max+1 is still inside.
        assert ZoneMath.contains(0, 60, 0, 10, 80, 10, 5.0D, 70.0D, 5.0D);
        assert ZoneMath.contains(0, 60, 0, 10, 80, 10, 0.0D, 60.0D, 0.0D);
        assert ZoneMath.contains(0, 60, 0, 10, 80, 10, 11.0D, 81.0D, 11.0D);
        assert !ZoneMath.contains(0, 60, 0, 10, 80, 10, 11.5D, 70.0D, 5.0D);
        // Below the floor is outside, which is what makes the depth setting matter.
        assert !ZoneMath.contains(0, 60, 0, 10, 80, 10, 5.0D, 59.0D, 5.0D);
    }

    private static void testEdgeDistance() {
        // Dead centre of a 0..10 square is 5.5 from the nearest edge, and inside is negative.
        double centre = ZoneMath.signedEdgeDistance(0, 0, 10, 10, 5.5D, 5.5D);
        assert centre < 0.0D;
        // Near an edge is a smaller magnitude than deep inside, so "how deep" is comparable.
        double nearEdge = ZoneMath.signedEdgeDistance(0, 0, 10, 10, 0.5D, 5.5D);
        assert nearEdge > centre : "deeper intrusions must read as further inside";
    }

    private static void testVolume() {
        assert ZoneMath.volume(0, 0, 0, 0, 0, 0) == 1L;
        assert ZoneMath.volume(0, 0, 0, 9, 9, 9) == 1000L;
        // Large bases must not overflow int arithmetic.
        assert ZoneMath.volume(0, -64, 0, 500, 320, 500) > 0L;
    }

    private static void testBoundarySampling() {
        // A 20x20 base has an 80-block perimeter; at 8-block spacing that is 10 posts.
        assert ZoneMath.boundarySampleCount(0, 0, 19, 19, 8, 64) == 10;
        // Tiny zones still get a usable minimum.
        assert ZoneMath.boundarySampleCount(0, 0, 1, 1, 8, 64) == 4;
        // Huge zones stay bounded by the cap.
        assert ZoneMath.boundarySampleCount(0, 0, 999, 999, 4, 64) == 64;

        // Posts spread evenly and wrap around the perimeter.
        assert ZoneMath.perimeterFraction(0, 4) == 0.0D;
        assert ZoneMath.perimeterFraction(2, 4) == 0.5D;
        assert ZoneMath.perimeterFraction(4, 4) == 0.0D;
        assert ZoneMath.perimeterFraction(-1, 4) == 0.75D;
    }
}
