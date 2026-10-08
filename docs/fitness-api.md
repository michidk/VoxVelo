# Vehicle integration API (v1)

Voxel Fitness is a Fabric client service for Minecraft 26.3 / Java 25. Its mod ID is `voxel_fitness`; its jar is
`voxel-fitness-<version>.jar`. Depend on the matching version in `fabric.mod.json`. It has no VoxVelo or
GeckoLib dependency. Public integration types live in `dev.michidk.voxelfitness.api`; the retained
`dev.michidk.voxvelo.client.*` service packages are internal, not a vehicle integration API.

## Register a vehicle

From your `ClientModInitializer`, call `Fitness.register(new YourVehicleAdapter())`. Keep the returned
`AutoCloseable` if you need to unregister. Registration and all callbacks run on the client thread. Register
once, not every join. IDs must be namespaced and unique. If multiple adapters match, registration order wins.

Implement `VehicleAdapter`:

- `id()` identifies your integration, for example `my_mod:rowing_boat`.
- `supports(player, vehicle)` must check that the local player drives that vehicle, rather than merely rides as a passenger.
- `applyControls(player, vehicle, controls)` receives one immutable snapshot per tick. Convert watts to your vehicle's propulsion model, and route steering/braking/shifts through your normal server-authoritative controls.
- `sample(player, vehicle)` reports immutable `VehicleTelemetry` for the HUD, recording and resistance model.
- `deactivate()` clears any input you retain when paused, dismounted, disconnected, switched, unregistered, or when a callback fails.

`FitnessControls` contains power (watts), cadence (rpm), heart rate (bpm), steering (-1..1), brake (0..1), a
relative shift delta and optional absolute gear. Missing/stale sensor measurements are `null`; do not interpret
those as fresh zero measurements. Shifts are edges for that tick; consume each snapshot once. Input is drained
while no supported vehicle is active. `Fitness.controls()` returns the selected vehicle's latest snapshot, or
`NONE` when inactive. A power meter takes precedence over trainer watts; a heart-rate sensor takes precedence
over trainer heart rate. Steering is smoothed by the runtime.

`VehicleTelemetry` uses metres/blocks, m/s, watts, rpm and rise/run (`0.1` = 10% grade). It also supplies slope
force scaling, rolling-resistance coefficient, total vehicle+rider mass, optional virtual gear ratio and a
free-form gear label. Ratio `0` disables virtual shifting. This allows a custom drivetrain with any number of
gears. Fitness applies the user's intensity, smoothing and hardware limits before sending FTMS simulation
commands. Never send trainer commands from an adapter. Invalid numbers are sanitized; invalid positions fail
sampling and clear feedback. Vehicle replacement resets smoothing and interrupts recording continuity.

Distance is derived from reported positions tick by tick; off-vehicle gaps, dimension changes and teleports
are excluded. Your mod owns movement and networking; fitness does not move entities or invent vehicle physics.

## Sessions and settings

`Fitness.startSession()`, `stopSession()`, `isRecording()` and `sessionDistanceM()` manage the shared local
session. Stopping through the API exports FIT using the user's settings. The player can also press `R` for the
recording summary/export screen and `F8` for device, trainer and recording settings. Disconnect and game exit
save an unexported ride. There is one local session and one trainer owner, even with multiple vehicle mods.

`Fitness.openSettings(parent)` opens the fitness settings hub. `Fitness.addSettingsPage(labelKey, factory)` adds
a page button to that hub after the built-in Bluetooth, trainer, OpenBikeControl and recording pages; the button's
tooltip is the translation of `labelKey + ".tip"`, and the factory receives the screen to return to. Call it once
from your client initializer. `Fitness.settingsPages()` lists every page in order, so a vehicle mod can offer the
same pages in its own settings menu.

Settings use `config/voxvelo-fitness.json`, and ride exports use `.minecraft/voxvelo/rides/`.
The VoxVelo rename changes these paths and translation keys without migrating older installations.
Legacy road/stats fields remain in the config for the bicycle adapter; generic
fitness does not execute road following or bicycle network packets. Server ride maps and rider stats are
provided by the VoxVelo adapter when its server is installed.

## Working example and verification

[BoatFitness.java](../examples/fitness-boat/src/main/java/dev/michidk/example/BoatFitness.java) is a second
adapter requiring only the public fitness API and Minecraft/Fabric. Build it with
`./gradlew :fitness:fitnessExampleJar`; the optional demonstration jar appears at
`fitness/build/examples/fitness-boat-example.jar`. It is excluded from production/release jars. Its acceleration
is a simple client-prediction demonstration; production vehicles must use their own authoritative packets.

Compile against the fitness sources jar or local dependency (the repository currently publishes release jars,
not a Maven coordinate). A Loom multi-project consumer can use `implementation project(':fitness')` plus
`clientImplementation rootProject.project(':fitness').sourceSets.client.output`. Do not add bikes to the
consumer classpath. `:fitness:compileClientJava` and the boat example compile without bikes; `verifyBuildLayout`
checks all three jars for ownership/duplicates/dependencies. `verifyFitnessApi` checks selection, release,
non-bicycle physics and malformed measurements; existing device/session/FIT checks verify retained behavior.

Release project IDs for generic fitness are `MODRINTH_VOXEL_FITNESS_ID` and `CURSEFORGE_VOXEL_FITNESS_ID`.
The existing `*_FITNESS_ID` variables publish the bicycle adapter. All three jars are always attached to the
GitHub release; platform publishing is conditional on configured project IDs. The adapter is skipped on a platform until
its generic fitness project ID is configured, preventing publication with a missing dependency.
