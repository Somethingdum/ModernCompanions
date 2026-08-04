package com.majorbonghits.moderncompanions.entity.ai;

import com.majorbonghits.moderncompanions.core.CompanionCreeperPolicy;
import com.majorbonghits.moderncompanions.core.ModConfig;
import com.majorbonghits.moderncompanions.entity.AbstractHumanCompanionEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Fights creepers the way a competent player does: close in, hit, and step out
 * of the blast while the fuse burns, then re-enter once it stops swelling.
 *
 * <p>This deliberately replaces the old flee-on-sight behavior. It holds only
 * the MOVE flag, so the class attack goal keeps swinging and shooting; this goal
 * owns positioning alone. It also takes priority over ordinary movement when a
 * swelling creeper is about to catch the owner, in which case the companion
 * interposes rather than saving itself.
 */
public class CreeperTacticsGoal extends Goal {
    private static final double INTERCEPT_SPEED = 1.3D;
    private static final double BACK_OFF_SPEED = 1.4D;
    private static final int REPATH_INTERVAL_TICKS = 4;

    private final AbstractHumanCompanionEntity companion;
    private Creeper creeper;
    private int repathCooldown;

    public CreeperTacticsGoal(AbstractHumanCompanionEntity companion) {
        this.companion = companion;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (companion.isOrderedToSit()) return false;
        CompanionCreeperPolicy policy = ModConfig.safeGet(ModConfig.CREEPER_POLICY);
        if (policy == CompanionCreeperPolicy.AVOID) return false;

        Creeper candidate = nearestRelevantCreeper();
        if (candidate == null) return false;
        this.creeper = candidate;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return creeper != null && creeper.isAlive() && !companion.isOrderedToSit()
                && ModConfig.safeGet(ModConfig.CREEPER_POLICY) != CompanionCreeperPolicy.AVOID
                && companion.distanceTo(creeper) <= CompanionTargetRange.blocks();
    }

    @Override
    public void start() {
        repathCooldown = 0;
    }

    @Override
    public void stop() {
        creeper = null;
        companion.getNavigation().stop();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (creeper == null) return;
        companion.getLookControl().setLookAt(creeper, 30.0F, 30.0F);

        boolean swelling = isSwelling(creeper);
        boolean charged = creeper.isPowered();
        boolean ranged = hasRangedWeapon();
        double distance = companion.distanceTo(creeper);

        LivingEntity owner = companion.getOwner();
        if (owner != null && owner.level() == companion.level()) {
            double ownerDistance = owner.distanceTo(creeper);
            if (CreeperEngagementRules.shouldBodyBlockForOwner(swelling, charged, ownerDistance, distance)) {
                bodyBlock(owner);
                return;
            }
        }

        if (CreeperEngagementRules.shouldBackOff(ranged, swelling, charged, distance)) {
            backAwayFacing(charged);
            return;
        }

        // Not swelling, or safely outside the blast: let the class attack goal work
        // and only close the gap if the creeper is drifting out of reach.
        if (!ranged && distance > 3.0D && throttle()) {
            companion.getNavigation().moveTo(creeper, INTERCEPT_SPEED);
        }
    }

    /** Move between the owner and the creeper rather than away from the blast. */
    private void bodyBlock(LivingEntity owner) {
        Vec3 fromCreeperToOwner = owner.position().subtract(creeper.position());
        if (fromCreeperToOwner.lengthSqr() < 1.0E-4D) return;
        Vec3 interpose = creeper.position().add(fromCreeperToOwner.normalize().scale(1.2D));
        if (throttle()) {
            companion.getNavigation().moveTo(interpose.x, interpose.y, interpose.z, INTERCEPT_SPEED);
        }
    }

    /**
     * Retreat directly away from the fuse, staying oriented on the creeper. This
     * is spacing, not fleeing: the companion re-engages the moment the fuse stops.
     */
    private void backAwayFacing(boolean charged) {
        Vec3 away = companion.position().subtract(creeper.position());
        if (away.lengthSqr() < 1.0E-4D) {
            away = new Vec3(1.0D, 0.0D, 0.0D);
        }
        Vec3 retreat = creeper.position().add(away.normalize().scale(CreeperEngagementRules.safeDistance(charged)));
        if (throttle()) {
            companion.getNavigation().moveTo(retreat.x, retreat.y, retreat.z, BACK_OFF_SPEED);
        }
    }

    private boolean throttle() {
        if (--repathCooldown > 0) return false;
        repathCooldown = REPATH_INTERVAL_TICKS;
        return true;
    }

    /**
     * Only creepers this companion is actually dealing with matter: its own
     * target, or one that is closing on the owner and has to be intercepted.
     */
    private Creeper nearestRelevantCreeper() {
        if (companion.getTarget() instanceof Creeper targeted && targeted.isAlive()) {
            return targeted;
        }
        LivingEntity owner = companion.getOwner();
        if (owner == null || owner.level() != companion.level()) return null;

        double range = CompanionTargetRange.blocks();
        Creeper best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Creeper candidate : companion.level().getEntitiesOfClass(Creeper.class,
                companion.getBoundingBox().inflate(range), Creeper::isAlive)) {
            double ownerDistance = owner.distanceTo(candidate);
            if (!CreeperEngagementRules.shouldInterceptForOwner(ownerDistance, candidate.isPowered())) continue;
            double distance = companion.distanceTo(candidate);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return best;
    }

    /** A lit fuse; getSwellDir is positive while the creeper is counting down. */
    private static boolean isSwelling(Creeper creeper) {
        return creeper.getSwellDir() > 0 || creeper.isIgnited();
    }

    private boolean hasRangedWeapon() {
        return companion.getMainHandItem().getItem() instanceof ProjectileWeaponItem
                || companion.getMainHandItem().getItem() instanceof BowItem
                || companion.getMainHandItem().getItem() instanceof CrossbowItem
                || companion instanceof com.majorbonghits.moderncompanions.entity.magic.AbstractMageCompanion;
    }
}
