# VoxVelo Fitness

Turn Minecraft into an indoor cycling ride. Pedal a real smart trainer to power your VoxVelo bike, feel the terrain through trainer resistance, steer with OpenBikeControl, and export your workout as a FIT file.

**Requires matching releases of [VoxVelo Bikes](https://modrinth.com/project/voxvelo-bikes) and [VoxVelo Fitness Library](https://modrinth.com/project/voxvelo-fitness-lib). Install all three mods for bicycle fitness.**

![Cycling with the power, cadence, speed, grade and gear HUD](https://raw.githubusercontent.com/michidk/VoxVelo/main/.github/screenshots/02-riding-hud.png)

## Features

- Smart trainer power drives your bike. An optional power meter can supply power, and a heart rate sensor can track your effort.
- Trainer resistance follows hills and rolling resistance, with adjustable intensity and 12 virtual gears.
- OpenBikeControl steering, braking and shifting through compatible devices such as the [BikeControl phone app](https://bikecontrol.app).
- Hands-free road following with left/right choices at junctions.
- A fitness HUD with power, cadence, speed, grade, gear and heart rate.
- A server-wide rider overlay with configurable power and heart rate sharing.
- Ride recording, workout summaries, FIT export and in-game ride maps.

Keyboard riding remains available when devices are disconnected.

## Installation and device setup

Install Fabric Loader, [Fabric API](https://modrinth.com/mod/fabric-api), [GeckoLib](https://modrinth.com/mod/geckolib), and compatible releases of all three VoxVelo projects. Follow the Minecraft and dependency versions listed for your download.

1. Press `F8` to open Fitness Library settings, open Bluetooth Devices, and scan.
2. Assign your FTMS smart trainer to Trainer. Optionally assign a Cycling Power Service power meter and Heart Rate Service sensor.
3. Mount a VoxVelo bike and pedal. Devices are remembered and reconnect automatically.
4. Press `B` for bicycle settings, including road following and ride recording.

Bluetooth works on Windows, Linux and macOS. For multiplayer rider statistics and ride maps, install all three mods on the server as well.

## Steering and shifting

Connect an [OpenBikeControl](https://github.com/OpenBikeControl/openbikecontrol-protocol) controller over the network or Bluetooth LE. With BikeControl, select **OpenBikeControl Compatible**, target **Other Device**, and keep your phone on the computer's Wi-Fi or LAN for network discovery.

Shift up/down with `X` / `Z`, or use controller mappings. Configure trainer intensity and virtual shifting in Trainer Settings. Road Follow can steer along supported road blocks while you pedal.

![Riding along a generated track with lit borders](https://raw.githubusercontent.com/michidk/VoxVelo/main/.github/screenshots/03-on-the-road.png)

## Record your rides

Press `R` to start or stop recording and `H` to toggle the rider stats overlay. Export your ride as `.FIT` for Strava, Garmin Connect, intervals.icu or TrainingPeaks. Export a completed ride before starting another recording.

![The VoxVelo settings hub with fitness device, HUD and recording options](https://raw.githubusercontent.com/michidk/VoxVelo/main/.github/screenshots/07-settings.png)

Install matching versions of `voxvelo_bikes`, `voxvelo_fitness_lib`, and `voxvelo_fitness`.

[Full setup and troubleshooting](https://github.com/michidk/VoxVelo#fitness-features) · [Source code](https://github.com/michidk/VoxVelo) · [Report an issue](https://github.com/michidk/VoxVelo/issues)

VoxVelo's own code and assets are licensed under the [MIT License](https://github.com/michidk/VoxVelo/blob/main/LICENSE). The Fitness Library's bundled dependencies retain their own [third-party licenses](https://github.com/michidk/VoxVelo#third-party-licences).

Minecraft is a trademark of Mojang AB. VoxVelo is not affiliated with Mojang or Microsoft.
