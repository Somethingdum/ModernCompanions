package com.majorbonghits.moderncompanions.entity.ai;

/** No-world regression check for the sensing model's limits. */
public final class PerceptionRulesTest {
    private static final double BASE = 24.0D;

    public static void main(String[] args) {
        testModifiersReduceRange();
        testIntelligenceIsCapped();
        testFieldOfView();
        testMemoryDecay();
        testReactionDelay();
    }

    private static void testModifiersReduceRange() {
        double clear = PerceptionRules.sightRange(BASE, false, false, false, false, 4);
        assert clear == BASE : "an unmodified companion sees exactly its configured range";

        // Each condition must strictly reduce what the companion notices.
        assert PerceptionRules.sightRange(BASE, true, false, false, false, 4) < clear : "darkness";
        assert PerceptionRules.sightRange(BASE, false, true, false, false, 4) < clear : "rain";
        assert PerceptionRules.sightRange(BASE, false, false, true, false, 4) < clear : "thunder";
        assert PerceptionRules.sightRange(BASE, false, false, false, true, 4) < clear : "sneaking";

        // A storm is worse than plain rain, and modifiers stack.
        assert PerceptionRules.sightRange(BASE, false, true, true, false, 4)
                < PerceptionRules.sightRange(BASE, false, true, false, false, 4);
        assert PerceptionRules.sightRange(BASE, true, true, true, true, 4)
                < PerceptionRules.sightRange(BASE, true, false, false, false, 4);

        // Range never collapses to nothing, so a companion is never fully blind.
        assert PerceptionRules.sightRange(BASE, true, false, true, true, 1) >= 1.0D;
    }

    private static void testIntelligenceIsCapped() {
        // Smart companions notice more, but only a little; this is not a wallhack.
        assert PerceptionRules.intelligenceMultiplier(4) == 1.0D;
        assert PerceptionRules.intelligenceMultiplier(20) <= 1.20D;
        assert PerceptionRules.intelligenceMultiplier(100) <= 1.20D : "bonus must stay capped";
        assert PerceptionRules.intelligenceMultiplier(-100) >= 0.80D : "penalty must stay capped";
        assert PerceptionRules.intelligenceMultiplier(10) > PerceptionRules.intelligenceMultiplier(4);
    }

    private static void testFieldOfView() {
        // Directly ahead at distance: seen.
        assert PerceptionRules.withinFieldOfView(0.0D, 1.0D, 0.0D, 20.0D, 20.0D);
        // Directly behind at distance: not seen. Sneaking up from behind works.
        assert !PerceptionRules.withinFieldOfView(0.0D, 1.0D, 0.0D, -20.0D, 20.0D);
        // Behind but very close: noticed anyway.
        assert PerceptionRules.withinFieldOfView(0.0D, 1.0D, 0.0D, -3.0D, 3.0D);
        // Perpendicular at distance falls outside a 120-degree cone.
        assert !PerceptionRules.withinFieldOfView(0.0D, 1.0D, 20.0D, 0.0D, 20.0D);
        // Degenerate facing fails safe to "seen" rather than making them blind.
        assert PerceptionRules.withinFieldOfView(0.0D, 0.0D, 0.0D, 20.0D, 20.0D);
    }

    private static void testMemoryDecay() {
        assert PerceptionRules.memoryConfidence(0, 200) == 1.0F;
        assert PerceptionRules.memoryConfidence(200, 200) == 0.0F;
        assert PerceptionRules.memoryConfidence(400, 200) == 0.0F;
        // Strictly decreasing while it lasts.
        assert PerceptionRules.memoryConfidence(50, 200) > PerceptionRules.memoryConfidence(150, 200);
        // Hearsay is worth less than seeing it yourself.
        assert PerceptionRules.relayedConfidence(1.0F) < 1.0F;
        assert PerceptionRules.relayedConfidence(0.0F) == 0.0F;
    }

    private static void testReactionDelay() {
        // Never instant, never absurdly slow, and smarter means quicker.
        for (int intelligence = -20; intelligence <= 40; intelligence++) {
            int ticks = PerceptionRules.reactionTicks(intelligence);
            assert ticks >= 3 && ticks <= 10 : "reaction delay must stay bounded at INT " + intelligence;
        }
        assert PerceptionRules.reactionTicks(20) < PerceptionRules.reactionTicks(4);
    }
}
