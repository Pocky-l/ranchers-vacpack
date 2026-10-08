package com.pockyl.vacpack.registry;

import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.pockyl.vacpack.Vacpack;
import com.pockyl.vacpack.vacuum.Shot;
import com.pockyl.vacpack.vacuum.VacuumState;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Transient (never saved) per-entity state, kept in a capability on living entities and item entities.
 * Values are looked up by {@link Key}; nothing is allocated until an entity actually gets a value.
 */
public final class ModAttachments {
    public static final Capability<Data> DATA = CapabilityManager.get(new CapabilityToken<>() {
    });

    /** Input and timers of a player using a vacpack. */
    public static final Key<VacuumState> VACUUM_STATE = new Key<>(VacuumState::new);

    /** Set on items and mobs shot out of a vacpack: they are briefly immune to suction and shot items can hit mobs. */
    public static final Key<Shot> SHOT = new Key<>(() -> Shot.NONE);

    /** Set on released mobs and rocket-jumping players: game time until which their next landing deals no fall damage. */
    public static final Key<Long> FALL_GUARD = new Key<>(() -> Long.MIN_VALUE);

    private ModAttachments() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener((RegisterCapabilitiesEvent event) -> event.register(Data.class));
        MinecraftForge.EVENT_BUS.addGenericListener(Entity.class, ModAttachments::attach);
    }

    private static void attach(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof LivingEntity || event.getObject() instanceof ItemEntity) {
            event.addCapability(Vacpack.id("data"), new Provider());
        }
    }

    /** The value of {@code key}, created from its default if the entity has none yet. */
    public static <T> T getData(Entity entity, Key<T> key) {
        Data data = data(entity);
        return data != null ? data.getOrCreate(key) : key.defaultValue.get();
    }

    @Nullable
    public static <T> T getExistingDataOrNull(Entity entity, Key<T> key) {
        Data data = data(entity);
        return data != null ? data.get(key) : null;
    }

    public static <T> void setData(Entity entity, Key<T> key, T value) {
        Data data = data(entity);
        if (data != null) {
            data.values.put(key, value);
        }
    }

    public static void removeData(Entity entity, Key<?> key) {
        Data data = data(entity);
        if (data != null) {
            data.values.remove(key);
        }
    }

    @Nullable
    private static Data data(Entity entity) {
        return entity.getCapability(DATA).orElse(null);
    }

    /** Identifies one kind of per-entity value and its default. */
    public static final class Key<T> {
        private final Supplier<T> defaultValue;

        private Key(Supplier<T> defaultValue) {
            this.defaultValue = defaultValue;
        }
    }

    /** All values of one entity. */
    public static final class Data {
        private final Map<Key<?>, Object> values = new IdentityHashMap<>(4);

        @SuppressWarnings("unchecked")
        private <T> T get(Key<T> key) {
            return (T) values.get(key);
        }

        @SuppressWarnings("unchecked")
        private <T> T getOrCreate(Key<T> key) {
            return (T) values.computeIfAbsent(key, k -> k.defaultValue.get());
        }
    }

    private static final class Provider implements ICapabilityProvider {
        private final LazyOptional<Data> data = LazyOptional.of(Data::new);

        @Override
        public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> capability, @Nullable Direction side) {
            return DATA.orEmpty(capability, data);
        }
    }
}
