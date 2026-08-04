package com.majorbonghits.moderncompanions.entity.ai;

import com.majorbonghits.moderncompanions.entity.AbstractHumanCompanionEntity;
import com.majorbonghits.moderncompanions.entity.job.CompanionJob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/**
 * Keeps patrolling companions within their live patrol radius. Hysteresis
 * (exit at 60% of the entry radius) prevents boundary oscillation, and
 * repathing is throttled so the navigator is not restarted every tick.
 */
public class MoveBackToPatrolGoal extends Goal {
    private static final int REPATH_INTERVAL_TICKS = 10;
    private static final double EXIT_FRACTION = 0.6D;

    public Vec3 patrolVec;
    public AbstractHumanCompanionEntity companion;
    private int repathCooldown;

    public MoveBackToPatrolGoal(AbstractHumanCompanionEntity companion) {
        this.companion = companion;
        this.setFlags(java.util.EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (companion.getJob() != CompanionJob.NONE) return false;
        if (this.companion.getPatrolPos().isEmpty() || !companion.isPatrolling()) {
            return false;
        }
        this.patrolVec = Vec3.atBottomCenterOf(this.companion.getPatrolPos().orElse(companion.blockPosition()));
        return distanceToPatrol() > companion.getPatrolRadius();
    }

    @Override
    public boolean canContinueToUse() {
        if (companion.getJob() != CompanionJob.NONE) return false;
        if (this.companion.getPatrolPos().isEmpty() || !companion.isPatrolling()) {
            return false;
        }
        // Keep walking until well inside the radius so the goal does not flap at the edge.
        return distanceToPatrol() > Math.max(1.0D, companion.getPatrolRadius() * EXIT_FRACTION);
    }

    @Override
    public void start() {
        repathCooldown = 0;
    }

    @Override
    public void stop() {
        companion.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (companion.getTarget() != null || patrolVec == null) return;
        if (--repathCooldown <= 0 || companion.getNavigation().isDone()) {
            repathCooldown = REPATH_INTERVAL_TICKS;
            companion.getNavigation().moveTo(patrolVec.x, patrolVec.y, patrolVec.z, 1.0);
        }
    }

    private double distanceToPatrol() {
        return patrolVec == null ? 0.0D : patrolVec.distanceTo(Vec3.atBottomCenterOf(companion.blockPosition()));
    }
}
