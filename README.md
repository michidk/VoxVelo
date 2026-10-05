# VoxelVelo

Rideable, fully multiplayer bicycles for Minecraft (Fabric). Ride with the keyboard, or bring a smart
trainer (Bluetooth FTMS) and OpenBikeControl steering such as the BikeControl phone app.

> **Status:** Milestone 1 (multiplayer bicycle with keyboard riding) is implemented. FTMS, OpenBikeControl,
> mixed-input configuration, trainer feedback and the HUD/config UI are planned (see [Roadmap](#roadmap)).

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
| Java | 25 |

## Installation

- **Players:** put the mod jar and Fabric API in `mods/`.
- **Servers:** put the same two jars in the server's `mods/`. The mod is required on the server because the
  bicycle is a real synchronized entity. Vanilla servers are not supported.
- Dedicated servers never load any client code (input, rendering, and later Bluetooth/networking hardware).

## Multiplayer

Works on the integrated server, LAN and dedicated Fabric servers. The server owns position, velocity,
collisions, terrain and the mounted rider. The client sends a small versioned packet (`bike_control`,
protocol v1) at most once per tick on change and as a heartbeat every 5 ticks; every field is clamped on the
server, and if packets stop for 15 ticks (0.75 s) power and steering are dropped so a disconnected player
cannot keep accelerating. Only the seated player can control a bike; one rider per bike.

## Getting a bicycle

- **Craft:** leather in the top middle, iron ingots in the middle row and the two bottom corners
  (`" L " / "III" / "I I"`).
- **Creative:** Tools & Utilities tab, or `/give @s voxelvelo:bicycle`.
- Right-click a block to place it, right-click the bike to mount, `Shift` to dismount, punch it to pick it up.

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

Holding the pedal key produces 200 W of virtual power.

## Mixed input modes (planned)

Each control (propulsion, steering, brake, gear) will pick its own source, so these will all work:

```text
No hardware:   keyboard propulsion  + keyboard steering
Trainer only:  FTMS propulsion      + keyboard steering
Full setup:    FTMS propulsion      + OpenBikeControl steering
Hybrid:        keyboard propulsion  + OpenBikeControl steering
```

The composition layer (`BikeInputManager`, `BikeInputSource`, `ControlChannel`) already exists; today only
the keyboard source is registered.

## FTMS trainer support (planned, Milestone 3)

Client-side Bluetooth LE behind a `BluetoothBackend` abstraction. Trainer power drives the bike; trainer speed
is never used as Minecraft speed.

## OpenBikeControl and BikeControl phone steering (planned, Milestone 4)

Direct OpenBikeControl support over mDNS and BLE for steer left/right, brake and shifting; unknown actions are
ignored. A phone mounted on the handlebars running BikeControl will steer through standard OpenBikeControl
actions with no BikeControl-specific code.

## Trainer resistance feedback (planned, Milestone 6)

The server already measures gradient from real movement and the surface is a central abstraction
(`BikeSurface`), so uphill/sand can later raise FTMS resistance (optional, configurable, safely clamped).

## Configuration (planned)

Preferred devices, per-control sources, masses, max speed, HUD, resistance intensity, keyboard virtual power
and steering sensitivity. Today these are constants in `BikePhysics.Params` and `KeyboardBikeInput`.

## Building

Requires JDK 25.

```sh
./gradlew build        # jar in build/libs
./gradlew runClient    # dev client
./gradlew runServer    # dev server (accept the Minecraft EULA in run/eula.txt first)
```

## Layout

```text
src/main    common + server: BikeEntity, BikePhysics, BikeSurface, BikeControlState, packets, registries
src/client  client only: keyboard/input composition, packet sender, renderer/model
```

## Roadmap

1. Multiplayer bicycle (done)
2. Bicycle behavior polish and balancing
3. FTMS
4. OpenBikeControl
5. Mixed input configuration
6. Trainer feedback
7. HUD, config UI, better model and rider pose

## License

MIT, see `LICENSE`.
