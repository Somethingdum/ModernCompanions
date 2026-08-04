package com.majorbonghits.moderncompanions.core;

/** How readily a wounded companion breaks off to heal. */
public enum CompanionSurvivalProfile {
    /** Never breaks off. Fights to the death every time. */
    RECKLESS,
    /** Breaks off at the configured health threshold, heals, and returns. */
    DISCIPLINED,
    /** Breaks off earlier, trading uptime for survivability. */
    CAUTIOUS
}
