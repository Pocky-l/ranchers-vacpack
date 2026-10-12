package com.pockyl.vacpack.vacuum;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.gameevent.GameEvent;

import com.pockyl.vacpack.Config;
import com.pockyl.vacpack.entity.TankShot;

/**
 * Feeding by shot: an animal hit by a shot of its own food eats it, as if a player fed it by hand. Babies grow up
 * faster, grown-ups fall in love (breeding), the shooter's hurt pets are healed. Animals that need taming first
 * (wolves, cats, horses...) only eat once they are tame.
 */
public final class ShotFeeding {
    private ShotFeeding() {
    }

    /**
     * Feeds the target if it wants the shot item; returns whether it ate it (the shot then deals no damage).
     * {@link TankShot} asks only for shots fired from the tank, like for the item effects.
     */
    public static boolean tryFeed(TankShot shot, LivingEntity target) {
        if (!Config.shotFeeding() || shot.carriesMob() || !(shot.level() instanceof ServerLevel level)
                || !(shot.getOwner() instanceof Player shooter) || !(target instanceof Animal animal) || !animal.isAlive()) {
            return false;
        }
        ItemStack food = shot.getItem();
        if (food.isEmpty() || !animal.isFood(food) || !feed(animal, shooter, food)) {
            return false;
        }
        level.playSound(null, animal.getX(), animal.getY(), animal.getZ(), SoundEvents.GENERIC_EAT, SoundSource.NEUTRAL, 1.0F,
                0.9F + level.random.nextFloat() * 0.2F);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, animal.getX(), animal.getY() + animal.getBbHeight() * 0.6, animal.getZ(), 5,
                animal.getBbWidth() * 0.4, animal.getBbHeight() * 0.3, animal.getBbWidth() * 0.4, 0.0);
        animal.gameEvent(GameEvent.EAT);
        return true;
    }

    private static boolean feed(Animal animal, Player shooter, ItemStack food) {
        boolean tame = isTame(animal);
        if (animal.isBaby()) {
            animal.ageUp(AgeableMob.getSpeedUpSecondsWhenFeeding(-animal.getAge()), true);
            return true;
        }
        if (!tame) {
            return false;
        }
        // Like feeding by hand: a hurt pet eats to heal first.
        boolean ownPet = animal instanceof TamableAnimal pet && shooter.getUUID().equals(pet.getOwnerUUID());
        if (ownPet && animal.getHealth() < animal.getMaxHealth()) {
            FoodProperties properties = food.getFoodProperties(animal);
            animal.heal(properties != null ? properties.nutrition() : 1.0F);
            return true;
        }
        if (animal.getAge() == 0 && animal.canFallInLove()) {
            // Sends the hearts to the clients; the shooter gets the breeding credit.
            animal.setInLove(shooter);
            return true;
        }
        return false;
    }

    /** Animals that have to be tamed before they breed count as tame only once they are. */
    private static boolean isTame(Animal animal) {
        if (animal instanceof TamableAnimal tamable) {
            return tamable.isTame();
        }
        if (animal instanceof AbstractHorse horse) {
            return horse.isTamed();
        }
        return true;
    }
}
