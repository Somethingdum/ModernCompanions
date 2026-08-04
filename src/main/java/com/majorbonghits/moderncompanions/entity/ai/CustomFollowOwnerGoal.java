package com.majorbonghits.moderncompanions.entity.ai;

import com.majorbonghits.moderncompanions.core.CompanionTeleportPolicy;
import com.majorbonghits.moderncompanions.core.ModConfig;
import com.majorbonghits.moderncompanions.entity.AbstractHumanCompanionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Follow-owner goal that walks rather than teleports.
 *
 * <p>Continuation deliberately does not depend on the navigator having an
 * unfinished path: the goal keeps running, and keeps repathing, until the
 * companion is actually back inside its return radius. A companion that cannot
 * currently find a route now falls back to steering toward the owner and
 * accumulating a routeless timer, which is what the teleport policy consults,
 * instead of stopping and immediately blinking to the owner's side.
 */
public class CustomFollowOwnerGoal extends Goal {
    private static final int TELEPORT_ATTEMPTS = 10;
    private static final int TELEPORT_RANGE = 3;
    private static final int RECALC_INTERVAL_TICKS = 10;
    /** 120-degree view cone; a teleport inside it would be visible to the owner. */
    private static final double COS_HALF_VIEW_CONE = Math.cos(Math.toRadians(60.0D));
    private static final double CATCH_UP_DISTANCE = 24.0D;

    private final AbstractHumanCompanionEntity companion;
    private final double speedModifier;
    private final boolean teleport;
    private LivingEntity owner;
    private int timeToRecalc;
    /** How long the navigator has failed to produce a usable path to the owner. */
    private int ticksWithoutRoute;
    private int ticksSinceTeleport = Integer.MAX_VALUE / 2;

    public CustomFollowOwnerGoal(AbstractHumanCompanionEntity companion, double speed, boolean teleport) {
        this.companion = companion;
        this.speedModifier = speed;
        this.teleport = teleport;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!companion.isFollowing() || companion.isOrderedToSit()) {
            return false;
        }
        LivingEntity livingentity = companion.getOwner();
        if (livingentity == null || livingentity.isSpectator() || livingentity.level() != companion.level()) {
            return false;
        }
        if (companion.distanceToSqr(livingentity) < leashDistanceSquared()) {
            return false;
        }
        this.owner = livingentity;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return owner != null
                && companion.isFollowing()
                && !companion.isOrderedToSit()
                && owner.level() == companion.level()
                && companion.distanceToSqr(owner) > returnDistanceSquared();
    }

    @Override
    public void start() {
        ticksWithoutRoute = 0;
        timeToRecalc = 0;
    }

    @Override
    public void stop() {
        this.owner = null;
        this.ticksWithoutRoute = 0;
        this.companion.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (ticksSinceTeleport < Integer.MAX_VALUE / 2) {
            ticksSinceTeleport++;
        }
        if (owner == null || owner.level() != companion.level()) {
            return;
        }

        companion.getLookControl().setLookAt(owner, 10.0F, companion.getMaxHeadXRot());

        if (--timeToRecalc > 0) {
            return;
        }
        timeToRecalc = RECALC_INTERVAL_TICKS;

        double distance = companion.distanceTo(owner);
        boolean routed = moveTowardOwner(distance);
        // A failed path is what earns a last-resort teleport, so track it rather
        // than teleporting the moment the companion is merely far away.
        ticksWithoutRoute = routed ? 0 : ticksWithoutRoute + RECALC_INTERVAL_TICKS;

        if (teleport && shouldTeleportNow(distance) && tryTeleportCloseToOwner()) {
            ticksWithoutRoute = 0;
            ticksSinceTeleport = 0;
        }
    }

    /**
     * Aims for the edge of the follow radius rather than the owner's feet, so
     * companions settle around the player instead of shoving into them.
     *
     * @return whether the navigator accepted a path
     */
    private boolean moveTowardOwner(double distance) {
        double speed = followSpeed(distance);
        Vec3 direction = companion.position().subtract(owner.position()).multiply(1.0D, 0.0D, 1.0D).normalize();
        if (direction.lengthSqr() < 1.0E-4D) {
            return companion.getNavigation().moveTo(owner, speed);
        }
        Vec3 returnPoint = owner.position().add(direction.scale(returnDistance()));
        if (companion.getNavigation().moveTo(returnPoint.x, returnPoint.y, returnPoint.z, speed)) {
            return true;
        }
        // The rally point may be inside a wall or off a ledge; the owner is a
        // coarser but usually reachable fallback before declaring no route.
        return companion.getNavigation().moveTo(owner, speed);
    }

    /** Far-behind companions hustle to catch up instead of relying on teleports. */
    private double followSpeed(double distance) {
        return distance > CATCH_UP_DISTANCE && companion.getTarget() == null
                ? speedModifier * ModConfig.safeGet(ModConfig.NAV_CATCH_UP_MULTIPLIER)
                : speedModifier;
    }

    private boolean shouldTeleportNow(double distance) {
        CompanionTeleportPolicy policy = ModConfig.safeGet(ModConfig.NAV_TELEPORT_POLICY);
        if (policy == CompanionTeleportPolicy.LEGACY) {
            // Legacy behavior stayed gated behind the original teleportLeash toggle.
            return ModConfig.safeGet(ModConfig.TELEPORT_LEASH)
                    && companion.distanceToSqr(owner) >= FollowLeashRules.teleportDistanceSquared(companion.getPatrolRadius());
        }
        return CompanionTeleportRules.shouldTeleport(policy,
                distance,
                ModConfig.safeGet(ModConfig.NAV_TELEPORT_MIN_DISTANCE),
                ticksWithoutRoute,
                ModConfig.safeGet(ModConfig.NAV_TELEPORT_NO_ROUTE_TICKS),
                companion.getTarget() != null,
                isVisibleToOwner(),
                ticksSinceTeleport,
                ModConfig.safeGet(ModConfig.NAV_TELEPORT_COOLDOWN_TICKS));
    }

    /** A teleport the owner cannot see does not break immersion. */
    private boolean isVisibleToOwner() {
        Vec3 look = owner.getLookAngle();
        Vec3 toCompanion = companion.position().subtract(owner.position());
        boolean inCone = CompanionTeleportRules.withinViewCone(
                look.x, look.z, toCompanion.x, toCompanion.z, COS_HALF_VIEW_CONE);
        return inCone && companion.hasLineOfSight(owner);
    }

    private double leashDistanceSquared() {
        double radius = Math.max(1.0D, companion.getPatrolRadius());
        return radius * radius;
    }

    private double returnDistanceSquared() {
        double distance = returnDistance();
        return distance * distance;
    }

    private double returnDistance() {
        return Math.max(1.0D, companion.getPatrolRadius() * 0.75D);
    }

    /**
     * Mimics vanilla pet recall: look for a nearby open spot around the owner before teleporting.
     */
    private boolean tryTeleportCloseToOwner() {
        BlockPos ownerPos = owner.blockPosition();
        int radius = Math.max(1, Math.min(TELEPORT_RANGE, companion.getPatrolRadius()));
        for (int attempt = 0; attempt < TELEPORT_ATTEMPTS; attempt++) {
            int dx = randomBetween(-radius, radius);
            int dz = randomBetween(-radius, radius);
            if (dx * dx + dz * dz > radius * radius) {
                continue;
            }
            BlockPos targetPos = ownerPos.offset(dx, 0, dz);
            if (isTeleportFriendly(targetPos)) {
                companion.teleportTo(targetPos.getX() + 0.5D, targetPos.getY(), targetPos.getZ() + 0.5D);
                companion.getNavigation().stop();
                return true;
            }
        }
        return false;
    }

    private boolean isTeleportFriendly(BlockPos pos) {
        return companion.level().isEmptyBlock(pos)
                && companion.level().isEmptyBlock(pos.above())
                && companion.level().noCollision(companion, companion.getBoundingBox().move(
                pos.getX() - companion.getX(),
                pos.getY() - companion.getY(),
                pos.getZ() - companion.getZ()));
    }

    private int randomBetween(int min, int max) {
        return companion.getRandom().nextInt(max - min + 1) + min;
    }
}
