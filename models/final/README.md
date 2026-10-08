# Editable model sources

This folder contains the current Blockbench sources and export metadata for the three bikes and the helmet.
These are editing sources; the game loads the exported assets under `src/main/resources/assets/voxvelo/`.

| Source | Purpose |
| --- | --- |
| `road_bike.bbmodel` / `road_bike.json` | Road bike geometry and rig measurements |
| `gravel_bike.bbmodel` / `gravel_bike.json` | Gravel bike geometry and rig measurements |
| `mountain_bike.bbmodel` / `mountain_bike.json` | Mountain bike geometry and rig measurements |
| `bike_helmet.bbmodel` / `bike_helmet.json` | Helmet geometry and export metadata |

## Editing and exporting

1. Edit the `.bbmodel` in Blockbench and export cuboid-only Bedrock geometry for GeckoLib.
2. Replace the matching bike export in `src/main/resources/assets/voxvelo/geckolib/models/entity/bikes/`,
   or the helmet export in `src/main/resources/assets/voxvelo/geckolib/models/armor/bike_helmet.geo.json`.
3. After changing a bike, update its matching exports in `geckolib/models/entity/bike_frames/` and
   `geckolib/models/entity/bike_parts/` too. Frames retain empty attachment bones; wheel and handlebar
   component geometry uses a `component` root with the original attachment pivot.
4. Update the matching metadata and `BikeRig` / `BikeType` measurements if attachment positions changed.
5. Run `./gradlew :verifyBikeModels` to check the exported bike geometry and component composition.

Bike coordinates use forward -Z, Y up, and 16 model units per block before `BikeRig.MODEL_SCALE`.
Preserve the `bike` root, `frame_colored` and its tintable tube descendants, `fork` with `handlebar` and
`front_wheel`, `rear_wheel`, `crank` with `left_pedal` and `right_pedal`, and `saddle` bones.
The fork's rest rotation aligns local Y with the steering axis; steering adds local Y rotation to that rest pose.
Wheels and crank rotate around local X. Metadata records saddle, grips, pivots, axes and bounds in export coordinates.
