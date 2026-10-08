package com.pockyl.vacpack.tank;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;

import java.util.List;
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

    public static final Codec<TankSlot> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ItemStack.OPTIONAL_CODEC.optionalFieldOf("item", ItemStack.EMPTY).forGetter(TankSlot::item),
            Codec.INT.optionalFieldOf("count", 0).forGetter(TankSlot::count),
            CompoundTag.CODEC.listOf().optionalFieldOf("mobs", List.of()).forGetter(TankSlot::mobs)
    ).apply(instance, TankSlot::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, TankSlot> STREAM_CODEC = StreamCodec.composite(
            ItemStack.OPTIONAL_STREAM_CODEC, TankSlot::item,
            ByteBufCodecs.VAR_INT, TankSlot::count,
            ByteBufCodecs.COMPOUND_TAG.apply(ByteBufCodecs.list()), TankSlot::mobs,
            TankSlot::new);

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
        return holdsMobs() ? MobData.type(mobs.getFirst()) : Optional.empty();
    }

    public boolean matchesItem(ItemStack stack) {
        return holdsItems() && ItemStack.isSameItemSameComponents(item, stack);
    }

    public boolean matchesMob(EntityType<?> type) {
        return mobType().map(t -> t == type).orElse(false);
    }

    // ItemStack has identity equality, but data components must compare by value.
    @Override
    public boolean equals(Object o) {
        return o instanceof TankSlot other
                && count == other.count
                && ItemStack.matches(item, other.item)
                && mobs.equals(other.mobs);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * ItemStack.hashItemAndComponents(item) + count) + mobs.hashCode();
    }
}
