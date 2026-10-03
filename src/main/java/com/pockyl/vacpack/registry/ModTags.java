package com.pockyl.vacpack.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;

import com.pockyl.vacpack.Vacpack;

public final class ModTags {
    /** Mobs the vacpack can suck up (still limited by the max mob size config). */
    public static final TagKey<EntityType<?>> VACUUMABLE = TagKey.create(Registries.ENTITY_TYPE, Vacpack.id("vacuumable"));

    /** Items the vacpack ignores. */
    public static final TagKey<Item> NOT_VACUUMABLE = TagKey.create(Registries.ITEM, Vacpack.id("not_vacuumable"));

    private ModTags() {
    }
}
