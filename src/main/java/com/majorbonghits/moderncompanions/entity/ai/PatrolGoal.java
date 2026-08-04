package com.majorbonghits.moderncompanions.entity.ai;

import com.majorbonghits.moderncompanions.entity.AbstractHumanCompanionEntity;
import com.majorbonghits.moderncompanions.entity.job.CompanionJob;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * Patrolling random stroll constrained to the companion's live patrol radius.
 * Radius is read from the entity on every use so radius changes never require
 * re-registering the goal; the old cached-radius field is what drove duplicate
 * goal registration on load.
 */
public class PatrolGoal extends RandomStrollGoal {
    protected final float probability;
    public Vec3 patrolVec;
    public AbstractHumanCompanionEntity companion;

    public PatrolGoal(AbstractHumanCompanionEntity mob, int interval) {
        this(mob, 1.0D, 0.001F, interval);
    }

    public PatrolGoal(AbstractHumanCompanionEntity mob, double speed, float probability, int interval) {
        super(mob, speed);
        this.probability = probability;
        this.companion = mob;
        this.interval = interval;
    }

    @Override
    public boolean canUse() {
        if (companion.getPatrolPos().isEmpty() || !companion.isPatrolling()) {
            return false;
        }
        // If the companion has an active job, let the job goals drive movement instead of patrol strolling.
        if (companion.getJob() != CompanionJob.NONE) {
            return false;
        }
        this.patrolVec = Vec3.atBottomCenterOf(companion.getPatrolPos().orElse(companion.blockPosition()));
        return super.canUse();
    }

    @Nullable
    @Override
    protected Vec3 getPosition() {
        Vec3 candidate = this.mob.getRandom().nextFloat() >= this.probability ? getRandomAroundPatrol() : super.getPosition();
        if (candidate == null) {
            candidate = super.getPosition();
        }
        return candidate;
    }

    private Vec3 getRandomAroundPatrol() {
        if (patrolVec == null) return null;
        return LandRandomPos.getPosTowards(this.mob, companion.getPatrolRadius(), 7, patrolVec);
    }
}
