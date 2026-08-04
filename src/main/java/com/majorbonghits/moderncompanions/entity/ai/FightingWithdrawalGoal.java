package com.majorbonghits.moderncompanions.entity.ai;

import com.majorbonghits.moderncompanions.core.CompanionSurvivalProfile;
import com.majorbonghits.moderncompanions.core.ModConfig;
import com.majorbonghits.moderncompanions.entity.AbstractHumanCompanionEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;

/**
 * A wounded companion opens distance so it can heal, then rejoins the fight.
 *
 * <p>This is a fighting withdrawal, not a rout, and the difference is the whole
 * point. The companion moves backwards while its look control stays locked on
 * the enemy, so it never turns its back and sprints away. {@link ResolveRules}
 * decides whether withdrawing is permitted at all; five conditions veto it,
 * including the owner fighting nearby, which no amount of damage overrides.
 */
public class FightingWithdrawalGoal extends Goal {
    private static final int REPATH_INTERVAL_TICKS = 5;
    private static final double WITHDRAW_SPEED = 1.2D;
    /** Allies within this range are worth falling back behind. */
    private static final double ALLY_SEARCH_RANGE = 16.0D;

    private final AbstractHumanCompanionEntity companion;
    private LivingEntity threat;
    private int repathCooldown;

    public FightingWithdrawalGoal(AbstractHumanCompanionEntity companion) {
        this.companion = companion;
        // MOVE and LOOK: the companion keeps facing the threat while retreating.
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity current = companion.getTarget();
        if (current == null || !current.isAlive() || companion.isOrderedToSit()) return false;
        if (!evaluate(current)) return false;
        this.threat = current;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (threat == null || !threat.isAlive() || companion.isOrderedToSit()) return false;
        // Recovered enough to be useful again: rejoin rather than hovering at range.
        if (ResolveRules.shouldReengage(healthFraction(), configuredThreshold(), profile())) return false;
        // A veto that becomes true mid-withdrawal (owner joins the fight, cornered)
        // must end the withdrawal immediately.
        return evaluate(threat);
    }

    @Override
    public void start() {
        repathCooldown = 0;
        companion.setWithdrawing(true);
    }

    @Override
    public void stop() {
        threat = null;
        companion.setWithdrawing(false);
        companion.getNavigation().stop();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (threat == null) return;
        // Locked on the threat the entire time; this is what stops it reading as fleeing.
        companion.getLookControl().setLookAt(threat, 30.0F, 30.0F);

        if (--repathCooldown > 0) return;
        repathCooldown = REPATH_INTERVAL_TICKS;

        Vec3 destination = fallBackPoint();
        companion.getNavigation().moveTo(destination.x, destination.y, destination.z, WITHDRAW_SPEED);
    }

    /**
     * Prefer falling back behind a healthy ally, since that puts something between
     * the companion and the threat. Otherwise open straight-line distance.
     */
    private Vec3 fallBackPoint() {
        LivingEntity anchor = nearestHealthyAlly();
        if (anchor != null) {
            Vec3 behind = anchor.position().subtract(threat.position());
            if (behind.lengthSqr() > 1.0E-4D) {
                return anchor.position().add(behind.normalize().scale(2.0D));
            }
        }
        Vec3 away = companion.position().subtract(threat.position());
        if (away.lengthSqr() < 1.0E-4D) {
            away = new Vec3(1.0D, 0.0D, 0.0D);
        }
        return threat.position().add(away.normalize().scale(ResolveRules.withdrawDistance()));
    }

    private LivingEntity nearestHealthyAlly() {
        List<AbstractHumanCompanionEntity> allies = companion.level().getEntitiesOfClass(
                AbstractHumanCompanionEntity.class,
                companion.getBoundingBox().inflate(ALLY_SEARCH_RANGE),
                other -> other != companion
                        && other.isAlive()
                        && other.getHealth() > other.getMaxHealth() * 0.5F
                        && sameOwner(other));
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (AbstractHumanCompanionEntity ally : allies) {
            double distance = companion.distanceToSqr(ally);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = ally;
            }
        }
        return best;
    }

    private boolean sameOwner(TamableAnimal other) {
        return companion.getOwnerUUID() != null && companion.getOwnerUUID().equals(other.getOwnerUUID());
    }

    /** Applies the full {@link ResolveRules} boundary to the current situation. */
    private boolean evaluate(LivingEntity current) {
        return ResolveRules.shouldWithdraw(healthFraction(),
                configuredThreshold(),
                isOwnerFightingNearby(),
                current instanceof Creeper,
                isCornered(),
                companion.isAvenging(),
                profile());
    }

    private double healthFraction() {
        float max = companion.getMaxHealth();
        return max <= 0.0F ? 1.0D : companion.getHealth() / max;
    }

    private double configuredThreshold() {
        return ModConfig.safeGet(ModConfig.COMBAT_WITHDRAW_HEALTH_FRACTION);
    }

    private CompanionSurvivalProfile profile() {
        return ModConfig.safeGet(ModConfig.COMBAT_SURVIVAL_PROFILE);
    }

    /** Guarantee 1: the owner fighting nearby vetoes withdrawal outright. */
    private boolean isOwnerFightingNearby() {
        LivingEntity owner = companion.getOwner();
        if (owner == null || owner.level() != companion.level()) return false;
        if (companion.distanceTo(owner) > ResolveRules.OWNER_SUPPORT_RANGE) return false;
        // "Fighting" means the owner is trading blows right now, not merely present.
        return owner.getLastHurtByMob() != null && owner.tickCount - owner.getLastHurtByMobTimestamp() < 100
                || owner.getLastHurtMob() != null && owner.tickCount - owner.getLastHurtMobTimestamp() < 100;
    }

    /**
     * Guarantee 4: nowhere to retreat to and no ally to fall back behind means
     * the companion stands and fights rather than shuffling into a wall.
     */
    private boolean isCornered() {
        if (nearestHealthyAlly() != null) return false;
        Vec3 destination = fallBackPointWithoutAlly();
        return companion.getNavigation().createPath(destination.x, destination.y, destination.z, 0) == null;
    }

    private Vec3 fallBackPointWithoutAlly() {
        Vec3 away = companion.position().subtract(threat == null ? companion.position() : threat.position());
        if (away.lengthSqr() < 1.0E-4D) {
            away = new Vec3(1.0D, 0.0D, 0.0D);
        }
        Vec3 origin = threat == null ? companion.position() : threat.position();
        return origin.add(away.normalize().scale(ResolveRules.withdrawDistance()));
    }
}
