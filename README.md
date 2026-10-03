# Rancher's Vacpack

A Slime Rancher inspired vacuum gun for Minecraft: suck up items and small mobs, store them in a tank and shoot them back out.

| | |
|---|---|
| Minecraft | 1.21.1 |
| Loader | NeoForge |
| Requires | [GeckoLib](https://www.curseforge.com/minecraft/mc-mods/geckolib) |
| License | MIT |

## Features

- **Vacuum** — hold *Use* (right click) to pull dropped items and small mobs in a cone in front of you.
  They float, swirl along a visible air vortex and shrink into the nozzle. Movement is not slowed down while vacuuming.
- **Tank** — 4 slots; each slot holds one kind of item (up to 64) or one kind of mob (up to 10).
  Captured mobs keep everything: name, health, equipment.
- **Shoot** — press *Attack* (left click) to launch one item or mob from the selected slot; hold it to fire continuously.
  Shot items smack into mobs, dealing a little damage and knockback.
- **Pulse Wave** — shooting with an empty slot releases a blast of air that knocks back mobs, items and projectiles.
- **Harvesting** — aim at a ripe sweet berry bush or glow berry vine while vacuuming to pull the berries off.
- **Slot selection** — `R` (rebindable) or *Sneak + Scroll*. The tank is shown next to the hotbar.
- **Vacuumable mobs** — small slimes and magma cubes, chickens, rabbits, frogs, tadpoles, bees, allays, axolotls,
  silverfish, endermites, bats, parrots, fish and armadillos. Big slimes, leashed mobs, riders and other players' pets
  cannot be vacuumed.
- Animated model: the drum spins while vacuuming and the gun kicks back on every shot.
- Custom sounds: motor hum while vacuuming, a slurp for every capture, a punchy shot and a buzz when the tank is full.
- HUD with a fill gauge per slot.
- Sneak + use on a block still opens chests, doors and other blocks while holding the vacpack.

## Crafting

```
I H I      I = Iron Ingot     H = Hopper
S B S      S = Slime Ball     B = Breeze Rod
I R I      R = Redstone
```

The recipe is unlocked once you pick up a Breeze Rod.

## Configuration

All numbers are in the server config (`<world>/serverconfig/vacpack-server.toml`, also editable from the in-game
mod list): range, cone angle, pull strength, capture distance, slot count, slot capacities, max mob size,
shot speed, cooldown and damage, pulse wave range, strength and cooldown. Item and mob vacuuming, berry harvesting and
the pulse wave can be switched off separately.

## Data packs

- `#vacpack:vacuumable` (entity types) — mobs that can be vacuumed.
- `#vacpack:not_vacuumable` (items) — items the vacpack ignores.

## Building

```sh
./gradlew build
```

The mod jar is written to `build/libs/`.

## License

This project is licensed under the [MIT License](LICENSE).
