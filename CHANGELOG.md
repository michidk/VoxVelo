# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.1.0] - Unreleased

First public release, for Minecraft 26.3 (Fabric). **VoxVelo** (`voxvelo`) is the bikes bike mod;
**VoxVelo Fitness** (`voxvelo_fitness`) is an optional add-on requiring the matching bikes version.

### Added

- Road bike and road bike frame use `voxvelo:road_bike` and `voxvelo:road_bike_frame`; legacy `race_bike`
  and `race_bike_frame` inventory items still load through registry aliases.

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
  the pause and options menus.
- `/voxvelo track` generator for seeded, terrain-following loop roads with slab slopes, lit borders, tunnels,
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

[0.1.0]: https://github.com/michidk/VoxVelo/releases/tag/v0.1.0
