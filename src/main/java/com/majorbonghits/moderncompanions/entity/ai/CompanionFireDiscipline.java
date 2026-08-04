package com.majorbonghits.moderncompanions.entity.ai;

import com.majorbonghits.moderncompanions.core.ModConfig;
import com.majorbonghits.moderncompanions.entity.AbstractHumanCompanionEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Live half of ranged fire discipline: gathers who is in the way and asks
 * {@link FireLineRules} whether the shot is safe.
 *
 * <p>Every ranged attack path routes through here, so an archer will hold fire
 * rather than put an arrow through the player standing between it and a
 * skeleton. Only friendlies block a shot; hostiles standing in the way are a
 * bonus, not an obstacle.
 */
public final class CompanionFireDiscipline {
    /** How far around the shot to look for friendlies that might be clipped. */
    private static final double SEARCH_PADDING = 4.0D;

    private CompanionFireDiscipline() {}

    /** Whether the companion may fire at this target right now. */
    public static boolean canFireAt(AbstractHumanCompanionEntity shooter, LivingEntity target) {
        if (target == null) return false;
        double clearance = ModConfig.safeGet(ModConfig.COMBAT_FIRE_LANE_CLEARANCE);
        if (clearance <= 0.0D) return true;

        Vec3 from = shooter.getEyePosition();
        Vec3 to = target.getEyePosition();

        for (LivingEntity friendly : friendliesNear(shooter, from, to)) {
            if (friendly == shooter || friendly == target) continue;
            Vec3 at = friendly.position().add(0.0D, friendly.getBbHeight() * 0.5D, 0.0D);
            if (FireLineRules.blocksShot(from.x, from.y, from.z, to.x, to.y, to.z, at.x, at.y, at.z, clearance)) {
                return false;
            }
        }
        return true;
    }

    /**
     * The owner and same-owner companions inside the shot's bounding region.
     * Bounding the search to the shot itself keeps this cheap enough to run on
     * every attack.
     */
    private static List<LivingEntity> friendliesNear(AbstractHumanCompanionEntity shooter, Vec3 from, Vec3 to) {
        var box = new net.minecraft.world.phys.AABB(from, to).inflate(SEARCH_PADDING);
        return shooter.level().getEntitiesOfClass(LivingEntity.class, box,
                candidate -> isFriendly(shooter, candidate));
    }

    private static boolean isFriendly(AbstractHumanCompanionEntity shooter, LivingEntity candidate) {
        if (!candidate.isAlive()) return false;
        if (candidate == shooter.getOwner()) return true;
        if (candidate instanceof TamableAnimal tame && shooter.getOwnerUUID() != null) {
            return shooter.getOwnerUUID().equals(tame.getOwnerUUID());
        }
        return false;
    }
}
