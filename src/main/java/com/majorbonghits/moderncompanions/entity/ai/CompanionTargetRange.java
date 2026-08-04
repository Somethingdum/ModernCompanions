package com.majorbonghits.moderncompanions.entity.ai;

import com.majorbonghits.moderncompanions.core.ModConfig;

/**
 * Single source of truth for how far a companion notices hostiles.
 *
 * <p>Minecraft derives a mob's path search radius from {@code FOLLOW_RANGE}, and
 * companions need that to be large so they can walk long distances instead of
 * teleporting. Target goals default to the same attribute, which would make a
 * long-legged companion aggro half a biome, so every companion target goal
 * overrides its follow distance with this value instead.
 */
public final class CompanionTargetRange {
    private CompanionTargetRange() {}

    public static double blocks() {
        return ModConfig.safeGet(ModConfig.PERCEPTION_TARGET_RANGE).doubleValue();
    }
}
