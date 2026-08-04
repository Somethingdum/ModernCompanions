package com.majorbonghits.moderncompanions.entity.ai;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * One thing a companion knows about, and how well it knows it.
 *
 * <p>Contacts are what make companions fair: a companion may only act on
 * something it has actually sensed, or been told about by a squadmate that
 * sensed it. Losing sight of a target does not delete the contact, it decays it,
 * so the companion searches the last known position rather than either tracking
 * perfectly through walls or forgetting instantly.
 */
public final class PerceivedContact {
    private final UUID id;
    private LivingEntity entity;
    private Vec3 lastKnownPos;
    private Vec3 lastKnownVelocity;
    private long lastSeenTick;
    private float confidence;
    private boolean relayed;

    public PerceivedContact(LivingEntity entity, long tick, boolean relayed) {
        this.id = entity.getUUID();
        this.entity = entity;
        this.lastKnownPos = entity.position();
        this.lastKnownVelocity = entity.getDeltaMovement();
        this.lastSeenTick = tick;
        this.confidence = relayed ? PerceptionRules.relayedConfidence(1.0F) : 1.0F;
        this.relayed = relayed;
    }

    public UUID id() {
        return id;
    }

    public LivingEntity entity() {
        return entity;
    }

    public Vec3 lastKnownPos() {
        return lastKnownPos;
    }

    public float confidence() {
        return confidence;
    }

    public boolean relayed() {
        return relayed;
    }

    public long lastSeenTick() {
        return lastSeenTick;
    }

    /** Refreshes a contact the companion can see right now. */
    public void refresh(LivingEntity seen, long tick, boolean directSighting) {
        this.entity = seen;
        this.lastKnownPos = seen.position();
        this.lastKnownVelocity = seen.getDeltaMovement();
        this.lastSeenTick = tick;
        if (directSighting) {
            this.confidence = 1.0F;
            this.relayed = false;
        } else if (!this.relayed) {
            // A relay never downgrades something already seen first-hand.
            this.confidence = Math.max(this.confidence, PerceptionRules.relayedConfidence(1.0F));
        } else {
            this.confidence = PerceptionRules.relayedConfidence(1.0F);
        }
    }

    /** Ages the contact; returns false once it should be dropped entirely. */
    public boolean decay(long tick, int memoryTicks) {
        int elapsed = (int) Math.max(0L, tick - lastSeenTick);
        this.confidence = PerceptionRules.memoryConfidence(elapsed, memoryTicks);
        return this.confidence > 0.0F && entity != null && entity.isAlive();
    }

    /**
     * Where the companion believes the target is now, extrapolated from its last
     * observed motion. Searching here rather than at the target's true position
     * is what makes losing a target look like searching instead of cheating.
     */
    public Vec3 predictedPos(long tick) {
        double elapsed = Math.max(0L, tick - lastSeenTick);
        return lastKnownPos.add(lastKnownVelocity.scale(Math.min(elapsed, 20.0D)));
    }
}
