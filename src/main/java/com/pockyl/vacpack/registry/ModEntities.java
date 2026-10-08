package com.pockyl.vacpack.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import com.pockyl.vacpack.Vacpack;
import com.pockyl.vacpack.entity.TankShot;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, Vacpack.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<TankShot>> TANK_SHOT = ENTITIES.register("tank_shot",
            () -> EntityType.Builder.<TankShot>of(TankShot::new, MobCategory.MISC)
                    .sized(0.4F, 0.4F)
                    .clientTrackingRange(8)
                    .updateInterval(10)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, Vacpack.id("tank_shot"))));

    public static final DeferredRegister<EntityDataSerializer<?>> DATA_SERIALIZERS = DeferredRegister.create(
            NeoForgeRegistries.Keys.ENTITY_DATA_SERIALIZERS, Vacpack.MOD_ID);

    /** Synced saved mob data of a {@link TankShot}; vanilla no longer has a serializer for compound tags. */
    public static final DeferredHolder<EntityDataSerializer<?>, EntityDataSerializer<CompoundTag>> COMPOUND_TAG = DATA_SERIALIZERS.register(
            "compound_tag", () -> new EntityDataSerializer<>() {
                @Override
                public StreamCodec<? super RegistryFriendlyByteBuf, CompoundTag> codec() {
                    return ByteBufCodecs.COMPOUND_TAG;
                }

                @Override
                public CompoundTag copy(CompoundTag value) {
                    return value.copy();
                }
            });

    private ModEntities() {
    }

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
        DATA_SERIALIZERS.register(modBus);
    }
}
