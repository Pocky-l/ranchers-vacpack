package com.pockyl.vacpack.vacuum;

import java.util.UUID;

/** Marks an entity that was just shot out of a vacpack: by whom and at which game time. */
public record Shot(UUID shooter, long time) {
    public static final Shot NONE = new Shot(new UUID(0, 0), Long.MIN_VALUE);

    public long age(long gameTime) {
        return gameTime - time;
    }
}
