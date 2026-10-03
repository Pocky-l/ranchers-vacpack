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
  Shots behave like ragdolls: they fly, bounce off walls, floors and mobs, roll and slide, and only turn back
  into a mob or item once they have almost stopped; mobs then get back on their feet, briefly invulnerable.
  Items smack into mobs for a little damage and knockback, mobs land unharmed.
- **Air stream holding** — mobs that do not fit into the tank (big slimes, cows, zombies...) float in front of the
  nozzle while you vacuum and follow your aim. Shoot to launch them, let go to drop them. Bosses cannot be held.
- **Pulse Wave** — shooting with an empty slot releases a blast of air that knocks back mobs, items and projectiles.
- **Harvesting** — aim at a ripe sweet berry bush or glow berry vine while vacuuming to pull the berries off.
- **Slot selection** — `R` (rebindable) or *Sneak + Scroll*. The tank is shown next to the hotbar.
- **Vacuumable mobs** — baby mobs of any kind (calves, piglets, baby villagers, baby zombies...), small slimes and
  magma cubes, chickens, rabbits, pigs, cats, ocelots, foxes, wolves, parrots, frogs, tadpoles, bees, allays, axolotls,
  squids, dolphins, fish, armadillos, bats, vexes, phantoms, cave spiders, silverfish and endermites. Big slimes,
  leashed mobs, riders and other players' pets cannot be vacuumed.
- Animated model: the core idles and spins up while vacuuming, the gun kicks back on shots and pulse waves, the
  nozzle gulps and the tank bulges on every capture, and the body clacks when switching slots.
- A glowing swirling vortex shows the airflow while vacuuming.
- Custom sounds and particles: motor spin-up, suction rush and spin-down, capture plops and slime squelches,
  air-cannon shots, pulse wave boom, landing thumps, slot clicks; air wisps, capture rings and shot puffs.
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

## Credits

Sound effects are built from CC0 sources: [Kenney](https://kenney.nl)'s Sci-Fi, Impact and Interface sound
packs, and the OpenGameArt uploads "wind whoosh loop", "Air whoosh", "Swishes Sound Pack" and "100 CC0 SFX".

## Building

```sh
./gradlew build
```

The mod jar is written to `build/libs/`.

## License

This project is licensed under the [MIT License](LICENSE).
