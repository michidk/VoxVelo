# VoxelVelo

Rideable, fully multiplayer bicycles for Minecraft (Fabric). Ride with the keyboard, or bring a smart
trainer (Bluetooth FTMS) and OpenBikeControl steering such as the BikeControl phone app.

> **Status:** Milestones 1 to 3 (multiplayer bicycles, keyboard riding, three bike types, physics, terrain,
> gradients, collisions and FTMS trainer power) are implemented. OpenBikeControl, full mixed-input configuration,
> trainer feedback and the HUD are planned (see [Roadmap](#roadmap)).

## What it does

- A real `BikeEntity` that is simulated **on the server**. Clients only send control intentions.
- Everyone sees the bike, the rider, steering, wheel and crank rotation.
- Power-based physics: rider power becomes drive force, with rolling resistance (per surface), air drag,
  slope gravity, braking, collisions and a top speed. Keyboard "pedaling" is a virtual power value fed into
  the same physics a trainer will use.
- No companion app, no custom bridge. Hardware talks directly to the Minecraft client using the standards.

## Supported versions

| Component | Version |
|-----------|---------|
| Minecraft | 26.3 |
| Fabric Loader | >= 0.19.5 |
| Fabric API | 0.161.0+26.3 |
| GeckoLib | 5.5.7 (required on clients and servers) |
| Java | 25 |

## Installation

- **Players:** put the mod jar, Fabric API and GeckoLib in `mods/`.
- **Servers:** put the same three jars in the server's `mods/`. The mod is required on the server because the
  bicycle is a real synchronized entity. Vanilla servers are not supported.
- Dedicated servers never load any client code (input, rendering, and later Bluetooth/networking hardware).

## Multiplayer

Works on the integrated server, LAN and dedicated Fabric servers. The server owns position, velocity,
collisions, terrain and the mounted rider. The client sends a small versioned packet (`bike_control`,
protocol v1) at most once per tick on change and as a heartbeat every 5 ticks; every field is clamped on the
server, and if packets stop for 15 ticks (0.75 s) power and steering are dropped so a disconnected player
cannot keep accelerating. Only the seated player can control a bike; one rider per bike.

## Bike types

Three bikes share one simulation and differ only in their parameters (`BikeType`):

| | Road | Gravel | Mountain |
|---|---|---|---|
| Mass | light | medium | heavy |
| Hard ground | fastest | good | slowest |
| Loose ground (sand, dirt) | very slow, little grip | good | best, most grip |
| Braking | weakest off-road | good | strongest |
| Turning | quick at speed | balanced | tight lock, stable |
| Ledges | slabs | slabs | full blocks (costs speed) |

At 200 W on flat stone the three cruise at roughly 34, 32 and 28 km/h; on sand 12, 21 and 21 km/h.

## Getting a bicycle

- **Craft** (leather on top, iron ingots around the middle):
  - Road bike: string in the centre
  - Gravel bike: gravel in the centre
  - Mountain bike: slime ball in the centre
- **Creative:** Tools & Utilities tab, or `/give @s voxelvelo:road_bike` (also `gravel_bike`, `mountain_bike`).
- Right-click a block to place it, right-click the bike to mount, `Shift` to dismount, punch it to pick it up.

## Collisions

- Blocks stop the bike; it keeps the speed component that slides along a wall. A hard head-on hit is audible and
  hurts the rider.
- Riding into a mob or player hurts and knocks them away in proportion to speed (and slows the bike). Slowly, you
  just nudge them aside.
- Other bikes, boats and minecarts are solid for a bike. Parked bikes are not solid for walking players.
- The bike stays glued to slopes and stairs instead of launching off them.

## Keyboard riding

Keyboard riding is a permanent, fully supported mode. Minecraft's movement keys work, plus the arrow keys.
All keys are rebindable under *Controls > VoxelVelo Bicycle*.

| Action | Default |
|--------|---------|
| Pedal | `W` / `Up` |
| Steer | `A` `D` / `Left` `Right` |
| Brake | `S` / `Down` |
| Hard brake | `Left Alt` |
| Shift gear up / down | `X` / `Z` |
| Dismount | `Shift` (vanilla sneak) |
| Bike settings | `B` |

Holding the pedal key produces 200 W of virtual power.

## Mixed input modes

Each control (propulsion, steering, brake, gear) picks its own source (`propulsionSource`, `steeringSource`,
`brakeSource`, `gearSource` in the config file). So far the keyboard and the FTMS trainer (propulsion only) exist;
the settings screen exposes propulsion. The combinations the design supports:

```text
No hardware:   keyboard propulsion  + keyboard steering
Trainer only:  FTMS propulsion      + keyboard steering
Full setup:    FTMS propulsion      + OpenBikeControl steering
Hybrid:        keyboard propulsion  + OpenBikeControl steering
```

The composition layer is `BikeInputManager`. A disconnected propulsion source gives zero power (coasting); for
steering, brake and gear a disconnected source falls back to the keyboard so the bike stays controllable.

## FTMS trainer support

Any smart trainer or indoor bike that exposes the standard Bluetooth **Fitness Machine Service** works. The
trainer power becomes the rider power in the same physics the keyboard uses; the speed a trainer reports is
only shown as telemetry and never moves the bike.

1. Press `B` (rebindable, *Open Bike Settings*) to open the settings.
2. *Choose Trainer...* > *Scan*, then click your trainer (devices advertising a fitness machine are marked
   with a star and sorted first).
3. Set *Propulsion* to *FTMS trainer*. Steering and braking stay on the keyboard.

What is read: instantaneous power, cadence, speed, resistance level and heart rate (when the trainer relays
one). With *Auto-connect Trainer* on, the mod reconnects to the chosen trainer when you are in a world and
retries with growing delays (4 s up to 30 s) after a drop, unless you pressed *Disconnect*.

If the trainer disconnects or stops sending for 3 s, its power becomes zero and the bike coasts; you can keep
riding by switching propulsion back to the keyboard. Mounting a bike while FTMS is selected but not connected
shows a hint.

Bluetooth uses [SimpleBLE](https://github.com/simpleble/simpleble) (WinRT on Windows, BlueZ on Linux,
CoreBluetooth on macOS), bundled in the mod jar and loaded only on the client, behind a `BluetoothBackend`
interface so it can be replaced. **SimpleBLE is licensed under BUSL-1.1: free for non-commercial use, a
commercial licence is needed otherwise** (see its `LICENSE.md`). Bluetooth being off or missing never affects
normal riding; the settings screen just says so.

Not verified on real hardware yet: the GATT code was written against the FTMS specification and the parser was
checked with hand-built payloads, but no trainer has been connected.

## OpenBikeControl and BikeControl phone steering (planned, Milestone 4)

Direct OpenBikeControl support over mDNS and BLE for steer left/right, brake and shifting; unknown actions are
ignored. A phone mounted on the handlebars running BikeControl will steer through standard OpenBikeControl
actions with no BikeControl-specific code.

## Trainer resistance feedback (planned, Milestone 6)

The server already measures gradient from real movement and the surface is a central abstraction
(`BikeSurface`), so uphill/sand can later raise FTMS resistance (optional, configurable, safely clamped).

## Configuration

Press `B` for the settings screen: trainer choice, auto-connect, propulsion source, keyboard virtual power
(50-600 W) and keyboard steering sensitivity. Settings live in `config/voxelvelo-client.json`. Rider and bike
mass, maximum speed, HUD and trainer-resistance options are planned.

## Building

Requires JDK 25.

```sh
./gradlew build        # jar in build/libs
./gradlew runClient    # dev client
./gradlew runServer    # dev server (accept the Minecraft EULA in run/eula.txt first)
```

## Models

Bikes are rendered with [GeckoLib](https://github.com/bernie-g/geckolib): models live in
`assets/voxelvelo/geckolib/models/entity/{road,gravel,mountain}_bike.geo.json` and share one animation file,
`geckolib/animations/entity/bicycle.animation.json` (crank and pedals). Wheel rotation and steering come from the
synced physics state in `BikeGeoRenderer` instead of keyframes, because they depend continuously on speed. Bones
the code relies on: `rear_wheel`, `front_wheel`, `front_assembly`, `crank`, `pedal_left`, `pedal_right`.

## Layout

```text
src/main    common + server: BikeEntity, BikePhysics, BikeSurface, BikeControlState, packets, registries
src/client  client only: keyboard/input composition, packet sender, GeckoLib renderer
./tools      one-off seed generator for the GeckoLib bike models (they are meant to be edited in Blockbench)
```

## Roadmap

1. Multiplayer bicycle (done)
2. Bicycle behavior polish and balancing
3. FTMS (done)
4. OpenBikeControl
5. Mixed input configuration
6. Trainer feedback
7. HUD, config UI, better model and rider pose

## License

MIT, see `LICENSE`.
