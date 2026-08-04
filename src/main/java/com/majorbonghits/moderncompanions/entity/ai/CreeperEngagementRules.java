package com.majorbonghits.moderncompanions.entity.ai;

import com.majorbonghits.moderncompanions.core.CompanionCreeperPolicy;

/**
 * Pure decision boundary for fighting creepers, kept dependency-free so it can
 * be regression-checked without a world.
 *
 * <p>The behavior being modelled is how a competent player fights a creeper:
 * close in, hit it, and step outside the blast radius while it swells rather
 * than either trading into the detonation or running away from it entirely.
 */
public final class CreeperEngagementRules {
    /** Vanilla creeper blast radius is 3; charged creepers double it. */
    public static final double BLAST_RADIUS = 3.0D;
    public static final double CHARGED_BLAST_RADIUS = 6.0D;
    /** Extra spacing beyond the blast so a companion is clear, not merely at the edge. */
    private static final double SAFETY_MARGIN = 1.5D;

    private CreeperEngagementRules() {}

    public static double blastRadius(boolean charged) {
        return charged ? CHARGED_BLAST_RADIUS : BLAST_RADIUS;
    }

    /** Distance a companion should back off to while a fuse is burning. */
    public static double safeDistance(boolean charged) {
        return blastRadius(charged) + SAFETY_MARGIN;
    }

    /** Whether this companion is allowed to attack creepers at all. */
    public static boolean mayEngage(CompanionCreeperPolicy policy, boolean hasRangedWeapon) {
        return switch (policy) {
            case ENGAGE -> true;
            case RANGED_ONLY -> hasRangedWeapon;
            case AVOID -> false;
        };
    }

    /**
     * Whether a melee companion should be backing away right now rather than
     * pressing the attack. Ranged companions never need to: they already fight
     * from beyond the blast radius.
     *
     * @param swelling whether the creeper's fuse is currently burning
     * @param distance current distance from the creeper
     */
    public static boolean shouldBackOff(boolean hasRangedWeapon, boolean swelling, boolean charged, double distance) {
        if (hasRangedWeapon) return false;
        return swelling && distance < safeDistance(charged);
    }

    /**
     * Whether a companion should put itself between the owner and a swelling
     * creeper. Only worth doing when the owner is actually inside the blast and
     * the companion is close enough to arrive in time.
     *
     * @param ownerDistanceToCreeper    how far the owner is from the creeper
     * @param companionDistanceToCreeper how far this companion is from the creeper
     */
    public static boolean shouldBodyBlockForOwner(boolean swelling, boolean charged,
                                                  double ownerDistanceToCreeper,
                                                  double companionDistanceToCreeper) {
        if (!swelling) return false;
        double blast = blastRadius(charged);
        return ownerDistanceToCreeper <= blast && companionDistanceToCreeper <= blast * 2.5D;
    }

    /**
     * Interception priority: a creeper closing on the owner outranks other
     * targets, because letting it arrive costs the owner far more than the
     * companion loses by breaking off whatever else it was doing.
     */
    public static boolean shouldInterceptForOwner(double ownerDistanceToCreeper, boolean charged) {
        return ownerDistanceToCreeper <= blastRadius(charged) + 4.0D;
    }
}
