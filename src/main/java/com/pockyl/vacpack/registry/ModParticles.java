package com.pockyl.vacpack.registry;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import com.pockyl.vacpack.Vacpack;

public final class ModParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLES =
            DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, Vacpack.MOD_ID);

    /** Air wisp flying into the nozzle. */
    public static final RegistryObject<SimpleParticleType> VACUUM = register("vacuum");
    /** Bubble that replaces airflow particles under water (vanilla bubble texture). */
    public static final RegistryObject<SimpleParticleType> VACUUM_BUBBLE = register("vacuum_bubble");
    /** Soft glowing dot of the suction vortex. */
    public static final RegistryObject<SimpleParticleType> VACUUM_DOT = register("vacuum_dot");
    /** Small ring popping at the nozzle when something is captured. */
    public static final RegistryObject<SimpleParticleType> CAPTURE_RING = register("capture_ring");
    /** Large expanding ring of a pulse wave. */
    public static final RegistryObject<SimpleParticleType> PULSE_RING = register("pulse_ring");
    /** Air puff at the nozzle when shooting. */
    public static final RegistryObject<SimpleParticleType> SHOT_PUFF = register("shot_puff");

    private ModParticles() {
    }

    private static RegistryObject<SimpleParticleType> register(String name) {
        return PARTICLES.register(name, () -> new SimpleParticleType(false));
    }

    public static void register(IEventBus modBus) {
        PARTICLES.register(modBus);
    }
}
