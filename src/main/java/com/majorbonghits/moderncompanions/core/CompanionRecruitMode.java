package com.majorbonghits.moderncompanions.core;

/** How an unowned companion joins the player. */
public enum CompanionRecruitMode {
    /** Any interaction with an unowned companion recruits it immediately. */
    INSTANT,
    /** Only a sneaking interaction recruits, so you can talk to residents you are not taking. */
    HANDSHAKE
}
