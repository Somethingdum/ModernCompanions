package com.majorbonghits.moderncompanions.squad;

import com.majorbonghits.moderncompanions.Constants;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server-wide squad roster, persisted once on the overworld.
 *
 * <p>Squads are deliberately global rather than per-level: a squad following the
 * player into the Nether is still the same squad, and an order issued before the
 * portal should survive the trip.
 */
public final class CompanionSquadData extends SavedData {
    private static final String DATA_NAME = Constants.MOD_ID + "_squads";
    /** Slot 1 exists for every player as soon as they recruit anyone. */
    public static final int DEFAULT_SLOT = 1;

    private final Map<UUID, Squad> squadsById = new HashMap<>();

    public CompanionSquadData() {}

    public static CompanionSquadData get(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            // Defensive: a server with no overworld cannot persist squads, but the
            // caller should still get a usable, empty roster rather than a crash.
            return new CompanionSquadData();
        }
        return overworld.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(CompanionSquadData::new, CompanionSquadData::load), DATA_NAME);
    }

    @Nullable
    public Squad byId(UUID squadId) {
        return squadId == null ? null : squadsById.get(squadId);
    }

    public List<Squad> squadsOf(UUID owner) {
        List<Squad> owned = new ArrayList<>();
        for (Squad squad : squadsById.values()) {
            if (squad.owner().equals(owner)) owned.add(squad);
        }
        owned.sort((a, b) -> Integer.compare(a.slot(), b.slot()));
        return owned;
    }

    @Nullable
    public Squad bySlot(UUID owner, int slot) {
        for (Squad squad : squadsById.values()) {
            if (squad.owner().equals(owner) && squad.slot() == slot) return squad;
        }
        return null;
    }

    /** Returns the squad in this slot, creating it on first use. */
    public Squad getOrCreate(UUID owner, int slot) {
        int clamped = Math.max(1, Math.min(Squad.MAX_SQUADS, slot));
        Squad existing = bySlot(owner, clamped);
        if (existing != null) return existing;

        Squad squad = new Squad(UUID.randomUUID(), owner, clamped, defaultName(clamped), clamped - 1);
        squadsById.put(squad.id(), squad);
        setDirty();
        return squad;
    }

    /** The squad a companion belongs to, or null if it has not been assigned one. */
    @Nullable
    public Squad squadOf(UUID owner, UUID companionId) {
        for (Squad squad : squadsById.values()) {
            if (squad.owner().equals(owner) && squad.contains(companionId)) return squad;
        }
        return null;
    }

    /**
     * Moves a companion into the given squad, removing it from any other squad
     * first so membership can never be ambiguous.
     */
    public Squad assign(UUID owner, UUID companionId, int slot) {
        unassign(owner, companionId);
        Squad squad = getOrCreate(owner, slot);
        squad.add(companionId);
        setDirty();
        return squad;
    }

    public boolean unassign(UUID owner, UUID companionId) {
        boolean changed = false;
        for (Squad squad : squadsById.values()) {
            if (squad.owner().equals(owner) && squad.remove(companionId)) changed = true;
        }
        if (changed) setDirty();
        return changed;
    }

    public void setOrder(Squad squad, SquadOrder order) {
        squad.setOrder(order);
        setDirty();
    }

    public void markChanged() {
        setDirty();
    }

    private static String defaultName(int slot) {
        // NATO-style names read better in a HUD than "Squad 3".
        String[] names = {"Alpha", "Bravo", "Charlie", "Delta", "Echo", "Foxtrot", "Golf", "Hotel"};
        return names[Math.floorMod(slot - 1, names.length)];
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        for (Squad squad : squadsById.values()) {
            list.add(squad.save());
        }
        tag.put("Squads", list);
        return tag;
    }

    private static CompanionSquadData load(CompoundTag tag, HolderLookup.Provider provider) {
        CompanionSquadData data = new CompanionSquadData();
        ListTag list = tag.getList("Squads", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            Squad squad = Squad.load(list.getCompound(i));
            data.squadsById.put(squad.id(), squad);
        }
        return data;
    }
}
