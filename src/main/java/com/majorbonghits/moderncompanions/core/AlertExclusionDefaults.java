package com.majorbonghits.moderncompanions.core;

import java.util.ArrayList;
import java.util.List;

/** Dependency-free default handling for Alert exclusions and its regression check. */
final class AlertExclusionDefaults {
    static final String CREEPER_ID = "minecraft:creeper";

    private AlertExclusionDefaults() {}

    /**
     * Adds the original safety default while retaining every player-configured exclusion.
     *
     * <p>Retained only so the historical migration keeps its regression coverage;
     * creepers are engaged rather than avoided now, and
     * {@link #withoutInjectedCreeper(List)} undoes this exactly once.
     */
    static List<String> withDefaultCreeper(List<? extends String> excludedMobs) {
        if (excludedMobs.contains(CREEPER_ID)) return List.copyOf(excludedMobs);

        List<String> migrated = new ArrayList<>(excludedMobs);
        migrated.add(CREEPER_ID);
        return List.copyOf(migrated);
    }

    /**
     * Removes the creeper entry that the earlier migration injected, so companions
     * will actually fight creepers. Every other exclusion is preserved untouched.
     *
     * <p>This runs once, guarded by its own marker, so a player who deliberately
     * re-adds creepers afterwards keeps that choice.
     */
    static List<String> withoutInjectedCreeper(List<? extends String> excludedMobs) {
        List<String> migrated = new ArrayList<>(excludedMobs);
        migrated.remove(CREEPER_ID);
        return List.copyOf(migrated);
    }
}
