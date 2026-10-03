package com.pockyl.vacpack.registry;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import com.pockyl.vacpack.Vacpack;
import com.pockyl.vacpack.vacuum.Shot;
import com.pockyl.vacpack.vacuum.VacuumState;

import java.util.function.Supplier;

/** Transient (never saved) per-entity state. */
public final class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Vacpack.MOD_ID);

    /** Input and timers of a player using a vacpack. */
    public static final Supplier<AttachmentType<VacuumState>> VACUUM_STATE = ATTACHMENTS.register(
            "vacuum_state", () -> AttachmentType.builder(VacuumState::new).build());

    /** Set on items and mobs shot out of a vacpack: they are briefly immune to suction and shot items can hit mobs. */
    public static final Supplier<AttachmentType<Shot>> SHOT = ATTACHMENTS.register(
            "shot", () -> AttachmentType.builder(() -> Shot.NONE).build());

    /** Set on released mobs: their first landing after a shot deals no fall damage. */
    public static final Supplier<AttachmentType<Boolean>> FALL_GUARD = ATTACHMENTS.register(
            "fall_guard", () -> AttachmentType.builder(() -> false).build());

    private ModAttachments() {
    }

    public static void register(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
    }
}
