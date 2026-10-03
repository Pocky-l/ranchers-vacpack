package com.pockyl.vacpack.registry;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import com.pockyl.vacpack.Vacpack;

public final class ModParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(Registries.PARTICLE_TYPE, Vacpack.MOD_ID);

    /** Air wisp flying into the nozzle. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> VACUUM = register("vacuum");
    /** Soft glowing dot of the suction vortex. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> VACUUM_DOT = register("vacuum_dot");
    /** Small ring popping at the nozzle when something is captured. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> CAPTURE_RING = register("capture_ring");
    /** Large expanding ring of a pulse wave. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> PULSE_RING = register("pulse_ring");
    /** Air puff at the nozzle when shooting. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SHOT_PUFF = register("shot_puff");

    private ModParticles() {
    }

    private static DeferredHolder<ParticleType<?>, SimpleParticleType> register(String name) {
        return PARTICLES.register(name, () -> new SimpleParticleType(false));
    }

    public static void register(IEventBus modBus) {
        PARTICLES.register(modBus);
    }
}
