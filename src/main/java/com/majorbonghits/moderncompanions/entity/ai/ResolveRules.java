package com.majorbonghits.moderncompanions.entity.ai;

import com.majorbonghits.moderncompanions.core.CompanionSurvivalProfile;

/**
 * Pure decision boundary for breaking off a fight, kept dependency-free so the
 * anti-cowardice guarantees can be regression-checked without a world.
 *
 * <p>The design goal is a companion that tries not to die without ever reading
 * as a coward. Withdrawal is a fighting withdrawal: it happens only when it buys
 * something, and five conditions override it outright. The goal that consumes
 * this moves the companion backwards while still facing the enemy, so even when
 * it does break off it never turns and runs.
 */
public final class ResolveRules {
    /** Owner fights within this distance and the companion stays, at any health. */
    public static final double OWNER_SUPPORT_RANGE = 12.0D;
    /** How much earlier a cautious companion breaks off. */
    private static final double CAUTIOUS_THRESHOLD = 0.45D;
    /** Distance a withdrawing companion tries to open up before healing. */
    private static final double WITHDRAW_DISTANCE = 8.0D;

    private ResolveRules() {}

    /**
     * @param healthFraction        current health over max health
     * @param configuredThreshold   health fraction below which a disciplined companion breaks off
     * @param ownerFightingNearby   owner is in combat within {@link #OWNER_SUPPORT_RANGE}
     * @param fightingCreeper       current target is a creeper
     * @param cornered              no retreat route and no ally to fall back behind
     * @param avenging              owner was recently downed or killed
     * @param profile               configured survival profile
     */
    public static boolean shouldWithdraw(double healthFraction,
                                         double configuredThreshold,
                                         boolean ownerFightingNearby,
                                         boolean fightingCreeper,
                                         boolean cornered,
                                         boolean avenging,
                                         CompanionSurvivalProfile profile) {
        // Guarantee 1: never abandon the owner mid-fight, at any health.
        if (ownerFightingNearby) return false;
        // Guarantee 5: avenging a downed owner suspends self-preservation entirely.
        if (avenging) return false;
        // Guarantee 3: creepers are spaced, never fled; CreeperTacticsGoal owns that.
        if (fightingCreeper) return false;
        // Guarantee 4: with nowhere to go and nobody to fall back behind, fight.
        if (cornered) return false;
        // Guarantee 2 is a property of how the withdrawal moves, not whether it happens.

        return healthFraction <= withdrawThreshold(configuredThreshold, profile);
    }

    public static double withdrawThreshold(double configuredThreshold, CompanionSurvivalProfile profile) {
        return switch (profile) {
            case RECKLESS -> 0.0D; // health can never fall below zero, so this never triggers
            case DISCIPLINED -> configuredThreshold;
            case CAUTIOUS -> Math.max(configuredThreshold, CAUTIOUS_THRESHOLD);
        };
    }

    /** How far a withdrawing companion tries to open up before it stops retreating. */
    public static double withdrawDistance() {
        return WITHDRAW_DISTANCE;
    }

    /**
     * Whether a companion that already broke off has recovered enough to rejoin.
     * Requires clear headroom over the break-off point so it does not oscillate
     * in and out of the fight one hit at a time.
     */
    public static boolean shouldReengage(double healthFraction,
                                         double configuredThreshold,
                                         CompanionSurvivalProfile profile) {
        double threshold = withdrawThreshold(configuredThreshold, profile);
        return healthFraction >= Math.min(1.0D, threshold + 0.25D);
    }

    /**
     * Second Wind: a one-per-fight damage reduction granted only while actually
     * withdrawing, so breaking off is survivable. This reduces incoming damage
     * and never increases outgoing damage, keeping it outside the power budget.
     */
    public static boolean shouldGrantSecondWind(boolean enabled,
                                                boolean withdrawing,
                                                boolean alreadyUsedThisFight,
                                                double healthFraction) {
        return enabled && withdrawing && !alreadyUsedThisFight && healthFraction <= 0.15D;
    }
}
