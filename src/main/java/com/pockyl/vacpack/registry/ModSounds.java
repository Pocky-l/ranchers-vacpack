package com.pockyl.vacpack.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import com.pockyl.vacpack.Vacpack;

/** Sounds are built from CC0 sources (Kenney, OpenGameArt) by the workspace's sound generator. */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, Vacpack.MOD_ID);

    public static final DeferredHolder<SoundEvent, SoundEvent> VACUUM_LOOP = register("vacuum_loop");
    public static final DeferredHolder<SoundEvent, SoundEvent> VACUUM_START = register("vacuum_start");
    public static final DeferredHolder<SoundEvent, SoundEvent> VACUUM_STOP = register("vacuum_stop");
    public static final DeferredHolder<SoundEvent, SoundEvent> CAPTURE = register("capture");
    public static final DeferredHolder<SoundEvent, SoundEvent> CAPTURE_SLIME = register("capture_slime");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHOOT = register("shoot");
    public static final DeferredHolder<SoundEvent, SoundEvent> PULSE = register("pulse");
    public static final DeferredHolder<SoundEvent, SoundEvent> TANK_FULL = register("tank_full");
    public static final DeferredHolder<SoundEvent, SoundEvent> SLOT_SWITCH = register("slot_switch");
    public static final DeferredHolder<SoundEvent, SoundEvent> HARVEST = register("harvest");
    public static final DeferredHolder<SoundEvent, SoundEvent> LAND = register("land");

    private ModSounds() {
    }

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(Vacpack.id(name)));
    }

    public static void register(IEventBus modBus) {
        SOUNDS.register(modBus);
    }
}
