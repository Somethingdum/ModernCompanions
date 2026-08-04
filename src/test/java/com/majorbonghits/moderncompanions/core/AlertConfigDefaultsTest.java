package com.majorbonghits.moderncompanions.core;

import java.util.List;

/** Regression check for the Alert exclusion migrations in both directions. */
public final class AlertConfigDefaultsTest {
    public static void main(String[] args) {
        testLegacyCreeperInjection();
        testCreeperRemoval();
    }

    /** The historical migration that added the creeper default. */
    private static void testLegacyCreeperInjection() {
        assert AlertExclusionDefaults.withDefaultCreeper(List.of()).equals(List.of("minecraft:creeper"));
        assert AlertExclusionDefaults.withDefaultCreeper(List.of("minecraft:ender_dragon"))
                .equals(List.of("minecraft:ender_dragon", "minecraft:creeper"));
        assert AlertExclusionDefaults.withDefaultCreeper(List.of("minecraft:creeper")).equals(List.of("minecraft:creeper"));
    }

    /** The reverse migration that lets companions fight creepers again. */
    private static void testCreeperRemoval() {
        assert AlertExclusionDefaults.withoutInjectedCreeper(List.of("minecraft:creeper")).equals(List.of());
        // Every other exclusion the player chose must survive.
        assert AlertExclusionDefaults.withoutInjectedCreeper(List.of("minecraft:ender_dragon", "minecraft:creeper"))
                .equals(List.of("minecraft:ender_dragon"));
        assert AlertExclusionDefaults.withoutInjectedCreeper(List.of("minecraft:warden"))
                .equals(List.of("minecraft:warden"));
        assert AlertExclusionDefaults.withoutInjectedCreeper(List.of()).equals(List.of());
        // Round trip: injecting then removing returns the original list.
        List<String> original = List.of("minecraft:ender_dragon", "minecraft:warden");
        assert AlertExclusionDefaults.withoutInjectedCreeper(
                AlertExclusionDefaults.withDefaultCreeper(original)).equals(original);
    }
}
