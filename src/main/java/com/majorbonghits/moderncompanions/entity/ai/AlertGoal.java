package com.majorbonghits.moderncompanions.entity.ai;

import com.majorbonghits.moderncompanions.ModernCompanions;
import com.majorbonghits.moderncompanions.core.ModConfig;
import com.majorbonghits.moderncompanions.entity.AbstractHumanCompanionEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * Picks a fight, using only what the companion actually knows.
 *
 * <p>This replaces vanilla nearest-valid target selection. Two things follow
 * from that. Companions can no longer notice something they have not sensed, so
 * darkness, weather, cover, and a sneaking player all work on them. And target
 * choice becomes a judgement rather than a distance comparison, so whatever is
 * hurting the owner gets dealt with before a nearer but harmless zombie.
 *
 * <p>The existing safety filters are unchanged: the unsafe-target tag and the
 * player-configured exclusion list still apply.
 */
public class AlertGoal extends Goal {
    private static final TagKey<net.minecraft.world.entity.EntityType<?>> UNSAFE_ALERT_TARGETS = TagKey.create(Registries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(ModernCompanions.MOD_ID, "alert_unsafe"));

    private final AbstractHumanCompanionEntity companion;
    /** Reaction delay, so companions are never frame-perfect on acquisition. */
    private int reactionDelay;
    private LivingEntity pending;

    public AlertGoal(AbstractHumanCompanionEntity companion) {
        this.companion = companion;
        this.setFlags(EnumSet.of(Flag.TARGET));
    }

    public static boolean isAlertTarget(LivingEntity entity) {
        String id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
        return AlertTargetRules.shouldTarget(entity.getType().getCategory() == MobCategory.MONSTER,
                entity.getType().is(UNSAFE_ALERT_TARGETS), ModConfig.safeGet(ModConfig.ALERT_EXCLUDED_MOBS).contains(id));
    }

    @Override
    public boolean canUse() {
        if (!companion.isAlert() || companion.isOrderedToSit()) return false;

        LivingEntity candidate = companion.perception().bestTarget(AlertGoal::isAlertTarget);
        if (candidate == null) {
            reactionDelay = 0;
            pending = null;
            return false;
        }

        LivingEntity current = companion.getTarget();
        if (current != null && current.isAlive()) {
            // Committed to a fight: only a decisively better target is worth switching to.
            if (!ThreatAssessment.shouldSwitch(companion.perception().scoreOf(current),
                    companion.perception().scoreOf(candidate))) {
                return false;
            }
        }

        // Spotting something and reacting to it are not the same instant.
        if (candidate != pending) {
            pending = candidate;
            reactionDelay = PerceptionRules.reactionTicks(companion.getIntelligence());
            return false;
        }
        return reactionDelay-- <= 0;
    }

    @Override
    public void start() {
        companion.setTarget(pending);
        reactionDelay = 0;
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = companion.getTarget();
        if (target == null || !target.isAlive() || !companion.isAlert()) return false;
        if (!companion.canHarm(target)) return false;
        // Holding a target the companion has entirely lost track of would be the
        // omniscience this layer exists to prevent; memory decay ends the pursuit.
        return companion.perception().knowsAbout(target);
    }

    @Override
    public void stop() {
        companion.setTarget(null);
        pending = null;
        reactionDelay = 0;
    }
}
