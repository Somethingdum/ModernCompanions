package com.majorbonghits.moderncompanions.entity.ai;

/**
 * Pure geometry for ranged fire discipline, kept dependency-free so it can be
 * regression-checked without a world.
 *
 * <p>The rule is simple and it fixes the most-complained-about companion
 * behaviour there is: do not take a shot whose path passes close to the owner or
 * an ally. It is a point-to-segment distance test, so it costs almost nothing.
 */
public final class FireLineRules {
    private FireLineRules() {}

    /**
     * Distance from a point to the shot line between shooter and target.
     *
     * <p>The line is treated as a segment rather than an infinite ray: someone
     * standing well behind the shooter, or beyond the target, is not in danger
     * and must not block the shot.
     */
    public static double distanceToShotLine(double shooterX, double shooterY, double shooterZ,
                                            double targetX, double targetY, double targetZ,
                                            double pointX, double pointY, double pointZ) {
        double dx = targetX - shooterX;
        double dy = targetY - shooterY;
        double dz = targetZ - shooterZ;
        double lengthSq = dx * dx + dy * dy + dz * dz;
        if (lengthSq < 1.0E-6D) {
            return distance(shooterX, shooterY, shooterZ, pointX, pointY, pointZ);
        }

        double t = ((pointX - shooterX) * dx + (pointY - shooterY) * dy + (pointZ - shooterZ) * dz) / lengthSq;
        t = Math.max(0.0D, Math.min(1.0D, t));
        double closestX = shooterX + dx * t;
        double closestY = shooterY + dy * t;
        double closestZ = shooterZ + dz * t;
        return distance(closestX, closestY, closestZ, pointX, pointY, pointZ);
    }

    /** Whether an ally at this point is close enough to the shot line to be hit. */
    public static boolean blocksShot(double shooterX, double shooterY, double shooterZ,
                                     double targetX, double targetY, double targetZ,
                                     double allyX, double allyY, double allyZ,
                                     double clearance) {
        return distanceToShotLine(shooterX, shooterY, shooterZ,
                targetX, targetY, targetZ, allyX, allyY, allyZ) < clearance;
    }

    private static double distance(double ax, double ay, double az, double bx, double by, double bz) {
        double dx = ax - bx;
        double dy = ay - by;
        double dz = az - bz;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
