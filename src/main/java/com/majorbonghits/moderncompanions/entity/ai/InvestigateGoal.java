package com.majorbonghits.moderncompanions.entity.ai;

import com.majorbonghits.moderncompanions.entity.AbstractHumanCompanionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * Walks over to look at something the companion has been told about but has not
 * actually seen.
 *
 * <p>This is the honest version of a guard noticing an intruder. The breach
 * tracker knows something is inside a warded zone, but the guard does not, so it
 * is handed a position rather than a target. Walking there usually brings the
 * intruder into view, at which point ordinary perception and targeting take
 * over. If nothing is there, the companion simply goes back to its post.
 */
public class InvestigateGoal extends Goal {
    private static final double ARRIVE_DISTANCE = 2.5D;
    private static final double SPEED = 1.15D;
    private static final int REPATH_INTERVAL_TICKS = 15;

    private final AbstractHumanCompanionEntity companion;
    private BlockPos destination;
    private int repathCooldown;

    public InvestigateGoal(AbstractHumanCompanionEntity companion) {
        this.companion = companion;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (companion.isOrderedToSit() || companion.getTarget() != null) return false;
        BlockPos cue = companion.getInvestigationCue().orElse(null);
        if (cue == null) return false;
        if (companion.blockPosition().closerThan(cue, ARRIVE_DISTANCE)) {
            // Already here and still nothing visible; the cue has done its job.
            companion.clearInvestigationCue();
            return false;
        }
        this.destination = cue;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        // Spotting something for real immediately outranks going to look for it.
        if (companion.getTarget() != null) return false;
        BlockPos cue = companion.getInvestigationCue().orElse(null);
        if (cue == null) return false;
        this.destination = cue;
        return !companion.blockPosition().closerThan(cue, ARRIVE_DISTANCE);
    }

    @Override
    public void start() {
        repathCooldown = 0;
    }

    @Override
    public void stop() {
        companion.clearInvestigationCue();
        companion.getNavigation().stop();
        destination = null;
    }

    @Override
    public void tick() {
        if (destination == null) return;
        companion.getLookControl().setLookAt(
                destination.getX() + 0.5D, destination.getY() + 1.0D, destination.getZ() + 0.5D);
        if (--repathCooldown > 0 && !companion.getNavigation().isDone()) return;
        repathCooldown = REPATH_INTERVAL_TICKS;
        companion.getNavigation().moveTo(
                destination.getX() + 0.5D, destination.getY(), destination.getZ() + 0.5D, SPEED);
    }
}
