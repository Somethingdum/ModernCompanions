package com.majorbonghits.moderncompanions.world.zone;

/**
 * Pure geometry for perimeter zones, kept dependency-free so it can be
 * regression-checked without a world.
 *
 * <p>Two clicked corners give X and Z. Y is the part that decides whether a
 * perimeter actually holds: a box that only covers the surface lets anything
 * tunnel underneath or fly over it, so the zone is expanded downward and upward
 * from the clicked corners rather than being limited to them.
 */
public final class ZoneMath {
    private ZoneMath() {}

    public static int minOf(int a, int b) {
        return Math.min(a, b);
    }

    public static int maxOf(int a, int b) {
        return Math.max(a, b);
    }

    /** Lower Y bound: the lower clicked corner, dropped by the configured depth. */
    public static int lowerBound(int cornerAY, int cornerBY, int depthBelow, int worldMinY) {
        return Math.max(worldMinY, Math.min(cornerAY, cornerBY) - depthBelow);
    }

    /** Upper Y bound: the higher clicked corner, raised by the configured height. */
    public static int upperBound(int cornerAY, int cornerBY, int heightAbove, int worldMaxY) {
        return Math.min(worldMaxY, Math.max(cornerAY, cornerBY) + heightAbove);
    }

    public static boolean contains(int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                                   double x, double y, double z) {
        return x >= minX && x <= maxX + 1.0D
                && y >= minY && y <= maxY + 1.0D
                && z >= minZ && z <= maxZ + 1.0D;
    }

    /**
     * Horizontal distance from a point to the nearest zone edge. Negative inside,
     * positive outside, so one number tells a guard both whether an intruder is in
     * the zone and how deep.
     */
    public static double signedEdgeDistance(int minX, int minZ, int maxX, int maxZ, double x, double z) {
        double dxLow = x - minX;
        double dxHigh = (maxX + 1.0D) - x;
        double dzLow = z - minZ;
        double dzHigh = (maxZ + 1.0D) - z;
        double horizontal = Math.min(Math.min(dxLow, dxHigh), Math.min(dzLow, dzHigh));
        return -horizontal;
    }

    /** Block volume, used to reject absurd zones before they are stored. */
    public static long volume(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        long dx = (long) maxX - minX + 1L;
        long dy = (long) maxY - minY + 1L;
        long dz = (long) maxZ - minZ + 1L;
        return dx * dy * dz;
    }

    /**
     * How many boundary sample points a zone of this footprint should produce.
     * Sentry posts are chosen from these, so the count scales with perimeter
     * length but stays bounded on very large bases.
     */
    public static int boundarySampleCount(int minX, int minZ, int maxX, int maxZ, int spacing, int maxSamples) {
        int width = maxX - minX + 1;
        int depth = maxZ - minZ + 1;
        int perimeter = 2 * (width + depth);
        int safeSpacing = Math.max(1, spacing);
        return Math.max(4, Math.min(maxSamples, perimeter / safeSpacing));
    }

    /**
     * Evenly distributes n posts around a rectangle's perimeter, returning the
     * position of index i as a fraction of the way around. Spreading guards over
     * distinct arcs is what makes several of them cover a base rather than
     * clustering on one corner.
     */
    public static double perimeterFraction(int index, int count) {
        if (count <= 0) return 0.0D;
        return (double) Math.floorMod(index, count) / count;
    }
}
