package com.majorbonghits.moderncompanions.entity.ai;

import com.majorbonghits.moderncompanions.entity.AbstractHumanCompanionEntity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.AABB;

import java.util.EnumSet;
import java.util.List;

/**
 * Custom revenge goal that avoids intra-owner friendly fire and never turns a
 * companion against its own owner (an accidental owner hit is not a reason to
 * fight back). The old ignore-class arrays were always empty and are removed.
 */
public class CustomHurtByTargetGoal extends TargetGoal {
    private static final TargetingConditions HURT_BY_TARGETING = TargetingConditions.forCombat().ignoreLineOfSight().ignoreInvisibilityTesting();
    private static final int ALERT_RANGE_Y = 10;
    private int timestamp;

    public CustomHurtByTargetGoal(PathfinderMob mob) {
        super(mob, true);
        this.setFlags(EnumSet.of(Goal.Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        int i = this.mob.getLastHurtByMobTimestamp();
        LivingEntity attacker = this.mob.getLastHurtByMob();
        if (i != this.timestamp && attacker != null) {
            if (attacker.getType() == EntityType.PLAYER && this.mob.level().getGameRules().getBoolean(GameRules.RULE_UNIVERSAL_ANGER)) {
                return false;
            }
            if (this.mob instanceof TamableAnimal self) {
                // Never retaliate against the owner, regardless of friendly-fire settings.
                if (self.getOwner() == attacker) {
                    return false;
                }
                if (attacker instanceof TamableAnimal tamed && self.getOwner() == tamed.getOwner()) {
                    return false;
                }
            }
            return this.canAttack(attacker, HURT_BY_TARGETING);
        }
        return false;
    }

    @Override
    public void start() {
        this.mob.setTarget(this.mob.getLastHurtByMob());
        this.targetMob = this.mob.getTarget();
        this.timestamp = this.mob.getLastHurtByMobTimestamp();
        this.unseenMemoryTicks = 300;
        this.alertOthers();
        super.start();
    }

    protected void alertOthers() {
        double range = this.getFollowDistance();
        AABB box = AABB.unitCubeFromLowerCorner(this.mob.position()).inflate(range, ALERT_RANGE_Y, range);
        List<? extends Mob> list = this.mob.level().getEntitiesOfClass(AbstractHumanCompanionEntity.class, box, EntitySelector.NO_SPECTATORS);
        for (Mob other : list) {
            if (this.mob == other || other.getTarget() != null) continue;
            if (this.mob instanceof TamableAnimal tame && other instanceof TamableAnimal otherTame
                    && tame.getOwner() != otherTame.getOwner()) {
                continue;
            }
            this.alertOther(other, this.mob.getLastHurtByMob());
        }
    }

    protected void alertOther(Mob mob, LivingEntity target) {
        mob.setTarget(target);
    }

    /**
     * Bounds both retaliation reach and the squad-alert radius to the configured
     * target range rather than the much larger pathfinding FOLLOW_RANGE.
     */
    @Override
    protected double getFollowDistance() {
        return CompanionTargetRange.blocks();
    }
}
