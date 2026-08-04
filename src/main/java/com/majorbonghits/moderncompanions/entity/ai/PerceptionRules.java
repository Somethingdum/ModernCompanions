package com.majorbonghits.moderncompanions.entity.ai;

/**
 * Pure sensing model, kept dependency-free so it can be regression-checked
 * without a world.
 *
 * <p>This is the anti-omniscience layer. Companions are meant to be sharp, not
 * clairvoyant, so what they can notice degrades with darkness, weather, and the
 * target sneaking, and it never becomes unlimited no matter how clever they are.
 * Intelligence buys awareness, capped, rather than raw range.
 */
public final class PerceptionRules {
    /** Forward cone half-angle. Anything outside it is only noticed up close. */
    public static final double FOV_COS_HALF_ANGLE = Math.cos(Math.toRadians(60.0D));
    /** Within this distance a companion is aware in every direction. */
    public static final double OMNIDIRECTIONAL_RANGE = 6.0D;
    /** Ceiling on the intelligence bonus, so a clever companion is not a wallhack. */
    private static final double MAX_INT_BONUS = 0.20D;

    private PerceptionRules() {}

    /**
     * Effective sight range in blocks after every modifier.
     *
     * @param baseRange   configured sight range
     * @param darkTarget  target stands in low light and is not burning or glowing
     * @param raining     precipitation at the companion's position
     * @param thundering  storm, which is darker still
     * @param sneaking    target is crouching
     * @param intelligence companion INT stat; 4 is the baseline
     */
    public static double sightRange(double baseRange, boolean darkTarget, boolean raining,
                                    boolean thundering, boolean sneaking, int intelligence) {
        double range = baseRange;
        if (darkTarget) range *= 0.4D;
        if (thundering) {
            range *= 0.5D;
        } else if (raining) {
            range *= 0.7D;
        }
        // Sneaking works on companions the same way it works on vanilla mobs; this
        // is deliberate, and is what keeps them fair to play against and alongside.
        if (sneaking) range *= 0.5D;
        return Math.max(1.0D, range * intelligenceMultiplier(intelligence));
    }

    public static double intelligenceMultiplier(int intelligence) {
        double raw = 1.0D + (intelligence - 4) * 0.02D;
        return Math.max(1.0D - MAX_INT_BONUS, Math.min(1.0D + MAX_INT_BONUS, raw));
    }

    /**
     * Whether a target lies within the companion's field of view. Close targets
     * are noticed regardless of facing; distant ones must be in the forward cone.
     */
    public static boolean withinFieldOfView(double lookX, double lookZ,
                                            double toTargetX, double toTargetZ,
                                            double distance) {
        if (distance <= OMNIDIRECTIONAL_RANGE) return true;
        double targetLength = Math.sqrt(toTargetX * toTargetX + toTargetZ * toTargetZ);
        double lookLength = Math.sqrt(lookX * lookX + lookZ * lookZ);
        if (targetLength < 1.0E-4D || lookLength < 1.0E-4D) return true;
        double dot = (lookX * toTargetX + lookZ * toTargetZ) / (targetLength * lookLength);
        return dot >= FOV_COS_HALF_ANGLE;
    }

    /**
     * Confidence in a contact that has not been seen for a while. Decays to zero
     * so a companion searches where a target was heading rather than tracking it
     * perfectly through walls.
     */
    public static float memoryConfidence(int ticksSinceSeen, int memoryTicks) {
        if (ticksSinceSeen <= 0) return 1.0F;
        if (memoryTicks <= 0 || ticksSinceSeen >= memoryTicks) return 0.0F;
        return 1.0F - (float) ticksSinceSeen / memoryTicks;
    }

    /** A relayed contact is worth acting on, but less than seeing it yourself. */
    public static float relayedConfidence(float originalConfidence) {
        return Math.max(0.0F, originalConfidence * 0.8F);
    }

    /**
     * Reaction delay in ticks between perceiving something and acting on it.
     * Companions are never frame-perfect; this is the main dial that keeps them
     * feeling like people rather than turrets.
     */
    public static int reactionTicks(int intelligence) {
        double raw = 8.0D - (intelligence - 4) * 0.4D;
        return (int) Math.round(Math.max(3.0D, Math.min(10.0D, raw)));
    }
}
