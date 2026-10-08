# VoxVelo Fitness Library

A reusable, vehicle-independent fitness library for Minecraft Fabric mods. Connect smart trainers, power meters, heart rate sensors and OpenBikeControl controllers, then use a public API to integrate them with your own vehicles.

**For bicycle fitness, install matching releases of [VoxVelo Bikes](https://modrinth.com/project/voxvelo-bikes), this library, and [VoxVelo Fitness](https://modrinth.com/project/voxvelo-fitness).** The library provides the device and recording services; a vehicle integration connects those services to gameplay.

## What the library provides

- Bluetooth Fitness Machine Service (FTMS) connections for smart trainers and indoor bikes.
- Cycling Power Service support for power meters and Heart Rate Service support for sensors.
- Device discovery, remembered connections and automatic reconnection.
- OpenBikeControl input over the network with mDNS discovery or Bluetooth LE, including controller menu navigation.
- Trainer feedback, fitness telemetry, a riding HUD and heart rate graph.
- Sessions, workout recording, ride summaries and FIT export for services such as Strava and Garmin Connect.

Bluetooth support is available on Windows, Linux and macOS. Vehicle feedback and recording samples activate when an adapter supports the local player's vehicle.

## Installation

Install Fabric Loader, [Fabric API](https://modrinth.com/mod/fabric-api), and a library release compatible with your Minecraft version. Install a supported vehicle mod and its fitness integration to use the library while riding. Device services run on the client; the library is safe to install on a dedicated server.

Open Fitness Library settings with `F8`, scan for Bluetooth devices, and choose the trainer, power and heart rate roles. Devices are remembered for future sessions.

Older library releases may use the name `voxel-fitness` and mod ID `voxel_fitness`. Use the files and dependencies specified by your chosen release.

## For mod developers

The library's public API lets a vehicle adapter consume fitness input and supply vehicle feedback and recording samples. A vehicle integration can use the library without depending on VoxVelo Bikes or GeckoLib.

Start with the [vehicle integration API documentation](https://github.com/michidk/VoxVelo/blob/main/docs/fitness-api.md) and the [boat integration example](https://github.com/michidk/VoxVelo/blob/main/examples/fitness-boat/src/main/java/dev/michidk/example/BoatFitness.java).

## Integration example

The screenshot below shows the library's fitness HUD used with VoxVelo Bikes and VoxVelo Fitness. Bicycles and bicycle-specific gameplay come from those projects.

![Fitness HUD showing power, cadence, speed, grade and gear in the VoxVelo bicycle integration](https://raw.githubusercontent.com/michidk/VoxVelo/main/.github/screenshots/02-riding-hud.png)

[Documentation](https://github.com/michidk/VoxVelo#readme) · [Source code](https://github.com/michidk/VoxVelo) · [Report an issue](https://github.com/michidk/VoxVelo/issues)

VoxVelo's own code and assets are licensed under the [MIT License](https://github.com/michidk/VoxVelo/blob/main/LICENSE). Bundled third-party code retains its permissive MIT, Apache-2.0 or BSD-3-Clause licenses; the full notices ship in the library jar under `META-INF/licenses/`. See [third-party licenses](https://github.com/michidk/VoxVelo#third-party-licences).

Minecraft is a trademark of Mojang AB. VoxVelo is not affiliated with Mojang or Microsoft.
