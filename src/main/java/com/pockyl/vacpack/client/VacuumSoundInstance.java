package com.pockyl.vacpack.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

import com.pockyl.vacpack.registry.ModSounds;

/** The suction hum: follows the player, fades in when vacuuming starts and out when it stops. */
public final class VacuumSoundInstance extends AbstractTickableSoundInstance {
    private static final float MAX_VOLUME = 0.45F;
    private static final float FADE_IN = 0.14F;
    private static final float FADE_OUT = 0.09F;

    private final Player player;
    private final boolean own;

    public VacuumSoundInstance(Player player) {
        super(ModSounds.VACUUM_LOOP.get(), SoundSource.PLAYERS, player.getRandom());
        this.player = player;
        // The listener sits at the local player's eyes: a source at the same spot has no direction, so OpenAL flips it
        // between the ears when turning. Play our own vacpack centred instead.
        this.own = player == Minecraft.getInstance().player;
        this.relative = own;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.0F;
        this.pitch = 0.9F;
        updatePosition();
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    @Override
    public void tick() {
        boolean active = !player.isRemoved() && ClientVacuumEffects.isVacuuming(player);
        if (active) {
            volume = Math.min(MAX_VOLUME, volume + FADE_IN);
            // Spin-up: the motor pitch rises to normal over the first moments.
            pitch = Math.min(1.0F, pitch + 0.02F);
        } else {
            volume -= FADE_OUT;
            pitch = Math.max(0.6F, pitch - 0.04F);
            if (volume <= 0.0F) {
                stop();
                return;
            }
        }
        updatePosition();
    }

    private void updatePosition() {
        if (own) {
            x = 0;
            y = 0;
            z = 0;
        } else {
            x = player.getX();
            y = player.getEyeY();
            z = player.getZ();
        }
    }
}
