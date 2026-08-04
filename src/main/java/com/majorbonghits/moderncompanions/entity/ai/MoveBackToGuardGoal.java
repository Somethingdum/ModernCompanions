package com.majorbonghits.moderncompanions.entity.ai;

import com.majorbonghits.moderncompanions.entity.AbstractHumanCompanionEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/**
 * Holds guarding companions at their assigned position. Separate entry/exit
 * distances stop the goal from oscillating on the block boundary, and
 * repathing is throttled instead of restarting the navigator every tick.
 */
public class MoveBackToGuardGoal extends Goal {
    private static final double ENTER_DISTANCE = 2.5D;
    private static final double EXIT_DISTANCE = 1.2D;
    private static final int REPATH_INTERVAL_TICKS = 10;

    public AbstractHumanCompanionEntity companion;
    public Vec3 guardVec;
    private int repathCooldown;

    public MoveBackToGuardGoal(AbstractHumanCompanionEntity companion) {
        this.companion = companion;
        this.setFlags(java.util.EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (this.companion.getPatrolPos().isEmpty() || !companion.isGuarding()) {
            return false;
        }
        this.guardVec = Vec3.atBottomCenterOf(this.companion.getPatrolPos().orElse(companion.blockPosition()));
        return distanceToPost() > ENTER_DISTANCE;
    }

    @Override
    public boolean canContinueToUse() {
        if (this.companion.getPatrolPos().isEmpty() || !companion.isGuarding()) {
            return false;
        }
        return distanceToPost() > EXIT_DISTANCE;
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
        if (companion.getTarget() != null || guardVec == null) return;
        if (--repathCooldown <= 0 || companion.getNavigation().isDone()) {
            repathCooldown = REPATH_INTERVAL_TICKS;
            companion.getNavigation().moveTo(guardVec.x, guardVec.y, guardVec.z, 1.0);
        }
    }

    private double distanceToPost() {
        return guardVec == null ? 0.0D : guardVec.distanceTo(Vec3.atBottomCenterOf(companion.blockPosition()));
    }
}
