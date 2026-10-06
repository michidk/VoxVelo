# VoxelVelo

Rideable, fully multiplayer bicycles for Minecraft (Fabric). Ride with the keyboard and mouse, or use the fitness
build and bring a smart trainer (Bluetooth FTMS) and OpenBikeControl steering such as the BikeControl phone app.
The mod comes in two builds, see [Two builds](#two-builds).

> **Status:** Milestones 1 to 7 are implemented: multiplayer bicycles, keyboard riding, three bike types with their
> own physics and models, terrain and collisions, FTMS trainer power with resistance feedback, OpenBikeControl steering,
> free source mixing, HUD, settings pages and rider pose. Hardware paths (trainer, OpenBikeControl devices) are
> checked against mock devices, not real ones (see the sections below).

## What it does

- A real `BikeEntity` that rides like a horse or a boat: the rider's own client simulates it, so steering is instant.
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

## Two builds

One source tree builds two jars with the same mod id, so worlds, servers and clients of either build work together:

| | `voxelvelo-<version>.jar` | `voxelvelo-fitness-<version>.jar` |
|---|---|---|
| Bikes, parts, physics, collisions, slopes, suspension | yes | yes |
| Keyboard and mouse riding, rider mass, riding HUD | yes | yes |
| FTMS trainer, secondary power meter, Bluetooth, resistance feedback | no | yes |
| OpenBikeControl (steering, brake, gears) | no | yes |
| Road following and junction prompts | no | yes |
| Rider stats overlay, heart rate, sharing | no | yes |
| Ride recording, FIT export, ride maps | no | yes |
| Bundled libraries (SimpleBLE, JmDNS) | none | yes |

The **core** jar is the raw bike mod: it contains no trainer, Bluetooth, OpenBikeControl, road-following or stats
code, none of their libraries and none of their text. The **fitness** jar is the full mod. Their packets are
optional, so a fitness client on a core server (or the other way round) simply has no stats overlay.

## Installation

- **Players:** put one of the two mod jars (core or fitness), Fabric API and GeckoLib in `mods/`. Do not install both.
- **Servers:** put the same three jars in the server's `mods/`. The mod is required on the server because the
  bicycle is a real synchronized entity. Vanilla servers are not supported.
- Dedicated servers never load any client code (input, rendering, Bluetooth and networking hardware).

## Multiplayer

Works on the integrated server, LAN and dedicated Fabric servers. Bikes ride like vanilla horses and boats: the
rider is the controlling passenger, their client simulates the physics (speed, steering, slope, collisions) and
vanilla vehicle movement carries the position to the server and the other players, with vanilla's movement checks.
Every tick the rider's client also sends a small versioned packet (`bike_state`, protocol v1) with speed, steering,
brake, gear, pedaling, power, surface, gradient and any wall crash. The server clamps every field (speed to the
bike's top speed), shows it to everyone else, and stays in charge of the consequences: crash and fall damage,
hurting what the bike runs into, tire wear and parts. If the packets stop for 20 ticks (1 s) the rider's power
counts as zero. A bike nobody rides is simulated by the server. Only the seated player can ride a bike; one rider
per bike.

Since the rider's client is trusted with the movement, a modified client could ride faster than the physics
allows (within vanilla's limits) or hide its crashes. That is the same trade-off vanilla makes for horses and boats.

## Generate a race track

Operators can build a closed checkpoint circuit starting at their current X/Z position:

```text
/voxelvelo track
/voxelvelo track 10 minecraft:smooth_stone 12 0.85 6 8 0
```

Arguments are positional and optional from the right:

| Argument | Default | Range / meaning |
|---|---|---|
| `length_km` | 10 | 0.25–20; horizontal centerline kilometres |
| `material` | `minecraft:smooth_stone` | Stable full solid block; no fluids, falling blocks or block entities |
| `checkpoints` | 12 | 8–24 spline checkpoints |
| `bends` | 0.85 | 0–1; higher values add deeper inward/outward bends |
| `width` | 6 | Road width in blocks, 3–11 |
| `grade_percent` | 8 | 1–15; maximum grade of the smooth height profile |
| `variant` | 0 | Non-negative variation index mixed with the Minecraft seed |

Randomness comes from the world seed, start X/Z and variant. Identical inputs reproduce the same horizontal
layout; final heights also depend on the terrain at build time. One block represents one metre. The kilometre
target measures the horizontal spline, so the actual ride is slightly longer when it climbs and descends.

The command samples ground beneath trees and plants. Water and lava count as surfaces too: a level crossing
places road blocks in the top liquid layer, with solid foundations extending to the seabed.

The road follows the terrain: it takes the ground height, with only as much cutting and filling as the maximum grade
requires, and where the track was ordered it is exactly the ground, so you stand on it. Over water and lava it never
sinks below the surface, so approaches may rise slightly above liquid level to preserve grade.
Grade limiting covers the full road width, overlaps and the loop seam. Slopes use half-block steps: adjacent
riding columns differ by at most half a block, including diagonals. Whole-height surfaces use the selected
material; half-height surfaces use its matching slab (for example, smooth stone, stone bricks or oak planks).
Materials without a matching slab, such as obsidian or concrete, use smooth-stone slabs for half steps.
The grade percentage describes the height profile before rounding; individual steps are not literal 8% ramps.
Five blocks of collision-free headroom are cleared above every road block, including vegetation. Invisible
structure-void blocks keep water and lava from flowing back into this space, including tunnel entrances,
while allowing players and bikes to pass through and leave the track. Neighboring surfaces are sampled
too, and water-height constraints extend onto dry approaches. In ceiling dimensions such as the Nether,
the survey starts at the command's height instead of the roof.

Every new track includes low stone-brick borders outside the requested riding width, with glowstone
spaced around the borders. Outdoor edges have no fences or raised walls, so riders can leave the track. Deep cuts through mountains receive stone-brick tunnel walls and ceilings with
recessed glowstone lighting and at least five blocks of headroom. Dry gaps of three blocks or more become
thin bridge decks with open edges and occasional edge piers, instead of solid embankments filling the valley.
Empty/void spans have decks without pillars extending to the world floor. Water and lava crossings retain
solid foundations: the generator does not build submerged bridges or underwater tunnels. These decorations
are automatic with the existing command; previously built tracks are not changed until regenerated.

Creation runs automatically to completion in batches, shows its progress in a purple boss bar to whoever ordered it,
and reports when finished. Build time depends on terrain, existing chunks and server hardware. Survey and placement
visit columns in chunk batches, with up to 32 chunks requested ahead through Minecraft's loading system.
World edits stay on the server thread; grading runs in the background. If you are still within a few blocks of the start
when it completes you are moved onto the road there. Additional requests queue;
there are no status or cancel commands. Liquids, deep foundations and steep terrain do not reject a route,
and the former 32-block earthwork limit is removed. Empty/void columns use the command's height as a fallback.
This is a terrain editor: blocks in the road corridor may be replaced. New chunk generation can still cause
pauses. Invalid materials/arguments and routes outside the world border are still rejected, and Minecraft's
build-height limit still applies. Stopping the server or a world-write failure keeps blocks already placed;
jobs do not resume after restart and there is no undo.

`verifyTracks` checks seed reproducibility, perimeter scaling, road connectivity, chunk batching, terrain grading, half-block
continuity, structure selection, lighting coverage and loop closure. The command ships in both core and fitness builds. See [track performance measurements](docs/track-generation-performance.md)
for benchmark results and reproduction steps.

## Bike types

Three bikes share one simulation and differ only in their parameters (`BikeType`):

| | Road | Gravel | Mountain |
|---|---|---|---|
| Mass | light | medium | heavy |
| Hard ground | fastest | good | slowest |
| Loose ground (sand, dirt) | very slow, little grip | good | best, most grip |
| Braking | weakest off-road | good | strongest |
| Turning | quick at speed | balanced | tight lock, stable |
| Ledges | full blocks, stiff (costs speed) | full blocks (costs less) | full blocks (costs least) |
| Suspension | stiff | some | long travel |

At 200 W on flat stone the three cruise at roughly 34, 32 and 28 km/h; on sand 12, 21 and 21 km/h.

## Getting a bicycle

- **Craft** from parts: a frame (race, gravel or mountain), two bike wheels, handlebars, a saddle and a pedal set.
  A wheel is a bare wheel plus a road, gravel or mountain tire. Every recipe is in the vanilla recipe book and is
  unlocked by picking up one of its ingredients (iron, leather, slime, a frame, ...).
  - **Recolor** a bike, a frame or the helmet with dyes, like leather armor: dyes mix with each other and with the
    current color.
  - **Re-tire** a wheel with any tire: a worn tire is replaced by a fresh one, or the wheel switches tread.
- **Creative:** Tools & Utilities tab, or `/give @s voxelvelo:road_bike` (also `gravel_bike`, `mountain_bike`).
- Right-click a block to place it, right-click the bike to mount, `Shift` to dismount, and sneak-use it with an empty hand to pick it up.

### Speed Boost enchantment

An assembled bike can receive **Speed Boost I–III** from an enchanting table or an enchanted book in an anvil.
Each level multiplies its actual movement speed by another 10%, after the normal riding physics; a rider's personal
speed limit still caps the final result. The enchantment remains on the bike when it is placed, saved and picked up.
Dismantling or destroying a bike drops its physical parts rather than the assembled bike, so the enchantment is
lost with the assembly.

## Bike damage and the wrench

Hard crashes and falls over three blocks damage the bicycle. Race bikes have 60 durability, gravel bikes 80 and
mountain bikes 100. At zero condition the bike is destroyed and drops its colored frame and every installed part.
Left-click an unoccupied bicycle with a Bike Wrench to repair it fully; right-click with the wrench to dismantle it
and drop those parts deliberately. Placed bikes have no inventory or maintenance screen, so changing a component
means dismantling and reassembling the bike. Whole-bike damage is controlled by `enableVehicleDamage` in
`config/voxelvelo-server.json`; creative riders are exempt unless `vehicleDamageInCreativeMode` is enabled.

## Collisions

- Blocks stop the bike; it keeps the speed component that slides along a wall. A hard head-on hit is audible and
  hurts the rider.
- Riding into a mob or player shoves or hurts them depending on the **closing speed** (the bike's speed minus the
  target's own speed along its heading, so running into an oncoming player counts double):
  - **Under 3 m/s (about 11 km/h): a shove.** No damage. It is a perfectly inelastic push: both end at the common
    speed, so a chicken is carried along while a cow or an iron golem barely moves and drags the bike down.
  - **From 3 m/s: a hit.** It deals `(closing speed - 2.5) * 0.7` half hearts (about 3 at 7 m/s), knocks the target
    away from the line of the bike (more for lighter targets) and plays a sound with crit particles. A target at
    the edge of the bike is swept to the side.
  - **The bike pays for it.** It loses speed and durability in proportion to the target's mass, estimated from its size: a cow
    takes most of 7 m/s, an iron golem stops the bike dead. A sudden loss of more than 9 m/s hurts the rider like
    hitting a wall.
  - Targets that cannot be hurt (PvP off, invulnerable, creative) are shoved instead. The hit area covers the
    whole distance travelled in a tick, so a fast bike cannot skip over someone. Riders of other bikes are left
    to the bike-on-bike collision below.
- Other bikes, boats and minecarts are solid for a bike. Parked bikes are not solid for walking players.
- The bike stays glued to slopes and stairs instead of launching off them.

## Slopes, falls and suspension

- **Climbing:** every bike rolls up a one-block step, so ordinary hills are rideable. Slabs and stairs are free;
  a full-block ledge costs speed, and the stiffer the bike the more (road 21 %, gravel 16 %, mountain 8 %).
- **Gravity follows the terrain.** The slope under the wheels is read from the blocks along your heading (4 blocks
  each way, smoothed), not guessed from how far you moved. A 25 % climb slows a bike at 200 W to about 11 km/h,
  and a coasting bike rolls downhill, speeding up (about 28 km/h after a 25 % descent of a few blocks, from
  18 km/h on the flat). Minecraft hills are steep, so the pull of gravity is scaled down, as for the trainer.
- **Fall damage:** the bike takes the blow for you. The rider still takes a share of the fall damage, less the
  more suspension the bike has, plus a few blocks of fall that the suspension swallows. An 8-block drop costs
  5 health points (2.5 hearts) on foot, about 3 on the road bike, 2 on the gravel bike and 1 on the mountain
  bike. The landing damages the bike too when vehicle damage is enabled.
- **Suspension** is a property of each bike type (`suspension`, 0 rigid to 1 long travel: road 0.15, gravel 0.45,
  mountain 0.90). More suspension means less fall damage, less speed lost on ledges, and a springier ride: the
  bike and rider sag and rebound when a slope starts or ends, on steps and on landings. A road bike settles at
  once, a mountain bike rings for a few bounces. Climbing also makes the body settle a little lower. The effect is
  drawn on the client only (a small bob of the whole bike); a true front and rear travel animation would need the
  model split into body and wheels.
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
3. Set *Propulsion* to *Trainer / power meter*. Steering and braking stay on the keyboard.

What is read: instantaneous power, cadence, speed, resistance level and heart rate (when the trainer relays
one). With *Auto-connect Trainer* on, the mod reconnects to the chosen trainer when you are in a world and
retries with growing delays (4 s up to 30 s) after a drop, unless you pressed *Disconnect*.

If the trainer disconnects or stops sending for 3 s, its power becomes zero and the bike coasts; you can keep
riding by switching propulsion back to the keyboard. Mounting a bike while FTMS is selected but not connected
shows a hint.

### Secondary power meter

A separate crank, pedal or hub power meter can connect alongside the trainer when it exposes the standard
Bluetooth **Cycling Power Service**. Open `B` > *Power Meter...*, scan and select it; devices advertising Cycling
Power are marked with a star. The selection is remembered and can reconnect automatically.

While *Trainer / power meter* is the propulsion source, a fresh external meter reading always overrides the
trainer's power estimate. Trainer cadence, speed, heart rate and resistance control continue to work normally.
If the meter disconnects or sends nothing for 3 seconds, power falls back to fresh trainer watts; if both sources
are unavailable, the bike coasts. Explicitly choosing keyboard propulsion still uses keyboard power.

Bluetooth uses [SimpleBLE](https://github.com/simpleble/simpleble) (WinRT on Windows, BlueZ on Linux,
CoreBluetooth on macOS), bundled in the mod jar and loaded only on the client, behind a `BluetoothBackend`
interface so it can be replaced. **SimpleBLE is licensed under BUSL-1.1: free for non-commercial use, a
commercial licence is needed otherwise** (see its `LICENSE.md`). Bluetooth being off or missing never affects
normal riding; the settings screen just says so.

Not verified on real hardware yet: the GATT code was written against the FTMS and Cycling Power specifications
and the parsers were checked with hand-built payloads, but no trainer or power meter has been connected.

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

Virtual shifting is enabled by default in the fitness build. Keep the real bicycle in one gear, then use
X/Z or OpenBikeControl to select the 12 virtual gears. In *Trainer...*, set *Physical ratio* to chainring
teeth divided by rear cog teeth (for example, 50/20 = 2.50). Gear 6 has a virtual ratio of 2.50; each
step increases that ratio by 13%. Set *Wheel* to the circumference in metres (default 2.10 m); this is
used when the trainer supplies cadence but no speed. Disable *Virtual Shifting* to restore terrain-only feedback.

The independent implementation calculates virtual road speed from trainer speed and the relative gear
ratio, then reflects slope, rolling and aerodynamic load back through that ratio. It translates the
result into an equivalent FTMS simulation grade, retaining the existing resistance limits. Higher gears
increase pedaling load on flats and climbs; downhill load can fall to zero, without requesting motor
assistance. Measured watts and cadence are never altered, and game speed still comes from measured power.
Gear updates can be sent every 150 ms; terrain and speed updates keep the 750 ms interval. Trainer commands
remain serialized and queued changes are coalesced. Virtual shifting requires *Resistance* to be enabled.

Physical principles were researched using [SHIFTR](https://github.com/JuergenLeber/SHIFTR),
[Tacx Virtual Shifting](https://github.com/Berg0162/Tacx-Virtual-Shifting),
[QZ](https://github.com/cagnulein/qdomyos-zwift), and
[KickrShiftr](https://github.com/jvivenot/KickrShiftr). No source code was copied or ported.
This generic FTMS approximation depends on the trainer's internal mass and resistance model; calibration
and real hardware testing are still needed. Missing or stale telemetry uses zero speed for load calculation.

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

## Rider stats and heart rate

Open `B` > *HUD & Rider Stats...* to switch the riding HUD, heart-rate graph, other-rider list, input-source
lines and junction prompt on or off individually. Settings are saved when you close the menu. Input-source
lines appear within the riding HUD; hiding junction prompts keeps road following active.

The top-left overlay lists other current bike riders across the server with colored **watts**, **speed
(km/h)**, **cadence (rpm)**, **heart rate** and **gear**, strongest first. Riders disappear on the next update when they
dismount. Toggle it with `H` (rebindable, *Toggle Rider Stats*) or in `B` > *Rider & HUD...*. The list fits the
available screen height and the server sends up to 64 riders.

While riding, the top-right panel displays your current BPM and a scrolling graph of the last 60 seconds,
sampled once per second. The vertical scale adjusts to your readings. Missing telemetry shows `-- bpm`
and leaves gaps in the graph. History resets when you dismount or change bikes. Its visibility is controlled independently by the *Heart Rate Graph* setting.

Your client sends trainer cadence and heart rate in the version 2 `rider_vitals` packet. Twice a second the
server sends a complete version 3 `rider_stats` snapshot with current riders, applied watts, bike speed,
cadence, shared heart rate and current gear. Without trainer cadence, cadence is estimated from the bike. Both server and
clients need the updated mod for these packets.

*Share My Watts* and *Share My Heart Rate* still control those values; hidden or unavailable values show
`--`. Speed and cadence are shown for every current rider. Trainer cadence is clamped to 0?300 rpm and
heart rate is validated at 30?250 bpm.

## Ride recording, FIT export and ride maps

Press `R` (rebindable, *Start / Stop Ride Recording*) or use `B` > *Ride Recording...* to start a session. While it
runs, the riding HUD shows a red `● REC` line with the time and distance. Press `R` again to stop: the summary opens
with distance, riding, elapsed and moving time, average and maximum speed, power, cadence and heart rate,
normalized power, work (kJ, about the same number of kcal) and ascent and descent. The last ride stays on the
*Ride Recording...* page until you start the next one.

- **Sampling:** one sample per second while you ride a bike: position, distance, speed, the watts going into the
  bike, cadence (the trainer's, otherwise estimated from the bike), heart rate from the trainer and the smoothed grade.
  Distance is the path the bike actually travelled. Time off the bike, a paused game, teleports and dimension
  changes are left out of riding time and distance, and the route gets a gap there. Moving time also leaves out
  standing still. A session stops by itself after 24 hours of samples.
- **Export .FIT** writes a FIT activity file (cycling, sub-sport *virtual activity*) to `.minecraft/voxelvelo/rides/`;
  the chat shows a clickable link and *Open Rides Folder* opens the folder. Strava, Garmin Connect,
  intervals.icu and TrainingPeaks import it. Leaving the world or closing the game ends a running session, and a
  ride you never exported is saved there automatically, so it is not lost.
- **GPS:** Minecraft has no latitude or longitude. With *GPS in FIT* on (the default) the ride is placed on the globe
  so websites draw its route: the start sits at `rideFitOriginLat`/`rideFitOriginLon` in
  `config/voxelvelo-fitness.json` (default 30 N, 40 W, open ocean in the mid Atlantic), one block is one metre, +X is
  east and +Z is south. Altitude is the block height. Turn it off for a distance-only file like an indoor ride.
- **Draw on Map** asks the server to draw the ride on a locked filled map, which lands in your inventory. The
  map's name gives the distance and its tooltip gives the start time, riding time, average power and climb. The
  route is a red line with a green start dot, and a black finish dot when the ride did not end where it started.
  Rides up to about 1.8 km across use a vanilla map scale centred on the route, so the map lines up with the world
  and shows your position. Longer rides are fitted to the map and do not track players. In survival this costs one
  empty map from your inventory; creative players get it free. There is a 10-second cooldown.
- **Map terrain** is drawn only from chunks that are loaded and lie within two chunks of where the server saw you
  ride a bike during this connection. Everything else stays blank map paper, and Nether-style dimensions show only
  the route. The route itself comes from your client, but the terrain comes from the server, so a map request
  cannot reveal parts of the world you have not ridden through, and it never loads or generates chunks. After
  reconnecting, a map of an earlier ride shows only the line.

The ride map packet (`ride_map`, protocol v1, serverbound) carries at most 1024 route points; the client thins
longer rides and keeps the ends of every stretch. `verifyRides` checks the summary numbers, decodes the FIT file
again (CRCs, messages, scales, timer events), and checks the GPS mapping, route thinning and map layout. The sample
FIT file it writes with `-PsampleFit=<path>` decodes without errors in Garmin's FIT SDK. Not yet tried: an
upload to Strava or Garmin Connect, and drawing a map in a running game.

## Configuration

Press `B` for the settings hub, which links to one page per topic. Both builds have:

- *Keyboard...*: keyboard virtual power (50-600 W) and keyboard steering sensitivity.
- *Rider & HUD...*: rider mass, speed limit and the riding HUD.

The fitness build adds:

- *Trainer...*: trainer choice, auto-connect, resistance on/off, intensity and the slope window.
- *OpenBikeControl...*: device and transports.
- *Input Sources...*: the source of each control (propulsion, steering, brake, gears).
- *Road & Terrain...*: the road block list, the widest road (max half-width) and the junction prompt with its cooldown.
- *Rider Stats...*: the rider stats overlay, what you share, and the input-source lines of the HUD.
- *Ride Recording...*: start and stop a ride, its stats, FIT export, ride maps and GPS in FIT.

Settings live in `config/voxelvelo-client.json` (both builds). The fitness build keeps its own in
`config/voxelvelo-fitness.json`, filled from the old shared file the first time it starts.

## Building

Requires JDK 25.

```sh
./gradlew build                # both jars: core/build/libs/voxelvelo-<version>.jar, build/libs/voxelvelo-fitness-<version>.jar
./gradlew :core:build          # only the core jar
./gradlew :runClient           # dev client, fitness build (a bare runClient also skips the core client)
./gradlew :core:runClient      # dev client, core build
./gradlew runServer            # dev server (accept the Minecraft EULA in run/eula.txt first)
```

Dev clients start with the sound muted.

The fitness build is the root project. The core build (`core/`) compiles the same source directories with the
fitness packages left out and without the fitness libraries on its classpath, so core code that reaches for fitness
code does not compile. Fitness code lives in these packages and files, listed in `core/build.gradle`:
`client/{bluetooth,ftms,obc,stats,road,ride,fitness}`, the trainer, OpenBikeControl and road-follow input sources,
the stats and junction HUD, the trainer, OpenBikeControl, road and sources screens, and `fitness/` plus the vitals,
stats and ride map packets on the common side. Core reaches them only through the `voxelvelo-extension` and
`voxelvelo-client-extension` entrypoints that the fitness jar declares.

## Models

Bikes are rendered with [GeckoLib](https://github.com/bernie-g/geckolib). The models are made in Blockbench and
exported as GeckoLib-ready, cuboid-only Bedrock geometry (GeckoLib 5.5.7 cannot draw meshes, so round tires are
48 rotated segments):

- `assets/voxelvelo/geckolib/models/entity/bikes/{race,gravel,mountain}_bike.geo.json` and the matching
  `textures/entity/bikes/*.png` (64x64, one atlas each). The road bike type uses the race bike model.
- `geckolib/animations/entity/bicycle.animation.json`: a one-second pedal loop shared by all bikes. The controller
  speed is the cadence, so it plays faster when you pedal harder and pauses when you coast.
- `models/final/` holds the editable `*.bbmodel` sources of these final models, their `*.json` metadata (saddle top,
  grips, steering pivot and axis, bounds) and the author's notes and validation report. To change a model, re-export
  from Blockbench, copy the files over, and update `BikeRig` if the metadata changed. (`models/` also keeps the
  earlier model drafts.)

Rig the code relies on (`bike` root): `frame_colored` with the tubes `top_tube`, `down_tube`, `seat_tube` and
`rear_stays`; `fork` with children `handlebar` and `front_wheel`; `rear_wheel`; `crank` with children
`left_pedal`/`right_pedal`; and `saddle`. Wheel spin and steering come from the synced physics state in
`BikeGeoRenderer`, not from keyframes, because they depend continuously on speed. The fork rests tilted along the
steering axis, so steering is a plain turn about its local Y added to that rest pose. Only `frame_colored` and the
tubes under it take the frame color (`BikeFrameTintLayer`); everything else keeps its own colors.

`BikeRig` (one per bike type) carries the measurements from the metadata. The seat position, the rider's arm
angles to the grips and the leg swing to the pedals are all derived from it, so a new bike model only needs new
numbers there. The models are drawn at `BikeRig.MODEL_SCALE` (0.9) of their authored size, making the bikes about
1.8 blocks long with 0.95-0.99-block saddle heights and roughly 0.35-block wheel radii. The inventory icons of the
parts are the 16x16 sprites from the same asset set.

## Layout

```text
src/main    common + server: BikeEntity, BikePhysics, BikeSurface, BikeControlState, packets, registries
src/client  client only: keyboard/input composition, packet sender, GeckoLib renderer
core/       the core build: same sources minus the fitness packages, its own module metadata
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
