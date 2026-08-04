package com.majorbonghits.moderncompanions.core;

/**
 * How aggressively a following companion may fall back to teleporting when it
 * cannot walk to its owner. Teleporting breaks immersion, so the default only
 * allows it as a genuine last resort and never inside the owner's view.
 */
public enum CompanionTeleportPolicy {
    /** Never teleport for leash reasons; companions walk or report that they are stuck. */
    NEVER,
    /** Teleport only when far away, routeless, out of combat, and outside the owner's view. */
    LAST_RESORT,
    /** Original behavior: teleport as soon as the leash distance is exceeded. */
    LEGACY
}
