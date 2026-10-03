package com.pockyl.vacpack.registry;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import com.pockyl.vacpack.Vacpack;
import com.pockyl.vacpack.tank.VacTank;

public final class ModDataComponents {
    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(
            Registries.DATA_COMPONENT_TYPE, Vacpack.MOD_ID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<VacTank>> TANK = COMPONENTS.registerComponentType(
            "tank", builder -> builder.persistent(VacTank.CODEC).networkSynchronized(VacTank.STREAM_CODEC).cacheEncoding());

    private ModDataComponents() {
    }

    public static void register(IEventBus modBus) {
        COMPONENTS.register(modBus);
    }
}
