package com.majorbonghits.moderncompanions.squad;

/**
 * A companion's standing posture.
 *
 * <p>Storage stays on the existing follow/patrol/guard synced booleans, which
 * every goal, job, and HUD provider already reads. What this adds is a single
 * consistent view over them: {@code setStance} is the only way to change
 * posture, so the three booleans can no longer drift into a combination like
 * following-and-guarding that the old scattered setters could produce.
 */
public enum CompanionStance {
    /** Stays with the owner and defends them. */
    ESCORT("escort"),
    /** Wanders within its radius around a fixed point. */
    PATROL("patrol"),
    /** Holds a fixed post and defends it. */
    WARD("ward"),
    /** Stands fast, takes no movement orders. */
    HOLD("hold");

    private final String id;

    CompanionStance(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public String translationKey() {
        return "stance.modern_companions." + id;
    }

    public static CompanionStance fromId(String id) {
        if (id != null) {
            for (CompanionStance stance : values()) {
                if (stance.id.equals(id)) return stance;
            }
        }
        return ESCORT;
    }

    /**
     * Derives the stance from the legacy booleans. Guard wins over patrol which
     * wins over follow, matching the precedence the movement goal priority bands
     * already enforce, so a legacy save with more than one flag set resolves the
     * same way it behaves.
     */
    public static CompanionStance derive(boolean following, boolean patrolling, boolean guarding) {
        if (guarding) return WARD;
        if (patrolling) return PATROL;
        if (following) return ESCORT;
        return HOLD;
    }

    public boolean following() {
        return this == ESCORT;
    }

    public boolean patrolling() {
        return this == PATROL;
    }

    public boolean guarding() {
        return this == WARD;
    }

    /** Stances that pin the companion to a place, and therefore need an anchor recorded. */
    public boolean needsAnchor() {
        return this == PATROL || this == WARD;
    }
}
