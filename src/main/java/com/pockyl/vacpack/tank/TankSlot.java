package com.pockyl.vacpack.tank;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One tank slot. Holds either a number of identical items or a list of captured mobs of one type, never both.
 *
 * @param item  item template (count 1), or empty for mob/empty slots
 * @param count number of stored items; unused for mob slots
 * @param mobs  full saved data of each captured mob, including its {@code id}
 */
public record TankSlot(ItemStack item, int count, List<CompoundTag> mobs) {
    public static final TankSlot EMPTY = new TankSlot(ItemStack.EMPTY, 0, List.of());

    public TankSlot {
        item = item.isEmpty() ? ItemStack.EMPTY : item.copyWithCount(1);
        mobs = List.copyOf(mobs);
        if (item.isEmpty()) {
            count = 0;
        }
    }

    public static TankSlot ofItems(ItemStack item, int count) {
        return count <= 0 ? EMPTY : new TankSlot(item, count, List.of());
    }

    public static TankSlot ofMobs(List<CompoundTag> mobs) {
        return new TankSlot(ItemStack.EMPTY, 0, mobs);
    }

    public static TankSlot load(CompoundTag tag) {
        ItemStack item = tag.contains("item", Tag.TAG_COMPOUND) ? ItemStack.of(tag.getCompound("item")) : ItemStack.EMPTY;
        List<CompoundTag> mobs = new ArrayList<>();
        ListTag list = tag.getList("mobs", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            mobs.add(list.getCompound(i));
        }
        return new TankSlot(item, tag.getInt("count"), mobs);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        if (!item.isEmpty()) {
            tag.put("item", item.save(new CompoundTag()));
            tag.putInt("count", count);
        }
        if (!mobs.isEmpty()) {
            ListTag list = new ListTag();
            mobs.forEach(mob -> list.add(mob.copy()));
            tag.put("mobs", list);
        }
        return tag;
    }

    public boolean isEmpty() {
        return item.isEmpty() && mobs.isEmpty();
    }

    public boolean holdsItems() {
        return !item.isEmpty() && count > 0;
    }

    public boolean holdsMobs() {
        return !mobs.isEmpty();
    }

    public int amount() {
        return holdsMobs() ? mobs.size() : count;
    }

    public Optional<EntityType<?>> mobType() {
        return holdsMobs() ? EntityType.by(mobs.get(0)) : Optional.empty();
    }

    /** The mob that would be shot next (the most recently captured one). */
    public CompoundTag lastMob() {
        return mobs.get(mobs.size() - 1);
    }

    public boolean matchesItem(ItemStack stack) {
        return holdsItems() && ItemStack.isSameItemSameTags(item, stack);
    }

    public boolean matchesMob(EntityType<?> type) {
        return mobType().map(t -> t == type).orElse(false);
    }

    // ItemStack has identity equality, but tank contents must compare by value.
    @Override
    public boolean equals(Object o) {
        return o instanceof TankSlot other
                && count == other.count
                && ItemStack.matches(item, other.item)
                && mobs.equals(other.mobs);
    }

    @Override
    public int hashCode() {
        int itemHash = item.isEmpty() ? 0 : 31 * item.getItem().hashCode() + Objects.hashCode(item.getTag());
        return 31 * (31 * itemHash + count) + mobs.hashCode();
    }
}
