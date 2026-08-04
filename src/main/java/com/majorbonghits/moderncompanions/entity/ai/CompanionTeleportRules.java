package com.majorbonghits.moderncompanions.entity.ai;

import com.majorbonghits.moderncompanions.core.CompanionTeleportPolicy;

/**
 * Pure decision boundary for the follow-leash teleport, kept dependency-free so
 * it can be regression-checked without a world.
 *
 * <p>LAST_RESORT is the interesting case: every condition must hold at once, so
 * a teleport only happens when the companion genuinely cannot walk back and the
 * owner cannot see it happen.
 */
public final class CompanionTeleportRules {
    private CompanionTeleportRules() {}

    /**
     * @param policy              configured teleport policy
     * @param distance            current distance to the owner, in blocks
     * @param minDistance         configured minimum distance before a teleport is considered
     * @param ticksWithoutRoute   how long the navigator has failed to produce a usable path
     * @param requiredNoRouteTicks configured routeless duration before a teleport is considered
     * @param inCombat            whether the companion currently has a target
     * @param visibleToOwner      whether the companion is inside the owner's view cone
     * @param ticksSinceTeleport  ticks elapsed since this companion last teleported
     * @param cooldownTicks       configured minimum spacing between teleports
     */
    public static boolean shouldTeleport(CompanionTeleportPolicy policy,
                                         double distance,
                                         double minDistance,
                                         int ticksWithoutRoute,
                                         int requiredNoRouteTicks,
                                         boolean inCombat,
                                         boolean visibleToOwner,
                                         int ticksSinceTeleport,
                                         int cooldownTicks) {
        return switch (policy) {
            case NEVER -> false;
            case LEGACY -> true;
            case LAST_RESORT -> distance >= minDistance
                    && ticksWithoutRoute >= requiredNoRouteTicks
                    && !inCombat
                    && !visibleToOwner
                    && ticksSinceTeleport >= cooldownTicks;
        };
    }

    /**
     * Whether a point lies inside the owner's forward view cone. Used to keep a
     * last-resort teleport from ever happening on screen.
     *
     * @param lookX          owner look vector X (normalized, horizontal)
     * @param lookZ          owner look vector Z (normalized, horizontal)
     * @param toCompanionX   owner-to-companion vector X (horizontal)
     * @param toCompanionZ   owner-to-companion vector Z (horizontal)
     * @param cosHalfFov     cosine of half the view cone; larger means a narrower cone
     */
    public static boolean withinViewCone(double lookX, double lookZ,
                                         double toCompanionX, double toCompanionZ,
                                         double cosHalfFov) {
        double length = Math.sqrt(toCompanionX * toCompanionX + toCompanionZ * toCompanionZ);
        if (length < 1.0E-4D) return true; // standing on the owner counts as visible
        double lookLength = Math.sqrt(lookX * lookX + lookZ * lookZ);
        if (lookLength < 1.0E-4D) return true; // no usable facing; assume visible
        double dot = (lookX * toCompanionX + lookZ * toCompanionZ) / (length * lookLength);
        return dot >= cosHalfFov;
    }
}
