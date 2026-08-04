package com.majorbonghits.moderncompanions.core;

/** How companions handle creepers. */
public enum CompanionCreeperPolicy {
    /** Kill creepers, spacing out of blast range while the fuse burns. */
    ENGAGE,
    /** Only companions with a ranged weapon engage; melee keeps its distance. */
    RANGED_ONLY,
    /** Original behavior: flee from creepers and never target them. */
    AVOID
}
