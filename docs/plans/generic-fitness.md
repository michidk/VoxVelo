# Generic fitness extraction

Device services live in `:fitness-lib` (`voxvelo_fitness_lib`). The `:fitness` module (`voxvelo_fitness`)
integrates those services with `:bikes` (`voxvelo_bikes`). All three are separately published mods.

| Module | Owns | Dependencies |
| --- | --- | --- |
| bikes | Vehicles, physics, recipes, keyboard control | Fabric, GeckoLib |
| fitness-lib | Devices, input snapshots, vehicle registration, resistance, HUD, FIT sessions/settings | Fabric; no bikes or adapter |
| fitness | Bike telemetry, fitness control bridge, roads, multiplayer stats/maps | bikes + fitness-lib |

Public client API: register a VehicleAdapter, read immutable FitnessControls, report immutable VehicleTelemetry, start/stop a session and open fitness settings. Adapters identify a local vehicle, apply controls and provide position/speed/grade/power/cadence plus resistance parameters. Physics remain with the adapter. One adapter owns feedback at a time; no vehicle, pause, disconnect or replacement interrupts distance and neutralizes feedback. Inputs are drained while inactive to prevent deferred shifts.

Each module owns its sources/resources. Packages are `dev.michidk.voxvelo.bikes`,
`dev.michidk.voxvelo.fitnesslib` and `dev.michidk.voxvelo.fitness`. External integrations use
`dev.michidk.voxvelo.fitnesslib.api`. Generic code compiles without bikes on its classpath; jar checks reject
duplicate classes/resources and forbidden cross-module references. A second example adapter and contract
checks exercise selection, deactivation, malformed telemetry and registration conflicts.

Feedback loop: `./gradlew build` (JDK25). Run contract checks and existing ride/device/physics checks, then
packaged Minecraft with Bikes alone, Fitness Library alone and all three jars. IDs, packages, namespaces
and configuration paths are breaking changes with no compatibility aliases or migration. Publish three
artifacts and declare both adapter dependencies.
