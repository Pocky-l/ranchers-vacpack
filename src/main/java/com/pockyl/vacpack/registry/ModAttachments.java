package com.pockyl.vacpack.registry;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import com.pockyl.vacpack.Vacpack;
import com.pockyl.vacpack.vacuum.VacuumState;

import java.util.function.Supplier;

/** Transient (never saved) per-entity state. */
public final class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Vacpack.MOD_ID);

    /** Input and timers of a player using a vacpack. */
    public static final Supplier<AttachmentType<VacuumState>> VACUUM_STATE = ATTACHMENTS.register(
            "vacuum_state", () -> AttachmentType.builder(VacuumState::new).build());

    /** Game time at which a mob was shot out of a vacpack; such mobs are briefly immune to suction. */
    public static final Supplier<AttachmentType<Long>> SHOT_AT = ATTACHMENTS.register(
            "shot_at", () -> AttachmentType.builder(() -> Long.MIN_VALUE).build());

    private ModAttachments() {
    }

    public static void register(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
    }
}
