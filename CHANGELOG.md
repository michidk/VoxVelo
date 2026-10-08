# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.1.0] - Unreleased

First public release, for Minecraft 26.3 (Fabric). **VoxVelo Bikes** (`voxvelo_bikes`) provides standalone
bicycles. **VoxVelo Fitness Library** (`voxvelo_fitness_lib`) provides vehicle-independent fitness services.
**VoxVelo Fitness** (`voxvelo_fitness`) requires matching versions of both. Each mod has its own jar and
Modrinth/CurseForge project. Old mod IDs, namespaces and configuration paths are not migrated.

### Added

- Road bike and road bike frame use `voxvelo_bikes:road_bike` and `voxvelo_bikes:road_bike_frame`.
- Separate `bikes`, `fitness-lib` and `fitness` modules, with independent Bikes and library compilation.
- CI verifies all three jars and publishes Fitness with both required project dependencies.

#### Bikes mod

- Multiplayer bicycle entity that rides like a horse or boat: the rider's client simulates it, the server validates
  the versioned `bike_state` packet and handles the consequences.
- Road, gravel and mountain bikes with their own physics, suspension and GeckoLib models, and a rider pose that
  follows the bars and pedals.
- Power-based physics with rolling resistance per surface, air drag, slope gravity read from the terrain, braking,
  one-block step climbing, suspension and reduced fall damage.
- Collisions: blocks, other bikes, boats and minecarts; mobs and players are shoved or hurt depending on closing
  speed and mass. Rotating multipart hitboxes.
- Craftable bike parts (frames, wheels, tires, handlebars, saddle, pedals, helmet) with recipe-book unlocks, dyeable
  frames and helmets, and re-tiring wheels with tire wear.
- Bike durability from crashes and falls, Bike Wrench repair and dismantling, server option to disable vehicle
  damage.
- Speed Boost I–III enchantment for assembled bikes.
- Keyboard riding with rebindable keys, rider mass and personal speed limit, and a settings hub (`B`) linked from
  the pause and options menus. Settings pages use the vanilla options layout, grouped under headings, and scroll
  when the window is small.
- `/voxvelo_bikes track` generator for seeded, terrain-following loop roads with slab slopes, lit borders, tunnels,
  bridges, water and lava crossings, tree removal, progress boss bar, `status` and `cancel`.
- Distance Cycled and Time on a Bicycle statistics.

#### Fitness add-on

- Bluetooth FTMS smart trainers as propulsion, plus secondary power meters (Cycling Power Service) and heart rate
  sensors (Heart Rate Service), managed in one Bluetooth Devices menu with auto-connect.
- Trainer resistance feedback from slope and surface, with a configurable slope window, intensity and safe limits.
- Virtual shifting with 12 gears, physical ratio and wheel circumference settings.
- OpenBikeControl steering, braking and shifting over mDNS/TCP or Bluetooth LE (works with the BikeControl phone
  app).
- Mixed input: keyboard, trainer, power meter and controller work together without choosing a mode.
- Road follow (hands-free steering) with configurable road blocks and LEFT / RIGHT junction prompts.
- Riding HUD, heart rate graph and a server-wide rider stats overlay with sharing controls.
- Ride recording with summaries, FIT export (optionally with GPS), and ride maps drawn by the server.
- Fitness settings hub (`F8`) listing the same pages as the bike settings; vehicle mods add pages with
  `Fitness.addSettingsPage`.

[0.1.0]: https://github.com/michidk/VoxVelo/releases/tag/v0.1.0
