package com.majorbonghits.moderncompanions.world.zone;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * An axis-aligned region a squad can be posted to defend.
 *
 * <p>Zones live in level data rather than on any entity, so several companions
 * can share one base and the zone outlives whoever is currently guarding it.
 * The vertical extent is expanded past the two clicked corners because a
 * surface-only box is trivially bypassed from below or above.
 */
public final class PerimeterZone {
    private static final int SAMPLE_SPACING = 8;
    private static final int MAX_SAMPLES = 32;

    private final UUID id;
    private final UUID owner;
    private String name;
    private final BlockPos cornerA;
    private final BlockPos cornerB;
    private final int minY;
    private final int maxY;

    public PerimeterZone(UUID id, UUID owner, String name, BlockPos cornerA, BlockPos cornerB, int minY, int maxY) {
        this.id = id;
        this.owner = owner;
        this.name = name;
        this.cornerA = cornerA.immutable();
        this.cornerB = cornerB.immutable();
        this.minY = minY;
        this.maxY = maxY;
    }

    public UUID id() {
        return id;
    }

    public UUID owner() {
        return owner;
    }

    public String name() {
        return name;
    }

    public void setName(String value) {
        if (value != null && !value.isBlank()) {
            this.name = value.length() > 32 ? value.substring(0, 32) : value;
        }
    }

    public int minX() {
        return ZoneMath.minOf(cornerA.getX(), cornerB.getX());
    }

    public int maxX() {
        return ZoneMath.maxOf(cornerA.getX(), cornerB.getX());
    }

    public int minZ() {
        return ZoneMath.minOf(cornerA.getZ(), cornerB.getZ());
    }

    public int maxZ() {
        return ZoneMath.maxOf(cornerA.getZ(), cornerB.getZ());
    }

    public int minY() {
        return minY;
    }

    public int maxY() {
        return maxY;
    }

    public BlockPos center() {
        return new BlockPos((minX() + maxX()) / 2, (minY + maxY) / 2, (minZ() + maxZ()) / 2);
    }

    public AABB box() {
        return new AABB(minX(), minY, minZ(), maxX() + 1.0D, maxY + 1.0D, maxZ() + 1.0D);
    }

    public boolean contains(Vec3 pos) {
        return ZoneMath.contains(minX(), minY, minZ(), maxX(), maxY, maxZ(), pos.x, pos.y, pos.z);
    }

    public boolean contains(BlockPos pos) {
        return ZoneMath.contains(minX(), minY, minZ(), maxX(), maxY, maxZ(),
                pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
    }

    /** Negative inside, and more negative the deeper in; see {@link ZoneMath#signedEdgeDistance}. */
    public double edgeDistance(Vec3 pos) {
        return ZoneMath.signedEdgeDistance(minX(), minZ(), maxX(), maxZ(), pos.x, pos.z);
    }

    public long volume() {
        return ZoneMath.volume(minX(), minY, minZ(), maxX(), maxY, maxZ());
    }

    /**
     * Evenly spaced points around the boundary, used as candidate sentry posts.
     * Spreading guards across distinct arcs is what gives a base real coverage
     * instead of several companions standing on the same corner.
     */
    public List<BlockPos> boundarySamples() {
        int count = ZoneMath.boundarySampleCount(minX(), minZ(), maxX(), maxZ(), SAMPLE_SPACING, MAX_SAMPLES);
        List<BlockPos> samples = new ArrayList<>(count);
        int width = maxX() - minX();
        int depth = maxZ() - minZ();
        int perimeter = Math.max(1, 2 * (width + depth));
        int y = (minY + maxY) / 2;

        for (int i = 0; i < count; i++) {
            double travelled = ZoneMath.perimeterFraction(i, count) * perimeter;
            samples.add(pointAlongPerimeter(travelled, width, depth, y));
        }
        return samples;
    }

    /** Walks the rectangle edge-by-edge to convert a perimeter distance into a position. */
    private BlockPos pointAlongPerimeter(double travelled, int width, int depth, int y) {
        double remaining = travelled;
        if (remaining < width) {
            return new BlockPos(minX() + (int) remaining, y, minZ());
        }
        remaining -= width;
        if (remaining < depth) {
            return new BlockPos(maxX(), y, minZ() + (int) remaining);
        }
        remaining -= depth;
        if (remaining < width) {
            return new BlockPos(maxX() - (int) remaining, y, maxZ());
        }
        remaining -= width;
        return new BlockPos(minX(), y, maxZ() - (int) Math.min(remaining, depth));
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putUUID("Owner", owner);
        tag.putString("Name", name);
        tag.putLong("CornerA", cornerA.asLong());
        tag.putLong("CornerB", cornerB.asLong());
        tag.putInt("MinY", minY);
        tag.putInt("MaxY", maxY);
        return tag;
    }

    public static PerimeterZone load(CompoundTag tag) {
        return new PerimeterZone(tag.getUUID("Id"), tag.getUUID("Owner"), tag.getString("Name"),
                BlockPos.of(tag.getLong("CornerA")), BlockPos.of(tag.getLong("CornerB")),
                tag.getInt("MinY"), tag.getInt("MaxY"));
    }
}
