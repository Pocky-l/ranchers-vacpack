package com.pockyl.vacpack.tank;

import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityProcessor;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntitySpawnRequest;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.Optional;

/** Converts mobs to and from the saved data kept in the tank and in shots. */
public final class MobData {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Stored mobs come back exactly as they were saved, like entities loaded from a chunk. */
    private static final EntitySpawnRequest LOAD = new EntitySpawnRequest(EntitySpawnReason.LOAD, true);
    private static final int DISPLAY_ENTITY_ID = -1;

    private MobData() {
    }

    /** Full save of an entity including its {@code id}, or null if the entity cannot be saved. */
    public static @Nullable CompoundTag save(Entity entity) {
        try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(entity.problemPath(), LOGGER)) {
            TagValueOutput output = TagValueOutput.createWithContext(reporter, entity.registryAccess());
            return entity.save(output) ? output.buildResult() : null;
        }
    }

    public static Optional<EntityType<?>> type(CompoundTag data) {
        return data.read("id", EntityType.CODEC);
    }

    /**
     * Creates a render copy of the saved entity that is never added to the level. Like the mob shown in a spawner, it
     * gets the display entity id, since renderers read the id and unassigned ids throw.
     */
    public static Optional<Entity> createForDisplay(CompoundTag data, Level level) {
        try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(LOGGER)) {
            Optional<Entity> entity = EntityType.create(TagValueInput.create(reporter, level.registryAccess(), data), level, LOAD);
            entity.ifPresent(e -> e.setId(DISPLAY_ENTITY_ID));
            return entity;
        }
    }

    /** Loads the saved entity with its passengers; {@code postLoad} runs before passengers are attached. */
    public static @Nullable Entity load(CompoundTag data, Level level, EntityProcessor postLoad) {
        return EntityType.loadEntityRecursive(data, level, LOAD, postLoad);
    }
}
