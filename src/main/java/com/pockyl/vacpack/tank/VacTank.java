package com.pockyl.vacpack.tank;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Immutable contents of a vacpack tank, stored as a data component on the item.
 * All mutators return a new instance; slot count and capacities come from the config and are passed in,
 * so a tank keeps working when the config changes (extra slots are kept but cannot be filled).
 */
public record VacTank(List<TankSlot> slots, int selected) {
    public static final VacTank EMPTY = new VacTank(List.of(), 0);

    public static final Codec<VacTank> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            TankSlot.CODEC.listOf().optionalFieldOf("slots", List.of()).forGetter(VacTank::slots),
            Codec.INT.optionalFieldOf("selected", 0).forGetter(VacTank::selected)
    ).apply(instance, VacTank::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, VacTank> STREAM_CODEC = StreamCodec.composite(
            TankSlot.STREAM_CODEC.apply(ByteBufCodecs.list()), VacTank::slots,
            ByteBufCodecs.VAR_INT, VacTank::selected,
            VacTank::new);

    public VacTank {
        slots = List.copyOf(slots);
        selected = Math.max(0, selected);
    }

    public TankSlot slot(int index) {
        return index >= 0 && index < slots.size() ? slots.get(index) : TankSlot.EMPTY;
    }

    public TankSlot selectedSlot() {
        return slot(selected);
    }

    public boolean isEmpty() {
        return slots.stream().allMatch(TankSlot::isEmpty);
    }

    public int totalAmount() {
        return slots.stream().mapToInt(TankSlot::amount).sum();
    }

    public VacTank withSlot(int index, TankSlot slot) {
        List<TankSlot> copy = new ArrayList<>(slots);
        while (copy.size() <= index) {
            copy.add(TankSlot.EMPTY);
        }
        copy.set(index, slot);
        while (!copy.isEmpty() && copy.getLast().isEmpty()) {
            copy.removeLast();
        }
        return new VacTank(copy, selected);
    }

    public VacTank select(int index, int slotCount) {
        return new VacTank(slots, Math.floorMod(index, Math.max(1, slotCount)));
    }

    public VacTank cycle(int delta, int slotCount) {
        return select(selected + delta, slotCount);
    }

    /** Slot that would receive this item: a matching slot with room first, then the first empty one; -1 if none. */
    public int slotForItem(ItemStack stack, int slotCount, int capacity) {
        for (int i = 0; i < slotCount; i++) {
            TankSlot slot = slot(i);
            if (slot.matchesItem(stack) && slot.count() < capacity) {
                return i;
            }
        }
        return firstEmpty(slotCount);
    }

    public int slotForMob(EntityType<?> type, int slotCount, int capacity) {
        for (int i = 0; i < slotCount; i++) {
            TankSlot slot = slot(i);
            if (slot.matchesMob(type) && slot.mobs().size() < capacity) {
                return i;
            }
        }
        return firstEmpty(slotCount);
    }

    private int firstEmpty(int slotCount) {
        for (int i = 0; i < slotCount; i++) {
            if (slot(i).isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    /** Inserts as much of {@code stack} as fits, spreading over several slots if needed. */
    public Insertion insertItem(ItemStack stack, int slotCount, int capacity) {
        VacTank tank = this;
        int remaining = stack.getCount();
        while (remaining > 0) {
            int index = tank.slotForItem(stack, slotCount, capacity);
            if (index < 0) {
                break;
            }
            TankSlot slot = tank.slot(index);
            int stored = slot.isEmpty() ? 0 : slot.count();
            int moved = Math.min(remaining, capacity - stored);
            tank = tank.withSlot(index, TankSlot.ofItems(stack, stored + moved));
            remaining -= moved;
        }
        return new Insertion(tank, stack.getCount() - remaining);
    }

    /** Stores a saved mob; returns null when no slot can take it. */
    public VacTank insertMob(EntityType<?> type, CompoundTag mob, int slotCount, int capacity) {
        int index = slotForMob(type, slotCount, capacity);
        if (index < 0) {
            return null;
        }
        List<CompoundTag> mobs = new ArrayList<>(slot(index).mobs());
        mobs.add(mob);
        return withSlot(index, TankSlot.ofMobs(mobs));
    }

    /** Removes one item or the most recently captured mob from the selected slot. */
    public Taken takeFromSelected() {
        TankSlot slot = selectedSlot();
        if (slot.holdsItems()) {
            ItemStack item = slot.item().copyWithCount(1);
            return new Taken(withSlot(selected, TankSlot.ofItems(slot.item(), slot.count() - 1)), item, null);
        }
        if (slot.holdsMobs()) {
            List<CompoundTag> mobs = new ArrayList<>(slot.mobs());
            CompoundTag mob = mobs.removeLast();
            return new Taken(withSlot(selected, mobs.isEmpty() ? TankSlot.EMPTY : TankSlot.ofMobs(mobs)), ItemStack.EMPTY, mob);
        }
        return new Taken(this, ItemStack.EMPTY, null);
    }

    public record Insertion(VacTank tank, int inserted) {
    }

    /** Result of a take: exactly one of {@code item} (non-empty) or {@code mob} (non-null) is set, or neither. */
    public record Taken(VacTank tank, ItemStack item, CompoundTag mob) {
        public boolean isEmpty() {
            return item.isEmpty() && mob == null;
        }
    }
}
