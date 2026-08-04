package com.majorbonghits.moderncompanions.world.zone;

import com.majorbonghits.moderncompanions.Constants;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Perimeter zones for one level.
 *
 * <p>Unlike squads, zones are deliberately per-dimension: a base in the Nether
 * is a different place from a base in the Overworld, and a zone's coordinates
 * are meaningless outside the level they were drawn in.
 */
public final class PerimeterZoneData extends SavedData {
    private static final String DATA_NAME = Constants.MOD_ID + "_zones";
    /** Guards against a mis-click drawing a zone across half the world. */
    public static final long MAX_ZONE_VOLUME = 8_000_000L;
    public static final int MAX_ZONES_PER_PLAYER = 16;

    private final Map<UUID, PerimeterZone> zonesById = new HashMap<>();

    public PerimeterZoneData() {}

    public static PerimeterZoneData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(PerimeterZoneData::new, PerimeterZoneData::load), DATA_NAME);
    }

    @Nullable
    public PerimeterZone byId(UUID zoneId) {
        return zoneId == null ? null : zonesById.get(zoneId);
    }

    public List<PerimeterZone> zonesOf(UUID owner) {
        List<PerimeterZone> owned = new ArrayList<>();
        for (PerimeterZone zone : zonesById.values()) {
            if (zone.owner().equals(owner)) owned.add(zone);
        }
        owned.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        return owned;
    }

    public void add(PerimeterZone zone) {
        zonesById.put(zone.id(), zone);
        setDirty();
    }

    public boolean remove(UUID zoneId) {
        boolean removed = zonesById.remove(zoneId) != null;
        if (removed) setDirty();
        return removed;
    }

    public void markChanged() {
        setDirty();
    }

    /** The zone containing a position, preferring the smallest when they overlap. */
    @Nullable
    public PerimeterZone zoneAt(UUID owner, net.minecraft.core.BlockPos pos) {
        PerimeterZone best = null;
        for (PerimeterZone zone : zonesById.values()) {
            if (!zone.owner().equals(owner) || !zone.contains(pos)) continue;
            if (best == null || zone.volume() < best.volume()) best = zone;
        }
        return best;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        for (PerimeterZone zone : zonesById.values()) {
            list.add(zone.save());
        }
        tag.put("Zones", list);
        return tag;
    }

    private static PerimeterZoneData load(CompoundTag tag, HolderLookup.Provider provider) {
        PerimeterZoneData data = new PerimeterZoneData();
        ListTag list = tag.getList("Zones", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            PerimeterZone zone = PerimeterZone.load(list.getCompound(i));
            data.zonesById.put(zone.id(), zone);
        }
        return data;
    }
}
