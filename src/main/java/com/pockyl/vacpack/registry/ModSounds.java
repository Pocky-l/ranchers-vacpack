package com.pockyl.vacpack.registry;

import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import com.pockyl.vacpack.Vacpack;

/** Sounds are built from CC0 sources (Kenney, OpenGameArt) by the workspace's sound generator. */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, Vacpack.MOD_ID);

    public static final RegistryObject<SoundEvent> VACUUM_LOOP = register("vacuum_loop");
    public static final RegistryObject<SoundEvent> VACUUM_START = register("vacuum_start");
    public static final RegistryObject<SoundEvent> VACUUM_STOP = register("vacuum_stop");
    public static final RegistryObject<SoundEvent> CAPTURE = register("capture");
    public static final RegistryObject<SoundEvent> CAPTURE_SLIME = register("capture_slime");
    public static final RegistryObject<SoundEvent> SHOOT = register("shoot");
    public static final RegistryObject<SoundEvent> PULSE = register("pulse");
    public static final RegistryObject<SoundEvent> TANK_FULL = register("tank_full");
    public static final RegistryObject<SoundEvent> SLOT_SWITCH = register("slot_switch");
    public static final RegistryObject<SoundEvent> HARVEST = register("harvest");
    public static final RegistryObject<SoundEvent> LAND = register("land");

    private ModSounds() {
    }

    private static RegistryObject<SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(Vacpack.id(name)));
    }

    public static void register(IEventBus modBus) {
        SOUNDS.register(modBus);
    }
}
