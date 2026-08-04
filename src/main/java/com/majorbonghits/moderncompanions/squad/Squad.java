package com.majorbonghits.moderncompanions.squad;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.DyeColor;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * A named group of one player's companions that can be ordered as a unit.
 *
 * <p>Membership lives here rather than on the entities so a squad survives its
 * members dying, unloading, or being carried between dimensions. Companions
 * cache their squad id for fast lookup, and that cache is reconciled against
 * this list, which is authoritative.
 */
public final class Squad {
    /** Squads are addressed by slot number, matching the 1-8 selection keys. */
    public static final int MAX_SQUADS = 8;

    private final UUID id;
    private final UUID owner;
    private final int slot;
    private String name;
    private int colorIndex;
    private final Set<UUID> members = new LinkedHashSet<>();
    private SquadOrder order = SquadOrder.follow();

    public Squad(UUID id, UUID owner, int slot, String name, int colorIndex) {
        this.id = id;
        this.owner = owner;
        this.slot = slot;
        this.name = name;
        this.colorIndex = colorIndex;
    }

    public UUID id() {
        return id;
    }

    public UUID owner() {
        return owner;
    }

    /** 1-based slot used by the selection keys and command arguments. */
    public int slot() {
        return slot;
    }

    public String name() {
        return name;
    }

    public void setName(String value) {
        if (value != null && !value.isBlank()) {
            this.name = value.length() > 32 ? value.substring(0, 32) : value;
        }
    }

    public int colorIndex() {
        return colorIndex;
    }

    public void setColorIndex(int value) {
        this.colorIndex = Math.floorMod(value, DyeColor.values().length);
    }

    public DyeColor color() {
        return DyeColor.values()[Math.floorMod(colorIndex, DyeColor.values().length)];
    }

    /** Unmodifiable view; use {@link #add} and {@link #remove} to change membership. */
    public List<UUID> members() {
        return List.copyOf(members);
    }

    public int size() {
        return members.size();
    }

    public boolean contains(UUID companionId) {
        return members.contains(companionId);
    }

    public boolean add(UUID companionId) {
        return members.add(companionId);
    }

    public boolean remove(UUID companionId) {
        return members.remove(companionId);
    }

    public SquadOrder order() {
        return order;
    }

    public void setOrder(SquadOrder value) {
        this.order = value == null ? SquadOrder.follow() : value;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putUUID("Owner", owner);
        tag.putInt("Slot", slot);
        tag.putString("Name", name);
        tag.putInt("Color", colorIndex);
        ListTag memberList = new ListTag();
        for (UUID member : members) {
            memberList.add(NbtUtils.createUUID(member));
        }
        tag.put("Members", memberList);
        tag.put("Order", order.save());
        return tag;
    }

    public static Squad load(CompoundTag tag) {
        Squad squad = new Squad(tag.getUUID("Id"), tag.getUUID("Owner"), tag.getInt("Slot"),
                tag.getString("Name"), tag.getInt("Color"));
        ListTag memberList = tag.getList("Members", Tag.TAG_INT_ARRAY);
        List<UUID> loaded = new ArrayList<>();
        for (int i = 0; i < memberList.size(); i++) {
            loaded.add(NbtUtils.loadUUID(memberList.get(i)));
        }
        squad.members.addAll(loaded);
        if (tag.contains("Order")) {
            squad.setOrder(SquadOrder.load(tag.getCompound("Order")));
        }
        return squad;
    }
}
