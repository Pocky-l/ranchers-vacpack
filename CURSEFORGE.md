# Rancher's Vacpack

A Slime Rancher inspired vacuum gun for Minecraft: suck up items and small mobs, carry them in a tank and shoot them back out — or blast everything away with a pulse of air.

**Requires [NeoForge](https://neoforged.net) 1.21.1 and [GeckoLib](https://www.curseforge.com/minecraft/mc-mods/geckolib).** Needed on both client and server.

## Features

* **Vacuum** — hold *Use* to suck in dropped items and small mobs. They swirl along a glowing air vortex and shrink into the nozzle. You keep full movement speed while vacuuming.
* **Tank** — 4 slots; each holds one kind of item (up to 64) or one kind of mob (up to 10). Captured mobs keep everything: name, health, equipment, even their owner. The HUD next to the hotbar shows spinning 3D models of them.
* **Shoot** — press *Attack* to launch one item or mob from the selected slot, hold it to fire continuously. Shots are ragdolls: they fly, bounce off walls and mobs, roll, flop onto their side and get back up. Items hit mobs for a little damage; mobs land unharmed.
* **Air stream holding** — mobs too big for the tank (big slimes, cows, zombies...) float in front of the nozzle and follow your aim. Shoot to launch them, let go to drop them.
* **Pulse Wave** — shooting with an empty slot releases a blast of air that sends mobs and items flying and deflects projectiles. Fire it at the ground right next to you for a rocket jump, wind charge style.
* **Harvesting** — vacuum ripe sweet berry bushes and glow berry vines to pull the berries off.
* **Feel** — animated model, third-person aiming pose, custom sounds and particles; under water the airflow turns into bubbles.

## Vacuumable mobs

Babies of any kind, small slimes and magma cubes, chickens, rabbits, pigs, cats, ocelots, foxes, wolves, parrots, frogs, tadpoles, bees, allays, axolotls, squids, dolphins, fish, armadillos, bats, vexes, phantoms, cave spiders, silverfish and endermites. Bosses, leashed mobs, riders and other players' pets are safe.

## Controls

| Action | Default |
| --- | --- |
| Vacuum / hold a mob | Hold *Use* (right click) |
| Shoot / launch held mob / pulse wave | *Attack* (left click), hold for auto-fire |
| Next tank slot | `R` or *Sneak* + *Scroll* |
| Use blocks while holding the vacpack | *Sneak* + *Use* |

## Crafting

|  |  |  |
| --- | --- | --- |
| Iron Ingot | Hopper | Iron Ingot |
| Slime Ball | Breeze Rod | Slime Ball |
| Iron Ingot | Redstone | Iron Ingot |

The recipe unlocks when you pick up a Breeze Rod.

## Configuration

Everything is configurable in the server config (`vacpack-server.toml`, also editable from the in-game mod list): vacuum range and strength, which mobs and items can be vacuumed, tank size, shot speed and damage, pulse wave strength and more. Data pack tags `#vacpack:vacuumable` and `#vacpack:not_vacuumable` control what can be picked up.

## Credits

Made by **Pocky**. Sound effects built from royalty-free sources: Kenney (CC0), OpenGameArt (CC0) and BigSoundBank.

Inspired by Slime Rancher. This is a fan-made mod and is not affiliated with Monomi Park.

Source code: [GitHub](https://github.com/Pocky-l/ranchers-vacpack)

<!-- more-mods:start -->
## More mods by Pocky

[![Holy Staff](https://raw.githubusercontent.com/Pocky-l/holy-staff/main/docs/icon.png)](https://www.curseforge.com/minecraft/mc-mods/holy-staff)

**[Holy Staff](https://www.curseforge.com/minecraft/mc-mods/holy-staff)** - A holy staff with three healing skills, aim previews and flying heal numbers. ([source](https://github.com/Pocky-l/holy-staff))

[![Lumen Rigs](https://raw.githubusercontent.com/Pocky-l/lumen-rigs/main/docs/icon.png)](https://www.curseforge.com/minecraft/mc-mods/lumen-rigs)

**[Lumen Rigs](https://www.curseforge.com/minecraft/mc-mods/lumen-rigs)** - Aimable spotlights, floodlights, searchlights and soft panels with colored light and visible beams. ([source](https://github.com/Pocky-l/lumen-rigs))

[![Neon Glowsticks](https://raw.githubusercontent.com/Pocky-l/neon-glowsticks/main/docs/icon.png)](https://www.curseforge.com/minecraft/mc-mods/neon-glowsticks)

**[Neon Glowsticks](https://www.curseforge.com/minecraft/mc-mods/neon-glowsticks)** - Throwable glowsticks that bounce, roll and light up the dark with colored light. ([source](https://github.com/Pocky-l/neon-glowsticks))

[![Petrichor: Rain & Storms](https://raw.githubusercontent.com/Pocky-l/petrichor/main/docs/icon.png)](https://www.curseforge.com/minecraft/mc-mods/petrichor-rain-storms)

**[Petrichor: Rain & Storms](https://www.curseforge.com/minecraft/mc-mods/petrichor-rain-storms)** - Realistic rain and storms: rain types, puddles, runoff and drips, branching lightning with delayed thunder. ([source](https://github.com/Pocky-l/petrichor))

[![Rustling Leaves](https://raw.githubusercontent.com/Pocky-l/rustling-leaves/main/docs/icon.png)](https://www.curseforge.com/minecraft/mc-mods/rustling-leaves)

**[Rustling Leaves](https://www.curseforge.com/minecraft/mc-mods/rustling-leaves)** - Physically simulated leaves: falling leaves, leaf piles you can wade through, rake and blow away, gusts, whirlwinds and leaf tools. ([source](https://github.com/Pocky-l/rustling-leaves))

<!-- more-mods:end -->
