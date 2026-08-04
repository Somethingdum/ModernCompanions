package com.majorbonghits.moderncompanions.entity.ai;

import com.majorbonghits.moderncompanions.core.ModConfig;
import com.majorbonghits.moderncompanions.entity.AbstractHumanCompanionEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What one companion currently knows about, and how it found out.
 *
 * <p>This is the layer that keeps companions sharp without making them
 * omniscient. Targeting reads exclusively from the contact list, so a companion
 * can only fight what it has sensed itself or been told about by a squadmate.
 * Sweeps are bounded and phase-offset per entity so a large party does not spike
 * the server tick.
 */
public final class CompanionPerception {
    /** Squadmates within this range share what they have seen. */
    private static final double RELAY_RANGE = 24.0D;
    private static final int MAX_CONTACTS = 16;

    private final AbstractHumanCompanionEntity companion;
    private final Map<UUID, PerceivedContact> contacts = new HashMap<>();
    private long lastSweepTick = Long.MIN_VALUE;

    public CompanionPerception(AbstractHumanCompanionEntity companion) {
        this.companion = companion;
    }

    public Collection<PerceivedContact> contacts() {
        return contacts.values();
    }

    @Nullable
    public PerceivedContact contact(UUID id) {
        return contacts.get(id);
    }

    public boolean knowsAbout(LivingEntity entity) {
        return entity != null && contacts.containsKey(entity.getUUID());
    }

    /**
     * Refreshes what the companion knows. Called on the entity tick; does its own
     * interval and phase-offset gating so callers do not have to.
     */
    public void tick() {
        long now = companion.level().getGameTime();
        int interval = Math.max(1, ModConfig.safeGet(ModConfig.PERCEPTION_SWEEP_INTERVAL));
        // Phase-offset by entity id so a party of companions never all sweep on the same tick.
        if (Math.floorMod(companion.getId(), interval) != Math.floorMod((int) now, interval)) return;
        if (now == lastSweepTick) return;
        lastSweepTick = now;

        sense(now);
        relayFromSquadmates(now);
        decayAndPrune(now);
    }

    /** First-hand sensing: everything the companion can actually see right now. */
    private void sense(long now) {
        double range = effectiveSightRange();
        AABB box = companion.getBoundingBox().inflate(range);
        Vec3 look = companion.getLookAngle();

        for (LivingEntity candidate : companion.level().getEntitiesOfClass(LivingEntity.class, box,
                other -> other != companion && other.isAlive() && isHostileCandidate(other))) {
            double distance = companion.distanceTo(candidate);
            if (distance > perceivedRangeFor(candidate, range)) continue;

            Vec3 toTarget = candidate.position().subtract(companion.position());
            if (!PerceptionRules.withinFieldOfView(look.x, look.z, toTarget.x, toTarget.z, distance)) continue;
            if (!companion.getSensing().hasLineOfSight(candidate)) continue;

            record(candidate, now, true);
        }
    }

    /**
     * A companion is also told what its squadmates can see. This is what lets a
     * perimeter work at all, and it is earned: somebody had to actually see it.
     */
    private void relayFromSquadmates(long now) {
        if (companion.getOwnerUUID() == null) return;
        List<AbstractHumanCompanionEntity> allies = companion.level().getEntitiesOfClass(
                AbstractHumanCompanionEntity.class,
                companion.getBoundingBox().inflate(RELAY_RANGE),
                other -> other != companion && other.isAlive() && sameOwner(other));

        for (AbstractHumanCompanionEntity ally : allies) {
            for (PerceivedContact allyContact : ally.perception().contacts()) {
                // Only first-hand sightings are relayed, so hearsay cannot bounce
                // between companions and keep itself alive indefinitely.
                if (allyContact.relayed() || allyContact.confidence() < 0.5F) continue;
                LivingEntity entity = allyContact.entity();
                if (entity == null || !entity.isAlive()) continue;
                record(entity, now, false);
            }
        }
    }

    private void record(LivingEntity entity, long now, boolean directSighting) {
        PerceivedContact existing = contacts.get(entity.getUUID());
        if (existing != null) {
            existing.refresh(entity, now, directSighting);
            return;
        }
        if (contacts.size() >= MAX_CONTACTS && !evictWeakest()) return;
        contacts.put(entity.getUUID(), new PerceivedContact(entity, now, !directSighting));
    }

    /** Keeps the contact list bounded by dropping the least certain entry. */
    private boolean evictWeakest() {
        UUID weakest = null;
        float lowest = Float.MAX_VALUE;
        for (Map.Entry<UUID, PerceivedContact> entry : contacts.entrySet()) {
            if (entry.getValue().confidence() < lowest) {
                lowest = entry.getValue().confidence();
                weakest = entry.getKey();
            }
        }
        if (weakest == null) return false;
        contacts.remove(weakest);
        return true;
    }

    private void decayAndPrune(long now) {
        int memoryTicks = ModConfig.safeGet(ModConfig.PERCEPTION_MEMORY_TICKS);
        contacts.values().removeIf(contact -> !contact.decay(now, memoryTicks));
    }

    /** Sight range after darkness, weather, and the companion's own intelligence. */
    private double effectiveSightRange() {
        return PerceptionRules.sightRange(
                ModConfig.safeGet(ModConfig.PERCEPTION_SIGHT_RANGE),
                false, companion.level().isRaining(), companion.level().isThundering(),
                false, companion.getIntelligence());
    }

    /** Per-target range, applying the terms that depend on the target itself. */
    private double perceivedRangeFor(LivingEntity target, double baseRange) {
        boolean dark = companion.level().getMaxLocalRawBrightness(target.blockPosition()) < 5
                && !target.isOnFire() && !target.isCurrentlyGlowing();
        return PerceptionRules.sightRange(
                ModConfig.safeGet(ModConfig.PERCEPTION_SIGHT_RANGE),
                dark, companion.level().isRaining(), companion.level().isThundering(),
                target.isCrouching(), companion.getIntelligence());
    }

    /**
     * Best target from what is known, or null when nothing is worth attacking.
     * Scoring lives in {@link ThreatAssessment}; this only supplies the facts.
     */
    @Nullable
    public LivingEntity bestTarget(java.util.function.Predicate<LivingEntity> eligible) {
        LivingEntity owner = companion.getOwner();
        double range = ModConfig.safeGet(ModConfig.PERCEPTION_SIGHT_RANGE);
        List<AbstractHumanCompanionEntity> allies = squadmates();

        LivingEntity best = null;
        double bestScore = Double.NEGATIVE_INFINITY;

        for (PerceivedContact contact : new ArrayList<>(contacts.values())) {
            LivingEntity entity = contact.entity();
            if (entity == null || !entity.isAlive() || !eligible.test(entity)) continue;
            if (!companion.canHarm(entity)) continue;

            double score = ThreatAssessment.score(
                    companion.distanceTo(entity),
                    range,
                    owner != null && isTargeting(entity, owner),
                    isTargeting(entity, companion),
                    isImminentThreat(entity),
                    healthFraction(entity),
                    false,
                    countAlliesEngaged(allies, entity),
                    contact.confidence());

            if (score > bestScore) {
                bestScore = score;
                best = entity;
            }
        }
        return best;
    }

    /** Score of the companion's current target, for switch hysteresis. */
    public double scoreOf(LivingEntity target) {
        if (target == null) return Double.NEGATIVE_INFINITY;
        PerceivedContact contact = contacts.get(target.getUUID());
        LivingEntity owner = companion.getOwner();
        return ThreatAssessment.score(
                companion.distanceTo(target),
                ModConfig.safeGet(ModConfig.PERCEPTION_SIGHT_RANGE),
                owner != null && isTargeting(target, owner),
                isTargeting(target, companion),
                isImminentThreat(target),
                healthFraction(target),
                false,
                countAlliesEngaged(squadmates(), target),
                contact == null ? 0.5F : contact.confidence());
    }

    private List<AbstractHumanCompanionEntity> squadmates() {
        if (companion.getOwnerUUID() == null) return List.of();
        return companion.level().getEntitiesOfClass(AbstractHumanCompanionEntity.class,
                companion.getBoundingBox().inflate(RELAY_RANGE),
                other -> other != companion && other.isAlive() && sameOwner(other));
    }

    private static int countAlliesEngaged(List<AbstractHumanCompanionEntity> allies, LivingEntity target) {
        int count = 0;
        for (AbstractHumanCompanionEntity ally : allies) {
            if (ally.getTarget() == target) count++;
        }
        return count;
    }

    private static boolean isTargeting(LivingEntity attacker, LivingEntity victim) {
        return attacker instanceof net.minecraft.world.entity.Mob mob && mob.getTarget() == victim;
    }

    /** Something about to do damage that cannot be undone by killing it a moment later. */
    private static boolean isImminentThreat(LivingEntity entity) {
        if (entity instanceof Creeper creeper) {
            return creeper.getSwellDir() > 0 || creeper.isIgnited();
        }
        return entity.isUsingItem()
                && (entity.getUseItem().getItem() instanceof BowItem
                || entity.getUseItem().getItem() instanceof CrossbowItem);
    }

    private static double healthFraction(LivingEntity entity) {
        float max = entity.getMaxHealth();
        return max <= 0.0F ? 1.0D : entity.getHealth() / max;
    }

    private boolean sameOwner(TamableAnimal other) {
        return companion.getOwnerUUID() != null && companion.getOwnerUUID().equals(other.getOwnerUUID());
    }

    /** Hostiles only; companions do not build contacts on livestock or allies. */
    private boolean isHostileCandidate(LivingEntity entity) {
        if (entity == companion.getOwner()) return false;
        if (entity instanceof TamableAnimal tame && sameOwner(tame)) return false;
        return entity.getType().getCategory() == net.minecraft.world.entity.MobCategory.MONSTER;
    }

    public void clear() {
        contacts.clear();
    }
}
