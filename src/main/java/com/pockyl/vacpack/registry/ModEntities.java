package com.pockyl.vacpack.registry;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import com.pockyl.vacpack.Vacpack;
import com.pockyl.vacpack.entity.TankShot;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, Vacpack.MOD_ID);

    public static final RegistryObject<EntityType<TankShot>> TANK_SHOT = ENTITIES.register("tank_shot",
            () -> EntityType.Builder.<TankShot>of(TankShot::new, MobCategory.MISC)
                    .sized(0.4F, 0.4F)
                    .clientTrackingRange(8)
                    .updateInterval(10)
                    .build(Vacpack.id("tank_shot").toString()));

    private ModEntities() {
    }

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
    }
}
