# Changelog

All notable changes to this mod are documented in this file.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.1.0+1.20.1] - Unreleased
### Changed
- Ported to Minecraft 1.20.1 (Forge). All features of 1.1.0 are included; the differences below come from things
  that do not exist in 1.20.1.
- The recipe uses a Blaze Rod instead of a Breeze Rod and unlocks when you pick up a Blaze Rod.
- The pulse wave burst close to a block works like a wind charge from later versions: it launches you and nearby mobs,
  toggles wooden doors, trapdoors and fence gates, presses buttons, flips levers, rings bells and blows out candles.
  It shows puffs of air instead of wind charge gusts, without the wind charge sound, and your next landing after the
  burst deals no fall damage.
- Armadillos are not in the game, so they cannot be vacuumed.
- There is no in-game config screen; edit `serverconfig/vacpack-server.toml` of the world instead.

## [1.1.0] - 2026-10-07
### Added
- Creative Vacpack: a pink vacpack for creative mode with bottomless tank slots and no delay between shots that
  vacuums any mob, big or small, except bosses. It is not craftable and sits in the Pocky Mods and Tools & Utilities
  creative tabs.

## [1.0.0] - 2026-10-04
### Added
- The Vacpack: hold right click to vacuum up dropped items and small mobs, left click to shoot them back out.
- A 4-slot tank that stores up to 64 items or 10 mobs per slot; captured mobs keep their name, health and equipment.
- Tank HUD with fill gauges and spinning 3D models of stored mobs; switch slots with `R` or Sneak + Scroll.
- Third-person aiming pose; airflow turns into bubbles under water.
- Suction vortex: vacuumed things float, swirl and shrink into the nozzle; a glowing spiral funnel with contracting
  rings shows the airflow.
- Vacuumable mobs include babies of any kind, cats, foxes, wolves, pigs, squids, dolphins, vexes, phantoms and more.
- Pulse Wave: shooting with an empty slot blasts mobs, items and projectiles away; fired at a block close by it
  launches you (rocket jump) without fall damage, like a wind charge.
- Shots are ragdolls: they bounce off blocks and mobs, roll and slide until they almost stop, then turn back into
  the mob or item, which keeps its momentum and is briefly protected from damage. Items hit mobs for a little damage
  and knockback.
- Air stream holding: mobs that do not fit into the tank float in front of the nozzle and can be launched.
- Vacuum ripe sweet berries and glow berries right off the plant.
- Animated GeckoLib model: idle and vacuum spin, recoil, pulse kick, capture gulp, slot switch clack.
- Custom sounds (from CC0 sources) and particles: motor spin-up/loop/spin-down, capture plops, slime squelches,
  shots, pulse wave, landing thumps, slot clicks, harvest plucks, full-tank warning; air wisps, capture rings,
  shot puffs, pulse rings.
- Crafting recipe with a Breeze Rod, unlocked when you pick one up.
- Shared "Pocky Mods" creative tab (also listed under Tools & Utilities) and a mod logo.
- Server config for range, power, capacities and shooting, plus data pack tags for vacuumable mobs and ignored items.
- English and Russian translations.
