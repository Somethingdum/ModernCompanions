package com.majorbonghits.moderncompanions.entity.ai;

import com.majorbonghits.moderncompanions.entity.AbstractHumanCompanionEntity;
import com.majorbonghits.moderncompanions.squad.CompanionSquadData;
import com.majorbonghits.moderncompanions.squad.Squad;
import com.majorbonghits.moderncompanions.squad.SquadOrder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * Walks a companion to the destination its squad was ordered to.
 *
 * <p>This is the goal behind "send that squad over there". It relies on the
 * navigation work rather than teleporting: a destination hundreds of blocks away
 * is walked in stages, repathing toward the target each time the navigator
 * finishes or fails, and reporting once rather than spamming when the route
 * genuinely cannot be found.
 */
public class SquadMoveToGoal extends Goal {
    private static final int REPATH_INTERVAL_TICKS = 20;
    private static final double ARRIVE_DISTANCE = 3.0D;
    private static final double SPEED = 1.15D;
    /** Consecutive failed repaths before the companion reports being unable to get there. */
    private static final int FAILURES_BEFORE_REPORT = 5;

    private final AbstractHumanCompanionEntity companion;
    private BlockPos destination;
    private int repathCooldown;
    private int consecutiveFailures;
    private boolean reportedBlocked;

    public SquadMoveToGoal(AbstractHumanCompanionEntity companion) {
        this.companion = companion;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        BlockPos target = currentDestination();
        if (target == null) return false;
        if (companion.blockPosition().closerThan(target, ARRIVE_DISTANCE)) return false;
        this.destination = target;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        BlockPos target = currentDestination();
        if (target == null) return false;
        // A new order mid-march replaces the destination rather than ending the goal.
        if (!target.equals(destination)) {
            destination = target;
            repathCooldown = 0;
            consecutiveFailures = 0;
            reportedBlocked = false;
        }
        return !companion.blockPosition().closerThan(destination, ARRIVE_DISTANCE);
    }

    @Override
    public void start() {
        repathCooldown = 0;
        consecutiveFailures = 0;
        reportedBlocked = false;
    }

    @Override
    public void stop() {
        destination = null;
        companion.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (destination == null) return;
        if (--repathCooldown > 0 && !companion.getNavigation().isDone()) return;
        repathCooldown = REPATH_INTERVAL_TICKS;

        boolean routed = companion.getNavigation().moveTo(
                destination.getX() + 0.5D, destination.getY(), destination.getZ() + 0.5D, SPEED);
        if (routed) {
            consecutiveFailures = 0;
            return;
        }

        // Out of navigator range or genuinely unreachable. Head for the furthest
        // point along the bearing that does path, so long marches make progress in
        // stages instead of failing outright.
        if (!stepTowardDestination()) {
            consecutiveFailures++;
            if (consecutiveFailures >= FAILURES_BEFORE_REPORT && !reportedBlocked) {
                reportedBlocked = true;
                companion.notifyCourierOwnerText(net.minecraft.network.chat.Component.translatable(
                        "message.modern_companions.squad.blocked"));
            }
        }
    }

    /** Path as far along the straight-line bearing as the navigator will accept. */
    private boolean stepTowardDestination() {
        double dx = destination.getX() + 0.5D - companion.getX();
        double dz = destination.getZ() + 0.5D - companion.getZ();
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 1.0E-3D) return false;

        for (double step : new double[] {48.0D, 32.0D, 16.0D, 8.0D}) {
            if (step >= length) continue;
            double x = companion.getX() + dx / length * step;
            double z = companion.getZ() + dz / length * step;
            int y = surfaceHeightNear(BlockPos.containing(x, companion.getY(), z));
            if (companion.getNavigation().moveTo(x, y, z, SPEED)) {
                consecutiveFailures = 0;
                return true;
            }
        }
        return false;
    }

    /** Cheap ground probe so an intermediate waypoint is not left floating or buried. */
    private int surfaceHeightNear(BlockPos pos) {
        if (!(companion.level() instanceof ServerLevel server) || !server.isLoaded(pos)) {
            return pos.getY();
        }
        return server.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos).getY();
    }

    /** The squad's ordered destination, if it applies to this companion here and now. */
    private BlockPos currentDestination() {
        if (companion.isOrderedToSit() || !companion.isTame()) return null;
        if (!(companion.level() instanceof ServerLevel server)) return null;
        if (companion.getOwnerUUID() == null) return null;

        Squad squad = CompanionSquadData.get(server.getServer())
                .squadOf(companion.getOwnerUUID(), companion.getUUID());
        if (squad == null) return null;

        SquadOrder order = squad.order();
        if (order.type() != SquadOrder.Type.MOVE_TO || order.pos() == null) return null;
        // An order given in another dimension simply does not apply here.
        if (!order.appliesIn(server.dimension())) return null;
        return order.pos();
    }
}
