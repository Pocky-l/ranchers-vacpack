package com.pockyl.vacpack.registry;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import com.pockyl.vacpack.Vacpack;
import com.pockyl.vacpack.item.VacpackItem;
import com.pockyl.vacpack.tank.VacTank;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Vacpack.MOD_ID);

    public static final DeferredItem<VacpackItem> VACPACK = ITEMS.registerItem("vacpack", properties -> new VacpackItem(properties
            .stacksTo(1)
            .rarity(Rarity.UNCOMMON)
            .component(ModDataComponents.TANK.get(), VacTank.EMPTY)));

    private ModItems() {
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}
