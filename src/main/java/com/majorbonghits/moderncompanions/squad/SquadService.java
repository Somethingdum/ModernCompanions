package com.majorbonghits.moderncompanions.squad;

import com.majorbonghits.moderncompanions.entity.AbstractHumanCompanionEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The one place squad membership and orders are changed.
 *
 * <p>Keeping this together matters because two things have to stay in step: the
 * persisted roster, which is authoritative, and the stance of every loaded
 * member, which is what actually drives behavior. Issuing an order through here
 * always does both.
 */
public final class SquadService {
    /** How far around the player loaded members are searched for when applying an order. */
    private static final double MEMBER_SEARCH_RADIUS = 256.0D;

    private SquadService() {}

    public static CompanionSquadData data(ServerPlayer player) {
        return CompanionSquadData.get(player.server);
    }

    /** The squad a companion belongs to, creating nothing. */
    @Nullable
    public static Squad squadOf(ServerPlayer owner, AbstractHumanCompanionEntity companion) {
        return data(owner).squadOf(owner.getUUID(), companion.getUUID());
    }

    /**
     * Puts a companion in a squad slot and immediately applies that squad's
     * standing order, so a companion joining a warding squad takes up the post
     * rather than waiting for the next order.
     */
    public static Squad assign(ServerPlayer owner, AbstractHumanCompanionEntity companion, int slot) {
        CompanionSquadData data = data(owner);
        Squad squad = data.assign(owner.getUUID(), companion.getUUID(), slot);
        companion.setSquadId(squad.id());
        applyOrder(companion, squad.order());
        return squad;
    }

    public static void unassign(ServerPlayer owner, AbstractHumanCompanionEntity companion) {
        data(owner).unassign(owner.getUUID(), companion.getUUID());
        companion.setSquadId(null);
    }

    /**
     * Issues an order to a whole squad and applies it to every loaded member.
     *
     * @return how many loaded members received it
     */
    public static int issue(ServerPlayer owner, Squad squad, SquadOrder order) {
        CompanionSquadData data = data(owner);
        data.setOrder(squad, order);

        int applied = 0;
        for (AbstractHumanCompanionEntity member : loadedMembers(owner, squad)) {
            applyOrder(member, order);
            applied++;
        }
        return applied;
    }

    /** Applies an order's posture to one companion. */
    public static void applyOrder(AbstractHumanCompanionEntity companion, SquadOrder order) {
        switch (order.type()) {
            case WORK -> {
                // Work has its own enable flag; the stance follows from it.
                companion.setStance(CompanionStance.PATROL);
                companion.setWorkEnabled(true);
            }
            case WARD -> {
                companion.setStance(CompanionStance.WARD);
                if (order.pos() != null && order.appliesIn(companion.level().dimension())) {
                    companion.setPatrolPos(order.pos());
                    companion.setPatrolRadius(order.radius());
                }
            }
            // MOVE_TO holds on arrival; SquadMoveToGoal owns the walking itself.
            default -> companion.setStance(order.stance());
        }
    }

    /**
     * Loaded members of a squad, across every dimension the server has open.
     * Members that are unloaded simply are not returned; they pick the order up
     * from the persisted roster when they load.
     */
    public static List<AbstractHumanCompanionEntity> loadedMembers(ServerPlayer owner, Squad squad) {
        List<AbstractHumanCompanionEntity> found = new ArrayList<>();
        for (ServerLevel level : owner.server.getAllLevels()) {
            for (UUID memberId : squad.members()) {
                Entity entity = level.getEntity(memberId);
                if (entity instanceof AbstractHumanCompanionEntity companion && companion.isAlive()) {
                    found.add(companion);
                }
            }
        }
        return found;
    }

    /**
     * Every owned companion loaded near the player. Used by bulk commands and by
     * the baton when recruiting a whole group into a squad at once.
     */
    public static List<AbstractHumanCompanionEntity> nearbyOwned(ServerPlayer owner) {
        if (!(owner.level() instanceof ServerLevel level)) return List.of();
        return level.getEntitiesOfClass(AbstractHumanCompanionEntity.class,
                owner.getBoundingBox().inflate(MEMBER_SEARCH_RADIUS),
                companion -> companion.isAlive() && companion.isTame()
                        && owner.getUUID().equals(companion.getOwnerUUID()));
    }

    /** Reconciles a loaded companion's cached squad id against the persisted roster. */
    public static void refreshSquadCache(ServerPlayer owner, AbstractHumanCompanionEntity companion) {
        Squad squad = squadOf(owner, companion);
        companion.setSquadId(squad == null ? null : squad.id());
    }

    public static Component describe(Squad squad) {
        return Component.literal(squad.slot() + ": " + squad.name())
                .withStyle(style -> style.withColor(squad.color().getTextColor()));
    }
}
