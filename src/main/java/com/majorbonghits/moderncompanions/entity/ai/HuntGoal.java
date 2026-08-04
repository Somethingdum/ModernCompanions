package com.majorbonghits.moderncompanions.entity.ai;

import com.majorbonghits.moderncompanions.ModernCompanions;
import com.majorbonghits.moderncompanions.entity.AbstractHumanCompanionEntity;
import com.majorbonghits.moderncompanions.entity.job.CompanionJob;
import com.majorbonghits.moderncompanions.core.ModConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;

/**
 * Hunts animals when the manual Hunt toggle is on.
 *
 * <p>Eligibility is data-driven rather than a fixed list of six vanilla mobs.
 * Any adult, untamed animal qualifies by default, so modded livestock works
 * without a code change, and the same {@code hunter_allowed} and
 * {@code hunter_denied} entity tags the Hunter profession uses apply here too.
 * The legacy {@code huntMobs} config list still adds entries, so an existing
 * configuration keeps working.
 */
public class HuntGoal extends NearestAttackableTargetGoal<LivingEntity> {
    private static final TagKey<net.minecraft.world.entity.EntityType<?>> DENIED_ANIMALS = TagKey.create(Registries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(ModernCompanions.MOD_ID, "hunter_denied"));
    private static final TagKey<net.minecraft.world.entity.EntityType<?>> ALLOWED_ANIMALS = TagKey.create(Registries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(ModernCompanions.MOD_ID, "hunter_allowed"));

    private final AbstractHumanCompanionEntity companion;

    public HuntGoal(AbstractHumanCompanionEntity companion) {
        super(companion, LivingEntity.class, true, HuntGoal::isHuntTarget);
        this.companion = companion;
    }

    private static boolean isHuntTarget(LivingEntity entity) {
        // An explicit deny always wins, so a pack can protect a specific creature.
        if (entity.getType().is(DENIED_ANIMALS)) return false;
        // Never hunt anything that belongs to somebody.
        if (entity instanceof TamableAnimal tameable && tameable.isTame()) return false;
        // Leave the young alone; killing them is both unhelpful and unpleasant.
        if (entity instanceof Animal animal && animal.isBaby()) return false;

        if (entity.getType().is(ALLOWED_ANIMALS)) return true;
        if (entity instanceof Animal) return true;
        // Legacy config list, retained so existing setups behave as before.
        return ModConfig.safeGet(ModConfig.HUNT_MOBS)
                .contains(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
    }

    @Override
    public boolean canUse() {
        // HunterJobGoal owns profession target selection; this remains manual hunting only.
        return companion.getJob() != CompanionJob.HUNTER
                && companion.isHunting() && super.canUse();
    }

    /** Hunt range stays independent of the (much larger) pathfinding FOLLOW_RANGE. */
    @Override
    protected double getFollowDistance() {
        return CompanionTargetRange.blocks();
    }
}
