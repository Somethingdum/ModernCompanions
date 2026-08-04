package com.majorbonghits.moderncompanions.world.zone;

import com.majorbonghits.moderncompanions.ModernCompanions;
import com.majorbonghits.moderncompanions.core.ModConfig;
import com.majorbonghits.moderncompanions.entity.AbstractHumanCompanionEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Watches guarded zones and tells the owner when something got in that nobody saw.
 *
 * <p>This is what makes the perimeter promise honest. Companions are limited to
 * what they perceive, which means a guarantee that nothing ever crosses the line
 * would be a lie. Instead: guards catch what they can see, and anything that
 * persists inside a zone without being perceived by any guard raises an explicit
 * warning after a grace period. Near-total coverage, and when it genuinely fails
 * the player is told rather than quietly losing a base.
 *
 * <p>Guards also get a fair home-field advantage inside their own zone: an
 * unperceived intruder produces a directional investigation cue, not a target
 * lock, so they walk over and look rather than magically knowing.
 */
@EventBusSubscriber(modid = ModernCompanions.MOD_ID)
public final class ZoneBreachTracker {
    /** Zone sweeps are cheap but not free; once a second is ample. */
    private static final int SWEEP_INTERVAL_TICKS = 20;
    /** Hard cap on entities considered per zone, so a mob farm cannot stall a tick. */
    private static final int MAX_INTRUDERS_PER_SWEEP = 24;
    /** Re-warn no more often than this, so a persistent breach does not spam. */
    private static final int WARN_COOLDOWN_TICKS = 200;

    /** First tick each unperceived intruder was seen inside a zone, keyed by zone and entity. */
    private static final Map<UUID, Map<UUID, Long>> UNSEEN_SINCE = new HashMap<>();
    private static final Map<UUID, Long> LAST_WARNED = new HashMap<>();

    private ZoneBreachTracker() {}

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % SWEEP_INTERVAL_TICKS != 0) return;

        for (ServerLevel level : event.getServer().getAllLevels()) {
            sweepLevel(level);
        }
    }

    private static void sweepLevel(ServerLevel level) {
        PerimeterZoneData data = PerimeterZoneData.get(level);
        long now = level.getGameTime();

        for (ServerPlayer player : level.players()) {
            for (PerimeterZone zone : data.zonesOf(player.getUUID())) {
                sweepZone(level, player, zone, now);
            }
        }
    }

    private static void sweepZone(ServerLevel level, ServerPlayer owner, PerimeterZone zone, long now) {
        // An unloaded zone is not a zone where nothing is happening; it is a zone
        // where nothing exists. Skipping it avoids phantom breaches on reload.
        if (!level.isLoaded(zone.center())) return;

        List<AbstractHumanCompanionEntity> guards = guardsFor(level, owner, zone);
        if (guards.isEmpty()) {
            UNSEEN_SINCE.remove(zone.id());
            return;
        }

        List<LivingEntity> intruders = level.getEntitiesOfClass(LivingEntity.class, zone.box(),
                entity -> entity.isAlive()
                        && entity.getType().getCategory() == MobCategory.MONSTER);
        if (intruders.size() > MAX_INTRUDERS_PER_SWEEP) {
            intruders = intruders.subList(0, MAX_INTRUDERS_PER_SWEEP);
        }

        Map<UUID, Long> unseen = UNSEEN_SINCE.computeIfAbsent(zone.id(), key -> new HashMap<>());
        List<LivingEntity> stillUnseen = new ArrayList<>();

        for (LivingEntity intruder : intruders) {
            if (perceivedByAnyGuard(guards, intruder)) {
                // A guard has eyes on it; this is handled, not a breach.
                unseen.remove(intruder.getUUID());
                continue;
            }
            unseen.putIfAbsent(intruder.getUUID(), now);
            stillUnseen.add(intruder);
        }

        // Forget entries for anything that left or died.
        unseen.keySet().removeIf(id -> intruders.stream().noneMatch(e -> e.getUUID().equals(id)));

        nudgeGuardsToward(guards, stillUnseen);
        warnIfOverdue(owner, zone, unseen, now);
    }

    /**
     * Home-field advantage: a guard is given the intruder's rough position to go
     * and look at, not the intruder itself. It still has to actually see it before
     * it will fight it.
     */
    private static void nudgeGuardsToward(List<AbstractHumanCompanionEntity> guards, List<LivingEntity> unseen) {
        if (unseen.isEmpty()) return;
        for (LivingEntity intruder : unseen) {
            AbstractHumanCompanionEntity nearest = null;
            double best = Double.MAX_VALUE;
            for (AbstractHumanCompanionEntity guard : guards) {
                if (guard.getTarget() != null) continue; // already busy
                double distance = guard.distanceToSqr(intruder);
                if (distance < best) {
                    best = distance;
                    nearest = guard;
                }
            }
            if (nearest != null) {
                nearest.setInvestigationCue(intruder.blockPosition());
            }
        }
    }

    private static void warnIfOverdue(ServerPlayer owner, PerimeterZone zone, Map<UUID, Long> unseen, long now) {
        if (unseen.isEmpty()) return;
        long graceTicks = ModConfig.safeGet(ModConfig.ZONE_ALERT_SECONDS) * 20L;

        boolean overdue = unseen.values().stream().anyMatch(since -> now - since >= graceTicks);
        if (!overdue) return;

        long lastWarned = LAST_WARNED.getOrDefault(zone.id(), Long.MIN_VALUE);
        if (now - lastWarned < WARN_COOLDOWN_TICKS) return;
        LAST_WARNED.put(zone.id(), now);

        owner.sendSystemMessage(Component.translatable("message.modern_companions.zone.breach",
                Component.literal(zone.name()), unseen.size()));
        owner.playNotifySound(SoundEvents.NOTE_BLOCK_DIDGERIDOO.value(), SoundSource.PLAYERS, 0.7F, 0.6F);
    }

    private static boolean perceivedByAnyGuard(List<AbstractHumanCompanionEntity> guards, LivingEntity intruder) {
        for (AbstractHumanCompanionEntity guard : guards) {
            if (guard.getTarget() == intruder || guard.perception().knowsAbout(intruder)) return true;
        }
        return false;
    }

    /** Companions currently warding this zone, identified by their assigned post. */
    private static List<AbstractHumanCompanionEntity> guardsFor(ServerLevel level, ServerPlayer owner, PerimeterZone zone) {
        return level.getEntitiesOfClass(AbstractHumanCompanionEntity.class, zone.box().inflate(24.0D),
                companion -> companion.isAlive()
                        && companion.isTame()
                        && owner.getUUID().equals(companion.getOwnerUUID())
                        && companion.isGuarding()
                        && companion.getPatrolPos().map(zone::contains).orElse(false));
    }

    /** Drops retained state for a zone that no longer exists. */
    public static void forget(UUID zoneId) {
        UNSEEN_SINCE.remove(zoneId);
        LAST_WARNED.remove(zoneId);
    }
}
