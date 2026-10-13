package com.pockyl.vacpack.vacuum;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import com.pockyl.vacpack.Vacpack;

/**
 * The vacpack advancements ({@code data/vacpack/advancement/vacpack/}). Each has a single {@code minecraft:impossible}
 * criterion named {@code done} that the vacuum code awards when the player does the thing.
 */
public final class VacAdvancements {
    /** First mob caught in the tank. */
    public static final String VAC_ATTACK = "vac_attack";
    /** A tank slot filled up with slimes. */
    public static final String SLIME_COLLECTOR = "slime_collector";
    /** Someone else's projectile blown back with a pulse wave. */
    public static final String RETURN_TO_SENDER = "return_to_sender";

    private VacAdvancements() {
    }

    public static void award(Player player, String name) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        AdvancementHolder advancement = serverPlayer.server.getAdvancements().get(Vacpack.id("vacpack/" + name));
        if (advancement != null) {
            serverPlayer.getAdvancements().award(advancement, "done");
        }
    }
}
