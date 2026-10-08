package com.pockyl.vacpack.registry;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import com.pockyl.vacpack.Vacpack;
import com.pockyl.vacpack.item.VacpackItem;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Vacpack.MOD_ID);

    public static final RegistryObject<VacpackItem> VACPACK = ITEMS.register("vacpack", () -> new VacpackItem(new Item.Properties()
            .stacksTo(1)
            .rarity(Rarity.UNCOMMON), false));

    /** Creative only: not craftable, unlimited slots, no shot cooldown, takes any mob except bosses. */
    public static final RegistryObject<VacpackItem> CREATIVE_VACPACK = ITEMS.register("creative_vacpack",
            () -> new VacpackItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.EPIC), true));

    private ModItems() {
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}
