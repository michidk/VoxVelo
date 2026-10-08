# Generic fitness extraction

The current add-on owns device services but reads bicycle entities throughout feedback, HUD and recording. Extract those services into `:fitness` (`voxel_fitness`), retaining the existing `voxvelo_fitness` ID for the bicycle adapter.

| Module | Owns | Dependencies |
| --- | --- | --- |
| bikes | Vehicles, physics, recipes, keyboard control | Fabric, GeckoLib |
| fitness | Devices, input snapshots, vehicle registration, resistance, HUD, FIT sessions/settings | Fabric; no bikes or adapter |
| bicycle adapter | Bike telemetry, fitness control bridge, roads, multiplayer stats/maps | bikes + fitness |

Public client API: register a VehicleAdapter, read immutable FitnessControls, report immutable VehicleTelemetry, start/stop a session and open fitness settings. Adapters identify a local vehicle, apply controls and provide position/speed/grade/power/cadence plus resistance parameters. Physics remain with the adapter. One adapter owns feedback at a time; no vehicle, pause, disconnect or replacement interrupts distance and neutralizes feedback. Inputs are drained while inactive to prevent deferred shifts.

Files move unchanged where possible into fitness/src/client/java. Existing internal packages are retained; external integrations use dev.michidk.voxelfitness.api. Generic code compiles without bikes on its classpath; jar checks reject duplicate classes/resources. A second example adapter and contract checks exercise selection, deactivation, malformed telemetry and registration conflicts.

Feedback loop: `./gradlew :build :bikes:build :fitness:build` (JDK25). Run contract checks and existing ride/device/physics checks, then packaged Minecraft with bikes alone, generic fitness alone and all three jars. Preserve existing config path and translation keys during migration. Publish three artifacts and declare both adapter dependencies.
