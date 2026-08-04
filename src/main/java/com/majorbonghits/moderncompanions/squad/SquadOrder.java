package com.majorbonghits.moderncompanions.squad;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

/**
 * A standing order issued to a whole squad.
 *
 * <p>Orders are data, not behavior: goals read the current order and act on it.
 * A destination carries its dimension so a squad ordered to a spot in the
 * Overworld does not try to walk there after following the player into the
 * Nether; it simply has no applicable order until it returns.
 */
public record SquadOrder(Type type,
                         @Nullable ResourceKey<Level> dimension,
                         @Nullable BlockPos pos,
                         int radius) {

    public enum Type {
        /** Stay with the owner. */
        FOLLOW("follow"),
        /** Walk to a point and hold there. */
        MOVE_TO("move_to"),
        /** Stand fast where you are. */
        HOLD("hold"),
        /** Guard a point within a radius. */
        WARD("ward"),
        /** Work the assigned job. */
        WORK("work");

        private final String id;

        Type(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public String translationKey() {
            return "order.modern_companions." + id;
        }

        public static Type fromId(String id) {
            if (id != null) {
                for (Type type : values()) {
                    if (type.id.equals(id)) return type;
                }
            }
            return FOLLOW;
        }
    }

    public static SquadOrder follow() {
        return new SquadOrder(Type.FOLLOW, null, null, 0);
    }

    public static SquadOrder hold() {
        return new SquadOrder(Type.HOLD, null, null, 0);
    }

    public static SquadOrder work() {
        return new SquadOrder(Type.WORK, null, null, 0);
    }

    public static SquadOrder moveTo(ResourceKey<Level> dimension, BlockPos pos) {
        return new SquadOrder(Type.MOVE_TO, dimension, pos.immutable(), 0);
    }

    public static SquadOrder ward(ResourceKey<Level> dimension, BlockPos pos, int radius) {
        return new SquadOrder(Type.WARD, dimension, pos.immutable(), Math.max(1, radius));
    }

    /** The stance a companion adopts while carrying out this order. */
    public CompanionStance stance() {
        return switch (type) {
            case FOLLOW -> CompanionStance.ESCORT;
            case MOVE_TO, HOLD -> CompanionStance.HOLD;
            case WARD -> CompanionStance.WARD;
            case WORK -> CompanionStance.PATROL;
        };
    }

    /** Whether this order applies in the given dimension. */
    public boolean appliesIn(ResourceKey<Level> level) {
        return dimension == null || dimension.equals(level);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Type", type.id());
        if (dimension != null) tag.putString("Dimension", dimension.location().toString());
        if (pos != null) tag.putLong("Pos", pos.asLong());
        tag.putInt("Radius", radius);
        return tag;
    }

    public static SquadOrder load(CompoundTag tag) {
        Type type = Type.fromId(tag.getString("Type"));
        ResourceKey<Level> dimension = null;
        if (tag.contains("Dimension")) {
            ResourceLocation id = ResourceLocation.tryParse(tag.getString("Dimension"));
            if (id != null) dimension = ResourceKey.create(Registries.DIMENSION, id);
        }
        BlockPos pos = tag.contains("Pos") ? BlockPos.of(tag.getLong("Pos")) : null;
        return new SquadOrder(type, dimension, pos, tag.getInt("Radius"));
    }
}
