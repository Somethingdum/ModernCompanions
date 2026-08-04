package com.majorbonghits.moderncompanions.entity.ai;

import com.majorbonghits.moderncompanions.core.ModConfig;
import com.majorbonghits.moderncompanions.entity.AbstractHumanCompanionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;

/**
 * Ground navigation tuned for humanoid companions.
 *
 * <p>Two things separate this from vanilla mob navigation. First, the visited-node
 * budget is multiplied well above the vanilla 1.0, because companions have to
 * solve player-built structures, cave systems, and long overland routes rather
 * than the few-metre hops a wandering mob needs. Second, the node evaluator
 * treats things that hurt a humanoid as hazards, so routes go around lava,
 * fire, and contact damage instead of straight through them.
 */
public final class CompanionGroundPathNavigation extends GroundPathNavigation {
    public CompanionGroundPathNavigation(AbstractHumanCompanionEntity companion, Level level) {
        super(companion, level);
        // Vanilla caps the search at FOLLOW_RANGE * 16 nodes. Companions need a much
        // larger budget to route around buildings and terrain rather than give up.
        this.setMaxVisitedNodesMultiplier(ModConfig.safeGet(ModConfig.NAV_NODE_BUDGET_MULTIPLIER).floatValue());
    }

    @Override
    protected PathFinder createPathFinder(int maxVisitedNodes) {
        this.nodeEvaluator = new CompanionWalkNodeEvaluator();
        this.nodeEvaluator.setCanPassDoors(true);
        return new PathFinder(this.nodeEvaluator, maxVisitedNodes);
    }

    private static final class CompanionWalkNodeEvaluator extends WalkNodeEvaluator {
        @Override
        public PathType getPathTypeOfMob(PathfindingContext context, int x, int y, int z, Mob mob) {
            BlockPos pos = new BlockPos(x, y, z);
            PathType hazard = companionHazard(context.level(), pos);
            if (hazard != null) {
                return hazard;
            }
            return super.getPathTypeOfMob(context, x, y, z, mob);
        }
    }

    /**
     * Hazards a humanoid companion should never walk into, and hazards it should
     * merely pay a high cost to cross. Returns null when the position is not a
     * companion-specific hazard so vanilla classification still applies.
     */
    private static PathType companionHazard(BlockGetter level, BlockPos pos) {
        if (isBlocking(level.getBlockState(pos)) || isBlocking(level.getBlockState(pos.below()))) {
            // BLOCKED is only used for things that reliably kill a humanoid; anything
            // survivable stays costed so a companion standing in it can path out.
            return PathType.BLOCKED;
        }
        if (isContactDamage(level.getBlockState(pos)) || isContactDamage(level.getBlockState(pos.below()))) {
            // DAMAGE_OTHER carries a configurable malus rather than an outright refusal,
            // so a cactus in the way is avoided but never strands a companion.
            return PathType.DAMAGE_OTHER;
        }
        return null;
    }

    private static boolean isBlocking(BlockState state) {
        return state.is(Blocks.POINTED_DRIPSTONE)
                || state.is(Blocks.MAGMA_BLOCK)
                || state.is(Blocks.CAMPFIRE)
                || state.is(Blocks.SOUL_CAMPFIRE)
                || state.is(Blocks.FIRE)
                || state.is(Blocks.SOUL_FIRE)
                || state.is(Blocks.LAVA)
                || state.is(Blocks.LAVA_CAULDRON);
    }

    private static boolean isContactDamage(BlockState state) {
        return state.is(Blocks.CACTUS)
                || state.is(Blocks.SWEET_BERRY_BUSH)
                || state.is(Blocks.WITHER_ROSE);
        // Powder snow is handled by its own PathType malus rather than here, so
        // vanilla's leather-boots handling still applies.
    }
}
