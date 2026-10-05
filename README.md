# VoxelVelo

Rideable, fully multiplayer bicycles for Minecraft (Fabric). Ride with the keyboard, or bring a smart
trainer (Bluetooth FTMS) and OpenBikeControl steering such as the BikeControl phone app.

> **Status:** Milestones 1 to 7 are implemented: multiplayer bicycles, keyboard riding, three bike types with their
> own physics and models, terrain and collisions, FTMS trainer power with resistance feedback, OpenBikeControl steering,
> free source mixing, HUD, settings pages and rider pose. Hardware paths (trainer, OpenBikeControl devices) are
> checked against mock devices, not real ones (see the sections below).

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

Each control (propulsion, steering, brake, gear) picks its own source: press `B` > *Input Sources...* (stored as
`propulsionSource`, `steeringSource`, `brakeSource` and `gearSource` in the config file). The keyboard, the FTMS
trainer (propulsion only) and OpenBikeControl (steering, brake, gears) can be combined freely:

```text
No hardware:                  keyboard propulsion + keyboard steering
Trainer only:                 FTMS propulsion
Trainer + keyboard steering:  FTMS propulsion + keyboard steering
Trainer + keyboard brake:     FTMS propulsion + keyboard brake
Full setup:                   FTMS propulsion + OpenBikeControl steering (+ brake, gears)
Hybrid:                       keyboard propulsion + OpenBikeControl steering
```

The keyboard keeps working next to the other sources instead of being switched off:

- **Brake:** the keyboard brake always works; with OpenBikeControl braking, the stronger of the two wins.
- **Steering:** the selected source steers; while it is idle (steering near zero) the keyboard can steer.
- **Gears:** shifts from the keyboard and from the selected source both count.
- **Propulsion:** only the selected source provides power, so a connected trainer is never mixed with the pedal
  key. A disconnected trainer means zero power (coasting), not a silent switch to the keyboard.

A disconnected source for steering, brake or gears falls back to the keyboard so the bike stays controllable.

## Road follow (hands-free steering)

For trainer riding without OpenBikeControl or keyboard: set *Steering* to **Road follow** in
`B` > *Input Sources...* and the bike steers itself along the centre of a road.

- **What is a road:** a column whose top block is one of your *road blocks* with two passable blocks above it
  (air, grass, flowers, ...). Default blocks are stone and gravel; edit the list in `B` > *Road & Terrain...*.
  Entries are block ids (`minecraft:gravel`, `stone`) or `#tags`; *Add Block Under Me* and *Add Block I Look At*
  take them from the world, and *Reset* restores stone + gravel.
- **Centre:** the road is measured across the bike's heading, from the left edge to the right edge of the
  continuous run of road columns under the bike. The bike steers toward a point a few blocks ahead on that
  centre line, so curves are cut smoothly instead of zig-zagging.
- **Width limit:** roads wider than *Max Half-Width* (2-16 blocks each side) are treated as open ground.
- **Gives up quietly:** with no road under the bike, or on open ground, the source reports no steering, so the
  keyboard (or whatever else is idle-capable) takes over and you never lose control.

### Junctions

With *Steering* on **Road follow** the bike also copes with crossings, forks and turn-offs:

- **Detection:** the road ahead is checked for a place where it suddenly widens, meets a cross street or forks.
  Around its middle every road that really connects to it counts as a way on (parallel roads that do not connect
  are ignored). A lone way on that is not straight ahead is just a corner: the bike takes it without asking.
- **The question:** with two or more ways on, a big **LEFT / RIGHT** prompt appears about four seconds before the
  turn (further out when you are fast). Answer by steering on the keyboard (`A` / `D` or the steer keys) or with
  OpenBikeControl; the picked side turns green and you can change your mind until the turn starts. A bar shows how
  long is left and the line under it says what happens if you do nothing.
- **No answer, or still in cooldown:** the bike takes the widest way on, and picks at random among ways that are
  about equally wide. Straight on counts as a way on. After a question there is a cooldown (30 s by default), so
  a street of closely spaced crossings does not nag; junctions inside it are decided automatically.
- **Settings:** *Junction Prompt* on/off and *Prompt Cooldown* (5-120 s) are on `B` > *Road & Terrain...*. With the
  prompt off, every junction is decided by the widest-way rule.
- **Limits:** the turn follows the bike's own turning circle, so a fast bike (about 30 km/h and up) cuts the
  corner of a narrow road and may run onto the verge for a moment; slow down for tight junctions. The geometry was
  checked in closed-loop simulation (crossroads, T, corner, fork, bends) and in game on a built crossroads, but
  not on real trainers, curved city layouts or multiplayer.

### Slope window

Trainers react slowly, so the resistance does not follow the block directly under the wheel. The grade is a
least-squares slope along the bike's heading over a window of **10 to 40 blocks** in front of and behind it
(*Slope Window* in `B` > *Trainer...*, default 10). Single steps and stairs blend into a smooth grade.

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

## OpenBikeControl and BikeControl phone steering

[OpenBikeControl](https://github.com/OpenBikeControl/openbikecontrol-protocol) (OBC) is the open protocol that
controllers such as the [BikeControl](https://bikecontrol.app) phone app use to talk to trainer apps. VoxelVelo
implements it directly, with no BikeControl-specific code. It is optional; the bike is fully playable without it.

Both official transports are supported:

- **Network:** the device advertises `_openbikecontrol._tcp.local.` over mDNS and VoxelVelo connects to it over
  TCP. You can also type `host:port` by hand, which skips mDNS (useful for a controller running on the same
  machine, or when multicast is blocked).
- **Bluetooth LE:** devices advertising the OBC service are listed next to the network ones.

Actions used (everything else is ignored):

| OBC action | Effect |
|---|---|
| Steer Left / Steer Right (`0x18`/`0x19`) | steering; pressed is full lock, analog values scale it; ramped smoothly |
| Brake (`0x1A`) | brake; 1 is full, otherwise (value - 1) percent, capped at 100 % |
| Shift Up / Shift Down (`0x01`/`0x02`) | one gear per press |
| Gear Set (`0x03`) | absolute gear (value - 1), clamped to 1-12 |

Setup: press `B` > *OpenBikeControl...*, pick the transports, click your device (or enter `host:port`), then in
*Input Sources...* set *Steering* (and optionally *Brake* and *Gears*) to *OpenBikeControl*. The selected device
is remembered and reconnected automatically unless you press *Disconnect*. If the device disconnects, every
button is released at once and the controls it drove fall back to the keyboard; the server smooths the steering
back to centre.

For the BikeControl phone setup, mount the phone on the handlebars, let BikeControl turn its orientation into
steering, and select it here: Minecraft only ever sees standard OBC actions, never the phone sensors.

Verified against the spec with a throwaway mock device (mDNS discovery, TCP, every decoded action, in game up to
the server-side steering value). Not yet verified with the real BikeControl app or a hardware controller, so the
exact transport BikeControl offers on your phone is unconfirmed; that is what the manual `host:port` and the
two transports are for. The TCP stream has no length prefix in the spec; like the reference app, each received
chunk is treated as one message.

## Trainer resistance feedback

With an FTMS trainer as the propulsion source, the terrain you ride over changes how the trainer feels:

```text
server: gradient measured from real movement + surface under the bike   (synced entity data)
   -> rider client: TrainerFeedback maps it with the bike type physics
   -> FTMS Control Point: Request Control, Start, Set Indoor Bike Simulation Parameters
   -> trainer resistance
```

- **Uphill** is harder and **downhill** is easier. The grade sent is the game slope scaled by the bike's slope
  factor (the same scaling the physics uses, since Minecraft hills are far steeper than roads), then by the
  *Intensity* setting.
- **Surface** sets the rolling resistance: path and stone are light, grass and dirt heavier, sand heaviest. It
  includes the tyre of the bike type (a road bike on sand is much heavier than a mountain bike).
- **Safe limits:** grade is clamped to -10 % .. +15 %, rolling resistance to the FTMS maximum, intensity to
  0-200 %, updates are smoothed and sent at most about once per 0.75 s, and only one command is in flight so a
  slow trainer is never flooded.
- **Optional:** *Resistance* on/off and *Intensity* are in the settings (`B`). The feature only runs while you
  ride, the trainer is the propulsion source and is connected. Trainers that do not advertise the simulation
  feature (Target Setting Features bit 13), or fail the commands, are left alone and riding is unaffected.
- When you dismount, switch propulsion away or leave the game, the trainer is returned to a flat road
  (best effort on exit).

Checked against a scripted fake trainer (command order, byte encoding, clamping, lost-control retry); not yet
tried on real trainer hardware, so expect to tune the intensity and compare against how your trainer reacts.

## HUD, rider settings and pose

While riding, a small HUD in the bottom-right shows power, cadence, speed, grade and gear (and heart rate when
the trainer relays it), plus one line per control with a **green or red dot**: which source drives it, and
whether it is connected. Cadence comes from the trainer when available, otherwise it is estimated from the bike.

*Rider & HUD...* in the settings (`B`):

- **Rider mass** (40-150 kg) and an optional personal **speed limit** (5-100 km/h, or off). They are sent to the
  server in a small versioned packet (`rider_settings`, protocol v1), clamped there, kept per player and dropped
  on disconnect, and then used in that rider's physics. Other players are unaffected.
- **HUD** on/off and whether the input source lines are shown.

Riders are posed on the bike for everyone who sees them: hands forward on the bars and legs following the pedals.
The pedal phase is read from the same GeckoLib controller that turns the crank, so legs and pedals stay in step.
This needs two small client-side mixins (`AvatarRendererMixin`, `HumanoidModelMixin`) because Fabric has no hook
for posing a player model; they only touch players that are riding a bike.

## Other riders' watts and pulse

A small overlay in the top-left lists the other riders on the server with their **watts** and **heart rate**,
strongest first. It is empty (and invisible) when nobody shares. Toggle it with `H` (rebindable, *Toggle Rider
Stats*) or in the settings (`B` > *Rider & HUD...*).

How it works: your client tells the server your heart rate (from the trainer, if it relays one) and what you share
(`rider_vitals` packet, protocol v1, sent on change and every 5 s). Twice a second the server sends every player a
compact list (`rider_stats` packet) of the riders in the same dimension: UUID, the watts the server is actually
applying to that rider's bike, and their heart rate. Values a rider does not share are simply left out, and a
pulse the trainer stops sending disappears after about 10 s. The server clamps every value (heart rate 30-250 bpm).

**Privacy:** heart rate is health data, so sharing is explicit and per kind: *Share My Watts* and *Share My Heart
Rate* in the settings (both on by default, so a group sees each other out of the box; switch them off to stay
private). Nothing is sent about players who are not riding or have nothing to share.

## Configuration

Press `B` for the settings hub, which links to one page per topic:

- *Trainer...*: trainer choice, auto-connect, resistance on/off, intensity and the slope window.
- *OpenBikeControl...*: device and transports.
- *Input Sources...*: the source of each control (propulsion, steering, brake, gears), keyboard virtual power
  (50-600 W) and keyboard steering sensitivity.
- *Road & Terrain...*: the road block list, the widest road (max half-width) and the junction prompt with its cooldown.
- *Rider & HUD...*: rider mass, speed limit, HUD options, the rider stats overlay and what you share.

Settings live in `config/voxelvelo-client.json`.

## Building

Requires JDK 25.

```sh
./gradlew build        # jar in build/libs
./gradlew runClient    # dev client
./gradlew runServer    # dev server (accept the Minecraft EULA in run/eula.txt first)
```

## Models

Bikes are rendered with [GeckoLib](https://github.com/bernie-g/geckolib). The models are made in Blockbench and
exported as GeckoLib-ready, cuboid-only Bedrock geometry (GeckoLib 5.5.7 cannot draw meshes, so round tires are
48 rotated segments):

- `assets/voxelvelo/geckolib/models/entity/{road,gravel,mountain}_bike.geo.json` and the matching
  `textures/entity/bicycle_*.png` (64x64, one atlas each).
- `geckolib/animations/entity/bicycle.animation.json`: a one-second pedal loop shared by all bikes. The controller
  speed is the cadence, so it plays faster when you pedal harder and pauses when you coast.
- `models/` holds the editable `*-cuboids.bbmodel` sources, the notes from the model author and
  `*.metadata.json` per bike (saddle top, grips, steering pivot and axis, bounds). To change a model, re-export
  from Blockbench, copy the files over, and update `BikeRig` if the metadata changed.

Bones the code relies on: `frame` (root), `rear_wheel`, `front_assembly` (fork, stem, bars) with child
`front_wheel`, and `crank` with children `pedal_left`/`pedal_right`. Wheel spin and steering come from the synced
physics state in `BikeGeoRenderer`, not from keyframes, because they depend continuously on speed. The fork
steers about the model's tilted steering axis, not world Y.

`BikeRig` (one per bike type) carries the measurements from the metadata. The seat position, the rider's arm
angles to the grips and the leg swing to the pedals are all derived from it, so a new bike model only needs new
numbers there. The models are drawn at `BikeRig.MODEL_SCALE` (0.7) of their authored size: at full size the
saddle is 1.3 blocks up and the bike 2.3 blocks long, which a player model (0.75-block legs) could not sit on or
pedal. The pedal cranks in the models are short (about 1.6 units), so the rider's legs swing more than the pedals.

## Layout

```text
src/main    common + server: BikeEntity, BikePhysics, BikeSurface, BikeControlState, packets, registries
src/client  client only: keyboard/input composition, packet sender, GeckoLib renderer
```

## Roadmap

1. Multiplayer bicycle (done)
2. Bicycle behavior polish and balancing
3. FTMS (done)
4. OpenBikeControl (done)
5. Mixed input configuration (done)
6. Trainer feedback (done)
7. HUD, config UI, rider pose and final bike models (done)

## License

MIT, see `LICENSE`.
