package com.pockyl.vacpack.registry;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import com.pockyl.vacpack.Vacpack;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Vacpack.MOD_ID);

    private ModItems() {
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}
