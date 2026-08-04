package com.majorbonghits.moderncompanions.entity.ai;

/**
 * Pure threat scoring, kept dependency-free so target priority can be
 * regression-checked without a world.
 *
 * <p>This is where combat competence comes from. Nothing here changes how hard a
 * companion hits; it changes what they choose to hit. The weights encode a
 * simple set of priorities: whatever is hurting the owner comes first, then
 * things that are about to do something irreversible, then wounded targets worth
 * finishing, and finally proximity. Targets an ally already has in hand are
 * discounted so a squad spreads out instead of dogpiling.
 */
public final class ThreatAssessment {
    // Weights are deliberately far apart in magnitude: the ordering between
    // categories should be stable rather than emergent from close arithmetic.
    private static final double W_ATTACKING_OWNER = 100.0D;
    private static final double W_IMMINENT = 60.0D;
    private static final double W_ATTACKING_SELF = 30.0D;
    private static final double W_WOUNDED = 15.0D;
    private static final double W_ZONE_INTRUSION = 25.0D;
    private static final double W_PROXIMITY = 20.0D;
    private static final double W_ALLY_ENGAGED = 18.0D;
    private static final double W_CONFIDENCE = 10.0D;

    /** Beyond this many allies already on a target, piling on adds nothing. */
    public static final int MAX_CLAIMANTS = 2;

    private ThreatAssessment() {}

    /**
     * @param distance          blocks from the companion to the target
     * @param maxRange          the companion's effective detection range
     * @param attackingOwner    the target is currently fighting the owner
     * @param attackingSelf     the target is currently fighting this companion
     * @param imminent          something irreversible is about to happen, such as a
     *                          creeper fuse or a drawn bow
     * @param healthFraction    target health over max health
     * @param insideZone        the target has crossed into a zone this companion guards
     * @param alliesEngaged     how many squadmates already have this target
     * @param confidence        how sure the companion is this contact is real
     */
    public static double score(double distance,
                               double maxRange,
                               boolean attackingOwner,
                               boolean attackingSelf,
                               boolean imminent,
                               double healthFraction,
                               boolean insideZone,
                               int alliesEngaged,
                               float confidence) {
        double score = 0.0D;
        if (attackingOwner) score += W_ATTACKING_OWNER;
        if (imminent) score += W_IMMINENT;
        if (attackingSelf) score += W_ATTACKING_SELF;
        if (insideZone) score += W_ZONE_INTRUSION;

        score += W_WOUNDED * (1.0D - clamp01(healthFraction));
        score += W_PROXIMITY * proximityTerm(distance, maxRange);
        score += W_CONFIDENCE * clamp01(confidence);

        // Discount, not exclusion: a squad should spread out, but if the dangerous
        // target is the one two allies are on, it can still win on its own merits.
        score -= W_ALLY_ENGAGED * Math.min(alliesEngaged, MAX_CLAIMANTS);
        return score;
    }

    /** Near targets matter more, falling off linearly to nothing at max range. */
    public static double proximityTerm(double distance, double maxRange) {
        if (maxRange <= 0.0D) return 0.0D;
        return clamp01(1.0D - distance / maxRange);
    }

    /**
     * Whether a companion should abandon its current target for a new one.
     * Requires a clear margin so companions commit to a fight instead of
     * flip-flopping between two similar threats every tick.
     */
    public static boolean shouldSwitch(double currentScore, double candidateScore) {
        return candidateScore > currentScore * 1.25D + 5.0D;
    }

    private static double clamp01(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }
}
